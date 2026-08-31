package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.RenderHandle;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects texture quads until AUI reaches a real render-state boundary.
 *
 * <p>Texture draws preserve submission order across render-handle changes.
 * Consecutive draws using the same handle are still emitted as one batch.</p>
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

    void add(RenderHandle renderHandle, boolean depthTest, Matrix4f matrix,
             float x, float y, float width, float height,
             float u0, float v0, float u1, float v1) {
        add(renderHandle, depthTest, false, matrix, x, y, width, height, u0, v0, u1, v1, 0xFFFFFFFF);
    }

    void add(RenderHandle renderHandle, boolean depthTest, Matrix4f matrix,
             float x, float y, float width, float height,
             float u0, float v0, float u1, float v1, int tintArgb) {
        add(renderHandle, depthTest, false, matrix, x, y, width, height, u0, v0, u1, v1, tintArgb);
    }

    void add(RenderHandle renderHandle, boolean depthTest, boolean projective, Matrix4f matrix,
             float x, float y, float width, float height,
             float u0, float v0, float u1, float v1, int tintArgb) {
        if (draws.size() >= MAX_QUEUED_QUADS) flush();
        Draw draw = drawPool.pollFirst();
        if (draw == null) draw = new Draw();
        draw.set(renderHandle, depthTest, projective, matrix, x, y, width, height, u0, v0, u1, v1, tintArgb);
        draws.add(draw);
    }

    void flush() {
        if (draws.isEmpty()) return;

        try {
            int segmentStart = 0;
            while (segmentStart < draws.size()) {
                boolean depthTest = draws.get(segmentStart).depthTest;
                boolean projective = draws.get(segmentStart).projective;
                int segmentEnd = segmentStart + 1;
                while (segmentEnd < draws.size()
                        && draws.get(segmentEnd).depthTest == depthTest
                        && draws.get(segmentEnd).projective == projective) {
                    segmentEnd++;
                }

                if (projective) {
                    flushProjectiveSegment(segmentStart, segmentEnd);
                } else if (depthTest) {
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
        flushOverlaySegment(start, end);
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

    private void flushProjectiveSegment(int start, int end) {
        draws.subList(start, end).sort(Comparator.comparingDouble(draw -> draw.projectedDepth));
        flushOverlaySegment(start, end);
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
        boolean projective;
        float projectedDepth;
        final Matrix4f matrix = new Matrix4f();
        float x, y, width, height, u0, v0, u1, v1;
        int tintArgb;

        void set(RenderHandle renderHandle, boolean depthTest, boolean projective, Matrix4f source,
                 float x, float y, float width, float height,
                 float u0, float v0, float u1, float v1, int tintArgb) {
            this.renderHandle = renderHandle;
            this.depthTest = depthTest;
            this.projective = projective;
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
            this.projectedDepth = projectedCenterDepth(source, x + width * 0.5f, y + height * 0.5f);
        }

        private static float projectedCenterDepth(Matrix4f matrix, float x, float y) {
            float z = matrix.m02() * x + matrix.m12() * y + matrix.m32();
            float w = matrix.m03() * x + matrix.m13() * y + matrix.m33();
            return Float.isFinite(w) && Math.abs(w) > 1.0e-6f && w != 1.0f ? z / w : z;
        }
    }

    private static final class Batch {
        RenderHandle renderHandle;
        final List<Draw> draws = new ArrayList<>();
    }
}
