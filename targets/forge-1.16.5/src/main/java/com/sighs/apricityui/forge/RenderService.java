package com.sighs.apricityui.forge;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.sighs.apricityui.render.LegacyShader;
import com.sighs.apricityui.render.MatrixBridge;
import com.sighs.apricityui.render.RenderBatchStats;
import com.sighs.apricityui.spi.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class RenderService implements AuiRenderService {
    public static final RenderService INSTANCE = new RenderService();
    private LegacyShader shader;

    private RenderService() { }

    private static Matrix4f matrix(int name) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloatv(name, buffer);
        return new Matrix4f(buffer);
    }

    private static void matrix(int mode, Matrix4f matrix) {
        int previous = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        RenderSystem.matrixMode(mode);
        RenderSystem.loadIdentity();
        RenderSystem.multMatrix(MatrixBridge.toMojang(matrix));
        RenderSystem.matrixMode(previous);
    }

    @Override public Matrix4f getModelViewMatrix() { return matrix(GL11.GL_MODELVIEW_MATRIX); }
    @Override public Matrix4f getProjectionMatrix() { return matrix(GL11.GL_PROJECTION_MATRIX); }
    @Override public void setModelViewMatrix(Matrix4f value) { matrix(GL11.GL_MODELVIEW, value); }
    @Override public void setProjectionMatrix(Matrix4f value) { matrix(GL11.GL_PROJECTION, value); }
    @Override public void enableDepthTest() { RenderSystem.enableDepthTest(); }
    @Override public void disableDepthTest() { RenderSystem.disableDepthTest(); }
    @Override public void enableBlend() { RenderSystem.enableBlend(); }
    @Override public void disableBlend() { RenderSystem.disableBlend(); }
    @Override public void setBlendFunc(int source, int destination) { RenderSystem.blendFunc(source, destination); }
    @Override public void setBlendFuncSeparate(int sr, int dr, int sa, int da) { RenderSystem.blendFuncSeparate(sr, dr, sa, da); }

    @Override public MeshBuilder beginMesh(MeshMode mode, MeshFormat format) {
        VertexFormat vertexFormat = format == MeshFormat.POSITION ? DefaultVertexFormats.POSITION
                : format == MeshFormat.POSITION_TEX ? DefaultVertexFormats.POSITION_TEX : DefaultVertexFormats.POSITION_COLOR;
        BufferBuilder builder = Tessellator.getInstance().getBuilder();
        builder.begin(mode == MeshMode.QUADS ? GL11.GL_QUADS : GL11.GL_TRIANGLES, vertexFormat);
        return MeshBuilder.of(builder);
    }
    private static IVertexBuilder vertex(Object mesh, Matrix4f matrix, float x, float y, float z) {
        return ((BufferBuilder) mesh).vertex(
                matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30(),
                matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31(),
                matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32());
    }
    @Override public void emitVertex(Object mesh, Matrix4f matrix, float x, float y, float z, int r, int g, int b, int a) {
        vertex(mesh, matrix, x, y, z).color(r, g, b, a).endVertex();
    }
    @Override public void emitVertexUV(Object mesh, Matrix4f matrix, float x, float y, float z, float u, float v) {
        vertex(mesh, matrix, x, y, z).uv(u, v).endVertex();
    }
    @Override public void submitMesh(Object mesh) {
        if (shader != null) shader.matrices();
        BufferBuilder builder = (BufferBuilder) mesh;
        builder.end();
        WorldVertexBufferUploader.end(builder);
    }

    private record TextureBatch(IRenderTypeBuffer.Impl source, RenderType type) { }
    @Override public Object beginTextureBatch(RenderHandle render) {
        setShader(null);
        return new TextureBatch(Minecraft.getInstance().renderBuffers().bufferSource(), render.as());
    }
    @Override public void emitTextureQuad(Object batch, Matrix4f matrix, float x, float y, float width, float height,
                                          float u0, float v0, float u1, float v1) {
        emitTextureQuad(batch, matrix, x, y, width, height, u0, v0, u1, v1, -1);
    }
    @Override public void emitTextureQuad(Object batch, Matrix4f matrix, float x, float y, float width, float height,
                                          float u0, float v0, float u1, float v1, int color) {
        TextureBatch handle = (TextureBatch) batch;
        IVertexBuilder vertices = handle.source().getBuffer(handle.type());
        var pose = MatrixBridge.toMojang(matrix);
        int a = color >>> 24, r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        vertices.vertex(pose, x, y + height, 0).color(r, g, b, a).uv(u0, v1).uv2(0xF000F0).endVertex();
        vertices.vertex(pose, x + width, y + height, 0).color(r, g, b, a).uv(u1, v1).uv2(0xF000F0).endVertex();
        vertices.vertex(pose, x + width, y, 0).color(r, g, b, a).uv(u1, v0).uv2(0xF000F0).endVertex();
        vertices.vertex(pose, x, y, 0).color(r, g, b, a).uv(u0, v0).uv2(0xF000F0).endVertex();
    }
    @Override public void flushTextureBatch(Object batch, RenderHandle render) {
        TextureBatch handle = (TextureBatch) batch;
        handle.source().endBatch(handle.type());
    }
    @Override public FboHandle createOffscreenTarget(int width, int height, boolean depth) {
        return FboHandle.of(new Framebuffer(width, height, depth, Minecraft.ON_OSX), width, height);
    }
    @Override public FboHandle getMainRenderTarget() {
        Framebuffer target = Minecraft.getInstance().getMainRenderTarget();
        return FboHandle.of(target, target.width, target.height);
    }
    private static void enableStencil(Framebuffer target) {
        if (target != null && target.useDepth && !target.isStencilEnabled()) target.enableStencil();
    }
    private static void fabulousStencil() {
        WorldRenderer renderer = Minecraft.getInstance().levelRenderer;
        if (renderer == null) return;
        enableStencil(renderer.getTranslucentTarget());
        enableStencil(renderer.getItemEntityTarget());
        enableStencil(renderer.getParticlesTarget());
        enableStencil(renderer.getWeatherTarget());
        enableStencil(renderer.getCloudsTarget());
    }
    @Override public void enableStencil(FboHandle target) {
        enableStencil(target.<Framebuffer>as());
        if (target.<Framebuffer>as() == Minecraft.getInstance().getMainRenderTarget()) fabulousStencil();
    }
    public void reconcileFabulousChainStencil() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null) return;
        enableStencil(minecraft.getMainRenderTarget());
        fabulousStencil();
    }
    @Override public void alignDepthFormatForCopy(FboHandle source, FboHandle destination) {
        if (source != null && destination != null && source.<Framebuffer>as().isStencilEnabled())
            enableStencil(destination.<Framebuffer>as());
    }
    @Override public boolean currentTargetHasStencil() {
        return GL11.glGetInteger(GL11.GL_STENCIL_BITS) > 0;
    }
    @Override public void destroyBuffers(FboHandle target) { target.<Framebuffer>as().destroyBuffers(); }
    @Override public void clear(FboHandle target, float r, float g, float b, float a) {
        Framebuffer framebuffer = target.as();
        framebuffer.setClearColor(r, g, b, a);
        framebuffer.clear(Minecraft.ON_OSX);
    }
    @Override public void bindWrite(FboHandle target, boolean viewport) { target.<Framebuffer>as().bindWrite(viewport); }
    @Override public void bindColorTexture(FboHandle target, int unit) {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
        RenderSystem.bindTexture(target.<Framebuffer>as().getColorTextureId());
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }
    @Override public void drawOffscreenTexture(Matrix4f matrix, FboHandle target, float width, float height) {
        try (RenderStateScope ignored = pushFilterRenderState()) {
            setShader(null);
            RenderSystem.enableTexture();
            RenderSystem.disableAlphaTest();
            RenderSystem.disableLighting();
            RenderSystem.color4f(1, 1, 1, 1);
            bindColorTexture(target, 0);
            enableBlend();
            setBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            MeshBuilder mesh = beginMesh(MeshMode.QUADS, MeshFormat.POSITION_TEX);
            mesh.vertexUV(matrix, 0, height, 0, 0, 1);
            mesh.vertexUV(matrix, width, height, 0, 1, 1);
            mesh.vertexUV(matrix, width, 0, 0, 1, 0);
            mesh.vertexUV(matrix, 0, 0, 0, 0, 0);
            mesh.submit();
        }
    }
    @Override public void blitFramebuffer(FboHandle source, FboHandle destination, int x0, int y0, int x1, int y1) {
        Framebuffer src = source.as(), dst = destination.as();
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
            GlStateManager._glBlitFrameBuffer(x0, y0, x1, y1, 0, 0, dst.width, dst.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }
    @Override public Object createDynamicTexture(String name, Object image, boolean linear) {
        DynamicTexture texture = new DynamicTexture((NativeImage) image);
        texture.setFilter(linear, false);
        return texture;
    }
    @Override public Object createDynamicTexture(String name, int width, int height, boolean linear) {
        DynamicTexture texture = new DynamicTexture(width, height, true);
        texture.setFilter(linear, false);
        return texture;
    }
    @Override public void uploadTextureRegion(Object texture, Object image, int x, int y, int width, int height, boolean linear) {
        ((DynamicTexture) texture).bind();
        ((NativeImage) image).upload(0, x, y, x, y, width, height, linear, false);
    }
    @Override public boolean uploadTexturePixels(Object texture, ByteBuffer pixels, int offset, int x, int y, int width, int height) {
        ((DynamicTexture) texture).bind();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        pixels.position(offset);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return true;
    }
    @Override public void drawScreenTexture(Object graphics, Object texture, Object location, int x, int y, int width, int height, float alpha) {
        try (RenderStateScope ignored = pushFilterRenderState()) {
            setShader(null);
            RenderSystem.enableTexture();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.color4f(1, 1, 1, alpha);
            Minecraft.getInstance().getTextureManager().bind((ResourceLocation) location);
            AbstractGui.blit((MatrixStack) graphics, x, y, width, height, 0, 0, width, height, width, height);
        }
    }
    @Override public void setImagePixel(Object image, int x, int y, int pixel) { ((NativeImage) image).setPixelRGBA(x, y, pixel); }
    @Override public void closeTexture(Object texture) { ((DynamicTexture) texture).close(); }
    @Override public void registerTexture(Object texture, Object location) {
        Minecraft.getInstance().getTextureManager().register((ResourceLocation) location, (DynamicTexture) texture);
    }
    @Override public void releaseTexture(Object location) { Minecraft.getInstance().getTextureManager().release((ResourceLocation) location); }
    @Override public void setShader(Object shader) {
        this.shader = (LegacyShader) shader;
        if (this.shader == null) GL20.glUseProgram(0);
        else { this.shader.bind(); RenderSystem.disableAlphaTest(); RenderSystem.disableLighting(); }
    }
    @Override public void setPositionColorShader() {
        setShader(null);
        RenderSystem.disableTexture();
        RenderSystem.disableLighting();
        RenderSystem.disableAlphaTest();
        RenderSystem.disableFog();
        RenderSystem.shadeModel(GL11.GL_SMOOTH);
    }
    @Override public void setShaderColor(float r, float g, float b, float a) { RenderSystem.color4f(r, g, b, a); }
    @Override public Object getBackgroundShader() { return ShaderRegistry.getBackgroundShader(); }
    @Override public Object getFilterShader() { return ShaderRegistry.getFilterShader(); }
    @Override public Object getFilterBlurShader() { return ShaderRegistry.getFilterBlurShader(); }
    @Override public Object getFilterBlendShader() { return ShaderRegistry.getFilterBlendShader(); }
    @Override public Object getFilterMaskShader(boolean luminance) { return ShaderRegistry.getFilterMaskShader(); }
    @Override public Object getFilterMaskMergeShader(MaskCompositeOp op) {
        return switch (op) {
            case INTERSECT -> ShaderRegistry.getFilterMaskIntersectShader();
            case SUBTRACT -> ShaderRegistry.getFilterMaskSubtractShader();
            case EXCLUDE -> ShaderRegistry.getFilterMaskExcludeShader();
        };
    }
    @Override public RenderStateScope pushBackgroundTarget(FboHandle target) {
        return com.sighs.apricityui.render.LegacyBackgroundTarget.open(this, target);
    }
    @Override public void destroyBackgroundTarget(FboHandle target) {
        com.sighs.apricityui.render.LegacyBackgroundTarget.destroy(this, target);
    }
    @Override public void setDepthFunc(int func) { RenderSystem.depthFunc(func); }
    @Override public void setDepthMask(boolean write) { RenderSystem.depthMask(write); }
    @Override public boolean isDepthTestEnabled() { return GL11.glIsEnabled(GL11.GL_DEPTH_TEST); }
    @Override public boolean isDepthMaskEnabled() { return GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK); }
    @Override public void enableCull() { RenderSystem.enableCull(); }
    @Override public void disableCull() { RenderSystem.disableCull(); }
    @Override public boolean isCullEnabled() { return GL11.glIsEnabled(GL11.GL_CULL_FACE); }
    @Override public void enablePolygonOffset() { RenderSystem.enablePolygonOffset(); }
    @Override public void disablePolygonOffset() { RenderSystem.disablePolygonOffset(); }
    @Override public void polygonOffset(float factor, float units) { RenderSystem.polygonOffset(factor, units); }
    @Override public void enableScissorTest() { GlStateManager._enableScissorTest(); }
    @Override public void disableScissorTest() { GlStateManager._disableScissorTest(); }
    @Override public void scissorBox(int x, int y, int width, int height) { GlStateManager._scissorBox(x, y, width, height); }
    @Override public void enableStencilTest() { GL11.glEnable(GL11.GL_STENCIL_TEST); }
    @Override public void disableStencilTest() { GL11.glDisable(GL11.GL_STENCIL_TEST); }
    @Override public void setStencilMask(int mask) { GL11.glStencilMask(mask); }
    @Override public void setStencilFunc(int func, int ref, int mask) { GL11.glStencilFunc(func, ref, mask); }
    @Override public void setStencilOp(int fail, int depthFail, int pass) { GL11.glStencilOp(fail, depthFail, pass); }
    @Override public void clearStencilBuffer() { GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT); }
    @Override public void clearDepthBuffer() {
        boolean write = isDepthMaskEnabled(), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        double depth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        try {
            RenderSystem.depthMask(true);
            disableScissorTest();
            GL11.glClearDepth(1);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL11.glClearDepth(depth);
            RenderSystem.depthMask(write);
            if (scissor) enableScissorTest();
        }
    }
    @Override public void setColorMask(boolean r, boolean g, boolean b, boolean a) { RenderSystem.colorMask(r, g, b, a); }
    @Override public boolean isOnRenderThread() { return RenderSystem.isOnRenderThread(); }
    @Override public void recordRenderCall(Runnable task) { RenderSystem.recordRenderCall(task::run); }
    @Override public String getGLVersionString() { return GL11.glGetString(GL11.GL_VERSION); }
    @Override public void flushSharedBuffers() {
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        RenderBatchStats.recordSharedFlush();
    }
    @Override public void setShaderUniformFloat(String name, float value) { if (shader != null) shader.set(name, value); }
    @Override public void setShaderUniform2f(String name, float a, float b) { if (shader != null) shader.set(name, a, b); }
    @Override public void setShaderUniform3f(String name, float a, float b, float c) { if (shader != null) shader.set(name, a, b, c); }
    @Override public void setShaderUniform4f(String name, float a, float b, float c, float d) { if (shader != null) shader.set(name, a, b, c, d); }
    @Override public void setShaderUniformI(String name, int value) { if (shader != null) shader.setInt(name, value); }

    @Override public RenderStateScope pushFilterRenderState() {
        LegacyShader previousShader = shader;
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] textures = new int[12];
        boolean[] textureEnabled = new boolean[12];
        for (int i = 0; i < textures.length; i++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureEnabled[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        }
        RenderSystem.activeTexture(active);
        FloatBuffer color = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, color);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = isDepthTestEnabled(), write = isDepthMaskEnabled(), cull = isCullEnabled();
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        int sr = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dr = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sa = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), da = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        int alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        boolean rescale = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL), fog = GL11.glIsEnabled(GL11.GL_FOG);
        return new RenderStateScope() {
            private boolean closed;
            @Override public void close() {
                if (closed) return;
                closed = true;
                shader = previousShader;
                GL20.glUseProgram(program);
                for (int i = 0; i < textures.length; i++) {
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
                    RenderSystem.bindTexture(textures[i]);
                    if (textureEnabled[i]) RenderSystem.enableTexture(); else RenderSystem.disableTexture();
                }
                RenderSystem.activeTexture(active);
                RenderSystem.color4f(color.get(0), color.get(1), color.get(2), color.get(3));
                setBlendFuncSeparate(sr, dr, sa, da);
                if (blend) enableBlend(); else disableBlend();
                if (depth) enableDepthTest(); else disableDepthTest();
                setDepthMask(write);
                setDepthFunc(depthFunc);
                if (cull) enableCull(); else disableCull();
                if (alpha) RenderSystem.enableAlphaTest(); else RenderSystem.disableAlphaTest();
                if (lighting) RenderSystem.enableLighting(); else RenderSystem.disableLighting();
                if (rescale) RenderSystem.enableRescaleNormal(); else RenderSystem.disableRescaleNormal();
                if (fog) RenderSystem.enableFog(); else RenderSystem.disableFog();
                RenderSystem.alphaFunc(alphaFunc, alphaRef);
                RenderSystem.shadeModel(shadeModel);
            }
        };
    }
}
