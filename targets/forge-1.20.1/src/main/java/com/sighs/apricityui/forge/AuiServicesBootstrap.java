package com.sighs.apricityui.forge;

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
        AuiServices.setClient(ClientService.INSTANCE);
        AuiServices.setNetwork(NetworkService.INSTANCE);
        AuiServices.setExpander(new ForgeDocumentExpander());
        AuiServices.setConfig(ConfigService.INSTANCE);
        AuiServices.setResources(ResourceService.INSTANCE);
        AuiServices.setKeys(KeyService.INSTANCE);
        AuiServices.setScript(ScriptService.INSTANCE);
    }

    private AuiServicesBootstrap() {
    }
}
