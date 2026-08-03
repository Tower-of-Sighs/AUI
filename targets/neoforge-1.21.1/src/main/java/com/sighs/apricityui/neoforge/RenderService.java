package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.spi.MeshFormat;
import com.sighs.apricityui.spi.MeshMode;
import com.sighs.apricityui.spi.RenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Forge implementation of {@link AuiRenderService}, backed by 1.20.1's
 * {@link RenderSystem} global state. On 1.21.5+ this maps to pipeline state.
 */
public final class RenderService implements AuiRenderService {
    public static final RenderService INSTANCE = new RenderService();

    private RenderService() {
    }

    @Override
    public void setProjectionMatrix(Matrix4f matrix) {
        RenderSystem.setProjectionMatrix(matrix, RenderSystem.getVertexSorting());
    }

    @Override
    public Matrix4f getProjectionMatrix() {
        return RenderSystem.getProjectionMatrix();
    }

    @Override
    public void enableDepthTest() {
        RenderSystem.enableDepthTest();
    }

    @Override
    public void disableDepthTest() {
        RenderSystem.disableDepthTest();
    }

    @Override
    public void enableBlend() {
        RenderSystem.enableBlend();
    }

    @Override
    public void setBlendFunc(int srcFactor, int dstFactor) {
        RenderSystem.blendFunc(srcFactor, dstFactor);
    }

    @Override
    public MeshBuilder beginMesh(MeshMode mode, MeshFormat format) {
        VertexFormat.Mode m = mode == MeshMode.QUADS ? VertexFormat.Mode.QUADS : VertexFormat.Mode.TRIANGLES;
        VertexFormat fmt = format == MeshFormat.POSITION ? DefaultVertexFormat.POSITION
                : format == MeshFormat.POSITION_TEX ? DefaultVertexFormat.POSITION_TEX
                : DefaultVertexFormat.POSITION_COLOR;
        BufferBuilder buf = Tesselator.getInstance().begin(m, fmt);
        return MeshBuilder.of(buf);
    }

    @Override
    public void emitVertex(Object mesh, Matrix4f mat, float x, float y, float z, int r, int g, int b, int a) {
        ((BufferBuilder) mesh).addVertex(mat, x, y, z).setColor(r, g, b, a);
    }

    @Override
    public void submitMesh(Object mesh) {
        // 1.21's build() returns null for an empty buffer (buildOrThrow() throws
        // "BufferBuilder was empty"); a batch with no vertices is a legit no-op.
        MeshData meshData = ((BufferBuilder) mesh).build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }
    }

    /** Bundles the {@link MultiBufferSource.BufferSource} with the render type it batches. */
    private record TextureBatchHandle(MultiBufferSource.BufferSource source, RenderType renderType) {
    }

    @Override
    public Object beginTextureBatch(RenderHandle render) {
        RenderType renderType = render.as();
        return new TextureBatchHandle(Minecraft.getInstance().renderBuffers().bufferSource(), renderType);
    }

    @Override
    public void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height,
                                float u0, float v0, float u1, float v1) {
        TextureBatchHandle handle = (TextureBatchHandle) batch;
        VertexConsumer consumer = handle.source().getBuffer(handle.renderType());
        consumer.addVertex(mat, x, y + height, 0.0F).setColor(255, 255, 255, 255).setUv(u0, v1).setLight(0xF000F0);
        consumer.addVertex(mat, x + width, y + height, 0.0F).setColor(255, 255, 255, 255).setUv(u1, v1).setLight(0xF000F0);
        consumer.addVertex(mat, x + width, y, 0.0F).setColor(255, 255, 255, 255).setUv(u1, v0).setLight(0xF000F0);
        consumer.addVertex(mat, x, y, 0.0F).setColor(255, 255, 255, 255).setUv(u0, v0).setLight(0xF000F0);
    }

    @Override
    public void flushTextureBatch(Object batch, RenderHandle render) {
        TextureBatchHandle handle = (TextureBatchHandle) batch;
        handle.source().endBatch(handle.renderType());
    }

    @Override
    public void emitVertexUV(Object mesh, Matrix4f mat, float x, float y, float z, float u, float v) {
        ((BufferBuilder) mesh).addVertex(mat, x, y, z).setUv(u, v);
    }

    @Override
    public FboHandle createOffscreenTarget(int width, int height, boolean useDepth) {
        TextureTarget target = new TextureTarget(width, height, useDepth, Minecraft.ON_OSX);
        return FboHandle.of(target, width, height);
    }

    @Override
    public FboHandle getMainRenderTarget() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        return FboHandle.of(target, target.width, target.height);
    }

    @Override
    public void enableStencil(FboHandle target) {
        target.<RenderTarget>as().enableStencil();
    }

    @Override
    public void destroyBuffers(FboHandle target) {
        target.<RenderTarget>as().destroyBuffers();
    }

    @Override
    public void clear(FboHandle target, float r, float g, float b, float a) {
        RenderTarget rt = target.as();
        rt.setClearColor(r, g, b, a);
        rt.clear(Minecraft.ON_OSX);
    }

    @Override
    public void bindWrite(FboHandle target, boolean setViewport) {
        target.<RenderTarget>as().bindWrite(setViewport);
    }

    @Override
    public void bindColorTexture(FboHandle target, int unit) {
        RenderSystem.setShaderTexture(unit, target.<RenderTarget>as().getColorTextureId());
    }

    @Override
    public void blitFramebuffer(FboHandle source, FboHandle target, int srcX0, int srcY0, int srcX1, int srcY1) {
        RenderTarget src = source.as();
        RenderTarget dst = target.as();
        int previousFbo = GlStateManager.getBoundFramebuffer();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                srcX0, srcY0, srcX1, srcY1,
                0, 0, dst.width, dst.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
        );
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
    }

    @Override
    public Object createDynamicTexture(String name, Object nativeImage, boolean linear) {
        DynamicTexture texture = new DynamicTexture((NativeImage) nativeImage);
        texture.setFilter(linear, false);
        return texture;
    }

    @Override
    public void uploadTextureRegion(Object texture, Object nativeImage, int x, int y, int width, int height, boolean linear) {
        ((DynamicTexture) texture).bind();
        ((NativeImage) nativeImage).upload(0, x, y, x, y, width, height, linear, false);
    }

    @Override
    public void closeTexture(Object texture) {
        ((DynamicTexture) texture).close();
    }

    @Override
    public void registerTexture(Object texture, Object location) {
        Minecraft.getInstance().getTextureManager().register((ResourceLocation) location, (net.minecraft.client.renderer.texture.AbstractTexture) texture);
    }

    @Override
    public void releaseTexture(Object location) {
        Minecraft.getInstance().getTextureManager().release((ResourceLocation) location);
    }

    @Override
    public void setShader(Object shader) {
        RenderSystem.setShader(() -> (ShaderInstance) shader);
    }

    @Override
    public void setPositionColorShader() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    @Override
    public void setShaderColor(float a, float r, float g, float b) {
        RenderSystem.setShaderColor(a, r, g, b);
    }

    @Override
    public Object getFilterShader() {
        return ShaderRegistry.getFilterShader();
    }

    @Override
    public Object getFilterBlurShader() {
        return ShaderRegistry.getFilterBlurShader();
    }
}
