package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

final class AuiSurfaceRenderer implements AutoCloseable {
    private final AuiMapGlProgram shader = new AuiMapGlProgram("native_surface");
    private final int vertices = GL30.glGenVertexArrays();
    private int color, height;
    private ByteBuffer upload;
    private AuiNativeTerrainRenderer.Surface surface;

    void update(AuiNativeTerrainRenderer.Surface next) {
        if (next == surface) return;
        if (next == null) { reset(); return; }
        int cells = Math.multiplyExact(next.width(), next.depth());
        if (next.width() < 1 || next.depth() < 1 || next.step() < 1 || next.heights().length != cells
                || next.rgba().length != Math.multiplyExact(cells, 4)) throw new IllegalArgumentException("Invalid surface dimensions");
        if (color == 0) { color = GL11.glGenTextures(); height = GL11.glGenTextures(); }
        int bytes = Math.multiplyExact(cells, 4);
        if (upload == null || upload.capacity() < bytes) {
            if (upload != null) MemoryUtil.memFree(upload);
            upload = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder());
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        upload.clear().put(next.rgba()).flip();
        texture(color, GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, next.width(), next.depth(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, upload);
        upload.clear();
        for (int y : next.heights()) upload.putFloat(y + 1.0F);
        upload.flip();
        texture(height, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, next.width(), next.depth(), 0, GL11.GL_RED, GL11.GL_FLOAT, upload);
        surface = next;
    }

    boolean draw(AuiMapCamera camera, int coverage, int coverageX, int coverageZ, float brightness) {
        if (surface == null) return false;
        shader.bind();
        shader.matrix("ProjMat", camera.projectionMatrix());
        shader.matrix("ModelViewMat", camera.viewMatrix());
        shader.vector("Grid", surface.width(), surface.depth(), surface.step(), brightness);
        shader.vector("CoverageRegion", (float) (camera.x() - coverageX * 16L), (float) (camera.z() - coverageZ * 16L), 0, 0);
        shader.vector("SurfaceOrigin", (float) (surface.x() - camera.x()), (float) -camera.y(), (float) (surface.z() - camera.z()), 0);
        int[] textures = {color, height, coverage};
        String[] samplers = {"SurfaceColor", "SurfaceHeight", "Coverage"};
        for (int i = 0; i < textures.length; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(textures[i]);
            shader.sampler(samplers[i], i);
        }
        GlStateManager._glBindVertexArray(vertices);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, Math.multiplyExact(Math.multiplyExact(surface.width(), surface.depth()), 6));
        return true;
    }

    private static void texture(int id, int filter) {
        GlStateManager._bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    void reset() {
        surface = null;
        if (color != 0) { GlStateManager._deleteTexture(color); color = 0; }
        if (height != 0) { GlStateManager._deleteTexture(height); height = 0; }
    }

    @Override public void close() {
        reset(); shader.close(); GL30.glDeleteVertexArrays(vertices);
        if (upload != null) { MemoryUtil.memFree(upload); upload = null; }
    }
}

