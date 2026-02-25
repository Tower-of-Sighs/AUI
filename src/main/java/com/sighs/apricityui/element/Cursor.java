package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.async.cursor.CursorAsyncHandler;
import com.sighs.apricityui.resource.async.cursor.CursorHandle;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Cursor extends Element {
    private static final Map<Integer, Long> STANDARD = new HashMap<>();
    private static boolean initialized = false;
    private static long currentHandle = 0L;

    private Cursor() {
        // Cursor 仅用于静态光标控制，不参与 DOM 树。
        super(null, "CURSOR_MANAGER");
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        put(GLFW.GLFW_ARROW_CURSOR);
        put(GLFW.GLFW_IBEAM_CURSOR);
        put(GLFW.GLFW_CROSSHAIR_CURSOR);
        put(GLFW.GLFW_HAND_CURSOR);
        put(GLFW.GLFW_HRESIZE_CURSOR);
        put(GLFW.GLFW_VRESIZE_CURSOR);
    }

    private static void put(int shape) {
        long handle = 0L;
        try {
            handle = GLFW.glfwCreateStandardCursor(shape);
        } catch (Throwable ignored) {
        }
        STANDARD.put(shape, handle);
    }

    public static void applyCssCursor(String cssValue) {
        applyCssCursor(null, cssValue);
    }

    /**
     * 应用 CSS 光标。
     * <p>
     * 支持：
     * <ul>
     *   <li>标准关键字（default/pointer/text/...）</li>
     *   <li>cursor: url("...") [hotspotX hotspotY]</li>
     * </ul>
     * url 资源会走异步加载，并在 READY 后切换为自定义 GLFW Cursor。
     */
    public static void applyCssCursor(String contextPath, String cssValue) {
        init();

        CursorUrlSpec urlSpec = parseUrlCursor(contextPath, cssValue);
        if (urlSpec != null) {
            CursorHandle handle = CursorAsyncHandler.INSTANCE.request(urlSpec.path(), urlSpec.hotspotX(), urlSpec.hotspotY());
            if (handle != null && handle.state() == com.sighs.apricityui.init.AbstractAsyncHandler.AsyncState.READY) {
                setWindowCursor(handle.glfwCursorHandle());
            } else {
                // 异步加载中/失败时先使用默认箭头，避免卡顿
                setWindowCursor(STANDARD.getOrDefault(GLFW.GLFW_ARROW_CURSOR, 0L));
            }
            return;
        }

        int shape = mapCssToStandardCursor(cssValue);
        long handle = STANDARD.getOrDefault(shape, STANDARD.getOrDefault(GLFW.GLFW_ARROW_CURSOR, 0L));
        if (handle == 0L) return;
        setWindowCursor(handle);
    }

    public static void resetToDefault() {
        applyCssCursor("default");
    }

    private static void setWindowCursor(long handle) {
        if (handle == 0L || handle == currentHandle) return;
        currentHandle = handle;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        long window = mc.getWindow().getWindow();
        if (window == 0L) return;

        GLFW.glfwSetCursor(window, handle);
    }

    private static int mapCssToStandardCursor(String v) {
        if (v == null) return GLFW.GLFW_ARROW_CURSOR;
        v = v.trim().toLowerCase(Locale.ROOT);

        return switch (v) {
            case "auto", "default" -> GLFW.GLFW_ARROW_CURSOR;
            case "pointer" -> GLFW.GLFW_HAND_CURSOR;
            case "text" -> GLFW.GLFW_IBEAM_CURSOR;
            case "crosshair" -> GLFW.GLFW_CROSSHAIR_CURSOR;
            case "ew-resize" -> GLFW.GLFW_HRESIZE_CURSOR;
            case "ns-resize" -> GLFW.GLFW_VRESIZE_CURSOR;
            default -> GLFW.GLFW_ARROW_CURSOR;
        };
    }

    /**
     * 解析 cursor: url("...") [x y]
     */
    private static CursorUrlSpec parseUrlCursor(String contextPath, String cssValue) {
        if (cssValue == null) return null;
        String v = cssValue.trim();
        if (v.isEmpty()) return null;

        int start = v.toLowerCase(Locale.ROOT).indexOf("url(");
        if (start < 0) return null;
        int end = v.indexOf(')', start + 4);
        if (end < 0) return null;

        String raw = v.substring(start + 4, end).replace("\"", "").replace("'", "").trim();
        if (raw.isEmpty()) return null;
        String resolved = contextPath == null ? raw : Loader.resolve(contextPath, raw);

        int hotspotX = 0;
        int hotspotY = 0;
        String tail = v.substring(end + 1).trim();
        if (!tail.isEmpty()) {
            // 允许 "url(...) 6 0"，多余 token 忽略
            String[] parts = tail.split("\\s+");
            if (parts.length >= 2) {
                try {
                    hotspotX = Integer.parseInt(parts[0]);
                    hotspotY = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return new CursorUrlSpec(resolved, hotspotX, hotspotY);
    }

    private record CursorUrlSpec(String path, int hotspotX, int hotspotY) {}
}
