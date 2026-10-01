(function () {
    'use strict';
    var previous, revision = -1, detailRevision = -1;
    var busy = false, viewerHook, macroMaterials, detailGroup, detailMeshes = [];
    var baseMeshes = [], baseHeight, detailHeight, targetHeight = 0, fittedDistance = 1;
    var clip = { value: false }, clipRect = { value: [0, 0, 0, 0] };
    var cameraRequest, cameraScheduled = false, lastView = '', lastViewAt = 0;
    window.addEventListener('message', function (event) {
        if (event.source !== window.parent || event.data.type !== 'aui-camera') return;
        var incoming = event.data;
        if (!cameraRequest || incoming.reset) cameraRequest = Object.assign({}, incoming);
        else {
            cameraRequest.rotationDelta = (cameraRequest.rotationDelta || 0) + (incoming.rotationDelta || 0);
            cameraRequest.zoomFactor = (cameraRequest.zoomFactor || 1) * (incoming.zoomFactor || 1);
        }
        if (!cameraScheduled) {
            cameraScheduled = true;
            requestAnimationFrame(function () {
                cameraScheduled = false;
                var app = window.bluemap;
                if (app && app.mapViewer) applyCamera(app.mapViewer);
            });
        }
    });

    function applyCamera(viewer) {
        if (!cameraRequest || !previous) return;
        var request = cameraRequest; cameraRequest = undefined;
        var controls = viewer.controlsManager;
        if (request.reset) {
            var framing = fit(viewer, baseMeshes, previous);
            controls.position.set(framing.x, framing.y, framing.z); targetHeight = framing.y;
            controls.distance = framing.distance; fittedDistance = framing.distance;
            controls.rotation = request.yaw; controls.angle = request.angle;
        }
        if (request.rotationDelta) controls.rotation += request.rotationDelta;
        if (request.zoomFactor) controls.distance = Math.min(999, Math.max(2 / previous.unit, controls.distance * request.zoomFactor));
        viewer.redraw();
    }

    function heightAt(frame, x, z) {
        if (!frame || !frame.heights) return undefined;
        var data = frame.data;
        var gx = (x - data.minX) / data.scale, gz = (z - data.minZ) / data.scale;
        if (gx < 0 || gz < 0 || gx >= data.width || gz >= data.depth) return undefined;
        var ix = Math.floor(gx), iz = Math.floor(gz);
        return (frame.heights[iz * data.width + ix] + 1) * data.verticalScale;
    }

    function fit(viewer, meshes, state) {
        var low = [Infinity, state.surfaceMinY, Infinity];
        var high = [-Infinity, state.surfaceMaxY, -Infinity];
        meshes.forEach(function (mesh) {
            mesh.geometry.computeBoundingBox();
            var box = mesh.geometry.boundingBox;
            [0, 2].forEach(function (index) {
                var axis = index === 0 ? 'x' : 'z';
                low[index] = Math.min(low[index], box.min[axis] * mesh.scale[axis] + mesh.position[axis]);
                high[index] = Math.max(high[index], box.max[axis] * mesh.scale[axis] + mesh.position[axis]);
            });
        });
        var radius = Math.hypot(high[0] - low[0], high[1] - low[1], high[2] - low[2]) / 2;
        var halfAngle = Math.min(75 * Math.PI / 360,
            Math.atan(Math.tan(75 * Math.PI / 360) * viewer.rootElement.clientWidth / viewer.rootElement.clientHeight));
        return { x: (low[0] + high[0]) / 2, y: (low[1] + high[1]) / 2,
            z: (low[2] + high[2]) / 2, distance: radius / Math.sin(halfAngle) * 1.08 };
    }

    function dispose(meshes) { meshes.forEach(function (mesh) { mesh.geometry.dispose(); }); }

    async function loadFrame(map, data, materials) {
        var settings = { tileSize: { x: data.tileSize, z: data.tileSize },
            scale: { x: data.renderScale, z: data.renderScale }, translate: { x: data.translate, z: data.translate } };
        var loader = new window.BlueMap.TileLoader(data.tileRoot, materials, settings,
            function () { return Promise.resolve(); }, new Set(), false);
        var meshes = new Array(data.tiles.length), next = 0;
        var heights = fetch(data.heightRoot, { cache: 'no-store' }).then(function (response) {
            if (!response.ok) throw new Error('Height map HTTP ' + response.status);
            return response.json();
        });
        async function worker() {
            while (next < data.tiles.length) {
                var index = next++, point = data.tiles[index];
                var mesh = await loader.load(point[0], point[1], function () { return false; }, true);
                mesh.scale.y = data.heightScale; mesh.updateMatrixWorld(true); meshes[index] = mesh;
            }
        }
        var jobs = await Promise.allSettled(Array.from({ length: Math.min(12, data.tiles.length) }, worker));
        var failed = jobs.find(function (result) { return result.status === 'rejected'; });
        if (failed) { dispose(meshes.filter(Boolean)); throw failed.reason; }
        return { meshes: meshes, loader: loader, settings: settings, height: { data: data, heights: await heights } };
    }

    function installHooks(viewer, map) {
        if (viewerHook === viewer) return;
        viewerHook = viewer;
        var sourceRender = viewer.render;
        viewer.render = function (delta) {
            if (previous && detailHeight) {
                var data = detailHeight.data, unit = previous.unit;
                var shiftX = Math.round(viewer.camera.position.x / 10000) * 10000;
                var shiftZ = Math.round(viewer.camera.position.z / 10000) * 10000;
                clipRect.value = [(data.minX - data.scale * 0.5) / unit - shiftX,
                    (data.minZ - data.scale * 0.5) / unit - shiftZ,
                    (data.minX + (data.width - 0.5) * data.scale) / unit - shiftX,
                    (data.minZ + (data.depth - 0.5) * data.scale) / unit - shiftZ];
            }
            sourceRender.call(viewer, delta);
        };
        var renderScene = viewer.renderer.render;
        viewer.renderer.render = function (scene, camera) {
            if (previous) viewer.data.uniforms.distance.value = viewer.controlsManager.distance * previous.unit;
            return renderScene.call(viewer.renderer, scene, camera);
        };
        macroMaterials = map.hiresMaterial.map(function (source) {
            var material = source.clone();
            material.uniforms = Object.assign({}, source.uniforms, { auiDetail: clip, auiDetailRect: clipRect });
            material.fragmentShader = 'uniform bool auiDetail;\nuniform vec4 auiDetailRect;\n' +
                source.fragmentShader.replace('void main() {', 'void main() {\n' +
                    'if (auiDetail && vWorldPosition.x >= auiDetailRect.x && vWorldPosition.z >= auiDetailRect.y ' +
                    '&& vWorldPosition.x < auiDetailRect.z && vWorldPosition.z < auiDetailRect.w) discard;');
            return material;
        });
        map.hiresTileManager.loadCloseTiles = function () {};
        map.hiresTileManager.loadAroundTile = function () {};
        var controls = viewer.controlsManager.controls;
        controls.maxDistance = 999;
        controls.mapHeight.update = function () {
            if (!previous) return;
            var x = this.manager.position.x * previous.unit, z = this.manager.position.z * previous.unit;
            var height = heightAt(detailHeight, x, z);
            if (height === undefined) height = heightAt(baseHeight, x, z);
            this.manager.position.y = height === undefined ? targetHeight : height / previous.unit;
        };
    }

    function report(viewer) {
        if (!previous) return;
        var controls = viewer.controlsManager;
        var x = controls.position.x * previous.unit, z = controls.position.z * previous.unit;
        var aspect = viewer.rootElement.clientWidth / viewer.rootElement.clientHeight;
        var span = controls.distance * previous.unit * 2 * Math.tan(75 * Math.PI / 360) * Math.max(1, aspect);
        var detailScale = heightAt(detailHeight, x, z) === undefined ? undefined : detailHeight.data.scale;
        window.auiPreviewState = { revision: revision, sceneKey: previous.sceneKey, scale: previous.scale,
            span: previous.span, unit: previous.unit, meshCount: baseMeshes.length + detailMeshes.length,
            detailScale: detailScale, detailRevision: detailRevision, cameraDistance: controls.distance,
            fittedDistance: fittedDistance, rotation: controls.rotation, centerY: controls.position.y,
            canvas: window.auiCanvasState || null, installedAt: performance.now() };
        window.parent.postMessage({ type: 'aui-preview-state', state: window.auiPreviewState }, '*');
        var now = performance.now();
        if (now - lastViewAt >= 150) {
            var view = { sceneKey: previous.sceneKey, x: x, z: z, span: span };
            var key = [view.sceneKey, Math.floor(x / 16), Math.floor(z / 16), Math.ceil(span / 32)].join(':');
            if (key !== lastView) {
                lastView = key;
                window.parent.postMessage({ type: 'aui-view', view: view }, '*');
            }
            lastViewAt = now;
        }
    }

    function clearDetail() {
        if (detailGroup && detailGroup.parent) detailGroup.parent.remove(detailGroup);
        dispose(detailMeshes); detailMeshes = []; detailHeight = null; detailGroup = null;
        detailRevision = -1; clip.value = false;
    }

    async function update() {
        var app = window.bluemap, viewer = app && app.mapViewer, map = viewer && viewer.map;
        if (!map || !map.isLoaded || !map.hiresTileManager) return;
        installHooks(viewer, map);
        applyCamera(viewer);
        report(viewer);
        if (busy) return;
        busy = true;
        try {
            var response = await fetch('/preview-state.json', { cache: 'no-store' });
            if (!response.ok) return;
            var state = await response.json();
            var manager = map.hiresTileManager;
            if (state.revision !== revision) {
                var loaded = await loadFrame(map, state, macroMaterials);
                var oldUnit = previous ? previous.unit : state.unit;
                if (previous && previous.sceneKey !== state.sceneKey) clearDetail();
                var replacement = manager.scene.clone(false); replacement.position.set(0, 0, 0);
                if (detailGroup) {
                    detailGroup.parent.remove(detailGroup);
                    detailMeshes.forEach(function (mesh) {
                        mesh.position.multiplyScalar(oldUnit / state.unit);
                        mesh.scale.multiplyScalar(oldUnit / state.unit); mesh.updateMatrixWorld(true);
                    });
                    replacement.add(detailGroup);
                }
                manager.removeAllTiles(); manager.sceneParent.remove(manager.scene);
                loaded.meshes.forEach(function (mesh) { replacement.add(mesh); });
                manager.scene = replacement; manager.sceneParent.add(replacement);
                baseMeshes = loaded.meshes; baseHeight = loaded.height;
                viewer.redraw();
                map.data.hires = loaded.settings;
                viewer.data.uniforms.hiresTileMap.value.scale.set(state.tileSize, state.tileSize);
                manager.tileLoader = loaded.loader;
                manager.centerTile.set(Math.floor(state.x / state.tileSize), Math.floor(state.z / state.tileSize));
                manager.unloaded = false;
                state.tiles.forEach(function (point, index) {
                    var tile = new window.BlueMap.Tile(point[0], point[1], manager.handleLoadedTile, manager.handleUnloadedTile);
                    tile.model = loaded.meshes[index]; tile.unloaded = false;
                    manager.tiles.set(window.BlueMap.hashTile(point[0], point[1]), tile);
                    manager.tileMap.setTile(point[0] - manager.centerTile.x + 50, point[1] - manager.centerTile.y + 50, window.BlueMap.TileMap.LOADED);
                });
                var controls = viewer.controlsManager;
                var framing = fit(viewer, loaded.meshes, state);
                var changedArea = !previous || Math.abs(previous.span - state.span) > state.span * 0.01 ||
                    Math.abs(previous.x * oldUnit - state.x * state.unit) > state.span * 0.1 ||
                    Math.abs(previous.z * oldUnit - state.z * state.unit) > state.span * 0.1;
                if (changedArea) {
                    var zoomRatio = previous ? controls.distance / fittedDistance : 1;
                    controls.position.set(framing.x, framing.y, framing.z);
                    controls.distance = Math.min(999, framing.distance * zoomRatio);
                    targetHeight = framing.y;
                } else {
                    controls.position.multiplyScalar(oldUnit / state.unit);
                    controls.distance *= oldUnit / state.unit;
                }
                controls.controls.minDistance = 1 / state.unit;
                viewer.camera.near = 0.05 / state.unit; viewer.camera.updateProjectionMatrix();
                fittedDistance = framing.distance;
                revision = state.revision; previous = state;
            }
            if (!state.detail) clearDetail();
            else if (state.detail.revision !== detailRevision) {
                var detail = await loadFrame(map, state.detail, map.hiresMaterial);
                clearDetail();
                detailGroup = manager.scene.clone(false); detailGroup.position.set(0, 0, 0);
                detail.meshes.forEach(function (mesh) { detailGroup.add(mesh); });
                manager.scene.add(detailGroup);
                detailMeshes = detail.meshes; detailHeight = detail.height;
                detailRevision = state.detail.revision; clip.value = true;
                viewer.redraw();
            }
            report(viewer);
        } catch (error) {
            console.error('AUI terrain frame failed', error);
        } finally { busy = false; }
    }
    setInterval(update, 50);
    window.addEventListener('beforeunload', function () {
        if (macroMaterials) macroMaterials.forEach(function (material) { material.dispose(); });
    });
})();
