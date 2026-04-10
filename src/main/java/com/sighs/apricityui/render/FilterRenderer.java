package com.sighs.apricityui.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.ShaderRegistry;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@SuppressWarnings("resource")
public final class FilterRenderer {
    private static final Deque<FilterLayer> FILTER_STACK = new ArrayDeque<>();
    private static final List<TextureTarget> FILTER_POOL = new ArrayList<>();
    private static int poolPointer = 0;

    private static final List<TextureTarget> BACKDROP_POOL = new ArrayList<>();
    private static int backdropPointer = 0;

    private static final int FILTER_UBO_SIZE = new Std140SizeCalculator()
            .putVec2() // InSize
            .putVec2() // GuiSize
            .putFloat() // BlurRadius
            .putFloat() // Brightness
            .putFloat() // Grayscale
            .putFloat() // Invert
            .putFloat() // HueRotate
            .putFloat() // Opacity
            .putVec2() // ShadowOffset
            .putFloat() // ShadowBlur
            .putVec4() // ShadowColor
            .putFloat() // ForceAlpha
            .putVec4() // ClipRect
            .putVec4() // ClipRadii
            .putFloat() // ClipEnabled
            .get();

    private static MappableRingBuffer filterUbo;

    private FilterRenderer() {
    }

    public static void beginFrame() {
        if (!FILTER_STACK.isEmpty()) {
            FILTER_STACK.clear();
        }
        poolPointer = 0;
        backdropPointer = 0;
        ensureUbo();
    }

    public static void endFrame() {
        if (!FILTER_STACK.isEmpty()) {
            FILTER_STACK.clear();
        }
    }

    public static void pushFilter() {
        ImageDrawer.flushBatch();
        Graph.endBatch();

        int width = currentColorView().getWidth(0);
        int height = currentColorView().getHeight(0);

        TextureTarget temp = acquireFilterTarget(width, height);
        clearTarget(temp);

        FilterLayer layer = new FilterLayer(
                temp,
                RenderSystem.outputColorTextureOverride,
                RenderSystem.outputDepthTextureOverride,
                Mask.suspendForOffscreen()
        );

        FILTER_STACK.push(layer);

        RenderSystem.outputColorTextureOverride = temp.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = temp.getDepthTextureView();
    }

    public static void popFilter(Filter.FilterState state) {
        if (FILTER_STACK.isEmpty()) return;

        ImageDrawer.flushBatch();
        Graph.endBatch();

        FilterLayer layer = FILTER_STACK.pop();

        // 首先恢复之前的渲染目标和遮罩状态；滤镜输出应当遵循父级的裁剪
        RenderSystem.outputColorTextureOverride = layer.prevColorOverride;
        RenderSystem.outputDepthTextureOverride = layer.prevDepthOverride;
        Mask.restore(layer.savedMaskState);

        applyFilter(layer.target, state, false, null);
    }

    public static void renderBackdrop(Element target, PoseStack poseStack) {
        if (target == null) return;
        Filter.FilterState state = Filter.getBackdropFilterOf(target);
        if (state == null || state.isEmpty()) return;

        ImageDrawer.flushBatch();
        Graph.endBatch();

        int width = currentColorView().getWidth(0);
        int height = currentColorView().getHeight(0);

        TextureTarget sourceCopy = acquireBackdropTarget(width, height);
        clearTarget(sourceCopy);
        blitToTarget(currentColorView(), sourceCopy);

        Rect rect = Rect.of(target);
        applyFilter(sourceCopy, state, true, rect);
    }

    private static void applyFilter(TextureTarget input, Filter.FilterState state, boolean forceAlpha, Rect clipRect) {
        if (input == null || input.getColorTextureView() == null) return;
        if (state == null) state = Filter.FilterState.EMPTY;

        int outW = currentColorView().getWidth(0);
        int outH = currentColorView().getHeight(0);

        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        float guiW = (float) (outW / guiScale);
        float guiH = (float) (outH / guiScale);

        writeFilterUbo(state, input.width, input.height, guiW, guiH, forceAlpha, clipRect);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuTextureView outColor = currentColorView();
        GpuTextureView outDepth = currentDepthViewOrNull();

        int stencilMask = Mask.getActiveStencilMask();

        try (RenderPass pass = encoder.createRenderPass(
                () -> "AUI Filter Pass",
                outColor,
                OptionalInt.empty(),
                outDepth,
                OptionalDouble.empty()
        )) {
            pass.setViewport(0, 0, outW, outH);
            Mask.applyScissorToRenderPass(pass, outH, guiScale);
            pass.setPipeline(ShaderRegistry.filterPipeline(stencilMask));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("ApricityFilter", new GpuBufferSlice(filterUbo.currentBuffer(), 0, FILTER_UBO_SIZE));
            pass.bindTexture("InSampler", input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, 3);
        }

        filterUbo.rotate();
    }

    private static void blitToTarget(GpuTextureView source, TextureTarget dest) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "AUI Backdrop Copy", dest.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(RenderPipelines.TRACY_BLIT);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
    }

    private static TextureTarget acquireFilterTarget(int width, int height) {
        TextureTarget target;
        if (poolPointer < FILTER_POOL.size()) {
            target = FILTER_POOL.get(poolPointer);
            if (target.width != width || target.height != height) {
                target.resize(width, height);
            }
        } else {
            target = new TextureTarget("AUI FilterTarget", width, height, true, true);
            FILTER_POOL.add(target);
        }
        poolPointer++;
        return target;
    }

    private static TextureTarget acquireBackdropTarget(int width, int height) {
        TextureTarget target;
        if (backdropPointer < BACKDROP_POOL.size()) {
            target = BACKDROP_POOL.get(backdropPointer);
            if (target.width != width || target.height != height) {
                target.resize(width, height);
            }
        } else {
            target = new TextureTarget("AUI BackdropTarget", width, height, true, true);
            BACKDROP_POOL.add(target);
        }
        backdropPointer++;
        return target;
    }

    private static void clearTarget(RenderTarget target) {
        if (target == null) return;
        GpuTexture color = target.getColorTexture();
        GpuTexture depth = target.getDepthTexture();
        if (color == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (depth != null) {
            encoder.clearColorAndDepthTextures(color, 0, depth, 1.0);
            try {
                encoder.clearStencilTexture(depth, 0);
            } catch (Throwable ignored) {
                // 某些深度格式可能不支持 stencil ，忽略掉
            }
        } else {
            encoder.clearColorTexture(color, 0);
        }
    }

    private static void ensureUbo() {
        if (filterUbo != null) return;
        filterUbo = new MappableRingBuffer(() -> "AUI Filter UBO", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, FILTER_UBO_SIZE);
    }

    private static void writeFilterUbo(Filter.FilterState state, int inW, int inH, float guiW, float guiH, boolean forceAlpha, Rect clipRect) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(filterUbo.currentBuffer(), false, true)) {
            Std140Builder b = Std140Builder.intoBuffer(view.data());

            b.putVec2(inW, inH);
            b.putVec2(guiW, guiH);

            b.putFloat(state.blurRadius());
            b.putFloat(state.brightness());
            b.putFloat(state.grayscale());
            b.putFloat(state.invert());
            b.putFloat(state.hueRotate());
            b.putFloat(state.opacity());

            b.putVec2(state.dropShadowX(), state.dropShadowY());
            b.putFloat(state.dropShadowBlur());

            int c = state.dropShadowColor();
            float a = ((c >>> 24) & 0xFF) / 255f;
            float r = ((c >>> 16) & 0xFF) / 255f;
            float g = ((c >>> 8) & 0xFF) / 255f;
            float bb = (c & 0xFF) / 255f;
            b.putVec4(r, g, bb, a);

            b.putFloat(forceAlpha ? 1.0f : 0.0f);

            if (clipRect != null) {
                Position p = clipRect.getBodyRectPosition();
                Size s = clipRect.getBodyRectSize();
                float[] radii = clipRect.getBodyRadius();

                b.putVec4((float) p.x, (float) p.y, (float) s.width(), (float) s.height());
                if (radii != null && radii.length >= 4) {
                    b.putVec4(radii[0], radii[1], radii[2], radii[3]);
                } else {
                    b.putVec4(0, 0, 0, 0);
                }
                b.putFloat(1.0f);
            } else {
                b.putVec4(0, 0, 0, 0);
                b.putVec4(0, 0, 0, 0);
                b.putFloat(0.0f);
            }
        }
    }

    private static GpuTextureView currentColorView() {
        if (RenderSystem.outputColorTextureOverride != null) {
            return RenderSystem.outputColorTextureOverride;
        }
        return Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
    }

    private static GpuTextureView currentDepthViewOrNull() {
        if (RenderSystem.outputDepthTextureOverride != null) {
            return RenderSystem.outputDepthTextureOverride;
        }
        var main = Minecraft.getInstance().getMainRenderTarget();
        return main.useDepth ? main.getDepthTextureView() : null;
    }

    private record FilterLayer(
            TextureTarget target,
            GpuTextureView prevColorOverride,
            GpuTextureView prevDepthOverride,
            Mask.MaskState savedMaskState
    ) {
    }
}
