package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.RenderHandle;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Collects texture quads until AUI reaches a real render-state boundary.
 *
 * <p>Depth-tested draws can be reordered by render handle because the depth
 * buffer determines visibility. Overlay draws keep their original order: an
 * alpha-composited overlay must not be moved across another texture.</p>
 *
 * <p>Draw/Batch objects are pooled: JFR 采样显示每个 quad 的 {@code new Matrix4f}
 * 拷贝加每次 flush 重建的批次容器，在动画场景下累计数百 MB 分配。渲染仅在
 * 渲染线程发生，池无需同步；flush 不可重入（emitTextureQuad 不会再调用 add）。</p>
 */
final class TextureRenderQueue {
    private static final int MAX_QUEUED_QUADS = 8192;

    private final List<Draw> draws = new ArrayList<>();
    private final ArrayDeque<Draw> drawPool = new ArrayDeque<>();
    private final ArrayDeque<Batch> batchPool = new ArrayDeque<>();
    private final List<Batch> batchesScratch = new ArrayList<>();
    private final IdentityHashMap<RenderHandle, Batch> batchByHandle = new IdentityHashMap<>();

    void add(RenderHandle renderHandle, boolean depthTest, Matrix4f matrix,
             float x, float y, float width, float height,
             float u0, float v0, float u1, float v1) {
        add(renderHandle, depthTest, matrix, x, y, width, height, u0, v0, u1, v1, 0xFFFFFFFF);
    }

    void add(RenderHandle renderHandle, boolean depthTest, Matrix4f matrix,
             float x, float y, float width, float height,
             float u0, float v0, float u1, float v1, int tintArgb) {
        if (draws.size() >= MAX_QUEUED_QUADS) flush();
        Draw draw = drawPool.pollFirst();
        if (draw == null) draw = new Draw();
        draw.set(renderHandle, depthTest, matrix, x, y, width, height, u0, v0, u1, v1, tintArgb);
        draws.add(draw);
    }

    void flush() {
        if (draws.isEmpty()) return;

        try {
            int segmentStart = 0;
            while (segmentStart < draws.size()) {
                boolean depthTest = draws.get(segmentStart).depthTest;
                int segmentEnd = segmentStart + 1;
                while (segmentEnd < draws.size()
                        && draws.get(segmentEnd).depthTest == depthTest) {
                    segmentEnd++;
                }

                if (depthTest) {
                    flushDepthTestedSegment(segmentStart, segmentEnd);
                } else {
                    flushOverlaySegment(segmentStart, segmentEnd);
                }
                segmentStart = segmentEnd;
            }
        } finally {
            for (int i = 0; i < draws.size(); i++) drawPool.offerFirst(draws.get(i));
            draws.clear();
        }
    }

    private void flushDepthTestedSegment(int start, int end) {
        batchesScratch.clear();
        batchByHandle.clear();
        for (int i = start; i < end; i++) {
            Draw draw = draws.get(i);
            Batch batch = batchByHandle.get(draw.renderHandle);
            if (batch == null) {
                batch = obtainBatch(draw.renderHandle);
                batchByHandle.put(draw.renderHandle, batch);
                batchesScratch.add(batch);
            }
            batch.draws.add(draw);
        }
        flushBatches();
    }

    private void flushOverlaySegment(int start, int end) {
        batchesScratch.clear();
        Batch previous = null;
        for (int i = start; i < end; i++) {
            Draw draw = draws.get(i);
            if (previous == null || previous.renderHandle != draw.renderHandle) {
                previous = obtainBatch(draw.renderHandle);
                batchesScratch.add(previous);
            }
            previous.draws.add(draw);
        }
        flushBatches();
    }

    private Batch obtainBatch(RenderHandle renderHandle) {
        Batch batch = batchPool.pollFirst();
        if (batch == null) batch = new Batch();
        batch.renderHandle = renderHandle;
        batch.draws.clear();
        return batch;
    }

    private void flushBatches() {
        for (int i = 0; i < batchesScratch.size(); i++) {
            Batch batch = batchesScratch.get(i);
            Object token = AuiServices.render().beginTextureBatch(batch.renderHandle);
            List<Draw> batchDraws = batch.draws;
            for (int j = 0; j < batchDraws.size(); j++) {
                Draw draw = batchDraws.get(j);
                AuiServices.render().emitTextureQuad(token, draw.matrix,
                        draw.x, draw.y, draw.width, draw.height,
                        draw.u0, draw.v0, draw.u1, draw.v1, draw.tintArgb);
            }
            AuiServices.render().flushTextureBatch(token, batch.renderHandle);
            RenderBatchStats.recordImageFlush();
            batch.renderHandle = null;
            batchPool.offerFirst(batch);
        }
        batchesScratch.clear();
    }

    private static final class Draw {
        RenderHandle renderHandle;
        boolean depthTest;
        final Matrix4f matrix = new Matrix4f();
        float x, y, width, height, u0, v0, u1, v1;
        int tintArgb;

        void set(RenderHandle renderHandle, boolean depthTest, Matrix4f source,
                 float x, float y, float width, float height,
                 float u0, float v0, float u1, float v1, int tintArgb) {
            this.renderHandle = renderHandle;
            this.depthTest = depthTest;
            this.matrix.set(source);
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.tintArgb = tintArgb;
        }
    }

    private static final class Batch {
        RenderHandle renderHandle;
        final List<Draw> draws = new ArrayList<>();
    }
}
