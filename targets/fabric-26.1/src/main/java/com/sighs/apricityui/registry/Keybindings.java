package com.sighs.apricityui.registry;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class Keybindings {
    // 26.1 的按键分类改为 Identifier 驱动的 KeyMapping.Category（标签键 key.category.<namespace>.<path>）。
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(ApricityUI.MODID, "apricityui"));

    public static final KeyMapping RELEASE_MOUSE = new KeyMapping("key.apricityui.release_mouse", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
    public static final KeyMapping RELOAD = new KeyMapping("key.apricityui.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping DEV_TOOLS = new KeyMapping("key.apricityui.dev_tools", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping RESOURCE_MANAGER = new KeyMapping("key.apricityui.resource_manager", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    private Keybindings() { }
}
