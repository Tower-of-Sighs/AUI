package com.sighs.apricityui.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.instance.ShaderRegistry;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.ShaderInstance;
import net.minecraft.util.math.vector.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class FilterRenderer {
    private static final Stack<Framebuffer> fboStack = new Stack<>();
    private static Framebuffer mainRenderTarget;
    private static final List<Framebuffer> fboPool = new ArrayList<>();
    private static int poolPointer = 0;

    public static void pushFilter() {
        boolean ON_OSX = Minecraft.ON_OSX;

        if (fboStack.isEmpty()) {
            mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
            poolPointer = 0;
        }

        Framebuffer temp;
        double width = Client.getWindow().getWidth();
        double height = Client.getWindow().getHeight();

        if (poolPointer < fboPool.size()) {
            temp = fboPool.get(poolPointer);
            if (temp.width != (int) width || temp.height != (int) height) {
                temp.destroyBuffers();
                temp = new Framebuffer((int) width, (int) height, true, ON_OSX);
                // --- 修复点 1: 必须手动开启 Stencil ---
                temp.enableStencil();
                fboPool.set(poolPointer, temp);
            }
        } else {
            temp = new Framebuffer((int) width, (int) height, true, ON_OSX);
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

    public static Framebuffer getCurrentTarget() {
        return fboStack.isEmpty() ? Minecraft.getInstance().getMainRenderTarget() : fboStack.peek();
    }

    public static void popFilter(Filter.FilterState state) {
        if (fboStack.isEmpty()) return;

        Framebuffer currentFbo = fboStack.pop();
        Framebuffer parentFbo = fboStack.isEmpty() ? mainRenderTarget : fboStack.peek();
        parentFbo.bindWrite(false);

        drawWithShader(currentFbo, state);
    }

    private static void drawWithShader(Framebuffer fbo, Filter.FilterState state) {
        ShaderInstance shader = ShaderRegistry.getFilterShader();

        float guiW = (float) Client.getWindow().getGuiScaledWidth();
        float guiH = (float) Client.getWindow().getGuiScaledHeight();
        Matrix4f matrix = Matrix4f.orthographic(0, guiW, guiH, 0);
        Matrix4f oldProjection = new Matrix4f(Base.getProjectionMatrix());
        Base.setProjectionMatrix(matrix);

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

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuilder();

        bufferbuilder.begin(GL11C.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        bufferbuilder.vertex(identity, 0, guiH, 0).uv(0, 0).endVertex();
        bufferbuilder.vertex(identity, guiW, guiH, 0).uv(1, 0).endVertex();
        bufferbuilder.vertex(identity, guiW, 0, 0).uv(1, 1).endVertex();
        bufferbuilder.vertex(identity, 0, 0, 0).uv(0, 1).endVertex();

        tessellator.end();

        GlStateManager._depthMask(true);
        GlStateManager._enableDepthTest();
        Base.setProjectionMatrix(oldProjection);
    }

    public static void renderBackdrop(Element target) {
        Framebuffer currentBound = fboStack.isEmpty() ? Minecraft.getInstance().getMainRenderTarget() : fboStack.peek();
        drawBackdropWithShader(currentBound, target);
    }

    private static void drawBackdropWithShader(Framebuffer sourceFbo, Element target) {
        ShaderInstance shader = ShaderRegistry.getFilterShader();
        Filter.FilterState state = Filter.getBackdropFilterOf(target);
        if (shader == null || state == null) return;

        float guiW = (float) Client.getWindow().getGuiScaledWidth();
        float guiH = (float) Client.getWindow().getGuiScaledHeight();
        Matrix4f matrix = Matrix4f.orthographic(0, guiW, guiH, 0);
        Matrix4f oldProjection = new Matrix4f(Base.getProjectionMatrix());
        Base.setProjectionMatrix(matrix);

        GlStateManager._enableBlend();
        GlStateManager._blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        setupUniforms(shader, state, sourceFbo);
        Base.setShader(shader);
        Base.setShaderTexture(0, sourceFbo.getColorTextureId());

        Rect rect = Rect.of(target);
        Position p = rect.getBodyRectPosition();
        Size s = rect.getBodyRectSize();

        // 使用 Mask 逻辑来确保 backdrop-filter 遵循 border-radius
        MatrixStack stack = new MatrixStack();
        Mask.pushMask(stack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuilder();

        bufferbuilder.begin(GL11C.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        bufferbuilder.vertex(identity, 0, guiH, 0).uv(0, 0).endVertex();
        bufferbuilder.vertex(identity, guiW, guiH, 0).uv(1, 0).endVertex();
        bufferbuilder.vertex(identity, guiW, 0, 0).uv(1, 1).endVertex();
        bufferbuilder.vertex(identity, 0, 0, 0).uv(0, 1).endVertex();

        tessellator.end();

        Mask.popMask(stack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());
        Base.setProjectionMatrix(oldProjection);
    }

    private static void setupUniforms(ShaderInstance shader, Filter.FilterState state, Framebuffer fbo) {
        if (shader.getUniform("ProjMat") != null) shader.getUniform("ProjMat").set(Base.getProjectionMatrix());
        if (shader.getUniform("BlurRadius") != null) shader.getUniform("BlurRadius").set(state.blurRadius());
        if (shader.getUniform("Brightness") != null) shader.getUniform("Brightness").set(state.brightness());
        if (shader.getUniform("Grayscale") != null) shader.getUniform("Grayscale").set(state.grayscale());
        if (shader.getUniform("Invert") != null) shader.getUniform("Invert").set(state.invert());
        if (shader.getUniform("HueRotate") != null) shader.getUniform("HueRotate").set(state.hueRotate());
        if (shader.getUniform("Opacity") != null) shader.getUniform("Opacity").set(state.opacity());
        if (shader.getUniform("InSize") != null) shader.getUniform("InSize").set((float) fbo.width, (float) fbo.height);
    }
}
