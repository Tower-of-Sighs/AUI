package com.sighs.apricityui.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Captures renderer geometry by material without submitting it to the world. */
final class ModelVertexCapture {
    final ModelMesh<RenderType> mesh = new ModelMesh<>();
    private final Map<RenderType, Capture> consumers = new LinkedHashMap<>();
    private Capture previous;

    VertexConsumer getBuffer(RenderType type) {
        if (previous != null) previous.endVertex();
        previous = consumers.computeIfAbsent(type, Capture::new);
        return previous;
    }

    void finish() {
        consumers.values().forEach(Capture::endVertex);
    }

    private final class Capture implements VertexConsumer {
        private final RenderType type;
        private final int faceSize;
        private final ArrayList<ModelMesh.Vertex> vertices = new ArrayList<>(4);
        private boolean pending;
        private float x, y, z, u, v, nx, ny, nz;
        private int color = -1, overlay, light;

        private Capture(RenderType type) {
            this.type = type;
            faceSize = type.primitiveTopology().primitiveLength;
            if (faceSize != 3 && faceSize != 4)
                throw new IllegalArgumentException("Unsupported model primitive: " + type.primitiveTopology());
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            endVertex();
            pending = true;
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) { this.color = color; return this; }
        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            color = a << 24 | r << 16 | g << 8 | b;
            return this;
        }
        @Override
        public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override
        public VertexConsumer setUv1(int u, int v) { overlay = u | v << 16; return this; }
        @Override
        public VertexConsumer setUv2(int u, int v) { light = u | v << 16; return this; }
        @Override
        // shortcut: ModelMesh has no secondary UV pair; add one if entity previews need it.
        public VertexConsumer setUv3(float u, float v) { return this; }
        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            nx = x;
            ny = y;
            nz = z;
            return this;
        }
        @Override
        public VertexConsumer setLineWidth(float width) { return this; }

        private void endVertex() {
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
