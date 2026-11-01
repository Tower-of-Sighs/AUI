package com.sighs.apricityui.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Captures the renderer's material and vertex output without submitting world draws. */
final class ModelVertexCapture implements MultiBufferSource {
    final ModelMesh<RenderType> mesh = new ModelMesh<>();
    private Capture previous;
    private final Map<RenderType, Capture> consumers = new LinkedHashMap<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (previous != null) previous.endVertex();
        previous = consumers.computeIfAbsent(type, Capture::new);
        return previous;
    }

    void finish() { consumers.values().forEach(Capture::endVertex); }

    private final class Capture implements VertexConsumer {
        private final RenderType type;
        private final int faceSize;
        private final ArrayList<ModelMesh.Vertex> vertices = new ArrayList<>(4);
        private boolean pending;
        private float x, y, z, u, v, nx, ny, nz;
        private int color = -1, overlay, light;

        Capture(RenderType type) {
            this.type = type;
            faceSize = type.mode() == VertexFormat.Mode.QUADS ? 4
                    : type.mode() == VertexFormat.Mode.TRIANGLES ? 3 : 0;
            if (faceSize == 0) throw new IllegalArgumentException("Unsupported model primitive: " + type.mode());
        }

        public VertexConsumer addVertex(float x, float y, float z) {
            endVertex();
            pending = true;
            this.x = (float) x; this.y = (float) y; this.z = (float) z; return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            color = a << 24 | r << 16 | g << 8 | b; return this;
        }
        public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; return this; }
        public VertexConsumer setUv1(int u, int v) { overlay = u | v << 16; return this; }
        public VertexConsumer setUv2(int u, int v) { light = u | v << 16; return this; }
        public VertexConsumer setNormal(float x, float y, float z) { nx = x; ny = y; nz = z; return this; }
        public void endVertex() {
            if (!pending) return;
            pending = false;
            vertices.add(new ModelMesh.Vertex(x, y, z, color, u, v, overlay, light, nx, ny, nz));
            if (vertices.size() == faceSize) {
                mesh.add(type, vertices);
                vertices.clear();
            }
        }
    }
}
