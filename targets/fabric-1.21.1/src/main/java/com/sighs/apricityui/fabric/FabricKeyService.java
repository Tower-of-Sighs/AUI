package com.sighs.apricityui.fabric;

import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.spi.AuiKeyService;

public final class FabricKeyService implements AuiKeyService {
    public static final FabricKeyService INSTANCE = new FabricKeyService();
    private FabricKeyService() { }
    public boolean isReleaseMouseDown() { return Keybindings.RELEASE_MOUSE.isDown(); }
    public int devToolsKey() { return Keybindings.DEV_TOOLS.getDefaultKey().getValue(); }
    public int resourceManagerKey() { return Keybindings.RESOURCE_MANAGER.getDefaultKey().getValue(); }
    public int reloadKey() { return Keybindings.RELOAD.getDefaultKey().getValue(); }
}
