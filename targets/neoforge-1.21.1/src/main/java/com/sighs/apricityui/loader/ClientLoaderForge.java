package com.sighs.apricityui.loader;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevToolsLogBridge;
import com.sighs.apricityui.dev.debug.ExternalDebugServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge client-setup hook for {@link ClientLoader}.
 *
 * <p>The shared client-resource reload logic lives in {@code common}; only the
 * event wiring (registration, enqueue work) is loader-specific and lives here.</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class ClientLoaderForge {
    private ClientLoaderForge() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        DevToolsLogBridge.install(ApricityUI.LOGGER);
        // 初始加载时不调用 ApricityJS.reload()，因为此时其他模组的客户端资源
        // （如模型层）可能尚未注册完毕，强制重载 KubeJS 客户端脚本会导致崩溃。
        event.enqueueWork(() -> {
            ExternalDebugServer.startIfEnabled();
            ClientLoader.reloadResources();
        });
    }
}
