package com.sighs.apricityui.spi;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * Reads a binding's <em>physical</em> state straight from SDL3.
 *
 * <p>{@link net.minecraft.client.KeyMapping#isDown()} is only maintained while
 * Minecraft itself feeds the key through its input channel: the down state is
 * cleared when a Screen opens, and consumed/repeated key events can leave it
 * stale. A shortcut that only needs "is this key held right now" — like the
 * hold-to-release-mouse binding, which defaults to Left Alt — should therefore
 * poll the input device directly instead of going through Minecraft's key-mapping
 * state machine, which otherwise makes it fight with other mods' Alt combos. The
 * {@code KeyMapping} stays the source of truth for <em>which</em> key is bound,
 * so players can keep rebinding it from the controls screen.</p>
 *
 * <p>26.3 polls SDL rather than GLFW, and neither call needs a window handle any
 * more: {@code SDL_GetKeyboardState} / {@code SDL_GetMouseState} are process
 * global. Both are only meaningful while the game window has keyboard focus,
 * exactly like the old {@code glfwGetKey}/{@code glfwGetMouseButton}, and both are
 * main-thread APIs — callers on other threads keep their existing guards.</p>
 */
public final class PhysicalKeyState {
    private PhysicalKeyState() {
    }

    /**
     * @param key a binding's key as reported by the loader's {@code KeyMapping}
     * @return whether that key is physically held right now; a {@code null} or
     * unbound key is never held
     */
    public static boolean isDown(InputConstants.Key key) {
        if (key == null) return false;
        try {
            if (key.getType() == InputConstants.Type.MOUSE) {
                // SDL button ids are 1-based (1 = left, 2 = middle, 3 = right, 4/5 = X1/X2) and
                // the state mask has bit (button - 1) set. GLFW used 0-based ids here before 26.3.
                int button = key.getValue();
                if (button < 1 || button > 32) return false;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FloatBuffer x = stack.mallocFloat(1);
                    FloatBuffer y = stack.mallocFloat(1);
                    int mask = SDLMouse.SDL_GetMouseState(x, y);
                    return (mask & (1 << (button - 1))) != 0;
                }
            }
            if (key.getType() == InputConstants.Type.KEYBOARD) {
                // Keyboard values are SDL scancodes on 26.3; isKeyDown indexes SDL_GetKeyboardState.
                int scancode = key.getValue();
                return scancode != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(scancode);
            }
        } catch (Throwable ignored) {
            // SDL is not initialised yet / already shut down, or we are off the main thread.
            return false;
        }
        // 26.3's InputConstants.Type has only KEYBOARD and MOUSE.
        return false;
    }
}
