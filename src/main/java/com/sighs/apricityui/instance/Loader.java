package com.sighs.apricityui.instance;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.dev.ToastManager;
import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.script.ApricityJS;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

@EventBusSubscriber(modid = ApricityUI.MOD_ID)
public class Loader {
    private static final String DEV_ASSET_ROOT = "src/main/resources/assets/apricityui/apricity";
    public enum ResourceLayer {
        RESOURCE_PACK,
        LOCAL_FOLDER,
        DEV_FOLDER
    }

    public record StaticResourceEntry(
            String path,
            String extension,
            ResourceLayer layer,
            String sourceRoot,
            String sourceDetail,
            long sizeBytes
    ) {
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(Loader::reload);
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
        DevTools.refresh();
        com.sighs.apricityui.dev.ResourceManager.refresh();
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
            for (Path projectRoot : getDevProjectRoots()) {
                for (Path candidate : buildProjectRootCandidates(projectRoot, normalizedPath)) {
                    if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                        return Files.newInputStream(candidate);
                    }
                }
            }
            Path local = FMLPaths.GAMEDIR.get().resolve("apricity/" + normalizedPath);
            if (Files.exists(local)) return Files.newInputStream(local);

            // 资源包
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ApricityUI.MOD_ID, "apricity/" + path);
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (res.isPresent()) return res.get().open();
        } catch (IOException ignored) {
        }
        return null;
    }

    public static List<StaticResourceEntry> listFinalStaticResources() {
        LinkedHashMap<String, StaticResourceEntry> merged = new LinkedHashMap<>();
        loadResourcePackEntries(merged);
        loadLocalFolderEntries(merged);
        loadDevFolderEntries(merged);
        return merged.values().stream()
                .sorted(Comparator.comparing(StaticResourceEntry::path))
                .toList();
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

        Stack<String> stack = new Stack<>();
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
        loadFromResourcePack();
        loadFromLocalFolder();
        loadFromDevFolders();
    }

    private void loadFromResourcePack() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> resources = manager.listResources("apricity",
                loc -> loc.getPath().endsWith("." + extension));

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (InputStream stream = entry.getValue().open()) {
                String path = entry.getKey().getPath(); // "apricity/modid/index.html"
                if (path.startsWith("apricity/")) path = path.substring(9);
                handler.accept(path, IOUtils.toString(stream, StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadFromLocalFolder() {
        try {
            Path root = FMLPaths.GAMEDIR.get().resolve("apricity");
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                return;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith("." + extension))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p, StandardCharsets.UTF_8);
                                String relPath = root.relativize(p).toString().replace("\\", "/");
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
                                handler.accept(relPath, content);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    private static List<Path> getDevResourceRoots() {
        Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
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

    private static List<Path> getDevProjectRoots() {
        Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();

        for (Path devRoot : getDevResourceRoots()) {
            Path current = devRoot;
            for (int depth = 0; depth <= 8 && current != null; depth++) {
                if (isProjectRoot(current)) {
                    candidates.add(current);
                    break;
                }
                current = current.getParent();
            }
        }

        Path base = gameDir;
        for (int depth = 0; depth <= 8 && base != null; depth++) {
            if (isProjectRoot(base)) {
                candidates.add(base);
            }
            base = base.getParent();
        }

        return new ArrayList<>(candidates);
    }

    private static boolean isProjectRoot(Path path) {
        if (path == null) return false;
        return Files.exists(path.resolve("build.gradle"))
                || Files.exists(path.resolve("settings.gradle"))
                || Files.exists(path.resolve(".git"));
    }

    private static List<Path> buildProjectRootCandidates(Path projectRoot, String normalizedPath) {
        ArrayList<Path> candidates = new ArrayList<>();
        if (projectRoot == null || normalizedPath == null || normalizedPath.isBlank()) return candidates;

        String[] parts = normalizedPath.replace("\\", "/").split("/");
        for (int i = 0; i < parts.length; i++) {
            String candidatePath = String.join("/", Arrays.copyOfRange(parts, i, parts.length));
            if (candidatePath.isBlank()) continue;
            candidates.add(projectRoot.resolve(candidatePath).normalize());
        }
        return candidates;
    }

    private static void loadResourcePackEntries(Map<String, StaticResourceEntry> merged) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> resources = manager.listResources("apricity", loc -> true);
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            String path = location.getPath();
            if (path.startsWith("apricity/")) path = path.substring(9);
            if (path.isBlank()) continue;
            Resource resource = entry.getValue();
            String sourcePack = safe(resource.sourcePackId());
            merged.put(path, new StaticResourceEntry(
                    path,
                    extensionOf(path),
                    ResourceLayer.RESOURCE_PACK,
                    "resource-pack",
                    sourcePack,
                    -1L
            ));
        }
    }

    private static void loadLocalFolderEntries(Map<String, StaticResourceEntry> merged) {
        Path root = FMLPaths.GAMEDIR.get().resolve("apricity").toAbsolutePath().normalize();
        loadFromRootEntries(merged, root, ResourceLayer.LOCAL_FOLDER, root.toString(), root.toString());
    }

    private static void loadDevFolderEntries(Map<String, StaticResourceEntry> merged) {
        List<Path> devRoots = getDevResourceRoots();
        if (devRoots.isEmpty()) return;
        List<Path> loadOrder = new ArrayList<>(devRoots);
        Collections.reverse(loadOrder);
        for (Path root : loadOrder) {
            String sourceRoot = root.toAbsolutePath().normalize().toString();
            loadFromRootEntries(merged, root, ResourceLayer.DEV_FOLDER, sourceRoot, sourceRoot);
        }
    }

    private static void loadFromRootEntries(
            Map<String, StaticResourceEntry> merged,
            Path root,
            ResourceLayer layer,
            String sourceRoot,
            String sourceDetail
    ) {
        try {
            if (!Files.exists(root) || !Files.isDirectory(root)) return;
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                String relPath = root.relativize(path).toString().replace("\\", "/");
                                if (relPath.isBlank()) return;
                                long size = Files.size(path);
                                merged.put(relPath, new StaticResourceEntry(
                                        relPath,
                                        extensionOf(relPath),
                                        layer,
                                        sourceRoot,
                                        sourceDetail,
                                        size
                                ));
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    private static String extensionOf(String path) {
        if (path == null) return "";
        int idx = path.lastIndexOf('.');
        if (idx < 0 || idx == path.length() - 1) return "";
        return path.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static List<Path> getWatchRoots() {
        List<Path> roots = new ArrayList<>(getDevResourceRoots());
        Path localRoot = FMLPaths.GAMEDIR.get().resolve("apricity").toAbsolutePath().normalize();
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
