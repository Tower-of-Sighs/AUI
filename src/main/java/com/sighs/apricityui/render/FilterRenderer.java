package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sighs.apricityui.style.Filter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.Stack;

public class FilterRenderer {

    private static final Stack<RenderTarget> framebufferStack = new Stack<>();
    private static ShaderInstance filterShader;

    // 初始化Shader (在Mod加载ClientProxy或事件中调用)
    public static void initShader(net.minecraft.server.packs.resources.ResourceProvider resourceProvider) {
        try {
            // 注意：这里需要你根据实际 assets 路径配置 shader json
            // 假设你的 shader json 叫 "ui_filter"
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

        // 创建或获取一个新的 Framebuffer
        // 注意：生产环境应该使用对象池来复用 RenderTarget，避免频繁 new
        RenderTarget newTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        newTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        newTarget.clear(Minecraft.ON_OSX);

        // 绑定新的 FBO
        newTarget.bindWrite(true);

        framebufferStack.push(newTarget);

        // 必须重置投影矩阵，因为 FBO 通常是 0..1 或者 0..width 坐标系，这里我们保持和屏幕一致
        RenderSystem.backupProjectionMatrix();
    }

    public static void popFilter(Filter.FilterState state) {
        if (framebufferStack.isEmpty()) return;

        RenderTarget currentTarget = framebufferStack.pop();

        // 恢复之前的 FBO (可能是屏幕，也可能是上一层 Filter)
        if (framebufferStack.isEmpty()) {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        } else {
            framebufferStack.peek().bindWrite(true);
        }
        RenderSystem.restoreProjectionMatrix();

        // 绘制刚才的 FBO 内容到当前 FBO，并应用 Shader
        drawTextureWithFilter(currentTarget, state);

        // 清理
        currentTarget.destroyBuffers();
    }

    private static void drawTextureWithFilter(RenderTarget target, Filter.FilterState state) {
        if (filterShader == null) return;

        RenderSystem.setShader(() -> filterShader);

        // --- 关键修复：手动注入矩阵 ---
        // 即使 ShaderInstance 会尝试自动绑定，手动设置能确保万无一失
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

        // 绑定 FBO 的纹理
        RenderSystem.setShaderTexture(0, target.getColorTextureId());

        // --- 关键修复：渲染状态 ---
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest(); // 必须禁用深度测试，否则全屏 Quad 可能会被剔除

        Tesselator tesselator = Tesselator.getInstance();

        Minecraft mc = Minecraft.getInstance();
        float width = (float) mc.getWindow().getGuiScaledWidth();
        float height = (float) mc.getWindow().getGuiScaledHeight();

        // 纹理坐标 (根据 Minecraft FBO 特性，可能需要 V 轴翻转，如果画面倒置请交换 v0/v1)
        float u0 = 0f;
        float u1 = 1f;
        float v0 = 1f; // 这里的 1 和 0 可能需要互换，取决于 GL 版本和 FBO 类型
        float v1 = 0f;

        Matrix4f mat = RenderSystem.getModelViewMatrix();

        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // 注意：Vertex 顺序必须是 CCW (逆时针) 或者关闭 CullFace
        // 下面是标准顺序
        bufferbuilder.addVertex(mat, 0, height, 0).setUv(u0, v0).setColor(255, 255, 255, 255);
        bufferbuilder.addVertex(mat, width, height, 0).setUv(u1, v0).setColor(255, 255, 255, 255);
        bufferbuilder.addVertex(mat, width, 0, 0).setUv(u1, v1).setColor(255, 255, 255, 255);
        bufferbuilder.addVertex(mat, 0, 0, 0).setUv(u0, v1).setColor(255, 255, 255, 255);

        // 恢复状态
        RenderSystem.enableDepthTest();
    }

    private static void safeSetUniform(String name, float... values) {
        // 具体的 Uniform 获取和设置逻辑，根据 MC 版本略有不同
//         filterShader.getUniform(name).set(values);
    }
}