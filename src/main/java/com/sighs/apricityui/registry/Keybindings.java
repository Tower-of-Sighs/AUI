package com.sighs.apricityui.registry;

import cc.sighs.oelib.registry.extra.KeyMappingRegister;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class Keybindings {
    public static final KeyMapping RELOAD = new KeyMapping("key.apricityui.reload",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_END,
            "key.categories.apricityui"
    );

    public static void registerKeyMapping() {
        KeyMappingRegister.register(RELOAD);
    }
}
