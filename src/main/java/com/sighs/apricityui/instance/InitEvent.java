package com.sighs.apricityui.instance;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.LocalStorage;
import com.sighs.apricityui.init.Window;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;

@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class InitEvent {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void localStorageInit(FMLClientSetupEvent event) {
        try {
            Window.window.localStorage.localStorage = NbtIo.readCompressed(LocalStorage.LOCAL_STORAGE_FILE_PATH, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            //文件不存在
            Window.window.localStorage.save();
        }
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
