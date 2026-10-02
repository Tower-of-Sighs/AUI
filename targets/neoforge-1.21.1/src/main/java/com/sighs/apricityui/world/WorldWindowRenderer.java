package com.sighs.apricityui.world;

import com.sighs.apricityui.ApricityUI;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

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
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (WorldWindow.windows.isEmpty()) return;

        for (WorldWindow window : WorldWindow.windows) {
            // 1.21.1 的 RenderLevelStageEvent 给的是一个只带局部变换的 PoseStack，
            // 相机视图矩阵走 getModelViewMatrix() 单独下发；只传 pose 会让剔除和
            // 命中测试漏掉视图旋转（绘制本身吃 RenderSystem 的 model-view，所以只有
            // 剔除/交互会错）。
            window.render(event.getPoseStack(), event.getModelViewMatrix(), event.getProjectionMatrix(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }
}
