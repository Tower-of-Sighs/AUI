package com.sighs.apricityui.world;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.render.MatrixBridge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class WorldWindowRenderer {
    private WorldWindowRenderer() { }
    @SubscribeEvent public static void onRenderWorld(RenderWorldLastEvent event) {
        if (WorldWindow.windows.isEmpty()) return;
        for (WorldWindow window : WorldWindow.windows)
            window.render(event.getMatrixStack(), MatrixBridge.fromMojang(event.getProjectionMatrix()), event.getPartialTicks());
    }
}
