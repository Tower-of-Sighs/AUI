package com.sighs.apricityui.init;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;

public class LocalStorage {
    public CompoundTag localStorage = new CompoundTag();

    //文件存储位置
    public static final File LOCAL_STORAGE_FILE_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(ApricityUI.MOD_ID)
            .resolve("localStorage.nbt")
            .toFile();

    public void save(){
        try{
            File parentDir = LOCAL_STORAGE_FILE_PATH.getParentFile();

            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    ApricityUI.LOGGER.error("Failed to create config directory for LocalStorage: {}", parentDir.getAbsolutePath());
                    return;
                }
            }

            NbtIo.writeCompressed(localStorage, LOCAL_STORAGE_FILE_PATH.toPath());
        }catch (IOException e){
            ApricityUI.LOGGER.error("Failed to save LocalStorage data to {}", LOCAL_STORAGE_FILE_PATH.getAbsolutePath(), e);
        }

    }
}
