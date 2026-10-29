package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.dom.ForgeDocumentExpander;
import com.sighs.apricityui.spi.AuiServices;

/**
 * Registers the loader-side service implementations.
 *
 * <p>Loaded lazily by {@link AuiServices} on first access, so headless test JVMs
 * that have this class on the classpath receive the real implementations while
 * environments without the loader fall back to the safe defaults.</p>
 */
public final class AuiServicesBootstrap {
    static {
        AuiServices.setNetwork(NetworkService.INSTANCE);
        AuiServices.setExpander(new ForgeDocumentExpander());
        AuiServices.setConfig(ConfigService.INSTANCE);
        AuiServices.setScript(ScriptService.INSTANCE);
        // Items 引用客户端专属类（blaze3d/Minecraft），只能在客户端注册
        // （见 ClientServicesBootstrap）；否则专用服务器加载本类时会
        // NoClassDefFoundError 并导致整个服务器崩溃。
    }

    private AuiServicesBootstrap() {
    }

    /**
     * Explicit trigger from the mod entry point. Referencing this method forces
     * the static initializer above to run, registering the real services.
     */
    public static void init() {
    }
}
