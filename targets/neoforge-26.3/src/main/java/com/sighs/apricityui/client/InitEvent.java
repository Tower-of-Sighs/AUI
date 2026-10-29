package com.sighs.apricityui.client;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.util.LocalStorage;
import com.sighs.apricityui.init.Window;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class InitEvent {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void localStorageInit(FMLClientSetupEvent event) {
        Window.window.localStorage.load();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tickCounter++;

        if (tickCounter >= 5000) {
            tickCounter = 0;
            Window.window.localStorage.save();
        }
    }
}
