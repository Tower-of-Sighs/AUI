package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Element;
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
        init();
        int shape = mapCssToStandardCursor(cssValue);
        long handle = STANDARD.getOrDefault(shape, STANDARD.getOrDefault(GLFW.GLFW_ARROW_CURSOR, 0L));
        if (handle == 0L) {
            return;
        }
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
}
