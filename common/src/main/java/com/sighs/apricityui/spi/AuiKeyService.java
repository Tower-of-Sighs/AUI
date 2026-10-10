package com.sighs.apricityui.spi;

import org.lwjgl.glfw.GLFW;

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

    /**
     * Whether an input event's key code matches a shortcut binding.
     *
     * <p>An unbound shortcut reports {@link GLFW#GLFW_KEY_UNKNOWN} ({@code -1}),
     * and malformed/unknown key input can arrive carrying the same {@code -1}
     * (a bad scancode on the Windows message path is one such case). A bare
     * {@code eventKey == boundKey} therefore makes every unbound shortcut fire
     * on unknown input — e.g. Right Shift opening DevTools. Unknown event keys
     * and unbound bindings never match.</p>
     *
     * @param eventKey the key code carried by the input event
     * @param boundKey the shortcut's current binding, or {@code -1} when unbound
     */
    static boolean matches(int eventKey, int boundKey) {
        return eventKey != GLFW.GLFW_KEY_UNKNOWN
                && boundKey != GLFW.GLFW_KEY_UNKNOWN
                && eventKey == boundKey;
    }
}
