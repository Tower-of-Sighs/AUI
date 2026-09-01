package com.sighs.apricityui.fabric;

import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.spi.AuiKeyService;

public final class FabricKeyService implements AuiKeyService {
    public static final FabricKeyService INSTANCE = new FabricKeyService();
    private FabricKeyService() { }
    public boolean isReleaseMouseDown() { return Keybindings.RELEASE_MOUSE.isDown(); }
    public int devToolsKey() { return keyCodeOrUnknown(Keybindings.DEV_TOOLS.getDefaultKey().getValue()); }
    public int resourceManagerKey() { return keyCodeOrUnknown(Keybindings.RESOURCE_MANAGER.getDefaultKey().getValue()); }
    public int reloadKey() { return keyCodeOrUnknown(Keybindings.RELOAD.getDefaultKey().getValue()); }

    private static int keyCodeOrUnknown(int value) {
        return value == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN ? -1 : value;
    }
}
