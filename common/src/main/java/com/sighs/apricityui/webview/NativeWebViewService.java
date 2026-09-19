package com.sighs.apricityui.webview;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.AuiWebViewService;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * WebView2-backed {@link AuiWebViewService}, shared by every loader target.
 *
 * <p>The backend is version-neutral: it drives {@link WebViewNative} and has no
 * Minecraft dependency beyond asking {@link AuiServices#client()} for a writable
 * data directory. That mirrors how {@code OpenAlAudioService} is shared from
 * {@code common} while each target only injects the bits that differ.</p>
 */
public final class NativeWebViewService implements AuiWebViewService {

    public static final NativeWebViewService INSTANCE = new NativeWebViewService();

    /** 30 fps is the sweet spot for the PNG capture path; see the class doc of the native host. */
    private static final int DEFAULT_FRAME_INTERVAL_MS = 33;

    // COREWEBVIEW2_MOUSE_EVENT_KIND values, which reuse the Win32 message ids.
    private static final int MOUSE_MOVE = 512;
    private static final int MOUSE_LEFT_DOWN = 513;
    private static final int MOUSE_LEFT_UP = 514;
    private static final int MOUSE_LEFT_DOUBLE = 515;
    private static final int MOUSE_RIGHT_DOWN = 516;
    private static final int MOUSE_RIGHT_UP = 517;
    private static final int MOUSE_RIGHT_DOUBLE = 518;
    private static final int MOUSE_MIDDLE_DOWN = 519;
    private static final int MOUSE_MIDDLE_UP = 520;
    private static final int MOUSE_MIDDLE_DOUBLE = 521;
    private static final int MOUSE_WHEEL = 522;
    private static final int MOUSE_HWHEEL = 526;
    private static final int MOUSE_LEAVE = 675;

    // COREWEBVIEW2_MOUSE_EVENT_VIRTUAL_KEYS flags.
    private static final int VK_LEFT_BUTTON = 0x1;
    private static final int VK_RIGHT_BUTTON = 0x2;
    private static final int VK_SHIFT = 0x4;
    private static final int VK_CONTROL = 0x8;
    private static final int VK_MIDDLE_BUTTON = 0x10;

    private static final int WHEEL_DELTA = 120;
    private static final int MAX_WHEEL_NOTCHES = 10;

    private NativeWebViewService() {
    }

    @Override
    public boolean isAvailable() {
        return WebViewNative.isAvailable();
    }

    @Override
    public String backendName() {
        return "webview2";
    }

    @Override
    public String unavailableReason() {
        return WebViewNative.unavailableReason();
    }

    /** Installed runtime version, for diagnostics; empty when unavailable. */
    public String browserVersion() {
        return WebViewNative.browserVersion();
    }

    @Override
    public View create(String url, int width, int height, boolean transparent, int frameIntervalMs) {
        if (!WebViewNative.isAvailable()) {
            return null;
        }
        long handle = WebViewNative.create(
                url,
                userDataDirectory(),
                Math.max(1, width),
                Math.max(1, height),
                transparent,
                true,
                frameIntervalMs <= 0 ? DEFAULT_FRAME_INTERVAL_MS : Math.max(8, frameIntervalMs));
        if (handle == 0L) {
            return null;
        }
        return new NativeView(handle);
    }

    /**
     * A per-installation profile directory so cookies and local storage survive a
     * restart, and so every iframe in one game process shares a single browser
     * process. Null lets WebView2 pick its own default.
     */
    private static String userDataDirectory() {
        try {
            Path gameDirectory = AuiServices.client().getGameDirectory();
            if (gameDirectory == null) {
                return null;
            }
            Path directory = gameDirectory.resolve("apricity").resolve("webview");
            Files.createDirectories(directory);
            return directory.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class NativeView implements View {
        private final long handle;
        private final int[] meta = new int[2];
        private final NativeFrame frame = new NativeFrame();
        private int[] pixels;
        private boolean closed;

        private NativeView(long handle) {
            this.handle = handle;
        }

        @Override
        public long id() {
            return handle;
        }

        @Override
        public boolean isValid() {
            return !closed && WebViewNative.isAlive(handle);
        }

        @Override
        public void navigate(String url) {
            if (closed || url == null) {
                return;
            }
            WebViewNative.navigate(handle, url);
        }

        @Override
        public void resize(int width, int height, double zoom) {
            if (closed) {
                return;
            }
            WebViewNative.setBoundsAndZoom(handle, Math.max(1, width), Math.max(1, height), zoom);
        }

        @Override
        public void setFrameInterval(int milliseconds) {
            if (closed) {
                return;
            }
            WebViewNative.setFrameInterval(handle, Math.max(8, milliseconds));
        }

        @Override
        public void setAutoCapture(boolean enabled) {
            if (closed) {
                return;
            }
            WebViewNative.setAutoCapture(handle, enabled);
        }

        @Override
        public void setFocus(boolean focused) {
            if (closed) {
                return;
            }
            WebViewNative.focus(handle, focused);
        }

        @Override
        public void mouseMove(int x, int y, int modifiers) {
            if (closed) {
                return;
            }
            WebViewNative.mouse(handle, MOUSE_MOVE, modifierKeys(modifiers), 0, x, y);
        }

        @Override
        public void mouseButton(int button, boolean pressed, boolean doubleClick, int modifiers, int x, int y) {
            if (closed) {
                return;
            }
            int kind;
            int flag;
            switch (button) {
                case 0 -> {
                    kind = pressed ? (doubleClick ? MOUSE_LEFT_DOUBLE : MOUSE_LEFT_DOWN) : MOUSE_LEFT_UP;
                    flag = VK_LEFT_BUTTON;
                }
                case 1 -> {
                    kind = pressed ? (doubleClick ? MOUSE_MIDDLE_DOUBLE : MOUSE_MIDDLE_DOWN) : MOUSE_MIDDLE_UP;
                    flag = VK_MIDDLE_BUTTON;
                }
                case 2 -> {
                    kind = pressed ? (doubleClick ? MOUSE_RIGHT_DOUBLE : MOUSE_RIGHT_DOWN) : MOUSE_RIGHT_UP;
                    flag = VK_RIGHT_BUTTON;
                }
                default -> {
                    return;
                }
            }
            int keys = modifierKeys(modifiers) | (pressed ? flag : 0);
            WebViewNative.mouse(handle, kind, keys, 0, x, y);
        }

        @Override
        public void mouseWheel(int delta, boolean horizontal, int modifiers, int x, int y) {
            if (closed || delta == 0) {
                return;
            }
            int notches = Math.max(-MAX_WHEEL_NOTCHES, Math.min(MAX_WHEEL_NOTCHES, delta));
            // A positive delta scrolls the content down, which is a negative Win32 wheel
            // delta (wheel rotated towards the user).
            int mouseData = (horizontal ? notches : -notches) * WHEEL_DELTA;
            WebViewNative.mouse(handle, horizontal ? MOUSE_HWHEEL : MOUSE_WHEEL,
                    modifierKeys(modifiers), mouseData, x, y);
        }

        @Override
        public void mouseLeave() {
            if (closed) {
                return;
            }
            WebViewNative.mouse(handle, MOUSE_LEAVE, 0, 0, 0, 0);
        }

        @Override
        public void keyDown(String key, String code, int modifiers, boolean repeat) {
            if (closed) {
                return;
            }
            WebViewNative.eval(handle, WebViewScript.keyScript("keydown", key, code, modifiers, repeat));
        }

        @Override
        public void keyUp(String key, String code, int modifiers) {
            if (closed) {
                return;
            }
            WebViewNative.eval(handle, WebViewScript.keyScript("keyup", key, code, modifiers, false));
        }

        @Override
        public void keyText(String text) {
            if (closed || text == null || text.isEmpty()) {
                return;
            }
            WebViewNative.eval(handle, WebViewScript.textScript(text));
        }

        @Override
        public void eval(String script) {
            if (closed || script == null) {
                return;
            }
            WebViewNative.eval(handle, script);
        }

        @Override
        public Frame pollFrame() {
            if (closed) {
                return null;
            }
            // -1 means a frame is pending but the buffer is missing or too small: read the
            // geometry from meta, grow, and ask again.
            for (int attempt = 0; attempt < 4; attempt++) {
                long sequence = WebViewNative.pollFrame(handle, pixels, meta);
                if (sequence == 0L) {
                    return null;
                }
                if (sequence > 0L) {
                    frame.update(meta[0], meta[1], pixels, sequence);
                    return frame;
                }
                int needed = meta[0] * meta[1];
                if (needed <= 0) {
                    return null;
                }
                if (pixels != null && pixels.length >= needed) {
                    return null;
                }
                pixels = new int[needed];
            }
            return null;
        }

        @Override
        public String status() {
            return WebViewNative.status(handle);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            WebViewNative.destroy(handle);
        }

        private static int modifierKeys(int glfwModifiers) {
            int keys = 0;
            if ((glfwModifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                keys |= VK_SHIFT;
            }
            if ((glfwModifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                keys |= VK_CONTROL;
            }
            return keys;
        }
    }

    /** Reused frame handle; the pixel array belongs to the view. */
    private static final class NativeFrame implements Frame {
        private int width;
        private int height;
        private int[] pixels;
        private long sequence;

        void update(int width, int height, int[] pixels, long sequence) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
            this.sequence = sequence;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int[] pixels() {
            return pixels;
        }

        @Override
        public long sequence() {
            return sequence;
        }
    }
}
