package com.sighs.apricityui.loader;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.ui.ToastManager;
import com.sighs.apricityui.task.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.task.FrameTaskScheduler;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.dom.DocumentRegistry;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import com.sighs.apricityui.world.WorldWindow;

public class ClientLoader extends Loader {
    private static final Object STATIC_RESOURCE_CACHE_LOCK = new Object();
    private static List<StaticResourceEntry> cachedFinalStaticResources = null;
    private static boolean reloadQueued;
    private static boolean reloadRequested;
    public ClientLoader(String extension) {
        super(extension);
    }

    public static void reload() {
        reloadRequested = true;
        if (reloadQueued) return;
        reloadQueued = true;
        String progressToast = ToastManager.show(
                "Reloading...",
                new ToastManager.ToastOptions(0, false, "", "", "", "")
        );

        // Leave one complete UI frame between the progress toast and the synchronous reload.
        FrameTaskScheduler.scheduleAfterFrames(2, deadlineNs -> {
            try {
                do {
                    reloadRequested = false;
                    long beginNs = System.nanoTime();
                    AuiServices.script().reload();
                    reloadResourcesInternal(beginNs);
                } while (reloadRequested);
            } finally {
                ToastManager.dismiss(progressToast);
                reloadQueued = false;
            }
            return true;
        });
    }

    /** Reloads all client resources after the loader's resource manager is ready. */
    public static void reloadResources() {
        reloadResourcesInternal(System.nanoTime());
    }

    private static void reloadResourcesInternal(long beginNs) {
        invalidateStaticResourceCache();
        ensureAsyncHandlersInitialized();
        AbstractAsyncHandler.clearAllAndBumpGeneration();
        CSS.clearCompiledStylesheets();
        Selector.clearCompiledCache();
        DocumentRegistry.resetCreateTimingState();
        ImageDrawer.clearRenderTypeCache();
        FontDrawer.clearCache();
        Font.prepareReload();
        warmUpDocumentInfrastructure();

        long scanStartNs = System.nanoTime();
        HTML.scan();
        long scanCostMs = (System.nanoTime() - scanStartNs) / 1_000_000L;

        long firstCreateWarmStartNs = System.nanoTime();
        int preparedTemplates = HTML.prepareTemplates();
        int preparedStylesheets = 0;
        for (HTML.TemplateResources template : HTML.preparedTemplateResources()) {
            preparedStylesheets += StyleAsyncHandler.INSTANCE.warmUpTemplateStyles(
                    template.path(),
                    template.externalStyleSrcs(),
                    template.inlineStyles(),
                    resolveWarmupViewport(template.path())
            );
        }
        long firstCreateWarmCostMs = (System.nanoTime() - firstCreateWarmStartNs) / 1_000_000L;
        ApricityUI.LOGGER.info(
                "[AUI Resource] first-create warm-up templates={} stylesheets={} cost={}ms",
                preparedTemplates,
                preparedStylesheets,
                firstCreateWarmCostMs
        );

        long refreshStartNs = System.nanoTime();
        Document.refreshAll();
        WorldWindow.windows.forEach(worldWindow -> worldWindow.document.refresh());
        DevTools.refresh();
        com.sighs.apricityui.dev.ResourceManager.refresh();
        long refreshCostMs = (System.nanoTime() - refreshStartNs) / 1_000_000L;

        long totalCostMs = (System.nanoTime() - beginNs) / 1_000_000L;
        ToastManager.show(
                "重载完成 " + totalCostMs + "ms (扫描 " + scanCostMs + "ms, 刷新 " + refreshCostMs + "ms)",
                new ToastManager.ToastOptions(4200, true, "", "", "", "")
        );
    }

    private static Size resolveWarmupViewport(String path) {
        try {
            ApricityViewport viewport = ApricityViewport.spec(path)
                    .createState(path)
                    .resolve(Minecraft.getInstance().getWindow());
            return new Size(viewport.layoutWidth(), viewport.layoutHeight());
        } catch (RuntimeException | LinkageError exception) {
            return new Size(1024, 768);
        }
    }

    private static void ensureAsyncHandlersInitialized() {
        ImageAsyncHandler.INSTANCE.id();
        StyleAsyncHandler.INSTANCE.id();
        NetworkAsyncHandler.INSTANCE.id();
    }

    private static void warmUpDocumentInfrastructure() {
        try {
            Style.warmUpMetadata();
        } catch (RuntimeException | LinkageError exception) {
            ApricityUI.LOGGER.warn("[AUI Resource] style metadata warm-up failed", exception);
        }
        try {
            Text.warmUpFontMetrics();
        } catch (RuntimeException | LinkageError exception) {
            ApricityUI.LOGGER.warn("[AUI Resource] font metrics warm-up failed", exception);
        }
        try {
            StyleAsyncHandler.INSTANCE.warmUpGlobalCss();
        } catch (RuntimeException | LinkageError exception) {
            ApricityUI.LOGGER.warn("[AUI Resource] global stylesheet warm-up failed", exception);
        }
        try {
            AuiServices.script().warmUp();
        } catch (RuntimeException | LinkageError exception) {
            ApricityUI.LOGGER.warn("[AUI Resource] script engine warm-up failed", exception);
        }
    }

    public static InputStream getResourceStream(String path) {
        InputStream filesystemStream = Loader.getResourceStream(path);
        if (filesystemStream != null) {
            return filesystemStream;
        }
        if (path == null || path.isEmpty()) return null;
        try {
            Optional<InputStream> resource = AuiServices.resources().openResource("apricity/" + path);
            return resource.orElse(null);
        } catch (RuntimeException | LinkageError exception) {
            ApricityUI.LOGGER.warn("[AUI Resource] failed to open resource-pack resource path={}", path, exception);
        }
        return null;
    }

    public static List<StaticResourceEntry> listFinalStaticResources() {
        synchronized (STATIC_RESOURCE_CACHE_LOCK) {
            if (cachedFinalStaticResources != null) return cachedFinalStaticResources;
        }

        LinkedHashMap<String, StaticResourceEntry> merged = new LinkedHashMap<>();
        loadResourcePackEntries(merged);
        loadFilesystemStaticResources(merged);
        List<StaticResourceEntry> entries = merged.values().stream()
                .sorted(Comparator.comparing(StaticResourceEntry::path))
                .toList();
        synchronized (STATIC_RESOURCE_CACHE_LOCK) {
            cachedFinalStaticResources = entries;
        }
        return entries;
    }

    public static void invalidateStaticResourceCache() {
        synchronized (STATIC_RESOURCE_CACHE_LOCK) {
            cachedFinalStaticResources = null;
        }
    }

    public static String readGlobalCSS() {
        try (InputStream stream = getResourceStream("global.css")) {
            if (stream != null) return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            ApricityUI.LOGGER.warn("[AUI Resource] failed to read global.css", exception);
        }
        return null;
    }

    private static void loadResourcePackEntries(Map<String, StaticResourceEntry> merged) {
        Map<String, String> resources = AuiServices.resources().listResourcePaths("apricity", "");
        for (Map.Entry<String, String> entry : resources.entrySet()) {
            String path = entry.getKey();
            if (path.isBlank()) continue;
            String sourcePack = Loader.safe(entry.getValue());
            merged.put(path, new StaticResourceEntry(
                    path,
                    Loader.extensionOf(path),
                    ResourceLayer.RESOURCE_PACK,
                    "resource-pack",
                    sourcePack,
                    -1L
            ));
        }
    }

    public void loadResources(BiConsumer<String, String> handler) {
        this.handler = handler;
        loadedResourceCount = 0;
        loadFromResourcePack();
        loadFromLocalFolder();
        loadFromDevFolders();
        ApricityUI.LOGGER.info("[AUI Resource] scanned extension={} loaded={}", extension, loadedResourceCount);
    }

    private void loadFromResourcePack() {
        Map<String, String> paths = AuiServices.resources().listResourcePaths("apricity", "." + extension);

        for (String path : paths.keySet()) {
            try (InputStream stream = AuiServices.resources().openResource("apricity/" + path).orElse(null)) {
                if (stream == null) continue;
                handler.accept(path, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                loadedResourceCount++;
            } catch (IOException exception) {
                ApricityUI.LOGGER.error(
                        "[AUI Resource] failed to read resource-pack {} path={}",
                        extension,
                        path,
                        exception
                );
            }
        }
    }
}
