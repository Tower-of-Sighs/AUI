package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.PointerBuffer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/** Immediate vanilla feature execution in the current AUI projection, depth and stencil domain. */
public final class FeatureRenderBridge {
    private FeatureRenderBridge() {
    }

    /** Prepare glyphs with the selected Font, not the dispatcher's default Font. */
    public static void submitText(SubmitNodeCollector collector, PoseStack pose, Font font,
                                  net.minecraft.util.FormattedCharSequence text, float x, float y,
                                  int color, boolean shadow) {
        font.prepareText(text, x, y, color, shadow, false, 0).visit(new Font.GlyphVisitor() {
            @Override
            public void acceptRenderable(TextRenderable renderable) {
                collector.submitCustomGeometry(pose, renderable.renderType(Font.DisplayMode.NORMAL),
                        (snapshot, vertices) -> renderable.render(snapshot.pose(), vertices, 15728880, false));
            }
        });
    }

    static void discard(SubmitNodeStorage storage) {
        storage.drainPhases(phase -> phase.sortInto((submit, strictlyOrdered) -> {
        }));
    }

    static void render(SubmitNodeStorage storage, RenderTarget target) {
        ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        ScissorState saved = new ScissorState(scissor);
        RenderTarget previousTarget = com.sighs.apricityui.render.OutputTargets.explicitTarget();
        Rect clip = intersect(saved.enabled() ? saved.x() : 0, saved.enabled() ? saved.y() : 0,
                saved.enabled() ? saved.width() : target.width,
                saved.enabled() ? saved.height() : target.height, target.width, target.height);
        try {
            if (target.getColorTextureView() == null || clip.empty()) return;
            com.sighs.apricityui.render.OutputTargets.setCurrent(target);
            // PreparedRenderType snapshots this state during prepareFrame.
            scissor.enable(clip.x(), clip.y(), clip.width(), clip.height());
            FeatureRenderDispatcher dispatcher = Minecraft.getInstance().gameRenderer.featureRenderDispatcher();
            try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(storage);
                 RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                         () -> "AUI immediate features", target.getColorTextureView(), Optional.empty(),
                         target.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                // CompiledRenderPipeline exposes only isClosed/close (even FrontendRenderPipeline
                // has no original definition). Intercept BEFORE compilation, at the public cache
                // boundary used by PreparedRenderType and custom feature renderers instead.
                AdaptingCache cache = new AdaptingCache();
                cache.previous = RenderSystem.setCurrentPipelineCache(cache);
                try {
                    FeatureRenderDispatcher.renderAllFeatures(new ClippedPass(pass, clip), frame);
                } finally {
                    RenderSystem.setCurrentPipelineCache(cache.previous);
                }
            }
        } finally {
            try {
                discard(storage);
            } finally {
                scissor.setFrom(saved);
                com.sighs.apricityui.render.OutputTargets.setCurrent(previousTarget);
            }
        }
    }

    /** Non-owning adapter: vanilla's cache owns compiled resources and shader reloads. */
    private static final class AdaptingCache extends PipelineCache {
        private PipelineCache previous;

        private AdaptingCache() {
            super(RenderSystem.getDevice(), null);
        }

        @Override
        public CompiledRenderPipeline get(RenderPipeline source) {
            RenderPipeline adapted = RenderService.INSTANCE.adaptPipeline(source, true);
            if (previous != null) {
                // Returning null here would let RenderSystem fall back to the UNADAPTED source.
                return java.util.Objects.requireNonNull(previous.get(adapted),
                        "Failed to compile adapted AUI feature pipeline " + source.getLocation());
            }
            // Resolve through the fallback cache without recursing into this adapter.
            // NeoForge pipeline modifiers are required to be idempotent.
            RenderSystem.setCurrentPipelineCache(null);
            try {
                return RenderSystem.getCompiledPipeline(adapted);
            } finally {
                RenderSystem.setCurrentPipelineCache(this);
            }
        }

        @Override
        public void close() {
            // No ownership: never close vanilla caches or the shared feature dispatcher.
        }
    }

    record Rect(int x, int y, int width, int height) {
        boolean empty() { return width <= 0 || height <= 0; }
    }

    static Rect intersect(int x, int y, int width, int height, int targetWidth, int targetHeight) {
        return intersect(x, y, width, height, new Rect(0, 0, Math.max(0, targetWidth), Math.max(0, targetHeight)));
    }

    static Rect intersect(int x, int y, int width, int height, Rect bounds) {
        long left = Math.max((long) x, bounds.x());
        long top = Math.max((long) y, bounds.y());
        long right = Math.min((long) x + Math.max(0, width), (long) bounds.x() + bounds.width());
        long bottom = Math.min((long) y + Math.max(0, height), (long) bounds.y() + bounds.height());
        return new Rect((int) Math.min(left, (long) bounds.x() + bounds.width()),
                (int) Math.min(top, (long) bounds.y() + bounds.height()),
                (int) Math.max(0, right - left), (int) Math.max(0, bottom - top));
    }

    /** Enforce the element clip even when third-party features change or disable scissors. */
    static final class ClippedPass implements RenderPass {
        private final RenderPass delegate;
        private final Rect bounds;
        private boolean empty;

        ClippedPass(RenderPass delegate, Rect bounds) {
            this.delegate = delegate;
            this.bounds = bounds;
            disableScissor();
        }

        @Override public void enableScissor(int x, int y, int width, int height) {
            Rect clip = intersect(x, y, width, height, bounds);
            empty = clip.empty();
            if (!empty) delegate.enableScissor(clip.x(), clip.y(), clip.width(), clip.height());
        }
        @Override public void disableScissor() { enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height()); }
        @Override public void pushDebugGroup(Supplier<String> label) { delegate.pushDebugGroup(label); }
        @Override public void popDebugGroup() { delegate.popDebugGroup(); }
        @Override public void writeTimestamp(GpuQueryPool pool, int index) { delegate.writeTimestamp(pool, index); }
        @Override public void setPipeline(CompiledRenderPipeline pipeline) { delegate.setPipeline(pipeline); }
        @Override public void setUniform(String name, GpuTextureView texture, GpuSampler sampler) { delegate.setUniform(name, texture, sampler); }
        @Override public void setUniform(String name, GpuBuffer buffer) { delegate.setUniform(name, buffer); }
        @Override public void setUniform(String name, GpuBufferSlice buffer) { delegate.setUniform(name, buffer); }
        @Override public void pushConstants(ByteBuffer value) { delegate.pushConstants(value); }
        @Override public void setViewport(int x, int y, int width, int height) { delegate.setViewport(x, y, width, height); }
        @Override public void setVertexBuffer(int slot, GpuBufferSlice buffer) { delegate.setVertexBuffer(slot, buffer); }
        @Override public void setIndexBuffer(GpuBuffer buffer, IndexType type) { delegate.setIndexBuffer(buffer, type); }
        @Override public void drawIndexed(int count, int instances, int first, int offset, int firstInstance) {
            if (!empty) delegate.drawIndexed(count, instances, first, offset, firstInstance);
        }
        @Override public void multiDrawIndexed(IntBuffer parameters, int instances, int firstInstance, int count) {
            if (!empty) delegate.multiDrawIndexed(parameters, instances, firstInstance, count);
        }
        @Override public void multiDrawIndexed(PointerBuffer offsets, IntBuffer counts, IntBuffer vertices, int count) {
            if (!empty) delegate.multiDrawIndexed(offsets, counts, vertices, count);
        }
        @Override public void drawIndexedIndirect(GpuBufferSlice commands, int count) { if (!empty) delegate.drawIndexedIndirect(commands, count); }
        @Override public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, GpuBuffer indexBuffer,
                                                      IndexType type, Collection<String> uniforms, T argument) {
            if (!empty) delegate.drawMultipleIndexed(draws, indexBuffer, type, uniforms, argument);
        }
        @Override public void draw(int count, int instances, int first, int firstInstance) {
            if (!empty) delegate.draw(count, instances, first, firstInstance);
        }
        @Override public void multiDraw(IntBuffer parameters, int instances, int firstInstance, int count) {
            if (!empty) delegate.multiDraw(parameters, instances, firstInstance, count);
        }
        @Override public void multiDraw(IntBuffer first, IntBuffer counts, int count) { if (!empty) delegate.multiDraw(first, counts, count); }
        @Override public void drawIndirect(GpuBufferSlice commands, int count) { if (!empty) delegate.drawIndirect(commands, count); }
        @Override public void close() { /* The caller owns the actual pass. */ }
    }
}
