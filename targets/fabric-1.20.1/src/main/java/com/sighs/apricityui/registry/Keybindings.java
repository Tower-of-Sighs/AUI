package com.sighs.apricityui.registry;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class Keybindings {
    public static final KeyMapping RELEASE_MOUSE = new KeyMapping("key.apricityui.release_mouse", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.apricityui");
    public static final KeyMapping RELOAD = new KeyMapping("key.apricityui.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.apricityui");
    public static final KeyMapping DEV_TOOLS = new KeyMapping("key.apricityui.dev_tools", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.apricityui");
    public static final KeyMapping RESOURCE_MANAGER = new KeyMapping("key.apricityui.resource_manager", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.apricityui");
    private Keybindings() { }
}
