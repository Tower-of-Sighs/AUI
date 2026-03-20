package com.sighs.apricityui.instance;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.ToastManager;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.script.ApricityJS;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class Loader {
    private static final String DEV_ASSET_ROOT = "src/main/resources/assets/apricityui/apricity";

    public static void setup() {
        reload();
    }

    public static void reload() {
        long beginNs = System.nanoTime();
        ApricityJS.reload();
        ensureAsyncHandlersInitialized();
        AbstractAsyncHandler.clearAllAndBumpGeneration();
        ImageDrawer.clearRenderTypeCache();
        FontDrawer.clearCache();
        Font.clear();
        long scanStartNs = System.nanoTime();
        HTML.scan();
        long scanCostMs = (System.nanoTime() - scanStartNs) / 1_000_000L;

        long refreshStartNs = System.nanoTime();
        Document.refreshAll();
        WorldWindow.windows.forEach(worldWindow -> worldWindow.document.refresh());
        long refreshCostMs = (System.nanoTime() - refreshStartNs) / 1_000_000L;

        long totalCostMs = (System.nanoTime() - beginNs) / 1_000_000L;
        ToastManager.show(
                "重载完成 " + totalCostMs + "ms (扫描 " + scanCostMs + "ms, 刷新 " + refreshCostMs + "ms)",
                new ToastManager.ToastOptions(4200, true, "#0f172a", "#e2e8f0", "#334155", "")
        );
    }

    private static void ensureAsyncHandlersInitialized() {
        ImageAsyncHandler.INSTANCE.id();
        StyleAsyncHandler.INSTANCE.id();
        NetworkAsyncHandler.INSTANCE.id();
    }

    public static InputStream getResourceStream(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
            for (Path devRoot : getDevResourceRoots()) {
                Path devPath = devRoot.resolve(normalizedPath).normalize();
                if (Files.exists(devPath) && Files.isRegularFile(devPath)) {
                    return Files.newInputStream(devPath);
                }
            }
            Path local = FabricLoader.getInstance().getGameDir().resolve("apricity/" + normalizedPath);
            if (Files.exists(local)) return Files.newInputStream(local);

            // 资源包
            ResourceLocation rl = new ResourceLocation(ApricityUI.MODID, "apricity/" + path);
            var minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getResourceManager() != null) {
                Optional<Resource> res = minecraft.getResourceManager().getResource(rl);
                if (res.isPresent()) return res.get().open();
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public static boolean isRemotePath(String path) {
        if (path == null) return false;
        String trimmed = path.trim();
        return trimmed.regionMatches(true, 0, "https://", 0, "https://".length());
    }

    public static String resolve(String context, String raw) {
        if (raw == null) return "";
        String trimmedRaw = raw.trim();
        if (trimmedRaw.isEmpty()) return "";
        if (isRemotePath(trimmedRaw)) return trimmedRaw;
        if (trimmedRaw.startsWith("/")) return trimmedRaw.substring(1); // 绝对路径

        String safeContext = context == null ? "" : context;
        String base = safeContext.contains("/") ? safeContext.substring(0, safeContext.lastIndexOf('/')) : "";
        String[] parts = (base + "/" + trimmedRaw).split("/");

        java.util.Stack<String> stack = new java.util.Stack<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!stack.isEmpty()) stack.pop();
            } else {
                stack.push(part);
            }
        }
        return String.join("/", stack);
    }

    private final String extension;
    private BiConsumer<String, String> handler = (k, c) -> {
    };

    public Loader(String extension) {
        this.extension = extension;
    }

    public void loadResources(BiConsumer<String, String> handler) {
        this.handler = handler;
        ApricityUI.LOGGER.info("ApricityUI Loader.loadResources: start extension={}", extension);
        loadFromResourcePack();
        loadFromLocalFolder();
        loadFromDevFolders();
        ApricityUI.LOGGER.info("ApricityUI Loader.loadResources: end extension={}", extension);
    }

    private void loadFromResourcePack() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        if (manager == null) {
            ApricityUI.LOGGER.warn("ApricityUI Loader.loadFromResourcePack: ResourceManager is null, skip pack scan");
            return;
        }
        Map<ResourceLocation, Resource> resources = manager.listResources("apricity",
                loc -> {
                    boolean match = loc.getPath().endsWith("." + extension);
                    if (match) {
                        ApricityUI.LOGGER.debug("ApricityUI Loader.loadFromResourcePack: matched {}", loc);
                    }
                    return match;
                });
        ApricityUI.LOGGER.info("ApricityUI Loader.loadFromResourcePack: found {} resources", resources.size());

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (InputStream stream = entry.getValue().open()) {
                String path = entry.getKey().getPath(); // "apricity/modid/index.html"
                if (path.startsWith("apricity/")) path = path.substring(9);
                ApricityUI.LOGGER.debug("ApricityUI Loader.loadFromResourcePack: put temple path={}", path);
                handler.accept(path, IOUtils.toString(stream, StandardCharsets.UTF_8));
            } catch (IOException e) {
                ApricityUI.LOGGER.error("ApricityUI Loader.loadFromResourcePack: error reading {}", entry.getKey(), e);
            }
        }
    }

    private void loadFromLocalFolder() {
        try {
            Path root = FabricLoader.getInstance().getGameDir().resolve("apricity");
            ApricityUI.LOGGER.info("ApricityUI Loader.loadFromLocalFolder: root={}", root);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                ApricityUI.LOGGER.info("ApricityUI Loader.loadFromLocalFolder: created root, nothing to load");
                return;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith("." + extension))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p, StandardCharsets.UTF_8);
                                String relPath = root.relativize(p).toString().replace("\\", "/");
                                ApricityUI.LOGGER.debug("ApricityUI Loader.loadFromLocalFolder: put temple path={}", relPath);
                                handler.accept(relPath, content);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    private void loadFromDevFolders() {
        List<Path> devRoots = getDevResourceRoots();
        ApricityUI.LOGGER.info("ApricityUI Loader.loadFromDevFolders: devRoots={}", devRoots);
        if (devRoots.isEmpty()) return;

        // 先加载更远层级，最后加载更近层级，让“最近的项目目录”优先级最高。
        List<Path> loadOrder = new ArrayList<>(devRoots);
        Collections.reverse(loadOrder);
        for (Path root : loadOrder) {
            loadFromRootFolder(root);
        }
    }

    private void loadFromRootFolder(Path root) {
        try {
            if (!Files.exists(root)) return;
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith("." + extension))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p, StandardCharsets.UTF_8);
                                String relPath = root.relativize(p).toString().replace("\\", "/");
                                ApricityUI.LOGGER.debug("ApricityUI Loader.loadFromRootFolder: put temple path={}", relPath);
                                handler.accept(relPath, content);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    private static List<Path> getDevResourceRoots() {
        Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        Path base = gameDir;
        for (int depth = 0; depth <= 6 && base != null; depth++) {
            Path candidate = base.resolve(DEV_ASSET_ROOT).normalize();
            if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                candidates.add(candidate);
            }
            base = base.getParent();
        }

        List<Path> roots = new ArrayList<>(candidates);
        roots.sort(Comparator.comparingInt((Path path) -> distanceFrom(gameDir, path)).reversed());
        return roots;
    }

    public static List<Path> getWatchRoots() {
        List<Path> roots = new ArrayList<>(getDevResourceRoots());
        Path localRoot = FabricLoader.getInstance().getGameDir().resolve("apricity").toAbsolutePath().normalize();
        if (Files.exists(localRoot) && Files.isDirectory(localRoot)) {
            roots.add(localRoot);
        }
        return roots;
    }

    private static int distanceFrom(Path gameDir, Path root) {
        try {
            Path parent = root.getParent();
            if (parent == null) return Integer.MAX_VALUE;
            return parent.getNameCount() - gameDir.getNameCount();
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    public static String readGlobalCSS() {
        try (InputStream is = getResourceStream("global.css")) {
            if (is != null) return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        return null;
    }
}
