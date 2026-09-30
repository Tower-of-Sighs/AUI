(function () {
    'use strict';
    var revision = -1;
    var previous;
    var busy = false;
    var installedManager;

    async function update() {
        var app = window.bluemap;
        var viewer = app && app.mapViewer;
        var map = viewer && viewer.map;
        if (busy || !map || !map.isLoaded || !map.hiresTileManager) return;
        busy = true;
        var meshes = [];
        var installed = false;
        try {
            var response = await fetch('/preview-state.json', { cache: 'no-store' });
            if (!response.ok) return;
            var state = await response.json();
            if (state.revision === revision) return;
            var manager = map.hiresTileManager;
            if (manager !== installedManager) {
                manager.loadCloseTiles = function () {};
                manager.loadAroundTile = function () {};
                installedManager = manager;
            }
            var settings = {
                tileSize: { x: state.tileSize, z: state.tileSize },
                scale: { x: state.scale, z: state.scale },
                translate: { x: state.translate, z: state.translate }
            };
            var loader = new window.BlueMap.TileLoader(state.tileRoot, map.hiresMaterial, settings,
                function () { return Promise.resolve(); }, new Set(), false);
            meshes = new Array(state.tiles.length);
            var next = 0;
            async function load() {
                while (next < state.tiles.length) {
                    var index = next++;
                    var point = state.tiles[index];
                    var mesh = await loader.load(point[0], point[1], function () { return false; }, true);
                    mesh.scale.y = state.scale;
                    mesh.updateMatrixWorld(true);
                    meshes[index] = mesh;
                }
            }
            var loads = await Promise.allSettled(Array.from({ length: Math.min(12, state.tiles.length) }, load));
            var failed = loads.find(function (result) { return result.status === 'rejected'; });
            if (failed) throw failed.reason;
            var replacement = manager.scene.clone(false);
            meshes.forEach(function (mesh) { replacement.add(mesh); });
            manager.removeAllTiles();
            manager.sceneParent.remove(manager.scene);
            manager.scene = replacement;
            manager.sceneParent.add(replacement);
            installed = true;
            map.data.hires = settings;
            viewer.data.uniforms.hiresTileMap.value.scale.set(state.tileSize, state.tileSize);
            manager.tileLoader = loader;
            manager.centerTile.set(Math.floor(state.x / state.tileSize), Math.floor(state.z / state.tileSize));
            manager.unloaded = false;
            state.tiles.forEach(function (point, index) {
                var tile = new window.BlueMap.Tile(point[0], point[1], manager.handleLoadedTile, manager.handleUnloadedTile);
                tile.model = meshes[index];
                tile.unloaded = false;
                manager.tiles.set(window.BlueMap.hashTile(point[0], point[1]), tile);
                manager.tileMap.setTile(point[0] - manager.centerTile.x + 50,
                    point[1] - manager.centerTile.y + 50, window.BlueMap.TileMap.LOADED);
            });
            var controls = viewer.controlsManager;
            if (!previous || previous.x !== state.x || previous.z !== state.z || previous.span !== state.span) {
                controls.position.set(state.x, state.y, state.z);
                controls.distance = state.distance;
            } else {
                controls.position.y += state.y - previous.y;
                if (Math.abs(controls.distance - previous.distance) < 1) controls.distance = state.distance;
            }
            revision = state.revision;
            previous = state;
            window.auiPreviewRevision = revision;
            window.auiPreviewState = { revision: revision, scale: state.scale, span: state.span,
                meshCount: meshes.length, installedAt: performance.now() };
        } catch (error) {
            console.error('AUI live terrain update failed', error);
        } finally {
            if (!installed) meshes.forEach(function (mesh) { if (mesh) mesh.geometry.dispose(); });
            busy = false;
        }
    }
    setInterval(update, 50);
})();
