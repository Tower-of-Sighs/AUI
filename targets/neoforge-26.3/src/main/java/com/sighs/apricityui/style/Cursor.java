package com.sighs.apricityui.style;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.resource.Image;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.image.ImageHandle;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLMouse;

import java.util.List;
import java.util.Locale;

import com.sighs.apricityui.task.AbstractAsyncHandler;
import com.sighs.apricityui.parser.CSS;

/**
 * CSS {@code cursor} handling: maps a CSS cursor value onto a vanilla
 * {@link CursorType} and keeps the {@code cursor: url(...)} pseudo cursor.
 *
 * <p>26.3 owns the standard cursors itself ({@link CursorTypes}) and applies the per-frame
 * request from {@code GuiGraphicsExtractor}; the game therefore no longer keeps a GLFW cursor
 * handle per shape. {@link #applyCssCursor} only records the request, and the GUI layer
 * ({@code ApricityGuiLayers}) forwards {@link #pendingType()} through
 * {@code GuiGraphicsExtractor.requestCursor} during the extraction pass — a
 * {@code Window.selectCursor} call made from an input event would be overwritten by
 * {@code Gui.applyCursor} at the end of the same frame.</p>
 */
public class Cursor {
    private static final float PSEUDO_CURSOR_Z = 1000.0F;

    /**
     * The diagonal resize arrow AUI maps {@code se-resize}/{@code nwse-resize} onto; vanilla has
     * no {@code CursorTypes} constant for it.
     */
    private static final int SDL_SYSTEM_CURSOR_NWSE_RESIZE = SDLMouse.SDL_SYSTEM_CURSOR_NWSE_RESIZE;

    private static CursorType resizeNwse;
    private static boolean initialized = false;
    /** Per-frame cursor request; {@code null} means "leave the cursor to vanilla". */
    private static volatile CursorType pending = null;
    private static boolean systemCursorHidden = false;
    private static CursorUrlSpec pseudoCursorSpec = null;

    /**
     * Creates the cursor AUI needs but vanilla does not expose.
     *
     * <p>Deliberately not done in a static initialiser: {@code SDL_CreateSystemCursor} returns
     * 0 until the SDL video subsystem is up, and a failed creation would be latched for the
     * whole session. By the time this runs (first mouse event, after the window exists) the
     * subsystem is always initialised.</p>
     */
    public static void init() {
        if (initialized) return;
        initialized = true;
        resizeNwse = CursorType.createStandardCursor(SDL_SYSTEM_CURSOR_NWSE_RESIZE, "resize_nwse", CursorTypes.RESIZE_ALL);
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
                    && handle.state() == com.sighs.apricityui.task.AbstractAsyncHandler.AsyncState.READY
                    && handle.texture() != null) {
                enablePseudoCursor(urlSpec);
            } else {
                // 图片未就绪时回退默认箭头，避免暂时无光标。
                disablePseudoCursor();
                pending = CursorTypes.ARROW;
            }
            return;
        }

        disablePseudoCursor();
        pending = mapCssToStandardCursor(cssValue);
    }

    /**
     * The cursor AUI wants this frame, or {@code null} when it has no opinion.
     *
     * <p>The GUI extraction pass forwards a non-null value through
     * {@code GuiGraphicsExtractor.requestCursor}.</p>
     */
    public static CursorType pendingType() {
        return pending;
    }

    public static void resetToDefault() {
        applyCssCursor("default");
    }

    public static void refreshFromDocuments() {
        refreshFromDocuments(AuiServices.client().getMousePositionDirectly());
    }

    public static void refreshFromDocuments(Position mousePosition) {
        if (mousePosition == null) {
            resetToDefault();
            return;
        }

        List<Document> documents = DocumentLayerOrder.frontToBack(Document.getAll());
        for (Document document : documents) {
            if (document.inWorld || document.isManuallyRendered()) continue;

            Element target = document.hitTestIfComplete(document.screenToDocumentPosition(mousePosition));
            if (target == null || target == document.body) continue;

            applyCssCursor(document.getPath(), resolveCssCursor(target, document.screenToDocumentPosition(mousePosition)));
            return;
        }
        resetToDefault();
    }

    private static String resolveCssCursor(Element target, Position mousePosition) {
        if (target instanceof com.sighs.apricityui.element.TextArea textArea
                && textArea.isResizeHandleAt(mousePosition)) {
            return textArea.getResizeCursor();
        }
        String cached = target.getRenderer().cursor.get();
        if (cached != null) return cached;

        for (Element element = target; element != null; element = element.parentElement) {
            String value = element.getComputedStyle().cursor;
            if (value == null) continue;
            value = value.trim();
            if (!value.isEmpty() && !value.equalsIgnoreCase("unset") && !value.equalsIgnoreCase("auto")) {
                target.getRenderer().cursor.set(value);
                return value;
            }
        }
        // 无显式 cursor（auto）：浏览器对可选中文本默认显示 I 形文本光标；
        // 控件（按钮/下拉）UA 光标为箭头，即使文字可选中也不切 I 形。
        String fallback = isTextCursorTarget(target, mousePosition) ? "text" : "default";
        target.getRenderer().cursor.set(fallback);
        return fallback;
    }

    /**
     * 悬停处是否应按文本光标（I 形）处理：输入控件，或命中点落在可选文本的行盒上。
     */
    private static boolean isTextCursorTarget(Element target, Position mousePosition) {
        if (target == null || mousePosition == null) return false;
        if (target instanceof com.sighs.apricityui.element.AbstractText) return true;
        String tag = target.tagName == null ? "" : target.tagName.trim().toUpperCase(Locale.ROOT);
        // 浏览器对 button/select/option 的 UA 光标是箭头（即使文字可选中）
        if ("BUTTON".equals(tag) || "SELECT".equals(tag) || "OPTION".equals(tag)) return false;
        // 不再是“属于含文本容器就 I 形”，而是鼠标必须真正落在单元的可选文本行盒上，
        // 空区域/内边距/边框处退回箭头，与浏览器一致。
        Element unit = com.sighs.apricityui.behavior.SelectionUnits.resolveUnit(target);
        return unit != null
                && com.sighs.apricityui.behavior.TextSelection.isPositionOverSelectableText(unit, mousePosition.x, mousePosition.y);
    }

    /**
     * Flushes and draws the pseudo cursor. Loaders call this from their GUI render pass.
     */
    public static void drawPseudoCursor(PoseStack poseStack) {
        if (poseStack == null || pseudoCursorSpec == null) return;

        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(pseudoCursorSpec.path());
        if (handle == null || handle.state() != com.sighs.apricityui.task.AbstractAsyncHandler.AsyncState.READY) return;

        Image.ITexture texture = handle.texture();
        if (texture == null || texture.getKey() == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        double guiScale = mc.getWindow().getGuiScale();
        if (guiScale <= 0) guiScale = 1.0;

        float width = (float) (texture.getWidth() / guiScale);
        float height = (float) (texture.getHeight() / guiScale);
        if (width <= 0 || height <= 0) return;

        int hotspotX = pseudoCursorSpec.hotspotX() >= 0 ? pseudoCursorSpec.hotspotX() : texture.getHotspotX();
        int hotspotY = pseudoCursorSpec.hotspotY() >= 0 ? pseudoCursorSpec.hotspotY() : texture.getHotspotY();
        float drawHotspotX = (float) (hotspotX / guiScale);
        float drawHotspotY = (float) (hotspotY / guiScale);

        Position mouse = AuiServices.client().getMousePositionDirectly();
        if (mouse == null) mouse = AuiServices.client().getMousePosition();
        float drawX = (float) mouse.x - drawHotspotX;
        float drawY = (float) mouse.y - drawHotspotY;

        poseStack.pushPose();
        Base.commitDraws();
        Base.resolveOffset(poseStack);
        poseStack.translate(0.0D, 0.0D, PSEUDO_CURSOR_Z);
        ImageDrawer.drawOverlay(poseStack, AuiServices.resources().locationOf(texture.getKey()), drawX, drawY, width, height, false);
        ImageDrawer.flushBatch();
        poseStack.popPose();
    }

    private static void enablePseudoCursor(CursorUrlSpec spec) {
        pseudoCursorSpec = spec;
        setSystemCursorHidden(true);
    }

    private static void disablePseudoCursor() {
        pseudoCursorSpec = null;
        setSystemCursorHidden(false);
    }

    /**
     * Hides the OS cursor while the texture cursor is drawn. SDL keeps the hidden state until
     * it is shown again, so this only has to run on the transition — vanilla never touches
     * {@code SDL_ShowCursor}/{@code SDL_HideCursor}.
     */
    private static void setSystemCursorHidden(boolean hidden) {
        if (systemCursorHidden == hidden) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        if (hidden) {
            SDLMouse.SDL_HideCursor();
        } else {
            SDLMouse.SDL_ShowCursor();
        }
        systemCursorHidden = hidden;
    }

    private static CursorType mapCssToStandardCursor(String v) {
        if (v == null) return CursorTypes.ARROW;
        v = v.trim().toLowerCase(Locale.ROOT);

        return switch (v) {
            case "auto", "default" -> CursorTypes.ARROW;
            case "pointer" -> CursorTypes.POINTING_HAND;
            case "text" -> CursorTypes.IBEAM;
            case "crosshair" -> CursorTypes.CROSSHAIR;
            case "ew-resize" -> CursorTypes.RESIZE_EW;
            case "ns-resize" -> CursorTypes.RESIZE_NS;
            case "se-resize", "nwse-resize" -> resizeNwse == null ? CursorTypes.RESIZE_ALL : resizeNwse;
            default -> CursorTypes.ARROW;
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

        int hotspotX = -1;
        int hotspotY = -1;
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

    private record CursorUrlSpec(String path, int hotspotX, int hotspotY) {
    }
}
