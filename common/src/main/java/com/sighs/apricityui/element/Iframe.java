package com.sighs.apricityui.element;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.AuiWebViewService;
import com.sighs.apricityui.spi.TextureKey;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code <iframe>} backed by an offscreen system web view instead of an embedded one.
 *
 * <p>The host framework never renders HTML; a real browser (WebView2 on Windows) does,
 * inside a window the user never sees, and publishes its pixels. This element turns
 * those pixels into an ordinary texture, so layout, clipping, transforms, stacking and
 * pointer hit testing all behave exactly like any other texture-backed element such as
 * {@link Canvas}.</p>
 *
 * <h2>Attributes</h2>
 * <ul>
 *   <li>{@code src} — absolute URL. A view is only created when this attribute is
 *       present, so decorative empty iframes cost nothing. Relative URLs are passed
 *       through unresolved: the engine has no document base URL to resolve against.</li>
 *   <li>{@code width}/{@code height} — intrinsic size in CSS pixels, used only when no
 *       CSS size applies; the same HTML defaults (300x150) as {@link Canvas}.</li>
 * </ul>
 *
 * <h2>Behaviour worth knowing</h2>
 * <ul>
 *   <li>The view's pixel resolution follows the content box scaled by the document
 *       viewport scale, so text stays crisp at non-1 GUI scale.</li>
 *   <li>Frames are polled from {@link #tick()} and uploaded in
 *       {@link #drawPhase(PoseStack, Base.RenderPhase)}, which is the phase contract the
 *       engine expects for GPU work.</li>
 *   <li>Keyboard input is delivered through the page (see {@code WebViewScript}) because
 *       WebView2 has no key injection API; committed characters arrive via
 *       {@link #insertText(String)}.</li>
 * </ul>
 */
@ElementRegister(Iframe.TAG_NAME)
public class Iframe extends Element {
    public static final String TAG_NAME = "IFRAME";

    /** HTML's default replaced-element size, shared with {@link Canvas}. */
    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 150;

    /** Upper bound on the hosted raster, so a runaway layout cannot allocate gigabytes. */
    private static final int MAX_VIEWPORT = 4096;

    /** WebView2 clamps ZoomFactor to this range by default and the SDK cannot widen it. */
    private static final double MIN_ZOOM = 0.25d;
    private static final double MAX_ZOOM = 5.0d;

    /** Bounds for {@code capture-scale}; below this the page stops being readable. */
    private static final double MIN_CAPTURE_SCALE = 0.25d;

    /**
     * Capture interval. This is only an upper bound on the request rate — the host never
     * starts a new capture while one is in flight, so a heavy page self-throttles.
     * 16 ms targets 60 fps for the codec to keep up with.
     */
    private static final int FRAME_INTERVAL_MS = 16;

    /** Ticks without a draw (20 Hz) before the capture loop is paused. */
    private static final int IDLE_TICKS_BEFORE_PAUSE = 40;

    private String requestedUrl;
    private String activeUrl;
    private boolean backendUnavailable;
    private CompletableFuture<AuiWebViewService.View> pendingView;
    private AuiWebViewService.View view;
    private int viewportWidth;
    private int viewportHeight;
    private double viewportZoom = Double.NaN;

    private NativeImage nativeImage;
    private Object texture;
    private TextureKey textureLocation;
    private boolean surfaceDirty;
    private int surfaceWidth;
    private int surfaceHeight;
    private int[] staging = new int[0];
    private int stagedWidth;
    private int stagedHeight;

    private boolean pointerInside;
    private int ticksSinceDraw = Integer.MAX_VALUE;
    private boolean capturePaused;
    /** Fraction of the box's device resolution to capture at; lower is faster but softer. */
    private double captureScale = 1.0d;
    private int captureQuality = AuiWebViewService.CAPTURE_AUTO;

    public Iframe(Document document) {
        super(document, TAG_NAME);
        // Internal listeners must be registered here, not in onInitFromDom: Element.init
        // rebuilds the instance through this constructor and expects the new instance to
        // wire its own handlers.
        addInternalEventListener("mousemove", this::handleMouseMove);
        addInternalEventListener("mousedown", this::handleMouseDown);
        addInternalEventListener("mouseup", this::handleMouseUp);
        addInternalEventListener("wheel", this::handleWheel);
        addInternalEventListener("keydown", this::handleKeyDown);
        addInternalEventListener("keyup", this::handleKeyUp);
        addInternalEventListener("focus", event -> setViewFocus(true));
        addInternalEventListener("blur", event -> setViewFocus(false));
    }

    @Override
    protected void onInitFromDom(Element origin) {
        requestedUrl = normalizeUrl(getAttributes().get("src"));
        captureQuality = parseCaptureQuality(getAttributes().get("capture"));
        captureScale = parseCaptureScale(getAttributes().get("capture-scale"));
        if (document != null) {
            document.markDirty(this, Drawer.RELAYOUT | Drawer.REPAINT);
        }
    }

    @Override
    public void setAttribute(String name, String value) {
        super.setAttribute(name, value);
        if ("src".equalsIgnoreCase(name)) {
            requestedUrl = normalizeUrl(value);
            if (view != null && requestedUrl != null) {
                activeUrl = requestedUrl;
                view.navigate(requestedUrl);
                view.setAutoCapture(true);
            }
            if (document != null) {
                document.markDirty(this, Drawer.REPAINT);
            }
        } else if ("capture-scale".equalsIgnoreCase(name)) {
            captureScale = parseCaptureScale(value);
        } else if ("capture".equalsIgnoreCase(name)) {
            captureQuality = parseCaptureQuality(value);
            if (view != null) {
                view.setCaptureQuality(captureQuality);
            }
        } else if ("width".equalsIgnoreCase(name) || "height".equalsIgnoreCase(name)) {
            if (document != null) {
                document.markDirty(this, Drawer.RELAYOUT | Drawer.REPAINT);
            }
        }
    }

    @Override
    public void removeAttribute(String name) {
        super.removeAttribute(name);
        if ("src".equalsIgnoreCase(name)) {
            requestedUrl = null;
            releaseView();
        }
        if ("width".equalsIgnoreCase(name) || "height".equalsIgnoreCase(name)) {
            if (document != null) {
                document.markDirty(this, Drawer.RELAYOUT | Drawer.REPAINT);
            }
        }
    }

    /** Intrinsic size from the {@code width}/{@code height} attributes, never from the frame. */
    public Size getIntrinsicSize() {
        return new Size(parseDimension(getAttributes().get("width"), DEFAULT_WIDTH),
                parseDimension(getAttributes().get("height"), DEFAULT_HEIGHT));
    }

    /** A page may host its own inputs, so the element always accepts committed text. */
    public boolean canEditText() {
        return true;
    }

    /** Receives committed characters from {@code charTyped}, mirroring {@code AbstractText}. */
    public void insertText(String text) {
        if (view != null && text != null && !text.isEmpty()) {
            view.keyText(text);
        }
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (backendUnavailable || document == null) {
            return;
        }
        tickView();
        tickIdleCapture();
        tickPointerLeave();
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                // Frames are picked up here rather than in tick(): tick runs at the client
                // tick rate (20 Hz), which would cap the embedded page at 20 fps however
                // fast the browser can paint. drawPhase runs once per rendered frame and
                // only issues native calls, so it stays inside the render-phase contract.
                ticksSinceDraw = 0;
                stageNewFrame();
                rectRenderer.drawBody(poseStack);
                drawView(poseStack, rectRenderer);
            }
            case BORDER -> rectRenderer.drawBorder(poseStack);
        }
    }

    /**
     * Stops pulling frames when the element has not been drawn for a while, and resumes on
     * the next draw. Without this an animating page keeps the browser and the capture
     * pipeline busy even while nothing on screen can show it.
     */
    private void tickIdleCapture() {
        if (view == null) {
            return;
        }
        if (ticksSinceDraw != Integer.MAX_VALUE) {
            ticksSinceDraw++;
        }
        boolean idle = ticksSinceDraw > IDLE_TICKS_BEFORE_PAUSE;
        if (idle == capturePaused) {
            return;
        }
        capturePaused = idle;
        view.setAutoCapture(!idle);
    }

    @Override
    public void onDisconnectedFromDocument() {
        releaseView();
        destroyTexture();
    }

    /** Diagnostic snapshot of the native host, or an explanation of why there is none. */
    public String status() {
        if (backendUnavailable) {
            return "unavailable: " + AuiServices.webView().unavailableReason();
        }
        if (view == null) {
            return pendingView == null ? "no view" : "starting";
        }
        return view.status();
    }

    // --- view lifecycle ------------------------------------------------------

    private void tickView() {
        if (view == null && pendingView == null && requestedUrl != null) {
            startView();
        }
        if (pendingView != null && pendingView.isDone()) {
            AuiWebViewService.View created = pendingView.join();
            pendingView = null;
            if (created == null) {
                backendUnavailable = true;
                return;
            }
            view = created;
            // Force the next pass to push bounds+zoom: the controller was created with the
            // raster size but not with the page zoom that makes the CSS viewport correct.
            viewportWidth = 0;
            viewportHeight = 0;
            viewportZoom = Double.NaN;
            capturePaused = false;
            view.setCaptureQuality(captureQuality);
        }
        if (view == null) {
            return;
        }
        if (!view.isValid()) {
            releaseView();
            backendUnavailable = true;
            return;
        }
        if (requestedUrl != null && !requestedUrl.equals(activeUrl)) {
            activeUrl = requestedUrl;
            view.navigate(activeUrl);
            view.setAutoCapture(true);
        }
        resizeViewportIfNeeded();
    }

    /**
     * Starting a browser instance costs a few hundred milliseconds, so it happens off the
     * tick thread and is picked up by a later tick. Nothing engine-visible is touched
     * there.
     */
    private void startView() {
        AuiWebViewService service = AuiServices.webView();
        if (!service.isAvailable()) {
            backendUnavailable = true;
            return;
        }
        double[] viewport = desiredViewport();
        String url = requestedUrl;
        activeUrl = url;
        int rasterWidth = (int) viewport[0];
        int rasterHeight = (int) viewport[1];
        pendingView = CompletableFuture.supplyAsync(
                () -> service.create(url, rasterWidth, rasterHeight, false, FRAME_INTERVAL_MS));
    }

    private void releaseView() {
        activeUrl = null;
        if (view != null) {
            view.close();
            view = null;
        }
        if (pendingView != null) {
            pendingView.cancel(true);
            pendingView = null;
        }
        viewportWidth = 0;
        viewportHeight = 0;
        viewportZoom = Double.NaN;
        pointerInside = false;
        surfaceDirty = false;
        stagedWidth = 0;
        stagedHeight = 0;
    }

    private void resizeViewportIfNeeded() {
        double[] viewport = desiredViewport();
        int rasterWidth = (int) viewport[0];
        int rasterHeight = (int) viewport[1];
        double zoom = viewport[2];
        if (rasterWidth == viewportWidth && rasterHeight == viewportHeight
                && Double.compare(zoom, viewportZoom) == 0) {
            return;
        }
        viewportWidth = rasterWidth;
        viewportHeight = rasterHeight;
        viewportZoom = zoom;
        view.resize(rasterWidth, rasterHeight, zoom);
    }

    /**
     * The iframe's raster size and page zoom.
     *
     * <p>Browser semantics: an iframe's CSS viewport is its content box in CSS pixels,
     * mapped onto the device pixels the box covers. WebView2 is started with a device
     * scale factor of 1, so the CSS viewport a page observes is {@code bounds / zoom};
     * feeding {@code zoom = bounds / contentBox} therefore makes the page see exactly the
     * content box while it is still rasterised at the box's device resolution. A real
     * browser does the same thing with {@code devicePixelRatio}.</p>
     *
     * <p>Returns {@code {rasterWidth, rasterHeight, zoom}}.</p>
     */
    private double[] desiredViewport() {
        Size contentSize = Box.of(this).innerSize();
        // Device pixels per document CSS pixel. getViewportScaleX() is only GUI pixels
        // per CSS pixel, which is the wrong factor: the raster has to be sized in real
        // device pixels or the page is rendered below screen resolution and upscaled.
        double deviceScale = document == null ? 1.0d : document.getViewport().scissorScale();
        if (!(deviceScale > 0.0d) || !Double.isFinite(deviceScale)) {
            deviceScale = 1.0d;
        }
        double boxWidth = Math.max(1.0d, contentSize.width());
        double boxHeight = Math.max(1.0d, contentSize.height());
        double scale = Math.max(MIN_CAPTURE_SCALE, Math.min(1.0d, captureScale));
        int rasterWidth = clampViewport((int) Math.round(boxWidth * deviceScale * scale));
        int rasterHeight = clampViewport((int) Math.round(boxHeight * deviceScale * scale));
        // Derive zoom from the raster we actually got, so a clamped raster still yields
        // the correct CSS viewport.
        double zoom = clampZoom(rasterWidth / boxWidth);
        return new double[]{rasterWidth, rasterHeight, zoom};
    }

    private static int clampViewport(int value) {
        return Math.max(1, Math.min(MAX_VIEWPORT, value));
    }

    /** WebView2 clamps ZoomFactor to [0.25, 5] by default and this SDK exposes no way to widen it. */
    private static double clampZoom(double zoom) {
        if (!(zoom > 0.0d) || !Double.isFinite(zoom)) {
            return 1.0d;
        }
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    // --- frames --------------------------------------------------------------

    /** Pulls the newest frame from the backend and stages a copy for {@code drawView}. */
    private void stageNewFrame() {
        if (view == null) {
            return;
        }
        AuiWebViewService.Frame frame = view.pollFrame();
        if (frame == null) {
            return;
        }
        int width = frame.width();
        int height = frame.height();
        int[] pixels = frame.pixels();
        if (width <= 0 || height <= 0 || pixels == null) {
            return;
        }
        int needed = width * height;
        if (needed > pixels.length) {
            return;
        }
        if (staging.length < needed) {
            staging = new int[needed];
        }
        // The service reuses its pixel array, so stage a copy; the texture upload happens
        // right after this in the same draw phase.
        System.arraycopy(pixels, 0, staging, 0, needed);
        stagedWidth = width;
        stagedHeight = height;
        surfaceDirty = true;
    }

    private void drawView(PoseStack poseStack, Rect rectRenderer) {
        syncTexture();
        if (textureLocation == null) {
            return;
        }
        Position contentPos = rectRenderer.getContentPosition();
        Size contentSize = Box.of(this).innerSize();
        if (contentSize.width() <= 0 || contentSize.height() <= 0) {
            return;
        }
        ImageDrawer.draw(poseStack, textureLocation,
                (float) contentPos.x, (float) contentPos.y,
                (float) contentSize.width(), (float) contentSize.height(), true);
    }

    private void syncTexture() {
        if (!surfaceDirty || stagedWidth <= 0 || stagedHeight <= 0) {
            return;
        }
        if (nativeImage == null || texture == null || textureLocation == null
                || nativeImage.getWidth() != stagedWidth || nativeImage.getHeight() != stagedHeight) {
            destroyTexture();
            nativeImage = new NativeImage(NativeImage.Format.RGBA, stagedWidth, stagedHeight, true);
            texture = AuiServices.render().createDynamicTexture("webview/" + uuid, nativeImage, true);
            textureLocation = TextureKey.of("webview/"
                    + UUID.nameUUIDFromBytes(uuid.toString().getBytes(StandardCharsets.UTF_8)));
            AuiServices.render().registerTexture(texture, AuiServices.resources().textureLocation(textureLocation));
        }
        AuiServices.render().writeImagePixels(nativeImage, 0, 0, stagedWidth, stagedHeight, staging);
        AuiServices.render().uploadTextureRegion(texture, nativeImage, 0, 0, stagedWidth, stagedHeight, true);
        surfaceDirty = false;
    }

    private void destroyTexture() {
        if (texture != null) {
            try {
                AuiServices.render().closeTexture(texture);
            } catch (Exception ignored) {
            }
        }
        texture = null;
        nativeImage = null;
        textureLocation = null;
    }

    // --- input ---------------------------------------------------------------

    private void handleMouseMove(Event event) {
        if (!(event instanceof MouseEvent mouse) || view == null) {
            return;
        }
        Position contentPos = Rect.of(this).getContentPosition();
        Size contentSize = Box.of(this).innerSize();
        double localX = mouse.clientX - contentPos.x;
        double localY = mouse.clientY - contentPos.y;
        if (localX < 0 || localY < 0 || localX > contentSize.width() || localY > contentSize.height()) {
            return;
        }
        pointerInside = true;
        view.mouseMove(scaleX(localX, contentSize.width()), scaleY(localY, contentSize.height()),
                modifiersOf(mouse));
    }

    private void handleMouseDown(Event event) {
        if (!(event instanceof MouseEvent mouse) || view == null) {
            return;
        }
        int[] point = toViewport(mouse);
        if (point == null) {
            return;
        }
        view.mouseButton(mouse.button, true, mouse.clickCount >= 2, modifiersOf(mouse), point[0], point[1]);
        consume(event);
    }

    private void handleMouseUp(Event event) {
        if (!(event instanceof MouseEvent mouse) || view == null) {
            return;
        }
        int[] point = toViewport(mouse);
        if (point == null) {
            return;
        }
        view.mouseButton(mouse.button, false, mouse.clickCount >= 2, modifiersOf(mouse), point[0], point[1]);
        consume(event);
    }

    private void handleWheel(Event event) {
        if (!(event instanceof MouseEvent mouse) || view == null) {
            return;
        }
        int[] point = toViewport(mouse);
        if (point == null) {
            return;
        }
        // Positive deltaY scrolls the content down, matching the SPI.
        int notches = mouse.deltaY > 0 ? 1 : -1;
        view.mouseWheel(notches, false, modifiersOf(mouse), point[0], point[1]);
        // Prevent the engine's own scroll default; this element owns the gesture.
        event.preventDefault();
        consume(event);
    }

    private static int modifiersOf(MouseEvent mouse) {
        int modifiers = 0;
        if (mouse.shiftKey) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (mouse.controlKey) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (mouse.altKey) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }
        return modifiers;
    }

    private void handleKeyDown(Event event) {
        if (!(event instanceof KeyEvent key) || view == null) {
            return;
        }
        view.keyDown(key.key, key.code, key.modifiers, key.repeat);
        // preventDefault is what makes Operation.onKeyPressed report the event as
        // consumed, which is what keeps Minecraft hotkeys from firing while the page
        // has focus.
        event.preventDefault();
    }

    private void handleKeyUp(Event event) {
        if (!(event instanceof KeyEvent key) || view == null) {
            return;
        }
        view.keyUp(key.key, key.code, key.modifiers);
        event.preventDefault();
    }

    private void setViewFocus(boolean focused) {
        if (view != null) {
            view.setFocus(focused);
        }
    }

    private void tickPointerLeave() {
        if (!pointerInside || view == null) {
            return;
        }
        Position screen = AuiServices.client().getMousePosition();
        if (screen == null) {
            return;
        }
        Position point = document.screenToDocumentPosition(screen);
        if (point == null) {
            return;
        }
        Position contentPos = Rect.of(this).getContentPosition();
        Size contentSize = Box.of(this).innerSize();
        double localX = point.x - contentPos.x;
        double localY = point.y - contentPos.y;
        if (localX >= 0 && localY >= 0 && localX <= contentSize.width() && localY <= contentSize.height()) {
            return;
        }
        pointerInside = false;
        view.mouseLeave();
    }

    /** Maps a document-space pointer position to viewport pixels; null when outside. */
    private int[] toViewport(MouseEvent mouse) {
        Position contentPos = Rect.of(this).getContentPosition();
        Size contentSize = Box.of(this).innerSize();
        double localX = mouse.clientX - contentPos.x;
        double localY = mouse.clientY - contentPos.y;
        if (localX < 0 || localY < 0 || localX > contentSize.width() || localY > contentSize.height()) {
            return null;
        }
        return new int[]{scaleX(localX, contentSize.width()), scaleY(localY, contentSize.height())};
    }

    private int scaleX(double localX, double contentWidth) {
        if (viewportWidth <= 0 || contentWidth <= 0) {
            return (int) Math.round(localX);
        }
        return clamp((int) Math.round(localX * viewportWidth / contentWidth), viewportWidth);
    }

    private int scaleY(double localY, double contentHeight) {
        if (viewportHeight <= 0 || contentHeight <= 0) {
            return (int) Math.round(localY);
        }
        return clamp((int) Math.round(localY * viewportHeight / contentHeight), viewportHeight);
    }

    private static int clamp(int value, int limit) {
        return Math.max(0, Math.min(limit, value));
    }

    private static void consume(Event event) {
        event.stopPropagation();
    }

    // --- helpers -------------------------------------------------------------

    /** {@code capture="lossless"} / {@code capture="fast"}; anything else means auto. */
    private static int parseCaptureQuality(String value) {
        if (value == null) {
            return AuiWebViewService.CAPTURE_AUTO;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("stream".equals(normalized) || "raw".equals(normalized)) {
            return AuiWebViewService.CAPTURE_STREAM;
        }
        if ("lossless".equals(normalized) || "png".equals(normalized)) {
            return AuiWebViewService.CAPTURE_LOSSLESS;
        }
        if ("fast".equals(normalized) || "jpeg".equals(normalized)) {
            return AuiWebViewService.CAPTURE_FAST;
        }
        return AuiWebViewService.CAPTURE_AUTO;
    }

    /** {@code capture-scale="0.5"} captures at half the box's device resolution. */
    private static double parseCaptureScale(String value) {
        if (value == null || value.isBlank()) {
            return 1.0d;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed) || parsed <= 0.0d) {
                return 1.0d;
            }
            return Math.max(MIN_CAPTURE_SCALE, Math.min(1.0d, parsed));
        } catch (NumberFormatException ignored) {
            return 1.0d;
        }
    }

    private static String normalizeUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int parseDimension(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, (int) Math.round(Double.parseDouble(value.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
