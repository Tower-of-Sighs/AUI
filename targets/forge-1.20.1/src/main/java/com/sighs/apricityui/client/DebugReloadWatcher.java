package com.sighs.apricityui.client;

import com.sighs.apricityui.ApricityUI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.parser.ResourceUsageIndex;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;

public final class DebugReloadWatcher {
    private static final long SCAN_INTERVAL_MS = 500L;
    private static final long RELOAD_THROTTLE_MS = 1000L;

    private static final Map<Path, Long> LAST_MODIFIED = new HashMap<>();
    private static long lastScanMs = 0L;
    private static long lastReloadMs = 0L;

    private DebugReloadWatcher() {
    }

    public static void tick() {
        if (!ApricityUIConfig.CLIENT.debugAutoReload.get()) {
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
                } catch (IOException exception) {
                    ApricityUI.LOGGER.warn("[DebugReload] failed to inspect file={}", path, exception);
                }
            });
        } catch (IOException exception) {
            ApricityUI.LOGGER.warn("[DebugReload] failed to scan root={}", root, exception);
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
        String logicalPath = toLogicalPath(path);
        if (logicalPath != null) {
            boolean handled = path.getFileName().toString().toLowerCase().endsWith(".html")
                    ? reloadTemplate(logicalPath)
                    : refreshAffectedDocuments(logicalPath);
            if (handled) return;
        }
        ClientLoader.reload();
        ApricityUI.LOGGER.info("[DebugReload] reload completed");
    }

    /**
     * HTML 变化：重读模板，只刷新使用它的 Document；新模板只注册，不重载任何页面。
     * 返回 false 表示需要回退全量重载（模板重读失败）。
     */
    private static boolean reloadTemplate(String logicalPath) {
        boolean isNew = HTML.getTemple(logicalPath) == null;
        if (!HTML.reload(logicalPath)) return false;
        if (isNew) {
            // 新页面还没有打开的 Document，注册进模板表即可被创建；
            // 资源管理器的文件列表缓存作废，下次打开能看到新文件。
            ClientLoader.invalidateStaticResourceCache();
            ApricityUI.LOGGER.info("[DebugReload] template registered: {}", logicalPath);
            return true;
        }
        int refreshed = refreshDocumentsOf(Set.of(logicalPath), false);
        ApricityUI.LOGGER.info("[DebugReload] template reloaded: {} ({} document(s) refreshed)", logicalPath, refreshed);
        return true;
    }

    /**
     * CSS/JS 变化：只刷新引用链上包含该资源的 Document。
     * CSS 只重挂样式，DOM/JS 原样保留；JS 必须重跑脚本，走完整刷新。
     * 没有打开的页面引用它时什么都不做——未打开的页面创建时本来就会读到新内容。
     */
    private static boolean refreshAffectedDocuments(String logicalPath) {
        boolean stylesOnly = logicalPath.endsWith(".css");
        Set<String> templates = logicalPath.equals("global.css") || logicalPath.equals("global.js")
                ? null
                : ResourceUsageIndex.affectedTemplates(logicalPath);
        if (stylesOnly) {
            StyleAsyncHandler.INSTANCE.invalidatePreparedStylesheets();
        } else if (!logicalPath.equals("global.js")) {
            HTML.invalidatePreparedTemplates(templates);
        }
        int refreshed = refreshDocumentsOf(templates, stylesOnly);
        ApricityUI.LOGGER.info("[DebugReload] resource changed: {} ({} document(s) {})",
                logicalPath, refreshed, stylesOnly ? "restyled" : "refreshed");
        return true;
    }

    /** 刷新使用指定模板集合的已打开 Document；templates 为 null 表示全部；stylesOnly 时只重挂样式。 */
    private static int refreshDocumentsOf(Set<String> templates, boolean stylesOnly) {
        int refreshed = 0;
        for (Document document : Document.getAll()) {
            if (document == null || document.isDisposed() || document.isReloadPersistent()) continue;
            if (templates != null && !templates.contains(document.getPath())) continue;
            if (stylesOnly) document.refreshStyles();
            else document.refresh();
            refreshed++;
        }
        return refreshed;
    }

    private static String toLogicalPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        for (Path root : Loader.getWatchRoots()) {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (absolute.startsWith(normalizedRoot)) {
                return normalizedRoot.relativize(absolute).toString().replace("\\", "/");
            }
        }
        return null;
    }
}
