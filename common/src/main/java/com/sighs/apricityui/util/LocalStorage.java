package com.sighs.apricityui.util;

import com.sighs.apricityui.ApricityUI;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * NBT 持久化对 MC 版本的差异全部收在下面几个反射助手里：
 * <ul>
 *   <li>{@code NbtIo.writeCompressed(CompoundTag, File)} 在 26.1 换成了
 *       {@code (CompoundTag, Path)}；</li>
 *   <li>{@code NbtIo.readCompressed(File)} 在 26.1 换成了
 *       {@code (Path, NbtAccounter)}；</li>
 *   <li>{@code CompoundTag.getAllKeys()} 在 26.1 改名为 {@code keySet()}；</li>
 *   <li>{@code CompoundTag.getString(String)} 在 26.1 返回 {@code Optional<String>}。</li>
 * </ul>
 * 逐个重载探测而不是按版本分支，这样同一份 common 代码在 1.20.1 / 1.21.1 / 26.1 上都能持久化。
 */
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
            writeCompressed(nbtIoClass, compoundTagClass, tag, storageFile);
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
            Object tag = readCompressed(nbtIoClass, storageFile);
            if (tag == null) return;

            data.clear();
            for (Object key : keysOf(compoundTagClass, tag)) {
                if (key == null) continue;
                String stringKey = String.valueOf(key);
                data.put(stringKey, stringOf(compoundTagClass, tag, stringKey));
            }
        } catch (ClassNotFoundException ignored) {
            // Pure unit tests can run without the Minecraft NBT runtime.
        } catch (ReflectiveOperationException e) {
            ApricityUI.LOGGER.error("Failed to reflectively load LocalStorage from {}", storageFile.getAbsolutePath(), e);
        } catch (Exception e) {
            save();
        }
    }

    /** Tries the 26.1 {@code Path} overload first, then the pre-26.1 {@code File} one. */
    private static void writeCompressed(Class<?> nbtIoClass, Class<?> compoundTagClass, Object tag, File storageFile)
            throws ReflectiveOperationException {
        try {
            Method pathWrite = nbtIoClass.getMethod("writeCompressed", compoundTagClass, Path.class);
            pathWrite.invoke(null, tag, storageFile.toPath());
            return;
        } catch (NoSuchMethodException ignored) {
            // 1.21.1 and earlier only take a File.
        }
        Method fileWrite = nbtIoClass.getMethod("writeCompressed", compoundTagClass, File.class);
        fileWrite.invoke(null, tag, storageFile);
    }

    /** Tries the 26.1 {@code (Path, NbtAccounter)} overload first, then the pre-26.1 {@code File} one. */
    private static Object readCompressed(Class<?> nbtIoClass, File storageFile) throws ReflectiveOperationException {
        try {
            Class<?> accounterClass = Class.forName("net.minecraft.nbt.NbtAccounter");
            Method pathRead = nbtIoClass.getMethod("readCompressed", Path.class, accounterClass);
            Object accounter = accounterClass.getMethod("unlimitedHeap").invoke(null);
            return pathRead.invoke(null, storageFile.toPath(), accounter);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // 1.21.1 and earlier only take a File.
        }
        Method fileRead = nbtIoClass.getMethod("readCompressed", File.class);
        return fileRead.invoke(null, storageFile);
    }

    /** 26.1 renamed {@code getAllKeys()} to {@code keySet()}. */
    private static Iterable<?> keysOf(Class<?> compoundTagClass, Object tag) throws ReflectiveOperationException {
        Method keys;
        try {
            keys = compoundTagClass.getMethod("keySet");
        } catch (NoSuchMethodException ignored) {
            keys = compoundTagClass.getMethod("getAllKeys");
        }
        Object raw = keys.invoke(tag);
        return raw instanceof Iterable<?> iterable ? iterable : List.of();
    }

    /** 26.1 returns {@code Optional<String>} from {@code getString}, earlier versions return the String. */
    private static String stringOf(Class<?> compoundTagClass, Object tag, String key) throws ReflectiveOperationException {
        Object raw = compoundTagClass.getMethod("getString", String.class).invoke(tag, key);
        if (raw instanceof Optional<?> optional) {
            return optional.map(String::valueOf).orElse("");
        }
        return raw == null ? "" : String.valueOf(raw);
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
