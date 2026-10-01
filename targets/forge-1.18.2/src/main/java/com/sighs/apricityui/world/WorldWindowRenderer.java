package com.sighs.apricityui.world;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.render.MatrixBridge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge world-render hook for {@link WorldWindow}.
 *
 * <p>The render invocation (which event, when, and which matrices to pass) is
 * loader/version-specific, so it lives in the loader target; the shared window
 * data, registry and {@link WorldWindow#render} logic stay in {@code common}.
 * Other loaders provide their own hook that calls {@code window.render(...)}.</p>
 */
@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class WorldWindowRenderer {
    private WorldWindowRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (WorldWindow.windows.isEmpty()) return;

        for (WorldWindow window : WorldWindow.windows) {
            // 1.19.2 的 RenderLevelStageEvent#getProjectionMatrix 还是 com.mojang.math.Matrix4f，
            // 而 WorldWindow#render（common）按 org.joml 书写。
            window.render(
                    event.getPoseStack(),
                    MatrixBridge.fromMojang(event.getProjectionMatrix()),
                    event.getPartialTick()
            );
        }
    }
}
