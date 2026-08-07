package com.sighs.apricityui.forge;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
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
        // Tesselator owns one reusable BufferBuilder on 1.20.1. Reusing it is
        // important here because BufferUploader consumes the finished mesh but
        // does not require a new builder for the next batch.
        VertexFormat.Mode m = mode == MeshMode.QUADS ? VertexFormat.Mode.QUADS : VertexFormat.Mode.TRIANGLES;
        VertexFormat fmt = format == MeshFormat.POSITION ? DefaultVertexFormat.POSITION
                : format == MeshFormat.POSITION_TEX ? DefaultVertexFormat.POSITION_TEX
                : DefaultVertexFormat.POSITION_COLOR;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(m, fmt);
        return MeshBuilder.of(buf);
    }

    @Override
    public void emitVertex(Object mesh, Matrix4f mat, float x, float y, float z, int r, int g, int b, int a) {
        ((BufferBuilder) mesh).vertex(mat, x, y, z).color(r, g, b, a).endVertex();
    }

    @Override
    public void submitMesh(Object mesh) {
        BufferUploader.drawWithShader(((BufferBuilder) mesh).end());
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
        consumer.vertex(mat, x, y + height, 0.0F).color(255, 255, 255, 255).uv(u0, v1).uv2(0xF000F0).endVertex();
        consumer.vertex(mat, x + width, y + height, 0.0F).color(255, 255, 255, 255).uv(u1, v1).uv2(0xF000F0).endVertex();
        consumer.vertex(mat, x + width, y, 0.0F).color(255, 255, 255, 255).uv(u1, v0).uv2(0xF000F0).endVertex();
        consumer.vertex(mat, x, y, 0.0F).color(255, 255, 255, 255).uv(u0, v0).uv2(0xF000F0).endVertex();
    }

    @Override
    public void flushTextureBatch(Object batch, RenderHandle render) {
        TextureBatchHandle handle = (TextureBatchHandle) batch;
        handle.source().endBatch(handle.renderType());
    }

    @Override
    public void emitVertexUV(Object mesh, Matrix4f mat, float x, float y, float z, float u, float v) {
        ((BufferBuilder) mesh).vertex(mat, x, y, z).uv(u, v).endVertex();
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
        RenderTarget rt = target.as();
        rt.enableStencil();
        if (rt == Minecraft.getInstance().getMainRenderTarget()) {
            enableFabulousChainStencil();
        }
    }

    /**
     * Keeps the Fabulous chain targets on the same depth format as the
     * stencil-enabled main target. Vanilla copies depth from the main target
     * into these every frame via a raw glBlitFramebuffer with no format check;
     * once the main target switches to GL_DEPTH32F_STENCIL8 the mismatch makes
     * the driver emit GL_INVALID_OPERATION ("Depth formats do not match") every
     * frame and the copy silently fails, so translucent terrain and particles
     * lose occlusion against the opaque world. Called on every stencil-mask
     * begin because these targets are recreated on resource reloads and
     * graphics-mode switches.
     */
    private static void enableFabulousChainStencil() {
        LevelRenderer levelRenderer = Minecraft.getInstance().levelRenderer;
        if (levelRenderer == null) return;
        enableStencilIfPresent(levelRenderer.getTranslucentTarget());
        enableStencilIfPresent(levelRenderer.getItemEntityTarget());
        enableStencilIfPresent(levelRenderer.getParticlesTarget());
        enableStencilIfPresent(levelRenderer.getWeatherTarget());
        enableStencilIfPresent(levelRenderer.getCloudsTarget());
    }

    private static void enableStencilIfPresent(RenderTarget target) {
        if (target != null && !target.isStencilEnabled()) target.enableStencil();
    }

    /**
     * Re-applies stencil to the Fabulous chain targets after they are recreated
     * by resource reloads or graphics-mode switches while the main target keeps
     * its stencil-enabled depth format. Called once per client tick; cheap
     * (null/boolean checks) unless a target actually needs rebuilding.
     */
    public void reconcileFabulousChainStencil() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main != null && main.isStencilEnabled()) enableFabulousChainStencil();
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
    public RenderStateScope pushFilterRenderState() {
        return LegacyFilterState.capture();
    }

    @Override
    public void blitFramebuffer(FboHandle source, FboHandle target, int srcX0, int srcY0, int srcX1, int srcY1) {
        RenderTarget src = source.as();
        RenderTarget dst = target.as();
        int previousReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
            GlStateManager._glBlitFrameBuffer(
                    srcX0, srcY0, srcX1, srcY1,
                    0, 0, dst.width, dst.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
            );
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFbo);
        }
    }

    private static final class LegacyFilterState implements RenderStateScope {
        private static final int SHADER_SAMPLER_COUNT = 12;

        private final ShaderInstance shader;
        private final int program;
        private final int[] shaderTextures;
        private final int[] boundTextures;
        private final int activeTexture;
        private final float[] shaderColor;
        private final boolean blend;
        private final int srcRgb;
        private final int dstRgb;
        private final int srcAlpha;
        private final int dstAlpha;
        private final boolean depthTest;
        private final int depthFunc;
        private final boolean depthMask;
        private final boolean cull;
        private boolean closed;

        private LegacyFilterState(ShaderInstance shader, int program, int[] shaderTextures,
                                  int[] boundTextures, int activeTexture, float[] shaderColor,
                                  boolean blend, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
                                  boolean depthTest, int depthFunc, boolean depthMask, boolean cull) {
            this.shader = shader;
            this.program = program;
            this.shaderTextures = shaderTextures;
            this.boundTextures = boundTextures;
            this.activeTexture = activeTexture;
            this.shaderColor = shaderColor;
            this.blend = blend;
            this.srcRgb = srcRgb;
            this.dstRgb = dstRgb;
            this.srcAlpha = srcAlpha;
            this.dstAlpha = dstAlpha;
            this.depthTest = depthTest;
            this.depthFunc = depthFunc;
            this.depthMask = depthMask;
            this.cull = cull;
        }

        private static LegacyFilterState capture() {
            int activeTexture = GlStateManager._getActiveTexture();
            int[] shaderTextures = new int[SHADER_SAMPLER_COUNT];
            int[] boundTextures = new int[SHADER_SAMPLER_COUNT];
            for (int unit = 0; unit < SHADER_SAMPLER_COUNT; unit++) {
                shaderTextures[unit] = RenderSystem.getShaderTexture(unit);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                boundTextures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            GlStateManager._activeTexture(activeTexture);

            return new LegacyFilterState(
                    RenderSystem.getShader(),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    shaderTextures,
                    boundTextures,
                    activeTexture,
                    RenderSystem.getShaderColor().clone(),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE)
            );
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;

            // ShaderInstance.apply() updates BlendMode.lastApplied in addition
            // to the actual GL state. Restore that cache before restoring the
            // captured raw bindings; vanilla's vignette relies on its manual
            // blend function not being overridden by an apparently new opaque
            // shader on the following frame.
            RenderSystem.setShader(() -> shader);
            if (shader != null) {
                shader.apply();
                shader.clear();
            }

            for (int unit = 0; unit < SHADER_SAMPLER_COUNT; unit++) {
                RenderSystem.setShaderTexture(unit, shaderTextures[unit]);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                GlStateManager._bindTexture(boundTextures[unit]);
            }
            GlStateManager._activeTexture(activeTexture);

            GlStateManager._glUseProgram(program);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            RenderSystem.depthFunc(depthFunc);
            GlStateManager._depthMask(depthMask);
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
        }
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
    public void setImagePixel(Object nativeImage, int x, int y, int pixel) {
        ((NativeImage) nativeImage).setPixelRGBA(x, y, pixel);
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

    @Override
    public void setDepthFunc(int func) {
        RenderSystem.depthFunc(func);
    }

    @Override
    public void setDepthMask(boolean write) {
        GlStateManager._depthMask(write);
    }

    @Override
    public boolean isDepthTestEnabled() {
        return GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    }

    @Override
    public boolean isDepthMaskEnabled() {
        return GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    }

    @Override
    public void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    @Override
    public void disableBlend() {
        RenderSystem.disableBlend();
    }

    @Override
    public void enableCull() {
        RenderSystem.enableCull();
    }

    @Override
    public void disableCull() {
        RenderSystem.disableCull();
    }

    @Override
    public boolean isCullEnabled() {
        return GL11.glIsEnabled(GL11.GL_CULL_FACE);
    }

    @Override
    public void enablePolygonOffset() {
        RenderSystem.enablePolygonOffset();
    }

    @Override
    public void disablePolygonOffset() {
        RenderSystem.disablePolygonOffset();
    }

    @Override
    public void polygonOffset(float factor, float units) {
        RenderSystem.polygonOffset(factor, units);
    }

    @Override
    public void enableScissorTest() {
        GlStateManager._enableScissorTest();
    }

    @Override
    public void scissorBox(int x, int y, int width, int height) {
        GlStateManager._scissorBox(x, y, width, height);
    }

    @Override
    public void disableScissorTest() {
        GlStateManager._disableScissorTest();
    }

    @Override
    public void enableStencilTest() {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
    }

    @Override
    public void disableStencilTest() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    @Override
    public void setStencilMask(int mask) {
        GL11.glStencilMask(mask);
    }

    @Override
    public void setStencilFunc(int func, int ref, int mask) {
        GL11.glStencilFunc(func, ref, mask);
    }

    @Override
    public void setStencilOp(int sfail, int dpfail, int dppass) {
        GL11.glStencilOp(sfail, dpfail, dppass);
    }

    @Override
    public void clearStencilBuffer() {
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
    }

    @Override
    public void setColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11.glColorMask(red, green, blue, alpha);
    }

    @Override
    public boolean isOnRenderThread() {
        return RenderSystem.isOnRenderThread();
    }

    @Override
    public void recordRenderCall(Runnable task) {
        RenderSystem.recordRenderCall(task::run);
    }

    @Override
    public String getGLVersionString() {
        return GL11.glGetString(GL11.GL_VERSION);
    }

    @Override
    public void flushSharedBuffers() {
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    private static void setShaderUniform(String name,
                                         java.util.function.Consumer<com.mojang.blaze3d.shaders.Uniform> setter) {
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) return;
        com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
        if (uniform != null) setter.accept(uniform);
    }

    @Override
    public void setShaderUniformFloat(String name, float value) {
        setShaderUniform(name, uniform -> uniform.set(value));
    }

    @Override
    public void setShaderUniform2f(String name, float a, float b) {
        setShaderUniform(name, uniform -> uniform.set(a, b));
    }

    @Override
    public void setShaderUniform3f(String name, float a, float b, float c) {
        setShaderUniform(name, uniform -> uniform.set(a, b, c));
    }

    @Override
    public void setShaderUniform4f(String name, float a, float b, float c, float d) {
        setShaderUniform(name, uniform -> uniform.set(a, b, c, d));
    }

    @Override
    public void setShaderUniformI(String name, int value) {
        setShaderUniform(name, uniform -> uniform.set(value));
    }
}
