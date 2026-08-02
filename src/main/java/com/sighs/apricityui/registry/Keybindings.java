package com.sighs.apricityui.registry;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = ApricityUI.MODID)
public class Keybindings {
    public static final KeyMapping RELEASE_MOUSE = new KeyMapping("key.apricityui.release_mouse",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.apricityui"
    );

    public static final KeyMapping RELOAD = new KeyMapping("key.apricityui.reload",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_END,
            "key.categories.apricityui"
    );

    public static final KeyMapping DEV_TOOLS = new KeyMapping("key.apricityui.dev_tools",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            "key.categories.apricityui"
    );

    public static final KeyMapping RESOURCE_MANAGER = new KeyMapping("key.apricityui.resource_manager",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            "key.categories.apricityui"
    );

    @SubscribeEvent
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(RELEASE_MOUSE);
        event.register(RELOAD);
        event.register(DEV_TOOLS);
        event.register(RESOURCE_MANAGER);
    }
}
