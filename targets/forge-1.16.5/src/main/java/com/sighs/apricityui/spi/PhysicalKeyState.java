package com.sighs.apricityui.spi;

import net.minecraft.client.util.InputMappings;
import org.lwjgl.glfw.GLFW;

public final class PhysicalKeyState {
    private PhysicalKeyState() {
    }

    public static boolean isDown(InputMappings.Input key) {
        if (key == null) return false;
        long window = AuiServices.client().getWindowHandle();
        if (window == 0L) return false;
        if (key.getType() == InputMappings.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputMappings.Type.KEYSYM) {
            return key.getValue() != GLFW.GLFW_KEY_UNKNOWN
                    && GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }
}
