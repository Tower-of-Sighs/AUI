package com.sighs.apricityui.init;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;

public class LocalStorage {
    public CompoundNBT localStorage = new CompoundNBT();

    // 文件存储位置
    public static final File LOCAL_STORAGE_FILE_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(ApricityUI.MODID)
            .resolve("localStorage.nbt")
            .toFile();

    public void save() {
        try {
            File parentDir = LOCAL_STORAGE_FILE_PATH.getParentFile();

            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    ApricityUI.LOGGER.error("Failed to create config directory for LocalStorage: {}", parentDir.getAbsolutePath());
                    return;
                }
            }

            CompressedStreamTools.writeCompressed(localStorage, LOCAL_STORAGE_FILE_PATH);
        } catch (IOException e) {
            ApricityUI.LOGGER.error("Failed to save LocalStorage data to {}", LOCAL_STORAGE_FILE_PATH.getAbsolutePath(), e);
        }

    }
}
