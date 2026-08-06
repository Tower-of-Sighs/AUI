package com.sighs.apricityui.fabric;

import com.sighs.apricityui.ApricityUI;

/** Defensive entry points used by the Fabric keyboard and mouse mixins. */
public final class FabricInput {
    private FabricInput() {
    }

    public static boolean keyPress(int key, int scanCode, int action, int modifiers) {
        if (action != org.lwjgl.glfw.GLFW.GLFW_PRESS
                && action != org.lwjgl.glfw.GLFW.GLFW_REPEAT
                && action != org.lwjgl.glfw.GLFW.GLFW_RELEASE) {
            return false;
        }
        try {
            return com.sighs.apricityui.client.Client.handleKeyInput(key, scanCode, action, modifiers);
        } catch (Throwable exception) {
            ApricityUI.LOGGER.error("[AUI Fabric] keyboard dispatch failed", exception);
            return false;
        }
    }

    public static boolean charTyped(int codePoint) {
        try {
            return com.sighs.apricityui.client.Client.handleCharTyped(codePoint);
        } catch (Throwable exception) {
            ApricityUI.LOGGER.error("[AUI Fabric] character dispatch failed", exception);
            return false;
        }
    }

    public static boolean mouseButton(int button, int action) {
        try {
            return com.sighs.apricityui.client.Client.handleMouseButton(button, action);
        } catch (Throwable exception) {
            ApricityUI.LOGGER.error("[AUI Fabric] mouse button dispatch failed", exception);
            return false;
        }
    }

    public static boolean mouseScroll(double delta) {
        try {
            return com.sighs.apricityui.client.Client.handleMouseScroll(delta);
        } catch (Throwable exception) {
            ApricityUI.LOGGER.error("[AUI Fabric] mouse scroll dispatch failed", exception);
            return false;
        }
    }
}
