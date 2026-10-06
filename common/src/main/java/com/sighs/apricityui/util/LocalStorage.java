package com.sighs.apricityui.util;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.spi.AuiServices;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Persists Web Storage values through the target's mapped NBT API. */
public class LocalStorage extends Storage {
    private static volatile File localStorageFilePath;

    public void save() {
        File storageFile = resolveStorageFile();
        if (storageFile == null) return;
        try {
            Path file = storageFile.toPath();
            Files.createDirectories(file.getParent());
            AuiServices.client().writeLocalStorage(file, data);
        } catch (IOException | RuntimeException failure) {
            ApricityUI.LOGGER.error("Failed to save LocalStorage data to {}", storageFile, failure);
        }
    }

    public void load() {
        File storageFile = resolveStorageFile();
        if (storageFile == null || !storageFile.isFile()) return;
        try {
            Map<String, String> loaded = AuiServices.client().readLocalStorage(storageFile.toPath());
            data.clear();
            data.putAll(loaded);
        } catch (IOException | RuntimeException failure) {
            ApricityUI.LOGGER.error("Failed to load LocalStorage data from {}", storageFile, failure);
        }
    }

    private static File resolveStorageFile() {
        File cached = localStorageFilePath;
        if (cached != null) return cached;
        synchronized (LocalStorage.class) {
            if (localStorageFilePath != null) return localStorageFilePath;
            Path configDir = AuiServices.client().getConfigDirectory();
            if (configDir == null) return null;
            localStorageFilePath = configDir.resolve(ApricityUI.MODID).resolve("localStorage.nbt").toFile();
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
