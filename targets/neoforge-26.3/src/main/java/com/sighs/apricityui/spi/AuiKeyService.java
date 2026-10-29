package com.sighs.apricityui.spi;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Loader-side keybinding access.
 *
 * <p>Keybinding registration (NeoForge {@code KeyMapping}) is loader-specific, so
 * {@code common} reads the bindings it needs through this interface. The loader
 * target implements it from its own keybinding registry.</p>
 *
 * <p>On 26.3 both the event key codes and the {@code KeyMapping} values are SDL
 * <em>scancodes</em> ({@link InputConstants#KEY_A} is {@code 4}), so the two sides of
 * every comparison below live in the same space.</p>
 */
public interface AuiKeyService {
    /**
     * Whether an input event's key code matches a shortcut binding.
     *
     * <p>Both sides are SDL scancodes on 26.3. An unbound shortcut reports {@code -1}
     * (AUI's own sentinel, produced by the loader target), and input carrying the host's
     * unknown code {@link InputConstants#UNKNOWN} ({@code 0}) must not be confused with
     * it. A bare {@code eventKey == boundKey} therefore makes every unbound shortcut fire
     * on unknown input — e.g. Right Shift opening DevTools. Unknown event keys and unbound
     * bindings never match.</p>
     *
     * @param eventKey the SDL scancode carried by the input event
     * @param boundKey the shortcut's current binding (SDL scancode), or {@code -1} when unbound
     */
    static boolean matches(int eventKey, int boundKey) {
        return eventKey != InputConstants.UNKNOWN.getValue()
                && boundKey != -1
                && eventKey == boundKey;
    }

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

    /**
     * Current key code (SDL scancode) of the DevTools keybinding, or {@code -1} when unbound.
     */
    int devToolsKey();

    /**
     * Current key code (SDL scancode) of the resource-manager keybinding, or {@code -1} when unbound.
     */
    int resourceManagerKey();

    /**
     * Current key code (SDL scancode) of the reload keybinding, or {@code -1} when unbound.
     */
    int reloadKey();
}
