package com.sighs.apricityui.registry;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.glfw.GLFW;

public class Keybindings {
    public static final KeyBinding RELOAD = new KeyBinding("key.apricityui.reload",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputMappings.Type.KEYSYM,
            GLFW.GLFW_KEY_END,
            "key.categories.apricityui"
    );

    public static void registerKeyMapping() {
        ClientRegistry.registerKeyBinding(RELOAD);
    }
}
