(function () {
    'use strict';
    var revision = -1;
    var previous;
    var busy = false;
    var installedManager;
    var fittedDistance;
    var fittedCenter;
    var targetHeight = 0;
    var heightControls;
    var requestedZoom = Number(new URLSearchParams(window.location.search).get('aui-zoom') || 1);
    var cameraRequest;
    window.addEventListener('message', function (event) {
        if (event.source === window.parent && event.data.type === 'aui-camera') cameraRequest = event.data;
    });
    function report(viewer) {
        if (!window.auiPreviewState) return;
        window.auiPreviewState.cameraDistance = viewer.controlsManager.distance;
        window.auiPreviewState.rotation = viewer.controlsManager.rotation;
        if (window.parent !== window) window.parent.postMessage({ type: 'aui-preview-state', state: window.auiPreviewState }, '*');
    }

    function fit(viewer, meshes) {
        var minimum = [Infinity, Infinity, Infinity];
        var maximum = [-Infinity, -Infinity, -Infinity];
        meshes.forEach(function (mesh) {
            mesh.geometry.computeBoundingBox();
            var box = mesh.geometry.boundingBox;
            ['x', 'y', 'z'].forEach(function (axis, index) {
                minimum[index] = Math.min(minimum[index], box.min[axis] * mesh.scale[axis] + mesh.position[axis]);
                maximum[index] = Math.max(maximum[index], box.max[axis] * mesh.scale[axis] + mesh.position[axis]);
            });
        });
        var radius = Math.hypot(maximum[0] - minimum[0], maximum[1] - minimum[1], maximum[2] - minimum[2]) / 2;
        var vertical = 75 * Math.PI / 360;
        var aspect = viewer.rootElement.clientWidth / viewer.rootElement.clientHeight;
        var halfAngle = Math.min(vertical, Math.atan(Math.tan(vertical) * aspect));
        return { x: (minimum[0] + maximum[0]) / 2, y: (minimum[1] + maximum[1]) / 2,
            z: (minimum[2] + maximum[2]) / 2, distance: radius / Math.sin(halfAngle) * 1.08 };
    }

    async function update() {
        var app = window.bluemap;
        var viewer = app && app.mapViewer;
        var map = viewer && viewer.map;
        if (busy || !map || !map.isLoaded || !map.hiresTileManager) return;
        if (cameraRequest && previous) {
            var controls = viewer.controlsManager;
            var request = cameraRequest; cameraRequest = undefined;
            if (request.reset) {
                var framing = fit(viewer, map.hiresTileManager.scene.children);
                controls.position.set(framing.x, framing.y, framing.z);
                controls.distance = framing.distance;
                controls.rotation = request.yaw; controls.angle = request.angle;
                fittedDistance = framing.distance;
            }
            if (request.rotationDelta) controls.rotation += request.rotationDelta;
            if (request.zoomFactor) controls.distance = Math.min(999, Math.max(5, controls.distance * request.zoomFactor));
            report(viewer);
        }
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
                scale: { x: state.renderScale, z: state.renderScale },
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
                    mesh.scale.y = state.heightScale;
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
            var framing = fit(viewer, meshes);
            framing.distance /= requestedZoom;
            targetHeight = framing.y;
            var mapHeight = controls.controls && controls.controls.mapHeight;
            if (mapHeight) controls.controls.maxDistance = 999;
            if (mapHeight && mapHeight !== heightControls) {
                mapHeight.update = function () {
                    this.manager.position.y = targetHeight;
                    this.manager.distance = Math.min(999, this.manager.distance);
                };
                heightControls = mapHeight;
            }
            if (!previous || previous.x !== state.x || previous.z !== state.z || previous.span !== state.span) {
                var zoomRatio = previous ? controls.distance / fittedDistance : 1;
                controls.position.set(framing.x, framing.y, framing.z);
                controls.distance = Math.min(999, framing.distance * zoomRatio);
            } else {
                if (Math.abs(controls.distance - fittedDistance) < 1 && fittedCenter
                        && Math.abs(controls.position.x - fittedCenter.x) < 0.01
                        && Math.abs(controls.position.z - fittedCenter.z) < 0.01) {
                    controls.position.set(framing.x, framing.y, framing.z);
                    controls.distance = framing.distance;
                }
            }
            fittedDistance = framing.distance;
            fittedCenter = framing;
            revision = state.revision;
            previous = state;
            window.auiPreviewRevision = revision;
            window.auiPreviewState = { revision: revision, scale: state.scale, span: state.span,
                meshCount: meshes.length, unit: state.unit, fittedDistance: framing.distance,
                centerY: framing.y,
                installedAt: performance.now() };
            report(viewer);
        } catch (error) {
            console.error('AUI live terrain update failed', error);
        } finally {
            if (!installed) meshes.forEach(function (mesh) { if (mesh) mesh.geometry.dispose(); });
            busy = false;
        }
    }
    setInterval(update, 50);
})();
