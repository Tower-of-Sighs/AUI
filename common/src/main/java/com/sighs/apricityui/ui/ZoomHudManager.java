package com.sighs.apricityui.ui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Drawer;

import java.util.List;

/**
 * 页面缩放提示 HUD。
 *
 * <p>Ctrl+滚轮、Ctrl+加号/减号或 Ctrl+0 改变 {@link Document} 的视口缩放后，
 * 在屏幕右下角短暂显示当前百分比，并提供“重置”按钮直接回到初始缩放。实现方式
 * 与 {@link ToastManager} 相同：一个常驻的 overlay Document，元素按需显示/隐藏，
 * 不参与页面自己的布局。</p>
 *
 * <p>HUD 只负责展示和转发重置；缩放状态仍然由 {@code ApricityViewport} 持有。</p>
 */
public final class ZoomHudManager {
    private static final String DOC_PATH = "devtools/zoom-hud.html";
    private static final String HUD_ID = "aui-zoom-hud";
    private static final String VALUE_ID = "aui-zoom-value";
    private static final String RESET_ID = "aui-zoom-reset";
    private static final String HUD_CLASS = "aui-zoom-hud";
    private static final String HUD_VISIBLE_CLASS = "aui-zoom-hud visible";
    private static final long HIDE_AFTER_NS = 3_000_000_000L;
    private static final long HIDE_AFTER_HOVER_NS = 800_000_000L;

    private static Document document;
    private static Element hud;
    private static Element value;
    private static Element boundHud;
    private static Element boundReset;
    private static Document target;
    private static long hideAtNs;
    private static boolean visible;
    private static boolean hovering;

    private ZoomHudManager() {
    }

    /**
     * 在最近一次缩放后调用；重复调用会刷新百分比并延长显示时间。
     */
    public static void show(Document targetDocument) {
        if (targetDocument == null || !targetDocument.isActive()) return;
        Document overlay = ensureOverlay();
        if (overlay == null || hud == null) return;

        target = targetDocument;
        if (value != null) {
            value.setTextContent(Math.round(targetDocument.getViewport().zoom() * 100.0d) + "%");
        }
        if (!visible) {
            visible = true;
            hud.setClassName(HUD_VISIBLE_CLASS);
        }
        hideAtNs = System.nanoTime() + HIDE_AFTER_NS;
        markDirty(overlay);
    }

    /**
     * Runs on the client tick, like {@link ToastManager#tick()}.
     */
    public static void tick() {
        if (!visible) return;
        if (target != null && !target.isActive()) {
            hide();
            return;
        }
        if (!hovering && System.nanoTime() >= hideAtNs) hide();
    }

    private static void hide() {
        if (!visible) return;
        visible = false;
        hovering = false;
        target = null;
        if (hud != null) hud.setClassName(HUD_CLASS);
        markDirty(document);
    }

    private static void resetZoom() {
        Document targetDocument = target;
        if (targetDocument == null || !targetDocument.isActive()) {
            hide();
            return;
        }
        targetDocument.resetViewportZoom();
        show(targetDocument);
    }

    private static Document ensureOverlay() {
        Document overlay = document;
        if (overlay == null || !overlay.isActive()) {
            List<Document> documents = Document.get(DOC_PATH);
            overlay = documents.isEmpty() ? null : documents.get(0);
            if (overlay == null) overlay = Document.create(DOC_PATH);
            if (overlay != null) overlay.setReloadPersistent(true);
            document = overlay;
        }
        if (overlay == null) return null;

        hud = overlay.getElementById(HUD_ID);
        value = overlay.getElementById(VALUE_ID);
        Element reset = overlay.getElementById(RESET_ID);
        if (hud == null) {
            document = null;
            return null;
        }
        if (hud != boundHud) {
            boundHud = hud;
            hud.addEventListener("mouseenter", event -> hovering = true);
            hud.addEventListener("mouseleave", event -> {
                hovering = false;
                hideAtNs = System.nanoTime() + HIDE_AFTER_HOVER_NS;
            });
        }
        if (reset != null && reset != boundReset) {
            boundReset = reset;
            reset.addEventListener("click", event -> resetZoom());
        }
        return overlay;
    }

    private static void markDirty(Document overlay) {
        if (overlay != null && overlay.body != null) {
            overlay.markDirty(overlay.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        }
    }
}
