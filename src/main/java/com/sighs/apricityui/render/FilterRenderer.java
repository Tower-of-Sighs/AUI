package com.sighs.apricityui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.style.Filter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.vector.Matrix4f;
import org.lwjgl.opengl.GL11C;

import java.util.Stack;

public class FilterRenderer {

    private static final Stack<Framebuffer> framebufferStack = new Stack<>();
    private static Object filterShader;

    public static void initShader(net.minecraft.resources.IResourceManager resourceManager) {
        filterShader = null;
    }

    public static Object getShader() {
        return filterShader;
    }

    public static void setShader(Object instance) {
        filterShader = instance;
    }

    public static void pushFilter() {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getMainRenderTarget().width;
        int height = mc.getMainRenderTarget().height;

        Framebuffer newTarget = new Framebuffer(width, height, true, false);
        newTarget.createBuffers(width, height, true);
        newTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        newTarget.clear(false);

        newTarget.bindWrite(true);

        framebufferStack.push(newTarget);
    }

    public static void popFilter(Filter.FilterState state) {
        if (framebufferStack.isEmpty()) return;

        Framebuffer currentTarget = framebufferStack.pop();

        if (framebufferStack.isEmpty()) {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        } else {
            framebufferStack.peek().bindWrite(true);
        }

        drawTextureWithFilter(currentTarget, state);
        currentTarget.destroyBuffers();
    }

    // FIXME
    private static void drawTextureWithFilter(Framebuffer target, Filter.FilterState state) {
        // 1.16.5 无 RenderSystem.setShader，filterShader 暂不支持，仅绘制纹理（无 blur/brightness 等效果）
        RenderSystem.bindTexture(target.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuilder();

        Minecraft mc = Minecraft.getInstance();
        float width = (float) mc.getWindow().getGuiScaledWidth();
        float height = (float) mc.getWindow().getGuiScaledHeight();

        float u0 = 0f;
        float u1 = 1f;
        float v0 = 1f;
        float v1 = 0f;

        Matrix4f mat = new Matrix4f();
        mat.setIdentity();

        bufferbuilder.begin(GL11C.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        bufferbuilder.vertex(mat, 0, height, 0).uv(u0, v0).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(mat, width, height, 0).uv(u1, v0).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(mat, width, 0, 0).uv(u1, v1).color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(mat, 0, 0, 0).uv(u0, v1).color(255, 255, 255, 255).endVertex();

        tessellator.end();
        RenderSystem.enableDepthTest();
    }
}
