package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Basic GPU chunk renderer: caller supplies projection, layers and shared terrain depth. */
public final class AuiNativeTerrainRenderer implements AutoCloseable {
    /** chunkMask selects the caller's 2x2 local 16x16 chunks; projection and mesh ownership stay with the caller. */
    public record Draw(GpuMesh mesh, Matrix4f modelView, int chunkMask, float skyLightFactor) { }

    private final AuiMapGlProgram program = new AuiMapGlProgram("native_map");
    private final AuiMapDepthTarget sceneDepth = new AuiMapDepthTarget();
    private final AtomicBoolean closed = new AtomicBoolean();

    public AuiNativeTerrainRenderer() { requireClientThread(); }

    /** Shared terrain depth, still bindable by the caller's entity pass. */
    public AuiMapDepthTarget depthTarget() { return sceneDepth; }

    /** Copies the CPU mesh into a fresh native buffer; the input mesh stays caller-owned. */
    public GpuMesh upload(AuiNativeMesh mesh) {
        requireClientThread();
        ByteBuffer vertices = mesh.vertices();
        int bytes = vertices.remaining();
        if (bytes == 0) return new GpuMesh(0, 0, 0, 0, 0, 0, mesh.minY(), mesh.maxY());
        int previousArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int array = GL30.glGenVertexArrays(), buffer = GL15.glGenBuffers();
        try {
            GlStateManager._glBindVertexArray(array);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 32, 0L);
            GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, 32, 12L);
            GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 32, 16L);
            GL30.glVertexAttribIPointer(3, 2, GL11.GL_SHORT, 32, 24L);
            GL20.glVertexAttribPointer(4, 4, GL11.GL_BYTE, true, 32, 28L);
            for (int index = 0; index < 5; index++) GL20.glEnableVertexAttribArray(index);
            return new GpuMesh(array, buffer, mesh.opaque().count(), mesh.translucent().start(),
                    mesh.translucent().count(), bytes, mesh.minY(), mesh.maxY());
        } catch (RuntimeException failure) {
            GL15.glDeleteBuffers(buffer); GL30.glDeleteVertexArrays(array);
            throw failure;
        } finally {
            GlStateManager._glBindVertexArray(previousArray);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    public void drawOpaque(RenderTarget target, Matrix4f projection, boolean perspective, List<Draw> draws) {
        drawMeshes(target, projection, draws, false);
    }

    public void drawTransparent(RenderTarget target, Matrix4f projection, boolean perspective, List<Draw> draws) {
        drawMeshes(target, projection, draws, true);
    }

    private void drawMeshes(RenderTarget target, Matrix4f projection, List<Draw> draws, boolean transparent) {
        if (closed.get()) return;
        requireClientThread();
        try (var state = new AuiMapRenderState(target, sceneDepth)) {
            prepareState(transparent);
            program.bind();
            program.matrix("ProjMat", projection);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getId());
            int previousMin = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            int previousMag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
            int minification = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL) > 0
                    ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST;
            try {
                // Atlas animation uploads and other render types can replace its filtering between frames.
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minification);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                program.sampler("Sampler0", 0);
                for (Draw draw : draws) {
                    GpuMesh mesh = draw.mesh();
                    int count = transparent ? mesh.translucentCount : mesh.opaqueCount;
                    if (count == 0) continue;
                    program.matrix("ModelViewMat", draw.modelView());
                    program.vector("Region", draw.chunkMask(), draw.skyLightFactor(), transparent ? 1 : 0, 0);
                    GlStateManager._glBindVertexArray(mesh.array);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, transparent ? mesh.translucentStart : 0, count);
                }
            } finally {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, previousMin);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, previousMag);
            }
        }
    }

    private static void prepareState(boolean transparent) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        if (transparent) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else RenderSystem.disableBlend();
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("AUI terrain rendering must run on the client thread");
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        program.close();
        sceneDepth.close();
    }

    /** Caller-owned GPU mesh; release its native resources with close(). */
    public static final class GpuMesh implements AutoCloseable {
        private final int array, buffer, opaqueCount, translucentStart, translucentCount;
        private final long bytes;
        private final float minY, maxY;
        private boolean deleted;

        private GpuMesh(int array, int buffer, int opaqueCount, int translucentStart, int translucentCount,
                        long bytes, float minY, float maxY) {
            this.array = array; this.buffer = buffer; this.opaqueCount = opaqueCount;
            this.translucentStart = translucentStart; this.translucentCount = translucentCount;
            this.bytes = bytes; this.minY = minY; this.maxY = maxY;
        }

        public long bytes() { return bytes; }
        public float minY() { return minY; }
        public float maxY() { return maxY; }
        public int opaqueCount() { return opaqueCount; }
        public int translucentCount() { return translucentCount; }

        @Override public void close() {
            if (deleted) return;
            deleted = true;
            if (buffer != 0) GL15.glDeleteBuffers(buffer);
            if (array != 0) GL30.glDeleteVertexArrays(array);
        }
    }
}
