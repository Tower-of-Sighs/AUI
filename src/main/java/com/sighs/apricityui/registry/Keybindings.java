package com.sighs.apricityui.registry;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public class Keybindings {
    public static final KeyMapping RELOAD = new KeyMapping("key.apricityui.reload",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.apricityui"
    );

    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(RELOAD);
    }
}
