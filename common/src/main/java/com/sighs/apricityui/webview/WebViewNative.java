package com.sighs.apricityui.webview;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;

/**
 * Raw JNI binding for the offscreen WebView2 host.
 *
 * <p>This class is deliberately free of any Minecraft or AUI dependency: it only
 * locates and loads {@code apricityui_webview.dll} (Windows x64) and exposes the
 * native entry points. {@link NativeWebViewService} is what adapts it to
 * {@link com.sighs.apricityui.spi.AuiWebViewService}.</p>
 *
 * <p>Every entry point is a no-op when the backend is unavailable, so callers can
 * use it without checking {@link #isAvailable()} at each call site. Failures never
 * throw; they surface through {@link #lastCreateError()} / {@link #lastError(long)}
 * and through {@link #isAlive(long)} returning false.</p>
 */
public final class WebViewNative {
    /** Resource path inside the jar; see {@code scripts/build-webview-native.ps1}. */
    private static final String LIBRARY_RESOURCE = "/assets/apricityui/native/windows-x64/apricityui_webview.dll";
    private static final String LIBRARY_NAME = "apricityui_webview.dll";

    private static final boolean AVAILABLE;
    private static final String UNAVAILABLE_REASON;
    private static final String BROWSER_VERSION;

    static {
        String reason = null;
        boolean loaded = false;
        String version = "";
        try {
            reason = unsupportedPlatformReason();
            if (reason == null) {
                loadNativeLibrary();
                loaded = true;
            }
        } catch (Throwable failure) {
            loaded = false;
            reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        if (loaded) {
            try {
                version = nBrowserVersion();
            } catch (Throwable ignored) {
                version = "";
            }
            if (version == null) {
                version = "";
            }
        }
        AVAILABLE = loaded;
        UNAVAILABLE_REASON = reason;
        BROWSER_VERSION = version;
    }

    private WebViewNative() {
    }

    /** True when the DLL is loaded and the WebView2 runtime is installed. */
    public static boolean isAvailable() {
        if (!AVAILABLE) {
            return false;
        }
        try {
            return nAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Human readable reason for {@link #isAvailable()} being false; empty when available. */
    public static String unavailableReason() {
        if (!AVAILABLE) {
            return UNAVAILABLE_REASON == null ? "native library not loaded" : UNAVAILABLE_REASON;
        }
        try {
            return nAvailable() ? "" : "WebView2 runtime is not installed";
        } catch (Throwable failure) {
            return String.valueOf(failure.getMessage());
        }
    }

    /** Installed WebView2 runtime version, or an empty string when unknown. */
    public static String browserVersion() {
        return BROWSER_VERSION;
    }

    private static String unsupportedPlatformReason() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("windows")) {
            return "offscreen WebView2 hosting is Windows-only (os=" + os + ")";
        }
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!arch.contains("64") || arch.contains("aarch64")) {
            return "offscreen WebView2 hosting needs a 64-bit x86 JVM (arch=" + arch + ")";
        }
        return null;
    }

    private static void loadNativeLibrary() throws IOException {
        byte[] payload;
        try (InputStream resource = WebViewNative.class.getResourceAsStream(LIBRARY_RESOURCE)) {
            if (resource == null) {
                throw new IOException("missing bundled native library " + LIBRARY_RESOURCE);
            }
            payload = readFully(resource);
        }
        if (payload.length == 0) {
            throw new IOException("bundled native library " + LIBRARY_RESOURCE + " is empty");
        }

        Path directory = Paths.get(System.getProperty("java.io.tmpdir"), "apricityui-webview");
        Files.createDirectories(directory);
        // Content-addressed name: an upgraded jar extracts a fresh file instead of
        // fighting the Windows file lock held by an already loaded DLL.
        Path library = directory.resolve("apricityui_webview-" + payload.length + "-"
                + Integer.toHexString(Arrays.hashCode(payload)) + ".dll");
        if (!Files.exists(library)) {
            Path staging = directory.resolve(library.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(staging)) {
                output.write(payload);
            }
            try {
                Files.move(staging, library, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException raced) {
                if (!Files.exists(library)) {
                    throw raced;
                }
                Files.deleteIfExists(staging);
            }
        }
        try {
            System.load(library.toAbsolutePath().toString());
        } catch (UnsatisfiedLinkError alreadyLoaded) {
            // Another classloader in this JVM already mapped the same file; that is fine
            // as long as the entry points resolve, which the caller probes next.
            if (!nAvailable()) {
                throw alreadyLoaded;
            }
        }
    }

    private static byte[] readFully(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(1 << 17);
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    public static long create(String url, String userDataDir, int width, int height,
                              boolean transparent, boolean autoCapture, int frameIntervalMs,
                              int frameFormat) {
        if (!AVAILABLE) {
            return 0L;
        }
        try {
            return nCreate(url, userDataDir, width, height, transparent, autoCapture, frameIntervalMs,
                    frameFormat);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static String lastCreateError() {
        if (!AVAILABLE) {
            return UNAVAILABLE_REASON == null ? "" : UNAVAILABLE_REASON;
        }
        try {
            return nLastCreateError();
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static void destroy(long handle) {
        if (!AVAILABLE || handle == 0L) {
            return;
        }
        try {
            nDestroy(handle);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isAlive(long handle) {
        if (!AVAILABLE || handle == 0L) {
            return false;
        }
        try {
            return nIsAlive(handle);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String lastError(long handle) {
        if (!AVAILABLE || handle == 0L) {
            return "";
        }
        try {
            return nLastError(handle);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Diagnostic snapshot of the native host; empty when the backend is unavailable. */
    public static String status(long handle) {
        if (!AVAILABLE || handle == 0L) {
            return "";
        }
        try {
            return nStatus(handle);
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static void navigate(long handle, String url) {
        run(handle, () -> nNavigate(handle, url));
    }

    /**
     * Sets the raster size and the page zoom in one native call, so the page never lays
     * out against a mismatched viewport. See
     * {@link com.sighs.apricityui.spi.AuiWebViewService.View#resize(int, int, double)}.
     */
    public static void setBoundsAndZoom(long handle, int width, int height, double zoom) {
        run(handle, () -> nSetBoundsAndZoom(handle, width, height, zoom));
    }

    /** Capture codec: 0 = PNG (lossless), 1 = JPEG (much cheaper on heavy pages). */
    public static void setFrameFormat(long handle, int format) {
        run(handle, () -> nSetFrameFormat(handle, format));
    }

    public static void setFrameInterval(long handle, int milliseconds) {
        run(handle, () -> nSetFrameInterval(handle, milliseconds));
    }

    public static void setAutoCapture(long handle, boolean enabled) {
        run(handle, () -> nSetAutoCapture(handle, enabled));
    }

    public static void requestCapture(long handle) {
        run(handle, () -> nRequestCapture(handle));
    }

    public static void focus(long handle, boolean focused) {
        run(handle, () -> nFocus(handle, focused));
    }

    public static void mouse(long handle, int kind, int virtualKeys, int mouseData, int x, int y) {
        run(handle, () -> nMouse(handle, kind, virtualKeys, mouseData, x, y));
    }

    public static void eval(long handle, String script) {
        run(handle, () -> nEval(handle, script));
    }

    /**
     * Maps the view's image update stream.
     *
     * @return a little-endian view of the shared block, or null when the backend has no
     *         stream; see {@code native/webview/src/frame_channel.h} for the layout.
     */
    public static java.nio.ByteBuffer mapChannel(long handle) {
        if (!AVAILABLE || handle == 0L) {
            return null;
        }
        try {
            return nMapChannel(handle);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private interface NativeCall {
        void invoke();
    }

    private static void run(long handle, NativeCall call) {
        if (!AVAILABLE || handle == 0L) {
            return;
        }
        try {
            call.invoke();
        } catch (Throwable ignored) {
        }
    }

    // --- native entry points -------------------------------------------------

    private static native boolean nAvailable();

    private static native String nBrowserVersion();

    private static native long nCreate(String url, String userDataDir, int width, int height,
                                       boolean transparent, boolean autoCapture, int frameIntervalMs,
                                       int frameFormat);

    private static native String nLastCreateError();

    private static native void nDestroy(long handle);

    private static native boolean nIsAlive(long handle);

    private static native String nLastError(long handle);

    private static native String nStatus(long handle);

    private static native void nNavigate(long handle, String url);

    private static native void nSetBoundsAndZoom(long handle, int width, int height, double zoom);

    private static native void nSetFrameFormat(long handle, int format);

    private static native void nSetFrameInterval(long handle, int milliseconds);

    private static native void nSetAutoCapture(long handle, boolean enabled);

    private static native void nRequestCapture(long handle);

    private static native void nFocus(long handle, boolean focused);

    private static native void nMouse(long handle, int kind, int virtualKeys, int mouseData, int x, int y);

    private static native void nEval(long handle, String script);

    private static native java.nio.ByteBuffer nMapChannel(long handle);
}
