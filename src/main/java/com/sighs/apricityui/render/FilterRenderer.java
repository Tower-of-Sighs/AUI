package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.instance.ShaderRegistry;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.math.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class FilterRenderer {
    private static final Stack<RenderTarget> fboStack = new Stack<>();
    private static RenderTarget mainRenderTarget;
    private static final List<RenderTarget> fboPool = new ArrayList<>();
    private static int poolPointer = 0;

    public static void beginFrame() {
        // 防御式清理：若上帧因异常或节点错配残留栈，避免 poolPointer 无界增长
        if (!fboStack.isEmpty()) {
            fboStack.clear();
        }
        mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
        poolPointer = 0;
    }

    public static void endFrame() {
        if (!fboStack.isEmpty()) {
            fboStack.clear();
            if (mainRenderTarget != null) {
                mainRenderTarget.bindWrite(false);
            }
        }
    }

    public static void pushFilter() {
        boolean ON_OSX = Minecraft.ON_OSX;

        if (fboStack.isEmpty()) {
            mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
            poolPointer = 0;
        }

        RenderTarget temp;
        double width = Client.getWindow().getWidth();
        double height = Client.getWindow().getHeight();

        if (poolPointer < fboPool.size()) {
            temp = fboPool.get(poolPointer);
            if (temp.width != (int) width || temp.height != (int) height) {
                temp.destroyBuffers();
                temp = new TextureTarget((int) width, (int) height, true, ON_OSX);
                // --- 修复点 1: 必须手动开启 Stencil ---
                temp.enableStencil();
                fboPool.set(poolPointer, temp);
            }
        } else {
            temp = new TextureTarget((int) width, (int) height, true, ON_OSX);
            // --- 修复点 1: 必须手动开启 Stencil ---
            temp.enableStencil();
            fboPool.add(temp);
        }
        poolPointer++;

        temp.setClearColor(0f, 0f, 0f, 0f);
        // 注意：这里的 clear 会清除当前绑定的 FBO 的缓冲区
        temp.clear(ON_OSX);
        fboStack.push(temp);
        temp.bindWrite(false);
    }

    public static RenderTarget getCurrentTarget() {
        return fboStack.isEmpty() ? Minecraft.getInstance().getMainRenderTarget() : fboStack.peek();
    }

    public static void popFilter(Filter.FilterState state) {
        if (fboStack.isEmpty()) return;

        RenderTarget currentFbo = fboStack.pop();
        RenderTarget parentFbo = fboStack.isEmpty() ? mainRenderTarget : fboStack.peek();
        parentFbo.bindWrite(false);

        drawWithShader(currentFbo, state);
    }

    private static void drawWithShader(RenderTarget fbo, Filter.FilterState state) {
        ShaderInstance shader = ShaderRegistry.getFilterShader();

        Matrix4f oldProjection = new Matrix4f(Base.getProjectionMatrix());

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value,
                GlStateManager.SourceFactor.ONE.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value
        );
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();

        if (shader == null) {
            Base.setPositionColorShader();
        } else {
            setupUniforms(shader, state, fbo);
            Base.setShader(shader);
        }

        Base.setShaderTexture(0, fbo.getColorTextureId());
        Base.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float guiW = (float) Client.getWindow().getGuiScaledWidth();
        float guiH = (float) Client.getWindow().getGuiScaledHeight();
        Matrix4f matrix = Matrix4f.orthographic(0.0F, guiW, guiH, 0.0F, -1000.0F, 1000.0F);
        Base.setProjectionMatrix(matrix);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();

        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(0, guiH, 0).uv(0, 0).endVertex();
        bufferbuilder.vertex(guiW, guiH, 0).uv(1, 0).endVertex();
        bufferbuilder.vertex(guiW, 0, 0).uv(1, 1).endVertex();
        bufferbuilder.vertex(0, 0, 0).uv(0, 1).endVertex();

        bufferbuilder.end();
        BufferUploader.end(bufferbuilder);

        GlStateManager._depthMask(true);
        GlStateManager._enableDepthTest();
        Base.setProjectionMatrix(oldProjection);
    }

    public static void renderBackdrop(Element target) {
        RenderTarget currentBound = fboStack.isEmpty() ? Minecraft.getInstance().getMainRenderTarget() : fboStack.peek();
        drawBackdropWithShader(currentBound, target);
    }

    private static void drawBackdropWithShader(RenderTarget sourceFbo, Element target) {
        ShaderInstance shader = ShaderRegistry.getFilterShader();
        Filter.FilterState state = Filter.getBackdropFilterOf(target);
        if (shader == null || state == null) return;

        Matrix4f oldProjection = new Matrix4f(Base.getProjectionMatrix());

        GlStateManager._enableBlend();
        GlStateManager._blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        setupUniforms(shader, state, sourceFbo);
        Base.setShader(shader);
        Base.setShaderTexture(0, sourceFbo.getColorTextureId());

        Rect rect = Rect.of(target);
        Position p = rect.getBodyRectPosition();
        Size s = rect.getBodyRectSize();

        PoseStack poseStack = new PoseStack();
        Mask.pushMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());

        float guiW = (float) Client.getWindow().getGuiScaledWidth();
        float guiH = (float) Client.getWindow().getGuiScaledHeight();
        Matrix4f matrix = Matrix4f.orthographic(0.0F, guiW, guiH, 0.0F, -1000.0F, 1000.0F);
        Base.setProjectionMatrix(matrix);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();

        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(0, guiH, 0).uv(0, 0).endVertex();
        bufferbuilder.vertex(guiW, guiH, 0).uv(1, 0).endVertex();
        bufferbuilder.vertex(guiW, 0, 0).uv(1, 1).endVertex();
        bufferbuilder.vertex(0, 0, 0).uv(0, 1).endVertex();

        bufferbuilder.end();
        BufferUploader.end(bufferbuilder);

        Mask.popMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());
        Base.setProjectionMatrix(oldProjection);
    }

    private static void setupUniforms(ShaderInstance shader, Filter.FilterState state, RenderTarget fbo) {
        if (shader.getUniform("BlurRadius") != null) shader.getUniform("BlurRadius").set(state.blurRadius());
        if (shader.getUniform("Brightness") != null) shader.getUniform("Brightness").set(state.brightness());
        if (shader.getUniform("Grayscale") != null) shader.getUniform("Grayscale").set(state.grayscale());
        if (shader.getUniform("Invert") != null) shader.getUniform("Invert").set(state.invert());
        if (shader.getUniform("HueRotate") != null) shader.getUniform("HueRotate").set(state.hueRotate());
        if (shader.getUniform("Opacity") != null) shader.getUniform("Opacity").set(state.opacity());
        if (shader.getUniform("InSize") != null) shader.getUniform("InSize").set((float) fbo.width, (float) fbo.height);
    }
}
