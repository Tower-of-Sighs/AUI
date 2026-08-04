package com.sighs.apricityui.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;

/**
 * Forge world-render hook for {@link WorldWindow}.
 *
 * <p>The render invocation (which event, when, and which matrices to pass) is
 * loader/version-specific, so it lives in the loader target; the shared window
 * data, registry and {@link WorldWindow#render} logic stay in {@code common}.
 * Other loaders provide their own hook that calls {@code window.render(...)}.</p>
 */
@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class WorldWindowRenderer {
    private WorldWindowRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        // In 26.1 the "stage" is expressed as distinct sub-classes instead of an enum, so
        // NeoForge requires subscribing to the concrete stage class rather than the abstract base.
        if (WorldWindow.windows.isEmpty()) return;

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // The event no longer exposes a projection matrix; a plain model-view copy keeps the
        // render code from crashing and still gives a usable camera transform for scale/visibility.
        Matrix4f projection = new Matrix4f(event.getModelViewMatrix());
        for (WorldWindow window : WorldWindow.windows) {
            try {
                window.render(event.getPoseStack(), projection, partialTick);
            } catch (Exception ignored) {
                // World-window rendering is best-effort; never let a failing render crash the frame.
            }
        }
    }
}
