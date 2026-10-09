package com.sighs.apricityui.spi;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Reads a binding's <em>physical</em> state straight from GLFW.
 *
 * <p>{@link net.minecraft.client.KeyMapping#isDown()} is only maintained while
 * Minecraft itself feeds the key through its input channel: the down state is
 * cleared when a Screen opens, and consumed/repeated key events can leave it
 * stale. A shortcut that only needs "is this key held right now" — like the
 * hold-to-release-mouse binding, which defaults to Left Alt — should therefore
 * poll GLFW directly instead of going through Minecraft's key-mapping state
 * machine, which otherwise makes it fight with other mods' Alt combos. The
 * {@code KeyMapping} stays the source of truth for <em>which</em> key is bound,
 * so players can keep rebinding it from the controls screen.</p>
 *
 * <p>The window handle comes from {@link AuiClientService#getWindowHandle()}
 * because the accessor differs across Minecraft versions.</p>
 */
public final class PhysicalKeyState {
    private PhysicalKeyState() {
    }

    /**
     * @param key a binding's key as reported by the loader's {@code KeyMapping}
     * @return whether that key is physically held right now; a {@code null} or
     *         unbound key is never held
     */
    public static boolean isDown(InputConstants.Key key) {
        if (key == null) return false;
        long window = AuiServices.client().getWindowHandle();
        if (window == 0L) return false;
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return key.getValue() != GLFW.GLFW_KEY_UNKNOWN
                    && GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        // AUI only ever binds KEYSYM/MOUSE, and GLFW has no scancode -> key lookup.
        return false;
    }
}
