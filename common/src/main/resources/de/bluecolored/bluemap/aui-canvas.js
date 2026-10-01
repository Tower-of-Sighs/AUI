(function () {
    'use strict';
    if (!window.chrome || !window.chrome.webview) return;
    var host = window.chrome.webview;
    var viewer;
    var gl;
    var slots = [null, null];
    var jobs = [];
    var generation = 0;
    var width = 0, height = 0;
    var enabled = true;
    var originalRender;
    var frames = 0;
    var lastMs = 0;
    var renderCalls = 0, gpuWaits = 0, slotWaits = 0, queuedFrames = 0;
    var publishedSequence = 0;

    function release() {
        jobs.forEach(function (job) {
            if (job.fence) gl.deleteSync(job.fence);
            gl.deleteBuffer(job.buffer);
        });
        jobs = [];
        slots.forEach(function (slot) { if (slot) host.releaseBuffer(slot.buffer); });
        slots = [null, null];
    }

    host.addEventListener('sharedbufferreceived', function (event) {
        var info = event.additionalData;
        if (!info || info.type !== 'aui-canvas') return;
        if (info.width !== width || info.height !== height) { host.releaseBuffer(event.getBuffer()); return; }
        if (info.generation !== generation) {
            if (gl) release();
            generation = info.generation;
        }
        var buffer = event.getBuffer();
        slots[info.slot] = { buffer: buffer, bytes: new Uint8Array(buffer), busy: false };
        if (slots[0] && slots[1]) {
            for (var i = 0; i < 2; i++) {
                var pbo = gl.createBuffer();
                gl.bindBuffer(gl.PIXEL_PACK_BUFFER, pbo);
                gl.bufferData(gl.PIXEL_PACK_BUFFER, width * height * 4, gl.STREAM_READ);
                jobs.push({ buffer: pbo, fence: null });
            }
            gl.bindBuffer(gl.PIXEL_PACK_BUFFER, null);
            viewer.renderer.domElement.style.opacity = '0';
            window.parent.postMessage({ type: 'aui-canvas-active' }, '*');
        }
    });

    host.addEventListener('message', function (event) {
        if (typeof event.data !== 'string') return;
        var parts = event.data.split('|');
        if (parts[0] === 'aui-canvas-ack' && Number(parts[1]) === generation) {
            var slot = slots[Number(parts[2])];
            if (slot) slot.busy = false;
        } else if (parts[0] === 'aui-canvas-run') enabled = parts[1] === '1';
    });

    function capture() {
        var w = gl.drawingBufferWidth, h = gl.drawingBufferHeight;
        if (w !== width || h !== height) {
            release();
            width = w; height = h;
            viewer.renderer.domElement.style.opacity = '';
            host.postMessage('aui-canvas-init|' + width + '|' + height);
            return;
        }
        if (!slots[0] || !slots[1] || !jobs.length) return;
        jobs.slice().sort(function (a, b) { return a.sequence - b.sequence; }).forEach(function (job) {
            if (!job.fence) return;
            var result = gl.clientWaitSync(job.fence, 0, 0);
            if (result !== gl.ALREADY_SIGNALED && result !== gl.CONDITION_SATISFIED) { gpuWaits++; return; }
            if (job.sequence <= publishedSequence) { gl.deleteSync(job.fence); job.fence = null; return; }
            var index = slots.findIndex(function (slot) { return slot && !slot.busy; });
            if (index < 0) { slotWaits++; return; }
            var started = performance.now();
            gl.bindBuffer(gl.PIXEL_PACK_BUFFER, job.buffer);
            gl.getBufferSubData(gl.PIXEL_PACK_BUFFER, 0, slots[index].bytes);
            gl.bindBuffer(gl.PIXEL_PACK_BUFFER, null);
            gl.deleteSync(job.fence); job.fence = null;
            slots[index].busy = true;
            publishedSequence = job.sequence;
            host.postMessage('aui-canvas-frame|' + generation + '|' + index);
            lastMs = performance.now() - started;
            frames++;
        });
        var free = jobs.find(function (job) { return !job.fence; });
        if (free) {
            gl.bindBuffer(gl.PIXEL_PACK_BUFFER, free.buffer);
            gl.readPixels(0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, 0);
            free.fence = gl.fenceSync(gl.SYNC_GPU_COMMANDS_COMPLETE, 0);
            gl.bindBuffer(gl.PIXEL_PACK_BUFFER, null);
            gl.flush();
            queuedFrames++;
            free.sequence = queuedFrames;
        }
        window.auiCanvasState = { frames: frames, renderCalls: renderCalls, gpuWaits: gpuWaits,
            slotWaits: slotWaits, queuedFrames: queuedFrames, readbackMs: lastMs,
            width: width, height: height, generation: generation };
    }

    var ready = setInterval(function () {
        var app = window.bluemap;
        if (!app || !app.mapViewer || !app.mapViewer.renderer) return;
        viewer = app.mapViewer;
        gl = viewer.renderer.getContext();
        viewer.renderer.setPixelRatio(window.devicePixelRatio);
        originalRender = viewer.render;
        viewer.render = function (delta) {
            if (!enabled) return;
            renderCalls++;
            originalRender.call(viewer, delta);
            capture();
        };
        clearInterval(ready);
    }, 50);
    window.addEventListener('beforeunload', function () { clearInterval(ready); if (gl) release(); });
})();
