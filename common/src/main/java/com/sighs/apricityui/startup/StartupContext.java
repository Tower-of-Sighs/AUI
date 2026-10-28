package com.sighs.apricityui.startup;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.webview.StartupLoadingOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * What a {@link StartupHook} gets to work with.
 *
 * <p>Two things live here: the startup page (a real browser, so a page can be shown before
 * the framework's own HTML engine exists) and the little the client knows about itself that
 * early. Hooks run before the game window is created, so the window sizes are the ones the
 * client reports at that moment — treat them as hints until the loading overlay has rendered.
 * The raster size is the one the page will actually be captured at, so laying a page out
 * against {@link #rasterWidth()}/{@link #rasterHeight()} is always right.</p>
 *
 * <p>All methods are safe to call from any thread and never throw: a missing backend, a
 * window that does not exist yet or a service that is still headless degrades to a neutral
 * value instead of failing the hook.</p>
 */
public final class StartupContext {
    /** Where the hook sits in the loading phase; informational. */
    private static final String PHASE = "startup";
    private static final Logger LOGGER = LoggerFactory.getLogger("apricityui/startup");

    /**
     * A project extracted from a mod is a small page, not a media dump: these bound what one
     * extraction is allowed to write into the instance.
     */
    private static final long MAX_PROJECT_FILE_BYTES = 4L << 20;
    private static final long MAX_PROJECT_BYTES = 32L << 20;
    private static final int MAX_PROJECT_FILES = 128;

    /** How often a project that is not resolvable yet is retried, and for how long. */
    private static final long RETRY_INTERVAL_MS = 500L;
    private static final int RETRY_ATTEMPTS = 240;

    private final long startedNanos;

    StartupContext(long startedNanos) {
        this.startedNanos = startedNanos;
    }

    // --- the startup page ----------------------------------------------------

    /**
     * Replaces the startup page with the given HTML.
     *
     * <p>Before the browser starts this is simply the page it opens; afterwards it is a
     * navigation, so a hook (or a thread it leaves behind) can switch pages mid-load.</p>
     */
    public void showPage(String html) {
        try {
            StartupLoadingOverlay.showPage(html);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Replaces the startup page with a URL ({@code https:}, {@code file:}, {@code data:} …).
     */
    public void showPageUrl(String url) {
        try {
            StartupLoadingOverlay.showPageUrl(url);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Loads a startup page project: a folder whose entry point is {@code index.html}.
     *
     * <p>{@code project} names a folder, not a file:</p>
     * <ul>
     *   <li>relative to the game's page root — {@code loading} or {@code startup/loading};</li>
     *   <li>relative to the game directory — {@code apricity/loading};</li>
     *   <li>an absolute folder path — {@code D:/.../apricity/loading};</li>
     *   <li>a mod resource folder — {@code mymod:apricity/loading} (or, for AUI's own jar,
     *       just {@code loading}).</li>
     * </ul>
     *
     * <p>The instance is checked first: the folder has to exist, must not be empty, and must
     * contain {@code index.html}. Anything else means "no project here", and the same folder is
     * extracted out of the mod jars into the page root and used from there — the browser cannot
     * read a jar, and a project's relative {@code css}/{@code js}/{@code img} only resolve once
     * they are real files. Existing files are never overwritten, so after the first launch the
     * folder belongs to the instance: your edits survive, and deleting it brings the jar's copy
     * back on the next launch.</p>
     *
     * <p>A jar folder cannot be read during mod construction yet (that is when hooks run, and
     * Minecraft has not built its resource manager), so a project that has to come from a jar is
     * retried in the background while the startup screen is alive. A project that never resolves
     * logs one warning and leaves the current page alone, so a typo falls back to the built-in
     * loading page instead of a blank screen.</p>
     */
    public void loadProject(String project) {
        final String url = resolveProject(project);
        if (url != null) {
            LOGGER.info("startup project: {} -> {}", project, url);
            showPageUrl(url);
            return;
        }
        retryLater(project);
    }

    /**
     * Retries a project that was not resolvable yet.
     *
     * <p>Only jar-backed projects get here (or a typo). The loop is bounded and stops as soon
     * as the startup screen is gone, so a wrong path costs one warning, not a thread for the
     * rest of the session.</p>
     */
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

    /**
     * Runs a script in the page, for a progress bar or any other live update.
     *
     * <p>Calls made before the view is ready are queued and run once it is. This is the way to
     * report progress from the thread that watches it.</p>
     */
    public void eval(String script) {
        try {
            StartupLoadingOverlay.eval(script);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Gives the loading screen back to vanilla.
     *
     * <p>For a hook that decides, after checking something, that it has nothing to show.
     * A later {@link #showPage(String)} revives it.</p>
     */
    public void hide() {
        try {
            StartupLoadingOverlay.hide();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Whether a page can be shown at all (the native host and the WebView runtime are there).
     */
    public boolean webViewAvailable() {
        return StartupLoadingOverlay.isWebViewAvailable();
    }

    /**
     * Why {@link #webViewAvailable()} is false; empty when it is true.
     */
    public String webViewUnavailableReason() {
        return StartupLoadingOverlay.webViewUnavailableReason();
    }

    // --- page geometry -------------------------------------------------------

    /**
     * The resolution the startup page is captured at: the provisional raster before the game
     * window exists, the real framebuffer size afterwards.
     */
    public int rasterWidth() {
        return StartupLoadingOverlay.rasterWidth();
    }

    public int rasterHeight() {
        return StartupLoadingOverlay.rasterHeight();
    }

    // --- client --------------------------------------------------------------

    /**
     * Framebuffer width in physical pixels, or {@code 0} when the window does not exist yet.
     */
    public int windowWidth() {
        return (int) Math.round(number(0.0d, () -> AuiServices.client().getWindowWidth()));
    }

    public int windowHeight() {
        return (int) Math.round(number(0.0d, () -> AuiServices.client().getWindowHeight()));
    }

    /**
     * Window width in scaled GUI units, or {@code 0} before the window exists.
     */
    public int guiWidth() {
        return (int) number(0.0d, () -> AuiServices.client().getScaledWidth());
    }

    public int guiHeight() {
        return (int) number(0.0d, () -> AuiServices.client().getScaledHeight());
    }

    /**
     * Physical pixels per GUI pixel; {@code 1} when it cannot be derived yet.
     */
    public double guiScale() {
        final int scaled = guiWidth();
        if (scaled <= 0) {
            return 1.0d;
        }
        final double physical = windowWidth();
        return physical > 0.0d ? physical / scaled : 1.0d;
    }

    /**
     * The instance's game directory, which is where {@code apricity/} lives.
     *
     * <p>Unlike the window sizes this is known before the window exists.</p>
     */
    public Path gameDirectory() {
        try {
            return AuiServices.client().getGameDirectory();
        } catch (Throwable failure) {
            return null;
        }
    }

    /**
     * Milliseconds since the startup hooks began running.
     */
    public long elapsedMillis() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    /**
     * Name of the phase the hook runs in: {@code "startup"}.
     */
    public String phase() {
        return PHASE;
    }

    // --- helpers -------------------------------------------------------------

    /**
     * Turns a project folder into the URL of its {@code index.html}, or null when there is no
     * usable project anywhere.
     *
     * <p>Order: a full URL is passed through; the folder is looked up in the instance
     * (absolute, relative to the game directory, then relative to the {@code apricity/} page
     * root) and has to hold a non-empty {@code index.html}; and finally the same folder is
     * extracted out of the mod jars into the page root and used. The instance therefore always
     * wins, and an extracted folder belongs to the instance from then on — delete it to get a
     * fresh copy from the jar.</p>
     */
    private String resolveProject(String value) {
        if (value == null) {
            return null;
        }
        final String project = value.trim();
        if (project.isEmpty()) {
            return null;
        }
        // A scheme, but not a Windows drive letter ("C:\..."), is a URL. A plain URL is used
        // as-is: it names a page, not a folder.
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
                // "loading" is a folder in the page root, which is where apricity/ pages live.
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

    /**
     * {@code index.html} of a usable project folder, or null when the folder is missing, empty
     * or has no entry point — all three mean "extract it instead".
     */
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

    /**
     * Extracts a project folder out of the mod jars into the instance's page root and returns
     * the URL of the extracted {@code index.html}.
     *
     * <p>Extraction is what makes a project shipped in a jar usable at all: the browser cannot
     * read a jar, and a project's relative {@code css}/{@code js}/{@code img} only resolve once
     * they are real files. Existing files are never overwritten — the instance owns them after
     * the first launch. Bounded, and best effort: a single oversized or missing file must not
     * keep the page from showing.</p>
     */
    private String extractProject(String value) {
        final Path gameDirectory = gameDirectory();
        if (gameDirectory == null) {
            return null;
        }
        final Path pageRoot = gameDirectory.resolve("apricity");
        final String namespace;
        /** Path of the folder inside the namespace, e.g. {@code apricity/loading}. */
        final String resourceFolder;
        /** Where it lands under the page root, e.g. {@code loading}. */
        final String relativeFolder;
        final int colon = value.indexOf(':');
        if (colon > 1) {
            // An explicit namespace is an explicit resource path: mymod:apricity/loading.
            namespace = value.substring(0, colon);
            resourceFolder = stripLeadingSlashes(value.substring(colon + 1));
            relativeFolder = stripPagePrefix(resourceFolder);
        } else {
            // A bare path is relative to the page root, which every mod keeps under apricity/.
            namespace = "apricityui";
            relativeFolder = stripPagePrefix(value);
            resourceFolder = "apricity/" + relativeFolder;
        }
        if (relativeFolder.isEmpty() || relativeFolder.contains("..")
                || resourceFolder.isEmpty() || resourceFolder.contains("..")) {
            return null;
        }
        // Resource paths map onto the page root one for one, with the leading apricity/
        // dropped: apricity/loading is apricity/loading on disk.
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
                // A missing or oversized file must not keep the project from loading.
            }
        }
        if (copied > 0) {
            LOGGER.info("startup project: extracted {} file(s) into {}", copied, target);
        }
        return projectUrl(target);
    }

    /**
     * Writes a stream to a file, refusing anything larger than {@code limit}.
     */
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

    /** {@code apricityui:apricity/theme/x/loading} lands on {@code apricity/theme/x/loading}. */
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
