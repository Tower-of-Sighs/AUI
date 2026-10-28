package com.sighs.apricityui.startup;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.webview.StartupLoadingOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class StartupContext {

    private static final String PHASE = "startup";
    private static final Logger LOGGER = LoggerFactory.getLogger("apricityui/startup");

    private static final long MAX_PROJECT_FILE_BYTES = 4L << 20;
    private static final long MAX_PROJECT_BYTES = 32L << 20;
    private static final int MAX_PROJECT_FILES = 128;

    private static final long RETRY_INTERVAL_MS = 500L;
    private static final int RETRY_ATTEMPTS = 240;

    private final long startedNanos;

    StartupContext(long startedNanos) {
        this.startedNanos = startedNanos;
    }

    public void showPage(String html) {
        try {
            StartupLoadingOverlay.showPage(html);
        } catch (Throwable ignored) {
        }
    }

    public void showPageUrl(String url) {
        try {
            StartupLoadingOverlay.showPageUrl(url);
        } catch (Throwable ignored) {
        }
    }

    public void loadProject(String project) {
        final String url = resolveProject(project);
        if (url != null) {
            LOGGER.info("startup project: {} -> {}", project, url);
            showPageUrl(url);
            return;
        }
        retryLater(project);
    }

    private void retryLater(String project) {
        final Thread thread = new Thread(() -> {
            for (int attempt = 0; attempt < RETRY_ATTEMPTS; attempt++) {
                try {
                    Thread.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!StartupLoadingOverlay.isShowingStartupPage()) {
                    return;
                }
                final String url = resolveProject(project);
                if (url != null) {
                    LOGGER.info("startup project (late): {} -> {}", project, url);
                    showPageUrl(url);
                    return;
                }
            }
            LOGGER.warn("startup project never resolved: {} (a project is a folder holding "
                    + "index.html, relative to the game directory or its apricity/ page root, "
                    + "then the mod jars)", project);
        }, "aui-startup-project");
        thread.setDaemon(true);
        thread.start();
    }

    public void eval(String script) {
        try {
            StartupLoadingOverlay.eval(script);
        } catch (Throwable ignored) {
        }
    }

    public void hide() {
        try {
            StartupLoadingOverlay.hide();
        } catch (Throwable ignored) {
        }
    }

    public boolean webViewAvailable() {
        return StartupLoadingOverlay.isWebViewAvailable();
    }

    public String webViewUnavailableReason() {
        return StartupLoadingOverlay.webViewUnavailableReason();
    }

    public int rasterWidth() {
        return StartupLoadingOverlay.rasterWidth();
    }

    public int rasterHeight() {
        return StartupLoadingOverlay.rasterHeight();
    }

    public int windowWidth() {
        return (int) Math.round(number(0.0d, () -> AuiServices.client().getWindowWidth()));
    }

    public int windowHeight() {
        return (int) Math.round(number(0.0d, () -> AuiServices.client().getWindowHeight()));
    }

    public int guiWidth() {
        return (int) number(0.0d, () -> AuiServices.client().getScaledWidth());
    }

    public int guiHeight() {
        return (int) number(0.0d, () -> AuiServices.client().getScaledHeight());
    }

    public double guiScale() {
        final int scaled = guiWidth();
        if (scaled <= 0) {
            return 1.0d;
        }
        final double physical = windowWidth();
        return physical > 0.0d ? physical / scaled : 1.0d;
    }

    public Path gameDirectory() {
        try {
            return AuiServices.client().getGameDirectory();
        } catch (Throwable failure) {
            return null;
        }
    }

    public long elapsedMillis() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    public String phase() {
        return PHASE;
    }

    private String resolveProject(String value) {
        if (value == null) {
            return null;
        }
        final String project = value.trim();
        if (project.isEmpty()) {
            return null;
        }

        final int colon = project.indexOf(':');
        if (colon > 1) {
            if (isPageUrl(project)) {
                return project;
            }
            return extractProject(project);
        }
        final Path gameDirectory = gameDirectory();
        if (gameDirectory == null) {
            return null;
        }
        final Path pageRoot = gameDirectory.resolve("apricity");
        try {
            final Path direct = Path.of(project);
            if (direct.isAbsolute()) {
                final String url = projectUrl(direct);
                if (url != null) {
                    return url;
                }
            } else {
                final String fromGameDirectory = projectUrl(gameDirectory.resolve(direct));
                if (fromGameDirectory != null) {
                    return fromGameDirectory;
                }

                final String fromPageRoot = projectUrl(pageRoot.resolve(direct));
                if (fromPageRoot != null) {
                    return fromPageRoot;
                }
                final String stripped = projectUrl(pageRoot.resolve(stripPagePrefix(project)));
                if (stripped != null) {
                    return stripped;
                }
            }
        } catch (InvalidPathException | SecurityException ignored) {
            return null;
        }
        return extractProject(project);
    }

    private static boolean isPageUrl(String value) {
        return value.startsWith("http:") || value.startsWith("https:") || value.startsWith("file:")
                || value.startsWith("data:") || value.startsWith("about:");
    }

    private static String projectUrl(Path directory) {
        try {
            if (!Files.isDirectory(directory)) {
                return null;
            }
            try (java.util.stream.Stream<Path> entries = Files.list(directory)) {
                if (entries.findAny().isEmpty()) {
                    return null;
                }
            }
            final Path index = directory.resolve("index.html");
            return Files.isRegularFile(index) ? index.toUri().toString() : null;
        } catch (Throwable failure) {
            return null;
        }
    }

    private String extractProject(String value) {
        final Path gameDirectory = gameDirectory();
        if (gameDirectory == null) {
            return null;
        }
        final Path pageRoot = gameDirectory.resolve("apricity");
        final String namespace;

        final String resourceFolder;

        final String relativeFolder;
        final int colon = value.indexOf(':');
        if (colon > 1) {

            namespace = value.substring(0, colon);
            resourceFolder = stripLeadingSlashes(value.substring(colon + 1));
            relativeFolder = stripPagePrefix(resourceFolder);
        } else {

            namespace = "apricityui";
            relativeFolder = stripPagePrefix(value);
            resourceFolder = "apricity/" + relativeFolder;
        }
        if (relativeFolder.isEmpty() || relativeFolder.contains("..")
                || resourceFolder.isEmpty() || resourceFolder.contains("..")) {
            return null;
        }

        final Path target = pageRoot.resolve(relativeFolder).normalize();
        if (!target.startsWith(pageRoot)) {
            return null;
        }
        final java.util.Map<String, String> entries;
        try {
            entries = AuiServices.resources().listResourcePaths(resourceFolder, "");
        } catch (Throwable failure) {
            return null;
        }
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        long totalBytes = 0L;
        int copied = 0;
        for (String relative : entries.keySet()) {
            if (copied >= MAX_PROJECT_FILES || totalBytes >= MAX_PROJECT_BYTES) {
                break;
            }
            if (relative == null || relative.isBlank() || relative.contains("..")) {
                continue;
            }
            final String cleaned = relative.replace('\\', '/');
            final Path file = target.resolve(cleaned).normalize();
            if (!file.startsWith(target) || Files.exists(file)) {
                continue;
            }
            try (java.io.InputStream input = AuiServices.resources()
                    .openResource(namespace + ":" + resourceFolder + "/" + cleaned).orElse(null)) {
                if (input == null) {
                    continue;
                }
                totalBytes += copyCapped(input, file, MAX_PROJECT_FILE_BYTES);
                copied++;
            } catch (Throwable ignored) {

            }
        }
        if (copied > 0) {
            LOGGER.info("startup project: extracted {} file(s) into {}", copied, target);
        }
        return projectUrl(target);
    }

    private static long copyCapped(java.io.InputStream input, Path target, long limit)
            throws java.io.IOException {
        Files.createDirectories(target.getParent());
        final Path staging = target.resolveSibling(target.getFileName() + ".part");
        long written = 0L;
        try (java.io.OutputStream output = Files.newOutputStream(staging)) {
            final byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) > 0; ) {
                written += read;
                if (written > limit) {
                    throw new java.io.IOException("resource is larger than " + limit + " bytes");
                }
                output.write(buffer, 0, read);
            }
        } catch (java.io.IOException failure) {
            try {
                Files.deleteIfExists(staging);
            } catch (java.io.IOException ignored) {
            }
            throw failure;
        }
        Files.move(staging, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return written;
    }

    private static String stripPagePrefix(String path) {
        String cleaned = stripLeadingSlashes(path);
        if (cleaned.startsWith("apricity/")) {
            cleaned = cleaned.substring("apricity/".length());
        }
        return cleaned;
    }

    private static String stripLeadingSlashes(String path) {
        String cleaned = path.replace('\\', '/').trim();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned;
    }

    private static double number(double fallback, java.util.function.DoubleSupplier supplier) {
        try {
            final double value = supplier.getAsDouble();
            return Double.isFinite(value) ? value : fallback;
        } catch (Throwable failure) {
            return fallback;
        }
    }
}
