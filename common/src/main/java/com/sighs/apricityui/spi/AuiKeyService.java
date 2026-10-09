package com.sighs.apricityui.spi;

/**
 * Loader-side keybinding access.
 *
 * <p>Keybinding registration (Forge {@code KeyMapping}) is loader-specific, so
 * {@code common} reads the bindings it needs through this interface. The loader
 * target implements it from its own keybinding registry.</p>
 */
public interface AuiKeyService {
    /**
     * Whether the release-mouse keybinding is currently held down.
     *
     * <p>Implementations must resolve <em>which</em> key is bound from the
     * loader's keybinding registry, but read the physical state directly (see
     * {@link PhysicalKeyState}) instead of {@code KeyMapping.isDown()}, so a
     * mod-key default such as Left Alt does not go through Minecraft's input
     * channel and collide with other mods' Alt combos.</p>
     */
    boolean isReleaseMouseDown();

    /** Current key code of the DevTools keybinding, or {@code -1} when unbound. */
    int devToolsKey();

    /** Current key code of the resource-manager keybinding, or {@code -1} when unbound. */
    int resourceManagerKey();

    /** Current key code of the reload keybinding, or {@code -1} when unbound. */
    int reloadKey();
}
