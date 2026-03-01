package com.sighs.apricityui.instance;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.LocalStorage;
import com.sighs.apricityui.init.Window;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class InitEvent {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void localStorageInit(FMLClientSetupEvent event) {
        try {
            Window.window.localStorage.localStorage = NbtIo.readCompressed(LocalStorage.LOCAL_STORAGE_FILE_PATH);
        } catch (IOException e) {
            // 文件不存在
            Window.window.localStorage.save();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickCounter++;

            if (tickCounter >= 5000) {
                tickCounter = 0;
                Window.window.localStorage.save();
            }
        }
    }
}
