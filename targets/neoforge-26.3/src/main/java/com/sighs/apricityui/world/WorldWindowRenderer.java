package com.sighs.apricityui.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.neoforge.RenderService;
import com.sighs.apricityui.render.OutputTargets;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * NeoForge 26.3 world hook, after both classic transparency and OIT have closed
 * their passes. AUI uploads buffers and opens its own passes, which is forbidden
 * inside AfterTranslucentBlocks (and that event does not run with OIT).
 */
@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class WorldWindowRenderer {
    private WorldWindowRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent.AfterLevel event) {
        Matrix4f projectionMatrix = WorldProjection.FRAME.take();
        if (WorldWindow.windows.isEmpty() || projectionMatrix == null) return;

        Matrix4f viewMatrix = new Matrix4f(event.getModelViewMatrix());
        float partialTick = event.getLevelRenderState().worldPartialTicks;
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try (var ignored = RenderService.INSTANCE.pushWorldProjection(projectionMatrix)) {
            // LevelRenderer has popped its camera stack before AfterLevel. Set both
            // GPU view and common's CPU view explicitly; neither includes the model.
            modelView.set(viewMatrix);
            OutputTargets.setCurrent(Minecraft.getInstance().gameRenderer.mainRenderTarget());
            for (WorldWindow window : java.util.List.copyOf(WorldWindow.windows)) {
                event.getPoseStack().pushPose();
                try {
                    window.render(event.getPoseStack(), viewMatrix, projectionMatrix, partialTick);
                } finally {
                    event.getPoseStack().popPose();
                }
            }
        } finally {
            modelView.popMatrix();
        }
    }
}
