package com.sighs.apricityui.init;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LocalStorage {
    public CompoundTag localStorage = new CompoundTag();

    //文件存储位置
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

            NbtIo.writeCompressed(localStorage, LOCAL_STORAGE_FILE_PATH);
        } catch (IOException e) {
            ApricityUI.LOGGER.error("Failed to save LocalStorage data to {}", LOCAL_STORAGE_FILE_PATH.getAbsolutePath(), e);
        }

    }

    public String getItem(String key) {
        if (key == null || key.isBlank()) return null;
        return localStorage.contains(key) ? localStorage.getString(key) : null;
    }

    public void setItem(String key, String value) {
        if (key == null || key.isBlank()) return;
        localStorage.putString(key, value == null ? "null" : value);
        save();
    }

    public void removeItem(String key) {
        if (key == null || key.isBlank()) return;
        localStorage.remove(key);
        save();
    }

    public void clear() {
        localStorage = new CompoundTag();
        save();
    }

    public int getLength() {
        return localStorage.getAllKeys().size();
    }

    public String key(int index) {
        if (index < 0) return null;
        List<String> keys = new ArrayList<>(localStorage.getAllKeys());
        if (index >= keys.size()) return null;
        return keys.get(index);
    }
}
