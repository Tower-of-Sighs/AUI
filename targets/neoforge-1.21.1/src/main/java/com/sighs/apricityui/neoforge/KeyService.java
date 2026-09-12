package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.spi.AuiKeyService;

/**
 * Forge implementation of {@link AuiKeyService}, backed by the loader's
 * {@link Keybindings} {@code KeyMapping} registry.
 */
public final class KeyService implements AuiKeyService {
    public static final KeyService INSTANCE = new KeyService();

    private KeyService() {
    }

    @Override
    public boolean isReleaseMouseDown() {
        return Keybindings.RELEASE_MOUSE.isDown();
    }

    @Override
    public int devToolsKey() {
        return keyCodeOrUnknown(Keybindings.DEV_TOOLS);
    }

    @Override
    public int resourceManagerKey() {
        return keyCodeOrUnknown(Keybindings.RESOURCE_MANAGER);
    }

    @Override
    public int reloadKey() {
        return keyCodeOrUnknown(Keybindings.RELOAD);
    }

    private static int keyCodeOrUnknown(net.minecraft.client.KeyMapping mapping) {
        int value = mapping.getKey().getValue();
        // An unbound KeyMapping reports GLFW_KEY_UNKNOWN (-1). Return the same
        // sentinel as the headless AuiKeyService so shortcut comparisons never
        // match a real key code.
        return value == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN ? -1 : value;
    }
}
