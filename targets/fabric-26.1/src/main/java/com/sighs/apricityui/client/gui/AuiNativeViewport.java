package com.sighs.apricityui.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import com.sighs.apricityui.mixin.accessor.GuiGraphicsExtractorAccessor;
import org.jspecify.annotations.Nullable;

/** Composites a native GPU scene into one GUI rectangle after its surrounding UI. */
public final class AuiNativeViewport {
    @FunctionalInterface
    public interface Draw {
        /** Complete all passes, returning false without drawing while a replacement frame is unready. */
        boolean render(RenderTarget target, int width, int height);
    }

    public static void register() {
        PictureInPictureRendererRegistry.register(context -> new Renderer(context.bufferSource()));
    }

    public static void submit(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Draw draw) {
        if (width <= 0 || height <= 0) return;
        ((GuiGraphicsExtractorAccessor) (Object) graphics).aui$guiRenderState().addPicturesInPictureState(
                new State(x, y, x + width, y + height, graphics.scissorStack.peek(), draw));
    }

    public record State(int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea, Draw draw)
            implements PictureInPictureRenderState {
        @Override public float scale() { return 1; }
        @Override public @Nullable ScreenRectangle bounds() {
            return PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
        }
    }

    private static final class Renderer extends PictureInPictureRenderer<State> {
        private TextureTarget target;
        private boolean populated;
        private Renderer(MultiBufferSource.BufferSource bufferSource) { super(bufferSource); }
        @Override public Class<State> getRenderStateClass() { return State.class; }
        @Override protected String getTextureLabel() { return "AUI native viewport"; }
        @Override protected void renderToTexture(State state, PoseStack pose) {
            var output = RenderSystem.outputColorTextureOverride;
            if (output == null) throw new IllegalStateException("Native viewport requires a PIP color attachment");
            int width = output.texture().getWidth(0), height = output.texture().getHeight(0);
            if (target == null || target.width != width || target.height != height) {
                if (target != null) target.destroyBuffers();
                target = new TextureTarget("AUI native viewport", width, height, true);
                populated = false;
            }
            if (state.draw().render(target, state.x1() - state.x0(), state.y1() - state.y0())) populated = true;
            if (populated) RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    target.getColorTexture(), output.texture(), 0, 0, 0, 0, 0, width, height);
        }
        @Override public void close() {
            if (target != null) target.destroyBuffers();
            super.close();
        }
    }
    private AuiNativeViewport() { }
}
