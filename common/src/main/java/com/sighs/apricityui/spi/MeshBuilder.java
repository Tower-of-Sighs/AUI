package com.sighs.apricityui.spi;

import org.joml.Matrix4f;

/**
 * Opaque handle to the loader's vertex buffer (e.g. {@code BufferBuilder}).
 * Common geometry code emits colored vertices through this handle; the loader
 * performs the version-specific vertex/upload calls.
 */
public final class MeshBuilder {
    /**
     * Identity pose used once a projective matrix has already been applied to the position.
     */
    private static final Matrix4f IDENTITY = new Matrix4f();
    private final Object impl;

    MeshBuilder(Object impl) {
        this.impl = impl;
    }

    public static MeshBuilder of(Object impl) {
        return new MeshBuilder(impl);
    }

    /**
     * The loader's underlying buffer object.
     */
    public Object unwrap() {
        return impl;
    }

    /**
     * Emits a position+color vertex at z = 0.
     */
    public void vertex(Matrix4f mat, float x, float y, int color) {
        vertex(mat, x, y, 0f, color, 1.0f);
    }

    /**
     * Emits a position+color vertex at z = 0 with an alpha multiplier.
     */
    public void vertex(Matrix4f mat, float x, float y, int color, float alphaMultiplier) {
        vertex(mat, x, y, 0f, color, alphaMultiplier);
    }

    /**
     * Emits a position+color vertex at the given z.
     */
    public void vertex(Matrix4f mat, float x, float y, float z, int color) {
        vertex(mat, x, y, z, color, 1.0f);
    }

    public void vertex(Matrix4f mat, float x, float y, float z, int color, float alphaMultiplier) {
        int a = (int) (((color >> 24) & 0xFF) * alphaMultiplier);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        // CSS perspective() makes the matrix projective. Minecraft's vertex path applies the
        // pose affinely, so the divide has to happen here; z stays the caller's paint depth.
        if (!mat.isAffine()) {
            float w = mat.m03() * x + mat.m13() * y + mat.m23() * z + mat.m33();
            if (w > 0.0001f) {
                float px = mat.m00() * x + mat.m10() * y + mat.m20() * z + mat.m30();
                float py = mat.m01() * x + mat.m11() * y + mat.m21() * z + mat.m31();
                x = px / w;
                y = py / w;
            }
            mat = IDENTITY;
        }
        AuiServices.render().emitVertex(impl, mat, x, y, z, r, g, b, a);
    }

    /**
     * Emits a position+uv vertex (POSITION_TEX).
     */
    public void vertexUV(Matrix4f mat, float x, float y, float z, float u, float v) {
        if (!mat.isAffine()) {
            float w = mat.m03() * x + mat.m13() * y + mat.m23() * z + mat.m33();
            if (w > 0.0001f) {
                float px = mat.m00() * x + mat.m10() * y + mat.m20() * z + mat.m30();
                float py = mat.m01() * x + mat.m11() * y + mat.m21() * z + mat.m31();
                x = px / w;
                y = py / w;
            }
            mat = IDENTITY;
        }
        AuiServices.render().emitVertexUV(impl, mat, x, y, z, u, v);
    }

    /**
     * Finishes the mesh and submits it for drawing.
     */
    public void submit() {
        AuiServices.render().submitMesh(impl);
    }
}
