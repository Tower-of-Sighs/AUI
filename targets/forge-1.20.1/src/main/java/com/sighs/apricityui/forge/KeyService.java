package com.sighs.apricityui.forge;

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
        return Keybindings.DEV_TOOLS.getKey().getValue();
    }

    @Override
    public int resourceManagerKey() {
        return Keybindings.RESOURCE_MANAGER.getKey().getValue();
    }

    @Override
    public int reloadKey() {
        return Keybindings.RELOAD.getKey().getValue();
    }
}
