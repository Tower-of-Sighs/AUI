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

    /** Upper bound on the hosted viewport, so a runaway layout cannot allocate gigabytes. */
    private static final int MAX_VIEWPORT = 4096;

    /** 30 fps; the PNG capture path costs roughly 25 ms per frame regardless of size. */
    private static final int FRAME_INTERVAL_MS = 33;

    private String requestedUrl;
    private String activeUrl;
    private boolean backendUnavailable;
    private CompletableFuture<AuiWebViewService.View> pendingView;
    private AuiWebViewService.View view;
    private int viewportWidth;
    private int viewportHeight;

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
        tickFrame();
        tickPointerLeave();
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                drawView(poseStack, rectRenderer);
            }
            case BORDER -> rectRenderer.drawBorder(poseStack);
        }
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
            viewportWidth = 0;
            viewportHeight = 0;
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
        int[] viewport = desiredViewport();
        String url = requestedUrl;
        activeUrl = url;
        pendingView = CompletableFuture.supplyAsync(
                () -> service.create(url, viewport[0], viewport[1], false, FRAME_INTERVAL_MS));
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
        pointerInside = false;
        surfaceDirty = false;
        stagedWidth = 0;
        stagedHeight = 0;
    }

    private void resizeViewportIfNeeded() {
        int[] viewport = desiredViewport();
        if (viewport[0] == viewportWidth && viewport[1] == viewportHeight) {
            return;
        }
        viewportWidth = viewport[0];
        viewportHeight = viewport[1];
        view.resize(viewportWidth, viewportHeight);
    }

    /** Content box size in device pixels, which is the resolution the page renders at. */
    private int[] desiredViewport() {
        Size contentSize = Box.of(this).innerSize();
        double scaleX = document == null ? 1.0d : document.getViewportScaleX();
        double scaleY = document == null ? 1.0d : document.getViewportScaleY();
        int width = (int) Math.round(Math.max(1.0d, contentSize.width() * Math.max(0.01d, scaleX)));
        int height = (int) Math.round(Math.max(1.0d, contentSize.height() * Math.max(0.01d, scaleY)));
        return new int[]{Math.min(MAX_VIEWPORT, width), Math.min(MAX_VIEWPORT, height)};
    }

    // --- frames --------------------------------------------------------------

    private void tickFrame() {
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
        // The service reuses its pixel array, so stage a copy: the texture upload happens
        // in drawPhase, after this tick has returned.
        System.arraycopy(pixels, 0, staging, 0, needed);
        stagedWidth = width;
        stagedHeight = height;
        surfaceDirty = true;
        document.markDirty(this, Drawer.REPAINT);
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
