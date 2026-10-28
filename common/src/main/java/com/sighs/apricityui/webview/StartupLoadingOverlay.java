package com.sighs.apricityui.webview;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.TextureKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class StartupLoadingOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger("apricityui/startup");

    private static final boolean ENABLED = !"false".equalsIgnoreCase(
            System.getProperty("apricityui.startupOverlay", "true"));

    private static final TextureKey KEY = TextureKey.of("apricityui:startup/loading");

    private static final int CAPTURE_INTERVAL_MS = 50;

    private static final int MAX_RASTER_WIDTH = 2560;
    private static final int MAX_RASTER_HEIGHT = 1440;

    private static final long FADE_IN_NANOS = 250_000_000L;

    private static final long IDLE_STOP_NANOS = 4_000_000_000L;

    private static final long FIRST_DRAW_TIMEOUT_NANOS = 180_000_000_000L;

    private enum State {

        IDLE,

        STARTING,

        READY,

        UNAVAILABLE
    }

    private static volatile State state = State.IDLE;
    private static volatile long viewHandle;
    private static volatile FrameUpdateChannel reader;
    private static volatile boolean watchdogRunning;

    private static final int PROVISIONAL_WIDTH = 1600;
    private static final int PROVISIONAL_HEIGHT = 900;

    private static boolean provisional;

    private static boolean sizeChecked;

    private static Object texture;
    private static Object location;
    private static int canvasWidth;
    private static int canvasHeight;

    private static long startedNanos;
    private static long firstFrameNanos;
    private static long lastDrawNanos;

    private static volatile String pageUrlOverride;

    private static volatile boolean suppressed;

    private static final java.util.Queue<String> PENDING_SCRIPTS =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final int MAX_PENDING_SCRIPTS = 32;

    private static volatile Runnable firstDrawAction;

    private StartupLoadingOverlay() {
    }

    public static void runOnceOnFirstDraw(Runnable action) {
        firstDrawAction = action;
    }

    public static void prepare() {
        if (!ENABLED || suppressed) {
            return;
        }
        synchronized (StartupLoadingOverlay.class) {
            if (state != State.IDLE) {
                return;
            }
            if (pageUrl() == null) {
                return;
            }
            start(PROVISIONAL_WIDTH, PROVISIONAL_HEIGHT, true);
        }
    }

    public static void draw(Object graphics, int guiWidth, int guiHeight, int rasterWidth, int rasterHeight) {

        final Runnable once = firstDrawAction;
        if (once != null) {
            firstDrawAction = null;
            try {
                once.run();
            } catch (Throwable failure) {
                LOGGER.warn("startup first-draw action failed: {}", failure.toString());
            }
        }
        if (!ENABLED || suppressed) {
            return;
        }
        try {
            if (guiWidth <= 0 || guiHeight <= 0 || rasterWidth <= 0 || rasterHeight <= 0) {
                return;
            }
            if (state == State.IDLE) {
                synchronized (StartupLoadingOverlay.class) {

                    if (state == State.IDLE && pageUrl() != null) {
                        start(rasterWidth, rasterHeight, false);
                    }
                }
            }
            if (state != State.READY || reader == null) {
                return;
            }
            drain();
            if (adoptRealSize(rasterWidth, rasterHeight)) {
                return;
            }
            final Object currentTexture = texture;
            final Object currentLocation = location;
            if (currentTexture == null || currentLocation == null || firstFrameNanos == 0L) {
                return;
            }
            AuiServices.render().drawScreenTexture(graphics, currentTexture, currentLocation,
                    0, 0, guiWidth, guiHeight, fadeInAlpha());
            lastDrawNanos = System.nanoTime();
        } catch (Throwable failure) {
            unavailable("draw failed: " + failure);
        }
    }

    private static void start(int rasterWidth, int rasterHeight, boolean provisionalRaster) {
        state = State.STARTING;
        startedNanos = System.nanoTime();

        firstFrameNanos = 0L;
        lastDrawNanos = 0L;
        provisional = provisionalRaster;
        sizeChecked = false;
        final int width = clampRaster(rasterWidth);
        final int height = clampRaster(rasterHeight);
        Thread worker = new Thread(() -> create(width, height), "aui-startup-loading");
        worker.setDaemon(true);
        worker.start();
        ensureWatchdog();
    }

    private static boolean adoptRealSize(int rasterWidth, int rasterHeight) {
        if (!provisional || sizeChecked || texture == null || canvasWidth <= 0 || canvasHeight <= 0) {
            return false;
        }
        sizeChecked = true;
        final double wantedAspect = (double) rasterWidth / rasterHeight;
        final double currentAspect = (double) canvasWidth / canvasHeight;
        final boolean aspectOff = Math.abs(wantedAspect - currentAspect) / wantedAspect > 0.12d;
        final boolean tooSmall = rasterWidth > canvasWidth * 3 / 2 || rasterHeight > canvasHeight * 3 / 2;
        provisional = false;
        if (!aspectOff && !tooSmall) {
            return false;
        }
        final long handle = viewHandle;
        viewHandle = 0L;
        reader = null;
        state = State.IDLE;
        if (handle != 0L) {
            WebViewNative.destroy(handle);
        }
        synchronized (StartupLoadingOverlay.class) {
            start(rasterWidth, rasterHeight, false);
        }
        LOGGER.info("startup loading screen: re-rastered to {}x{} for the real window",
                rasterWidth, rasterHeight);
        return true;
    }

    private static void create(int width, int height) {
        final String page = pageUrl();
        if (page == null) {

            state = State.IDLE;
            return;
        }
        if (!WebViewNative.isAvailable()) {
            unavailable("webview unavailable: " + WebViewNative.unavailableReason());
            return;
        }
        final long handle = WebViewNative.create(page, profileDirectory(), width, height,
                false, true, CAPTURE_INTERVAL_MS, NativeWebViewService.FORMAT_AUTO);
        if (handle == 0L) {
            unavailable("view creation failed: " + WebViewNative.lastCreateError());
            return;
        }
        final ByteBuffer mapped = WebViewNative.mapChannel(handle);
        if (mapped == null) {
            WebViewNative.destroy(handle);
            unavailable("the view has no update channel");
            return;
        }
        final FrameUpdateChannel channel = new FrameUpdateChannel(mapped);
        if (!channel.isValid()) {
            WebViewNative.destroy(handle);
            unavailable("the update channel carries an unknown layout");
            return;
        }
        viewHandle = handle;
        reader = channel;
        state = State.READY;
        LOGGER.info("startup loading screen: view {}x{} ready", width, height);

        for (String script; (script = PENDING_SCRIPTS.poll()) != null; ) {
            WebViewNative.eval(handle, script);
        }
    }

    private static void ensureWatchdog() {
        if (watchdogRunning) {
            return;
        }
        watchdogRunning = true;
        Thread watchdog = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(500L);
                    if (state == State.IDLE || state == State.UNAVAILABLE) {
                        return;
                    }
                    final long now = System.nanoTime();
                    final boolean drawn = lastDrawNanos != 0L;
                    final long last = drawn ? lastDrawNanos : startedNanos;
                    if (now - last < (drawn ? IDLE_STOP_NANOS : FIRST_DRAW_TIMEOUT_NANOS)) {
                        continue;
                    }
                    final long handle = viewHandle;
                    viewHandle = 0L;
                    reader = null;
                    state = State.IDLE;
                    if (handle != 0L) {
                        WebViewNative.destroy(handle);
                        LOGGER.info("startup loading screen: view stopped after {} ms without a draw",
                                (now - last) / 1_000_000L);
                    }
                    return;
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                watchdogRunning = false;
            }
        }, "aui-startup-loading-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void unavailable(String reason) {
        state = State.UNAVAILABLE;
        reader = null;
        LOGGER.warn("startup loading screen disabled: {}", reason);
    }

    private static void drain() {
        final FrameUpdateChannel channel = reader;
        if (channel != null) {
            channel.drain(TARGET);
        }
    }

    private static final FrameUpdateChannel.Target TARGET = new FrameUpdateChannel.Target() {
        @Override
        public void resize(int width, int height) {
            ensureTexture(width, height);
        }

        @Override
        public void rect(int x, int y, int width, int height, ByteBuffer pixels, int byteOffset) {
            final Object current = texture;
            if (current == null) {
                return;
            }
            if (AuiServices.render().uploadTexturePixels(current, pixels, byteOffset,
                    x, y, width, height) && firstFrameNanos == 0L) {
                firstFrameNanos = System.nanoTime();
            }
        }
    };

    private static void ensureTexture(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (texture != null && canvasWidth == width && canvasHeight == height) {
            return;
        }
        releaseTexture();
        final Object created = AuiServices.render().createDynamicTexture(KEY.value(), width, height, true);
        if (created == null) {
            unavailable("the render backend cannot create a dynamic texture");
            return;
        }
        final Object resolved = AuiServices.resources().textureLocation(KEY);
        AuiServices.render().registerTexture(created, resolved);
        texture = created;
        location = resolved;
        canvasWidth = width;
        canvasHeight = height;
        LOGGER.info("startup loading screen: texture {}x{}", width, height);
    }

    private static void releaseTexture() {
        final Object current = texture;
        final Object resolved = location;
        texture = null;
        location = null;
        canvasWidth = 0;
        canvasHeight = 0;
        if (current == null) {
            return;
        }
        try {

            if (resolved != null) {
                AuiServices.render().releaseTexture(resolved);
            } else {
                AuiServices.render().closeTexture(current);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String profileDirectory() {
        try {
            java.nio.file.Path directory = AuiServices.client().getGameDirectory();
            if (directory == null) {
                return null;
            }
            java.nio.file.Path resolved = directory.resolve("apricity").resolve(".cache").resolve("webview");
            java.nio.file.Files.createDirectories(resolved);
            return resolved.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String pageUrl() {
        final String installed = pageUrlOverride;
        if (installed != null) {
            return installed;
        }
        return userPageUrl();
    }

    private static String userPageUrl() {
        try {
            final java.nio.file.Path directory = AuiServices.client().getGameDirectory();
            if (directory == null) {
                return null;
            }
            final java.nio.file.Path root = directory.resolve("apricity");
            final java.nio.file.Path index = root.resolve("startup").resolve("index.html");
            if (java.nio.file.Files.isRegularFile(index)) {
                return index.toAbsolutePath().toUri().toString();
            }
            final java.nio.file.Path single = root.resolve("startup.html");
            if (java.nio.file.Files.isRegularFile(single)) {
                return single.toAbsolutePath().toUri().toString();
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void showPage(String html) {
        if (html == null || html.isEmpty()) {
            return;
        }
        showPageUrl("data:text/html;base64,"
                + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8)));
    }

    public static void showPageUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        pageUrlOverride = url;
        suppressed = false;
        final long handle = viewHandle;
        if (state == State.READY && handle != 0L) {
            WebViewNative.navigate(handle, url);
        }
    }

    public static void eval(String script) {
        if (script == null || script.isEmpty()) {
            return;
        }
        final long handle = viewHandle;
        if (state == State.READY && handle != 0L) {
            WebViewNative.eval(handle, script);
            return;
        }
        if (PENDING_SCRIPTS.size() < MAX_PENDING_SCRIPTS) {
            PENDING_SCRIPTS.add(script);
        }
    }

    public static void hide() {
        suppressed = true;
        PENDING_SCRIPTS.clear();
        final long handle = viewHandle;
        viewHandle = 0L;
        reader = null;
        state = State.IDLE;
        if (handle != 0L) {
            WebViewNative.destroy(handle);
        }
    }

    public static boolean isWebViewAvailable() {
        try {
            return ENABLED && WebViewNative.isAvailable();
        } catch (Throwable failure) {
            return false;
        }
    }

    public static String webViewUnavailableReason() {
        if (!ENABLED) {
            return "the startup loading overlay is disabled (-Dapricityui.startupOverlay=false)";
        }
        try {
            return WebViewNative.unavailableReason();
        } catch (Throwable failure) {
            return String.valueOf(failure.getMessage());
        }
    }

    public static boolean isShowingStartupPage() {
        return ENABLED && !suppressed && state != State.UNAVAILABLE;
    }

    public static int rasterWidth() {
        return canvasWidth > 0 ? canvasWidth : PROVISIONAL_WIDTH;
    }

    public static int rasterHeight() {
        return canvasHeight > 0 ? canvasHeight : PROVISIONAL_HEIGHT;
    }

    private static int clampRaster(int value) {
        return Math.max(64, value);
    }

    private static float fadeInAlpha() {
        final long first = firstFrameNanos;
        if (first == 0L) {
            return 0.0f;
        }
        float progress = (System.nanoTime() - first) / (float) FADE_IN_NANOS;
        return progress >= 1.0f ? 1.0f : progress;
    }

}
