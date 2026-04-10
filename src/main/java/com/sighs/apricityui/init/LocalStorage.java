package com.sighs.apricityui.init;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalStorage {
    public CompoundTag localStorage = new CompoundTag();

    public static final Path LOCAL_STORAGE_FILE_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(ApricityUI.MODID)
            .resolve("localStorage.nbt");

    public void save() {
        try {
            Path parentDir = LOCAL_STORAGE_FILE_PATH.getParent();

            if (parentDir != null && Files.notExists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            NbtIo.writeCompressed(localStorage, LOCAL_STORAGE_FILE_PATH);

        } catch (IOException e) {
            ApricityUI.LOGGER.error("Failed to save LocalStorage data to {}", LOCAL_STORAGE_FILE_PATH, e);
        }
    }

    // 可以不用但不能没有😭
    public void load() {
        try {
            if (Files.exists(LOCAL_STORAGE_FILE_PATH)) {
                this.localStorage = NbtIo.readCompressed(LOCAL_STORAGE_FILE_PATH, NbtAccounter.unlimitedHeap());
            }
        } catch (IOException e) {
            ApricityUI.LOGGER.error("Failed to load LocalStorage data", e);
        }
    }
}