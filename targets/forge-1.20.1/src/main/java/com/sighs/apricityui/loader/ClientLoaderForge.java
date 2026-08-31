package com.sighs.apricityui.loader;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.debug.ExternalDebugServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge client-setup hook for {@link ClientLoader}.
 *
 * <p>The shared client-resource reload logic lives in {@code common}; only the
 * event wiring (registration, enqueue work) is loader-specific and lives here.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class ClientLoaderForge {
    private ClientLoaderForge() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // 初始加载时不重建脚本作用域；资源完成注册后由常规 reload 路径统一处理。
        event.enqueueWork(() -> {
            ExternalDebugServer.startIfEnabled();
            ClientLoader.reloadResources();
        });
    }
}
