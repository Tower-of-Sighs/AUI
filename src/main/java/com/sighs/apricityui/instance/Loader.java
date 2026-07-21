package com.sighs.apricityui.instance;

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

    protected final String extension;
    protected BiConsumer<String, String> handler = (key, content) -> {
    };

    protected Loader(String extension) {
        this.extension = extension;
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
            Path local = getGameDir().resolve("apricity/" + normalizedPath);
            if (Files.exists(local) && Files.isRegularFile(local)) {
                return Files.newInputStream(local);
            }
        } catch (IOException ignored) {
        }
        InputStream bundled = Loader.class.getClassLoader()
                .getResourceAsStream("assets/apricityui/apricity/" + (path.startsWith("/") ? path.substring(1) : path));
        if (bundled != null) return bundled;
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
        if (trimmedRaw.startsWith("/")) return trimmedRaw.substring(1);

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

    static List<Path> getDevResourceRoots() {
        Path gameDir = getGameDir();
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

    static List<Path> getDevProjectRoots() {
        Path gameDir = getGameDir();
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

    static List<Path> buildProjectRootCandidates(Path projectRoot, String normalizedPath) {
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

    static void loadFilesystemStaticResources(Map<String, StaticResourceEntry> merged) {
        loadLocalFolderEntries(merged);
        loadDevFolderEntries(merged);
    }

    static String readGlobalCSS() {
        try (InputStream stream = getResourceStream("global.css")) {
            if (stream != null) return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        return null;
    }

    public static String readGlobalJS() {
        try (InputStream stream = getResourceStream("global.js")) {
            if (stream != null) return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        return null;
    }

    private static boolean isProjectRoot(Path path) {
        if (path == null) return false;
        return Files.exists(path.resolve("build.gradle"))
                || Files.exists(path.resolve("settings.gradle"))
                || Files.exists(path.resolve(".git"));
    }

    static String extensionOf(String path) {
        if (path == null) return "";
        int idx = path.lastIndexOf('.');
        if (idx < 0 || idx == path.length() - 1) return "";
        return path.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    static String safe(String value) {
        return value == null ? "" : value;
    }

    public static List<Path> getWatchRoots() {
        List<Path> roots = new ArrayList<>(getDevResourceRoots());
        Path localRoot = getGameDir().resolve("apricity").toAbsolutePath().normalize();
        if (Files.exists(localRoot) && Files.isDirectory(localRoot)) {
            roots.add(localRoot);
        }
        return roots;
    }

    protected void loadFromLocalFolder() {
        try {
            Path root = getGameDir().resolve("apricity");
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                return;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith("." + extension))
                        .forEach(path -> {
                            try {
                                String content = Files.readString(path, StandardCharsets.UTF_8);
                                String relPath = root.relativize(path).toString().replace("\\", "/");
                                handler.accept(relPath, content);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    protected void loadFromDevFolders() {
        List<Path> devRoots = getDevResourceRoots();
        if (devRoots.isEmpty()) return;

        // 先加载更远层级，最后加载更近层级，让最近的项目目录优先级最高。
        List<Path> loadOrder = new ArrayList<>(devRoots);
        Collections.reverse(loadOrder);
        for (Path root : loadOrder) {
            loadFromRootFolder(root);
        }
    }

    protected void loadFromRootFolder(Path root) {
        try {
            if (!Files.exists(root)) return;
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith("." + extension))
                        .forEach(path -> {
                            try {
                                String content = Files.readString(path, StandardCharsets.UTF_8);
                                String relPath = root.relativize(path).toString().replace("\\", "/");
                                handler.accept(relPath, content);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    private static void loadLocalFolderEntries(Map<String, StaticResourceEntry> merged) {
        Path root = getGameDir().resolve("apricity").toAbsolutePath().normalize();
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

    private static int distanceFrom(Path gameDir, Path root) {
        try {
            Path parent = root.getParent();
            if (parent == null) return Integer.MAX_VALUE;
            return parent.getNameCount() - gameDir.getNameCount();
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    public static Path getGameDirectory() {
        try {
            Class<?> fmlPathsClass = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object gameDirHolder = fmlPathsClass.getField("GAMEDIR").get(null);
            Object path = gameDirHolder.getClass().getMethod("get").invoke(gameDirHolder);
            if (path instanceof Path resolved) {
                return resolved.toAbsolutePath().normalize();
            }
        } catch (Throwable ignored) {
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static Path getGameDir() {
        return getGameDirectory();
    }
}
