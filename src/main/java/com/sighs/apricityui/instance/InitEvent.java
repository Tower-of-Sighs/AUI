package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.LocalStorage;
import com.sighs.apricityui.init.Window;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;

public class InitEvent {
    private static int tickCounter = 0;

    public static void initLocalStorage() {
        try {
            Window.window.localStorage.localStorage = NbtIo.readCompressed(LocalStorage.LOCAL_STORAGE_FILE_PATH);
        } catch (IOException e) {
            //文件不存在
            Window.window.localStorage.save();
        }
    }

    public static void onClientTick() {
        tickCounter++;
        if (tickCounter >= 5000) {
            tickCounter = 0;
            Window.window.localStorage.save();
        }
    }
}
