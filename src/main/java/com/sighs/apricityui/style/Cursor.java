package com.sighs.apricityui.style;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.resource.Image;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.image.ImageHandle;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Cursor {
    private static final Map<Integer, Long> STANDARD = new HashMap<>();
    private static boolean initialized = false;
    private static long currentHandle = 0L;
    private static boolean systemCursorHidden = false;
    private static CursorUrlSpec pseudoCursorSpec = null;

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
     * url 资源使用 ImageDrawer 渲染伪光标。
     */
    public static void applyCssCursor(String contextPath, String cssValue) {
        init();

        CursorUrlSpec urlSpec = parseUrlCursor(contextPath, cssValue);
        if (urlSpec != null) {
            ImageHandle handle = ImageAsyncHandler.INSTANCE.request(urlSpec.path());
            if (handle != null
                    && handle.state() == com.sighs.apricityui.init.AbstractAsyncHandler.AsyncState.READY
                    && handle.texture() != null) {
                enablePseudoCursor(urlSpec);
            } else {
                // 图片未就绪时回退默认箭头，避免暂时无光标。
                disablePseudoCursor();
                setWindowCursor(STANDARD.getOrDefault(GLFW.GLFW_ARROW_CURSOR, 0L));
            }
            return;
        }

        disablePseudoCursor();
        int shape = mapCssToStandardCursor(cssValue);
        long handle = STANDARD.getOrDefault(shape, STANDARD.getOrDefault(GLFW.GLFW_ARROW_CURSOR, 0L));
        if (handle == 0L) return;
        setWindowCursor(handle);
    }

    public static void resetToDefault() {
        applyCssCursor("default");
    }

    public static void drawPseudoCursor(PoseStack poseStack) {
        if (poseStack == null || pseudoCursorSpec == null) return;

        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(pseudoCursorSpec.path());
        if (handle == null || handle.state() != com.sighs.apricityui.init.AbstractAsyncHandler.AsyncState.READY) return;

        Image.ITexture texture = handle.texture();
        if (texture == null || texture.getLocation() == null) return;

        int width = texture.getWidth();
        int height = texture.getHeight();
        if (width <= 0 || height <= 0) return;

        Position mouse = Client.getMousePosition();
        float drawX = (float) mouse.x - pseudoCursorSpec.hotspotX();
        float drawY = (float) mouse.y - pseudoCursorSpec.hotspotY();

        Base.resolveOffset(poseStack);
        ImageDrawer.draw(poseStack, texture.getLocation(), drawX, drawY, width, height, false);
    }

    private static void enablePseudoCursor(CursorUrlSpec spec) {
        pseudoCursorSpec = spec;
        setSystemCursorHidden(true);
    }

    private static void disablePseudoCursor() {
        pseudoCursorSpec = null;
        setSystemCursorHidden(false);
    }

    private static void setSystemCursorHidden(boolean hidden) {
        if (systemCursorHidden == hidden) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        long window = mc.getWindow().getWindow();
        if (window == 0L) return;

        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, hidden ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
        systemCursorHidden = hidden;
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
