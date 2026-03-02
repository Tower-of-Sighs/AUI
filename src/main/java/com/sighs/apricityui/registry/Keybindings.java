package com.sighs.apricityui.registry;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = ApricityUI.MODID)
public class Keybindings {
    public static final KeyBinding RELOAD = new KeyBinding("key.apricityui.reload",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputMappings.Type.KEYSYM,
            GLFW.GLFW_KEY_END,
            "key.categories.apricityui"
    );

    @SubscribeEvent
    public static void registerKeyMapping(final FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(RELOAD);
    }
}
