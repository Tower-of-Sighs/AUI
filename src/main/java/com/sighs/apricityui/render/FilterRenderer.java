package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.sighs.apricityui.style.Filter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.Stack;

public class FilterRenderer {

    private static final Stack<RenderTarget> framebufferStack = new Stack<>();
    private static ShaderInstance filterShader;

    public static void initShader(net.minecraft.server.packs.resources.ResourceProvider resourceProvider) {
        try {
            filterShader = new ShaderInstance(resourceProvider, "filter", DefaultVertexFormat.POSITION_TEX_COLOR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ShaderInstance getShader() {
        return filterShader;
    }

    public static void setShader(ShaderInstance instance) {
        filterShader = instance;
    }

    public static void pushFilter() {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getMainRenderTarget().width;
        int height = mc.getMainRenderTarget().height;

        RenderTarget newTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        newTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        newTarget.clear(Minecraft.ON_OSX);

        newTarget.bindWrite(true);

        framebufferStack.push(newTarget);

        RenderSystem.backupProjectionMatrix();
    }

    public static void popFilter(Filter.FilterState state) {
        if (framebufferStack.isEmpty()) return;

        RenderTarget currentTarget = framebufferStack.pop();

        if (framebufferStack.isEmpty()) {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        } else {
            framebufferStack.peek().bindWrite(true);
        }

        RenderSystem.restoreProjectionMatrix();
        drawTextureWithFilter(currentTarget, state);
        currentTarget.destroyBuffers();
    }

    private static void drawTextureWithFilter(RenderTarget target, Filter.FilterState state) {
        if (filterShader == null) return;

        RenderSystem.setShader(() -> filterShader);

        if (filterShader.MODEL_VIEW_MATRIX != null) {
            filterShader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }
        if (filterShader.PROJECTION_MATRIX != null) {
            filterShader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }

        // 设置 Uniforms
        safeSetUniform("BlurRadius", state.blurRadius());
        safeSetUniform("Brightness", state.brightness());
        safeSetUniform("Grayscale", state.grayscale());
        safeSetUniform("Invert", state.invert());
        safeSetUniform("HueRotate", state.hueRotate());

        float w = (float) target.width;
        float h = (float) target.height;
        safeSetUniform("TexelSize", 1.0f / w, 1.0f / h);

        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();

        Minecraft mc = Minecraft.getInstance();
        float width = (float) mc.getWindow().getGuiScaledWidth();
        float height = (float) mc.getWindow().getGuiScaledHeight();

        float u0 = 0f;
        float u1 = 1f;
        float v0 = 1f;
        float v1 = 0f;

        Matrix4f mat = RenderSystem.getModelViewMatrix();

        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(mat, 0, height, 0).uv(u0, v0).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(mat, width, height, 0).uv(u1, v0).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(mat, width, 0, 0).uv(u1, v1).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(mat, 0, 0, 0).uv(u0, v1).color(255, 255, 255, 255).endVertex();

        tesselator.end();
        RenderSystem.enableDepthTest();
    }

    private static void safeSetUniform(String name, float... values) {
//         filterShader.getUniform(name).set(values);
    }
}