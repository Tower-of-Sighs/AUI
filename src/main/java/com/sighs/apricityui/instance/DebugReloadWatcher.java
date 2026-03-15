package com.sighs.apricityui.instance;

import com.sighs.apricityui.ApricityUI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class DebugReloadWatcher {
    private static final long SCAN_INTERVAL_MS = 500L;
    private static final long RELOAD_THROTTLE_MS = 1000L;

    private static final Map<Path, Long> LAST_MODIFIED = new HashMap<>();
    private static long lastScanMs = 0L;
    private static long lastReloadMs = 0L;

    private DebugReloadWatcher() {
    }

    public static void tick() {
        if (!ApricityUIConfig.get().debugAutoReload()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanMs < SCAN_INTERVAL_MS) {
            return;
        }
        lastScanMs = now;

        List<Path> roots = Loader.getWatchRoots();
        if (roots.isEmpty()) {
            return;
        }

        for (Path root : roots) {
            scanRoot(root, now);
        }
    }

    private static void scanRoot(Path root, long now) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(DebugReloadWatcher::isWatchedExtension)
                    .forEach(path -> {
                        try {
                            FileTime time = Files.getLastModifiedTime(path);
                            long lastModified = time.toMillis();
                            Long cached = LAST_MODIFIED.get(path);
                            if (cached == null) {
                                LAST_MODIFIED.put(path, lastModified);
                                return;
                            }
                            if (lastModified != cached) {
                                LAST_MODIFIED.put(path, lastModified);
                                triggerReload(path, now);
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static boolean isWatchedExtension(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js");
    }

    private static void triggerReload(Path path, long now) {
        if (now - lastReloadMs < RELOAD_THROTTLE_MS) {
            return;
        }
        lastReloadMs = now;
        ApricityUI.LOGGER.info("[DebugReload] change detected: {}", path.toAbsolutePath());
        Loader.reload();
        ApricityUI.LOGGER.info("[DebugReload] reload completed");
    }
}
