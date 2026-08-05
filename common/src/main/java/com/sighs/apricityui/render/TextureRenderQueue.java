package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.RenderHandle;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Collects texture quads until AUI reaches a real render-state boundary.
 *
 * <p>Depth-tested draws can be reordered by render handle because the depth
 * buffer determines visibility. Overlay draws keep their original order: an
 * alpha-composited overlay must not be moved across another texture.</p>
 */
final class TextureRenderQueue {
    private static final int MAX_QUEUED_QUADS = 8192;

    private final List<Draw> draws = new ArrayList<>();

    void add(RenderHandle renderHandle, boolean depthTest, Matrix4f matrix,
             float x, float y, float width, float height,
             float u0, float v0, float u1, float v1) {
        if (draws.size() >= MAX_QUEUED_QUADS) flush();
        draws.add(new Draw(renderHandle, depthTest, new Matrix4f(matrix),
                x, y, width, height, u0, v0, u1, v1));
    }

    void flush() {
        if (draws.isEmpty()) return;

        try {
            int segmentStart = 0;
            while (segmentStart < draws.size()) {
                boolean depthTest = draws.get(segmentStart).depthTest();
                int segmentEnd = segmentStart + 1;
                while (segmentEnd < draws.size()
                        && draws.get(segmentEnd).depthTest() == depthTest) {
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
            draws.clear();
        }
    }

    private void flushDepthTestedSegment(int start, int end) {
        List<Batch> batches = new ArrayList<>();
        IdentityHashMap<RenderHandle, Batch> byRenderHandle = new IdentityHashMap<>();
        for (int i = start; i < end; i++) {
            Draw draw = draws.get(i);
            Batch batch = byRenderHandle.get(draw.renderHandle());
            if (batch == null) {
                batch = new Batch(draw.renderHandle());
                byRenderHandle.put(draw.renderHandle(), batch);
                batches.add(batch);
            }
            batch.draws().add(draw);
        }
        flushBatches(batches);
    }

    private void flushOverlaySegment(int start, int end) {
        List<Batch> batches = new ArrayList<>();
        Batch previous = null;
        for (int i = start; i < end; i++) {
            Draw draw = draws.get(i);
            if (previous == null || previous.renderHandle() != draw.renderHandle()) {
                previous = new Batch(draw.renderHandle());
                batches.add(previous);
            }
            previous.draws().add(draw);
        }
        flushBatches(batches);
    }

    private void flushBatches(List<Batch> batches) {
        for (Batch batch : batches) {
            Object token = AuiServices.render().beginTextureBatch(batch.renderHandle());
            for (Draw draw : batch.draws()) {
                AuiServices.render().emitTextureQuad(token, draw.matrix(),
                        draw.x(), draw.y(), draw.width(), draw.height(),
                        draw.u0(), draw.v0(), draw.u1(), draw.v1());
            }
            AuiServices.render().flushTextureBatch(token, batch.renderHandle());
            RenderBatchStats.recordImageFlush();
        }
    }

    private record Draw(RenderHandle renderHandle, boolean depthTest, Matrix4f matrix,
                        float x, float y, float width, float height,
                        float u0, float v0, float u1, float v1) {
    }

    private record Batch(RenderHandle renderHandle, List<Draw> draws) {
        private Batch(RenderHandle renderHandle) {
            this(renderHandle, new ArrayList<>());
        }
    }
}
