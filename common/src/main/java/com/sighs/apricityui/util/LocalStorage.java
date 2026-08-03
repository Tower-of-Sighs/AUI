package com.sighs.apricityui.util;

import com.sighs.apricityui.ApricityUI;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class LocalStorage extends Storage {
    private static volatile File localStorageFilePath;

    public void save() {
        File storageFile = resolveStorageFile();
        if (storageFile == null) return;
        try {
            File parentDir = storageFile.getParentFile();

            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    ApricityUI.LOGGER.error("Failed to create config directory for LocalStorage: {}", parentDir.getAbsolutePath());
                    return;
                }
            }

            Class<?> compoundTagClass = Class.forName("net.minecraft.nbt.CompoundTag");
            Object tag = compoundTagClass.getConstructor().newInstance();
            Method putString = compoundTagClass.getMethod("putString", String.class, String.class);
            for (Map.Entry<String, String> entry : data.entrySet()) {
                putString.invoke(tag, entry.getKey(), entry.getValue());
            }

            Class<?> nbtIoClass = Class.forName("net.minecraft.nbt.NbtIo");
            Method writeCompressed = nbtIoClass.getMethod("writeCompressed", compoundTagClass, File.class);
            writeCompressed.invoke(null, tag, storageFile);
        } catch (ClassNotFoundException ignored) {
            // Pure unit tests can run without the Minecraft NBT runtime; persistence is skipped there.
        } catch (ReflectiveOperationException e) {
            ApricityUI.LOGGER.error("Failed to reflectively persist LocalStorage to {}", storageFile.getAbsolutePath(), e);
        } catch (Exception e) {
            ApricityUI.LOGGER.error("Failed to save LocalStorage data to {}", storageFile.getAbsolutePath(), e);
        }
    }

    public void load() {
        File storageFile = resolveStorageFile();
        if (storageFile == null || !storageFile.isFile()) return;
        try {
            Class<?> compoundTagClass = Class.forName("net.minecraft.nbt.CompoundTag");
            Class<?> nbtIoClass = Class.forName("net.minecraft.nbt.NbtIo");
            Method readCompressed = nbtIoClass.getMethod("readCompressed", File.class);
            Object tag = readCompressed.invoke(null, storageFile);
            if (tag == null) return;

            Method getAllKeys = compoundTagClass.getMethod("getAllKeys");
            Method getString = compoundTagClass.getMethod("getString", String.class);
            Object rawKeys = getAllKeys.invoke(tag);

            data.clear();
            if (rawKeys instanceof Set<?> keys) {
                for (Object key : keys) {
                    if (key == null) continue;
                    String stringKey = String.valueOf(key);
                    Object value = getString.invoke(tag, stringKey);
                    data.put(stringKey, value == null ? "" : String.valueOf(value));
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Pure unit tests can run without the Minecraft NBT runtime.
        } catch (ReflectiveOperationException e) {
            ApricityUI.LOGGER.error("Failed to reflectively load LocalStorage from {}", storageFile.getAbsolutePath(), e);
        } catch (Exception e) {
            save();
        }
    }

    private static File resolveStorageFile() {
        File cached = localStorageFilePath;
        if (cached != null) return cached;
        synchronized (LocalStorage.class) {
            if (localStorageFilePath != null) return localStorageFilePath;
            try {
                Path configDir = com.sighs.apricityui.spi.AuiServices.client().getConfigDirectory();
                if (configDir == null) {
                    // No loader runtime (pure unit tests): persistence is skipped.
                    return null;
                }
                localStorageFilePath = configDir.resolve(ApricityUI.MODID).resolve("localStorage.nbt").toFile();
            } catch (Throwable ignored) {
                // Pure unit tests can run without a Forge runtime; persistence is skipped there.
                return null;
            }
            return localStorageFilePath;
        }
    }

    public static File getStorageFile() {
        return resolveStorageFile();
    }

    @Override
    public void setItem(String key, String value) {
        if (key == null || key.isBlank()) return;
        super.setItem(key, value);
        save();
    }

    @Override
    public void removeItem(String key) {
        if (key == null || key.isBlank()) return;
        super.removeItem(key);
        save();
    }

    @Override
    public void clear() {
        super.clear();
        save();
    }
}
