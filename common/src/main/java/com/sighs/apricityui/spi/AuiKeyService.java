package com.sighs.apricityui.spi;

/**
 * Loader-side keybinding access.
 *
 * <p>Keybinding registration (Forge {@code KeyMapping}) is loader-specific, so
 * {@code common} reads the bindings it needs through this interface. The loader
 * target implements it from its own keybinding registry.</p>
 */
public interface AuiKeyService {
    /** Whether the release-mouse keybinding is currently held down. */
    boolean isReleaseMouseDown();

    /** Current key code of the DevTools keybinding, or {@code -1} when unbound. */
    int devToolsKey();

    /** Current key code of the resource-manager keybinding, or {@code -1} when unbound. */
    int resourceManagerKey();

    /** Current key code of the reload keybinding, or {@code -1} when unbound. */
    int reloadKey();
}
