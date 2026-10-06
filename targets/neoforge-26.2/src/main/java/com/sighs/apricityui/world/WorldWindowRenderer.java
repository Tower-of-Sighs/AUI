package com.sighs.apricityui.world;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.neoforge.RenderService;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * NeoForge 26.1 world-render hook for {@link WorldWindow}.
 *
 * <p>26.1 splits the old stage enum into per-stage sub-events and no longer
 * hands out the projection matrix / partial tick directly; both are recovered
 * from the level render state and the game's delta tracker. The stored AUI
 * projection is synced to the world projection first so filter passes inside
 * world documents restore the right matrix.</p>
 */
@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class WorldWindowRenderer {
    private WorldWindowRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (WorldWindow.windows.isEmpty()) return;

        Matrix4f projectionMatrix = event.getLevelRenderState().cameraRenderState.projectionMatrix;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RenderService.INSTANCE.setProjectionMatrix(new Matrix4f(projectionMatrix));

        for (WorldWindow window : WorldWindow.windows) {
            window.render(event.getPoseStack(), projectionMatrix, partialTick);
        }
    }
}
