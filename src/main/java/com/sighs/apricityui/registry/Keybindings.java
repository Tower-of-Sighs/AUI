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
    public static final KeyMapping RELOAD = new KeyMapping("key.apricityui.reload",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.apricityui"
    );

    @SubscribeEvent
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(RELOAD);
    }
}
