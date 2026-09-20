package com.sighs.apricityui.spi;

/**
 * Offscreen web view backend SPI.
 *
 * <p>A backend hosts a system web view (WebView2 on Windows) inside a window the user
 * never sees, and publishes its pixels as a plain RGBA buffer. {@code common} turns
 * that buffer into a texture, so {@code <iframe>} behaves like any other
 * texture-backed element — there is no browser embedding the AUI renderer has to
 * understand.</p>
 *
 * <p>headless default implementation is the unavailable backend:
 * {@link AuiServices.Defaults#WEBVIEW} reports {@code isAvailable() == false} and
 * {@code create(...) == null}, so the element can degrade to a placeholder without
 * a null check at every call site. In game the Windows backend is installed by
 * {@code NativeWebViewService} from each loader's client bootstrap.</p>
 *
 * <p>Threading: every method is safe to call from the tick/render thread and never
 * blocks on the browser. Pixels arrive asynchronously through {@link View#channel()},
 * a mapped block of shared memory the backend appends incremental image updates to, so
 * the renderer only ever touches (and uploads) the rectangles that changed.</p>
 */
public interface AuiWebViewService {

    /** Raw pixel stream when the platform offers one, otherwise the adaptive codec. Default. */
    int CAPTURE_AUTO = 0;
    /** Raw pixel stream only — no codec in the path at all. */
    int CAPTURE_STREAM = 1;
    /** Always lossless, whatever it costs per frame. */
    int CAPTURE_LOSSLESS = 2;
    /** Always fast, accepting codec artefacts. */
    int CAPTURE_FAST = 3;

    /** Whether this backend can host a view right now (runtime present, supported OS). */
    boolean isAvailable();

    /** Backend label for diagnostics, e.g. {@code "webview2"}. */
    default String backendName() {
        return "none";
    }

    /** Explains why {@link #isAvailable()} is false; empty when it is true. */
    default String unavailableReason() {
        return isAvailable() ? "" : backendName() + " backend unavailable";
    }

    /**
     * Creates a view. Returns null when the backend is unavailable or the browser
     * could not be started; the caller then falls back to a placeholder.
     *
     * @param url             initial location, or null for a blank page
     * @param width           viewport width in device pixels
     * @param height          viewport height in device pixels
     * @param transparent     ask the page to composite over transparent black
     * @param frameIntervalMs capture interval; larger values trade latency for CPU
     */
    View create(String url, int width, int height, boolean transparent, int frameIntervalMs);

    /** A hosted web view. All methods are no-ops once {@link #isValid()} is false. */
    interface View {

        /** Opaque identifier, stable for the lifetime of the view; useful in logs. */
        long id();

        /** False once the native host has shut down. */
        boolean isValid();

        void navigate(String url);

        /**
         * Sets the raster size and the page zoom together.
         *
         * <p>The CSS viewport the page sees is {@code width / zoom} CSS pixels: a real
         * browser keeps an iframe's CSS viewport equal to the element's content box and
         * maps it onto the box's device pixels, so callers pass
         * {@code width = contentBox × deviceScale} and {@code zoom = deviceScale} to get
         * both a conformant viewport and a 1:1 raster. The two are applied atomically so
         * the page never observes a half-applied state.</p>
         *
         * <p>The frame may lag one capture interval behind.</p>
         */
        void resize(int width, int height, double zoom);

        /** Capture interval in milliseconds; animating pages need ~16-33. */
        void setFrameInterval(int milliseconds);

        /** Pauses/resumes periodic capture without tearing the browser down. */
        void setAutoCapture(boolean enabled);

        /**
         * Capture quality for this view.
         *
         * <p>A capture path is usually lossless-but-slow or lossy-but-fast, and the right
         * choice depends on the content: a static panel is encoded once and then
         * de-duplicated, so quality is free, while a page that keeps changing needs frame
         * rate. {@link #CAPTURE_AUTO} picks per frame accordingly.</p>
         */
        void setCaptureQuality(int quality);

        /** Gives the page keyboard focus (independent of AUI's own focus ring). */
        void setFocus(boolean focused);

        /** Moves the pointer, in viewport pixels. @param modifiers GLFW modifier bits */
        void mouseMove(int x, int y, int modifiers);

        /**
         * @param button 0 = left, 1 = middle, 2 = right, matching DOM {@code MouseEvent.button}
         * @param modifiers GLFW modifier bits
         */
        void mouseButton(int button, boolean pressed, boolean doubleClick, int modifiers, int x, int y);

        /**
         * @param delta    wheel notches, positive scrolling the content down
         * @param modifiers GLFW modifier bits
         */
        void mouseWheel(int delta, boolean horizontal, int modifiers, int x, int y);

        /** Synthesises a pointer leaving the viewport so {@code :hover} is cleared. */
        void mouseLeave();

        /**
         * Synthesises {@code keydown}.
         *
         * @param key      DOM {@code KeyboardEvent.key} value, e.g. {@code "a"}, {@code "Enter"}
         * @param code     DOM {@code KeyboardEvent.code} value, e.g. {@code "KeyA"}
         * @param modifiers GLFW modifier bits, matching {@code KeyEvent.modifiers}
         * @param repeat   whether this is an auto-repeat
         */
        void keyDown(String key, String code, int modifiers, boolean repeat);

        /** Synthesises {@code keyup}. */
        void keyUp(String key, String code, int modifiers);

        /**
         * Delivers committed text — already through the OS input method — to the focused
         * editable element. Kept separate from {@link #keyDown} because a synthesised key
         * event never triggers a browser default action, so text has to be inserted
         * explicitly.
         */
        void keyText(String text);

        /** Runs script in the page. */
        void eval(String script);

        /**
         * The view's image update stream, or null when this backend cannot stream pixels.
         *
         * <p>The backend writes incremental image updates — the absolute pixels of the
         * rectangles that changed, plus the geometry — into a mapped block of shared memory
         * and this returns the reader's view of it. Nothing about a picture crosses JNI any
         * more, and a frame that changes one rectangle costs one rectangle.</p>
         *
         * <p>The returned handle is valid until {@link #close()}; the memory behind the
         * buffer is released there, so the reader must drop the buffer first.
         * {@link com.sighs.apricityui.webview.FrameUpdateChannel} parses it.</p>
         */
        Channel channel();

        /** Diagnostic snapshot for bug reports; never null. */
        String status();

        /** Releases the browser and its host window. Idempotent. */
        void close();
    }

    /**
     * A view's image update stream.
     *
     * <p>{@link #buffer()} is a little-endian mapping of the shared block; the layout is a
     * wire contract with the native host ({@code native/webview/src/frame_channel.h}) and is
     * read by {@link com.sighs.apricityui.webview.FrameUpdateChannel}.</p>
     */
    interface Channel {

        /** Mapped view of the update stream, or null once {@link #close()} has run. */
        java.nio.ByteBuffer buffer();

        /**
         * Drops the reader's mapping. Idempotent, and must be called before
         * {@link View#close()} — the native host unmaps the block when the view goes away,
         * so nothing may touch the buffer afterwards.
         */
        void close();
    }
}
