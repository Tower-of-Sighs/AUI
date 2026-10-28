package com.sighs.apricityui.webview;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.TextureKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The loading screen drawn by a real browser, before the framework's own stack exists.
 *
 * <p>{@code <iframe>} needs the document, layout, fonts, CSS and the whole service layer, none
 * of which is up while the game is loading its resources. This class therefore talks to the
 * native host directly — {@link WebViewNative} plus {@link FrameUpdateChannel}, both free of
 * any Minecraft or SPI dependency — and only asks the loader for three things: a texture of a
 * given size, a pixel upload, and a full-screen blit. The page itself is plain HTML rendered
 * by WebView2, so no part of AUI's HTML engine runs here.</p>
 *
 * <p>It is driven from a mixin on the vanilla loading overlay: every rendered frame drains the
 * update stream into the texture and draws it. The view is created on a worker thread because
 * {@code WebView2} takes a few hundred milliseconds to start, and nothing is drawn until the
 * first rectangle has been applied — until then the vanilla overlay shows through.</p>
 *
 * <p>Failure is never fatal: a missing runtime, a failed view or a backend that cannot upload
 * pixels simply leaves this class inactive, and the loading screen stays vanilla. The native
 * view is stopped once the overlay has not been drawn for a few seconds; the texture is kept
 * and reused by the next overlay (a resource reload draws one again).</p>
 *
 * <p>The page, and whether there is one at all, is decided by {@code @StartupHook} methods:
 * they run right before the browser starts (see
 * {@link com.sighs.apricityui.startup.StartupHooks}) and configure it through
 * {@link com.sighs.apricityui.startup.StartupContext}.</p>
 */
public final class StartupLoadingOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger("apricityui/startup");

    /**
     * {@code -Dapricityui.startupOverlay=false} keeps the vanilla loading screen.
     */
    private static final boolean ENABLED = !"false".equalsIgnoreCase(
            System.getProperty("apricityui.startupOverlay", "true"));

    private static final TextureKey KEY = TextureKey.of("apricityui:startup/loading");

    /**
     * A loading screen has nothing time-critical in it, and a capture costs the browser
     * process a full readback plus an encode: 20 fps is plenty and leaves the CPU to the
     * resource reload that is happening at the same time.
     */
    private static final int CAPTURE_INTERVAL_MS = 50;

    /** Bounds the raster (and the shared section behind it) for very large windows. */
    private static final int MAX_RASTER_WIDTH = 2560;
    private static final int MAX_RASTER_HEIGHT = 1440;

    private static final long FADE_IN_NANOS = 250_000_000L;
    /** How long the overlay may stop being drawn before the browser is shut down. */
    private static final long IDLE_STOP_NANOS = 4_000_000_000L;
    /**
     * How long a view started by {@link #prepare()} waits for the loading overlay to appear.
     * Mod loading can take a while, and the browser is useless until the first frame is drawn,
     * so this is generous; a view that never gets drawn is still released eventually.
     */
    private static final long FIRST_DRAW_TIMEOUT_NANOS = 180_000_000_000L;

    private enum State {
        /** No view yet; the next draw starts one. */
        IDLE,
        /** A worker thread is starting the view. */
        STARTING,
        /** The update stream is live. */
        READY,
        /** The backend cannot do this at all; never retried. */
        UNAVAILABLE
    }

    private static volatile State state = State.IDLE;
    private static volatile long viewHandle;
    private static volatile FrameUpdateChannel reader;
    private static volatile boolean watchdogRunning;

    /**
     * Raster the browser is started with before the game window exists; see {@link #prepare()}.
     */
    private static final int PROVISIONAL_WIDTH = 1600;
    private static final int PROVISIONAL_HEIGHT = 900;
    /** Set while the running view uses the provisional raster. Guarded by the class lock. */
    private static boolean provisional;
    /** Cleared once the real window has been compared against the provisional raster. */
    private static boolean sizeChecked;

    /** Render thread only from here down. */
    private static Object texture;
    private static Object location;
    private static int canvasWidth;
    private static int canvasHeight;

    private static long startedNanos;
    private static long firstFrameNanos;
    private static long lastDrawNanos;

    /** Page installed by a {@code @StartupHook}; null means the built-in one. */
    private static volatile String pageUrlOverride;
    /** Set by {@link #hide()}: stay out of the way until a page is shown again. */
    private static volatile boolean suppressed;
    /** Scripts handed to {@link #eval} before the view was ready; flushed once it is. */
    private static final java.util.Queue<String> PENDING_SCRIPTS =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final int MAX_PENDING_SCRIPTS = 32;
    /** Runs once, on the render thread, just before the first frame is drawn. */
    private static volatile Runnable firstDrawAction;

    private StartupLoadingOverlay() {
    }

    /**
     * Runs {@code action} once, on the render thread, immediately before the loading screen's
     * first frame is drawn.
     *
     * <p>This is the earliest moment a script platform can be handed the startup context: its
     * {@code startup_scripts} are loaded by then, while the mod-construction hooks are not. Only
     * one action is kept — the last caller wins, which is all the startup phase needs.</p>
     */
    public static void runOnceOnFirstDraw(Runnable action) {
        firstDrawAction = action;
    }

    /**
     * Starts the browser now, before the loading overlay exists.
     *
     * <p>Called from each loader's client bootstrap, while the mod is still being constructed:
     * the game window does not exist yet, so the raster is a provisional 16:9 size that
     * {@link #draw} replaces if the real window differs too much. Starting here is what makes
     * the page visible from the first frame of the loading overlay instead of a few seconds
     * into it — WebView2 needs a few hundred milliseconds to start, and it is competing with
     * the resource reload that is happening at the same time.</p>
     */
    public static void prepare() {
        if (!ENABLED || suppressed) {
            return;
        }
        synchronized (StartupLoadingOverlay.class) {
            if (state != State.IDLE) {
                return;
            }
            start(PROVISIONAL_WIDTH, PROVISIONAL_HEIGHT, true);
        }
    }

    /**
     * Draws the page over the screen, or does nothing when there is nothing to show yet.
     *
     * <p>Called from the vanilla loading overlay's render pass, so it runs on the render
     * thread with a live GL context. {@code graphics} is whatever that overlay passes around
     * (a {@code GuiGraphics}, a {@code GuiGraphicsExtractor} or a {@code PoseStack}); the
     * sizes come from the overlay's window rather than from a service, because this runs
     * before the framework's own stack is usable.</p>
     *
     * @param guiWidth     the scaled GUI size, which is the space the overlay draws in
     * @param rasterWidth  the framebuffer size, which is the resolution the page should have
     */
    public static void draw(Object graphics, int guiWidth, int guiHeight, int rasterWidth, int rasterHeight) {
        // Before anything else, give whoever registered an action (the script platform) its one
        // chance to install a page. It runs even when the built-in screen is disabled, so a
        // script still sees its event during the startup phase.
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
                    if (state == State.IDLE) {
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

    // --- view lifecycle ------------------------------------------------------

    private static void start(int rasterWidth, int rasterHeight, boolean provisionalRaster) {
        state = State.STARTING;
        startedNanos = System.nanoTime();
        // A previous overlay (a resource reload draws one again) leaves its timestamps behind;
        // keeping them would both skip the fade-in and let the watchdog think this view has
        // already gone idle.
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

    /**
     * Replaces a {@link #prepare()} view whose provisional raster does not suit the real window.
     *
     * <p>A loading page is a logo and a bar, so a 16:9 page stretched over a 16:10 window is
     * invisible; what does not survive is a raster several times smaller than the window. The
     * provisional view is dropped and a correctly sized one takes its place, which costs one
     * more start but only on unusual window sizes.</p>
     *
     * @return true when the current view was replaced and this frame has nothing to draw
     */
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

    /**
     * Starts the browser. On a worker thread: {@code WebViewNative.create} blocks until the
     * environment, controller and first navigation are up, which is a few hundred ms.
     */
    private static void create(int width, int height) {
        if (!WebViewNative.isAvailable()) {
            unavailable("webview unavailable: " + WebViewNative.unavailableReason());
            return;
        }
        final long handle = WebViewNative.create(pageUrl(), profileDirectory(), width, height,
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
        // Scripts queued before the view existed (a hook setting up its page) run now.
        for (String script; (script = PENDING_SCRIPTS.poll()) != null; ) {
            WebViewNative.eval(handle, script);
        }
    }

    /**
     * Shuts the browser down once the overlay has stopped being drawn and its pixels are gone.
     *
     * <p>Only the native side is released here: destroying a texture needs the render thread,
     * and after the loading screen there may be no further call into this class this session.
     * The texture is therefore kept and reused by the next overlay, which is also why re-entering
     * a load or reloading resources is cheap.</p>
     */
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

    // --- update stream -------------------------------------------------------

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
            // Registering handed ownership to the texture manager, which closes it on release;
            // an unregistered texture is ours to close.
            if (resolved != null) {
                AuiServices.render().releaseTexture(resolved);
            } else {
                AuiServices.render().closeTexture(current);
            }
        } catch (Throwable ignored) {
        }
    }

    // --- page ----------------------------------------------------------------

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

    /**
     * The page the browser opens: whatever a hook installed, else {@code apricity/startup.html}
     * if the instance has one, else the built-in page.
     */
    private static String pageUrl() {
        final String installed = pageUrlOverride;
        if (installed != null) {
            return installed;
        }
        final String userPage = userPageUrl();
        if (userPage != null) {
            LOGGER.info("startup loading screen: using the instance's apricity/startup project");
            return userPage;
        }
        final String encoded = Base64.getEncoder().encodeToString(PAGE.getBytes(StandardCharsets.UTF_8));
        return "data:text/html;base64," + encoded;
    }

    /**
     * A project dropped into the instance, so it can be edited without recompiling the mod.
     *
     * <p>{@code apricity/startup/} with an {@code index.html} is the project form, matching what
     * {@code StartupContext.loadProject} expects; a single {@code apricity/startup.html} is
     * still accepted for a page that has no assets of its own.</p>
     */
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

    // --- startup-phase API ---------------------------------------------------

    /**
     * Shows the given HTML in the startup page, replacing whatever was there.
     *
     * <p>Safe to call at any time: before the view starts it simply becomes the page the view
     * opens, and once the view is live the call navigates it. This is what a
     * {@code @StartupHook} uses.</p>
     */
    public static void showPage(String html) {
        if (html == null || html.isEmpty()) {
            return;
        }
        showPageUrl("data:text/html;base64,"
                + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Shows a page by URL ({@code https:}, {@code file:}, {@code data:} …).
     */
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

    /**
     * Runs a script in the page, for a progress bar or any other live update.
     *
     * <p>Scripts handed over before the view is ready are kept (a bounded queue) and run as
     * soon as it is, so a hook can set up a page and push its first state in one go.</p>
     */
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

    /**
     * Stops the startup page without waiting for the loading phase to end.
     *
     * <p>A hook that decides not to take over the loading screen calls this; the vanilla
     * screen is then shown as if this class did not exist. {@link #showPage} revives it.</p>
     */
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

    /**
     * Whether this process can show a page at all (the native host and its runtime are there).
     */
    public static boolean isWebViewAvailable() {
        try {
            return ENABLED && WebViewNative.isAvailable();
        } catch (Throwable failure) {
            return false;
        }
    }

    /**
     * Why {@link #isWebViewAvailable()} is false; empty when it is true.
     */
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

    /**
     * Whether the startup screen is still alive (starting, or live and being drawn).
     *
     * <p>Lets a caller that needs to retry something during the startup phase — resolving a
     * page out of a mod jar, for instance — stop once the loading screen is over.</p>
     */
    public static boolean isShowingStartupPage() {
        return ENABLED && !suppressed && (state == State.STARTING || state == State.READY);
    }

    /**
     * Raster the startup page is rendered at: the provisional size before the game window
     * exists, the real framebuffer size afterwards.
     */
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

    /**
     * The loading page. Plain HTML on purpose: it is rendered by the system browser, so nothing
     * of AUI's own HTML engine (elements, themes, scripts) is involved. A {@code data:} URL
     * keeps it independent of the mod's file layout.
     */
    private static final String PAGE = """
            <!doctype html><html><head><meta charset="utf-8"><style>
            html,body{margin:0;height:100%;overflow:hidden;background:#0b0e12;color:#e8eef6;
              font:16px/1.5 "Segoe UI","Microsoft YaHei",system-ui,sans-serif}
            .wrap{height:100%;display:flex;flex-direction:column;align-items:center;
              justify-content:center;gap:20px}
            .title{font-size:34px;font-weight:600;letter-spacing:8px;color:#f4f8fd}
            .sub{font-size:13px;letter-spacing:2px;color:#71819a}
            .bar{width:280px;height:3px;border-radius:2px;background:#18202b;overflow:hidden}
            .bar>i{display:block;height:100%;width:42%;border-radius:2px;
              background:linear-gradient(90deg,#3d7bfd,#7ad3ff);
              animation:slide 1.15s ease-in-out infinite}
            @keyframes slide{0%{transform:translateX(-105%)}100%{transform:translateX(345%)}}
            </style></head><body><div class="wrap">
            <div class="title">APRICITY</div>
            <div class="bar"><i></i></div>
            <div class="sub">正在加载资源…</div>
            </div></body></html>
            """;
}
