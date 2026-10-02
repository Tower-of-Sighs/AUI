package com.sighs.apricityui.registry;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
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
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.apricityui"
    );

    public static final KeyMapping DEV_TOOLS = new KeyMapping("key.apricityui.dev_tools",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.apricityui"
    );

    public static final KeyMapping RESOURCE_MANAGER = new KeyMapping("key.apricityui.resource_manager",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.apricityui"
    );

    // 1.18.2 的 Forge 还没有 RegisterKeyMappingsEvent（1.19 才加），按键只能在
    // FMLClientSetupEvent 里经 ClientRegistry.registerKeyBinding 注册；KeyMapping 本身
    // 带 KeyConflictContext/KeyModifier 的构造器 1.18.2 已经具备，故只改注册路径。
    @SubscribeEvent
    public static void registerKeyMapping(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(RELEASE_MOUSE);
        ClientRegistry.registerKeyBinding(RELOAD);
        ClientRegistry.registerKeyBinding(DEV_TOOLS);
        ClientRegistry.registerKeyBinding(RESOURCE_MANAGER);
    }
}
