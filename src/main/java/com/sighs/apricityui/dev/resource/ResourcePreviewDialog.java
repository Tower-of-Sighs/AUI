package com.sighs.apricityui.dev.resource;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ui.toast.ToastManager;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.ui.dialog.DialogWindow;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.instance.ApricityViewport;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.render.AABB;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Position;

/** Owns the resource-browser preview window and renders its document into its content viewport. */
public final class ResourcePreviewDialog {
    private static final double MIN_WIDTH = 360;
    private static final double MIN_HEIGHT = 240;
    private static ResourcePreviewDialog active;

    private Document owner;
    private Document preview;
    private Element overlay;
    private Element window;
    private Element viewport;
    private Element imageView;
    private DialogWindow dialog;
    private String sourcePath = "";
    private boolean imagePreview;
    private double left;
    private double top;
    private double width;
    private double height;
    private DragMode dragMode = DragMode.NONE;
    private double startX;
    private double startY;
    private double startLeft;
    private double startTop;
    private double startWidth;
    private double startHeight;

    public void open(Document owner, Loader.StaticResourceEntry entry) {
        if (owner == null || owner.body == null || entry == null) return;
        String path = safe(entry.path());
        if (path.isBlank()) return;
        close();
        this.owner = owner;
        this.sourcePath = path;
        this.imagePreview = isImage(entry);
        this.preview = imagePreview ? null : Document.create(path);
        if (!imagePreview && preview == null) {
            ToastManager.show("Preview unavailable");
            return;
        }
        if (preview != null) {
            preview.setReloadPersistent(false);
            preview.setManuallyRendered(true);
        }
        createWindow();
        active = this;
    }

    public void close() {
        if (active == this) active = null;
        if (dialog != null) dialog.close();
        if (overlay != null) overlay.remove();
        if (preview != null && !preview.isDisposed()) preview.remove();
        owner = null;
        preview = null;
        overlay = null;
        window = null;
        viewport = null;
        imageView = null;
        dialog = null;
        sourcePath = "";
        imagePreview = false;
        dragMode = DragMode.NONE;
    }

    public boolean isOpen() {
        return dialog != null && dialog.isOpen() && (imagePreview || (preview != null && !preview.isDisposed()));
    }

    public static void draw(PoseStack poseStack) {
        if (active != null) active.drawPreview(poseStack);
    }

    private void createWindow() {
        if (openFrameworkWindow()) return;
        ApricityViewport ownerViewport = owner.getViewport();
        double screenWidth = ownerViewport.layoutWidth();
        double screenHeight = ownerViewport.layoutHeight();
        width = Math.max(MIN_WIDTH, screenWidth * 0.8d);
        height = Math.max(MIN_HEIGHT, screenHeight * 0.8d);
        width = Math.min(width, screenWidth - 24);
        height = Math.min(height, screenHeight - 24);
        left = Math.max(12, (screenWidth - width) * 0.5d);
        top = Math.max(12, (screenHeight - height) * 0.5d);

        overlay = element("DIV", "resource-create-overlay opening resource-preview-overlay");
        overlay.setAttribute("style", "z-index:9000;padding:0;background:rgba(26,26,26,0.42);");
        window = element("DIV", "resource-create-dialog resource-preview-window");
        applyWindowBounds();

        Element header = element("DIV", "resource-create-heading resource-preview-header");
        header.setAttribute("style", "flex:0 0 auto;cursor:move;user-select:none;");
        header.append(text("DIV", sourcePath.toUpperCase(), "resource-create-title resource-preview-title", "flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;user-select:none;"));
        Element closeButton = text("BUTTON", "x", "resource-create-close resource-preview-close", "margin-left:16px;");
        closeButton.addEventListener("click", event -> { event.stopPropagation(); close(); });
        header.addEventListener("mousedown", event -> beginDrag(event, DragMode.MOVE));
        header.append(closeButton);
        window.append(header);

        viewport = element("DIV", "resource-preview-viewport");
        viewport.setAttribute("style", "position:relative;flex:1;min-height:0;margin-top:18px;background:#fff;border:1px solid var(--gray-light);overflow:hidden;");
        if (imagePreview) {
            imageView = element("IMG", "resource-preview-image");
            imageView.setAttribute("src", "/" + sourcePath);
            imageView.setAttribute("alt", sourcePath);
            imageView.setAttribute("style", "position:absolute;inset:0;width:100%;height:100%;object-fit:contain;display:block;");
            viewport.append(imageView);
        }
        viewport.addEventListener("mousedown", event -> forward(event));
        viewport.addEventListener("mouseup", event -> forward(event));
        viewport.addEventListener("mousemove", event -> forward(event));
        viewport.addEventListener("wheel", event -> forward(event));
        window.append(viewport);

        addResizeHandle("n", DragMode.N); addResizeHandle("ne", DragMode.NE); addResizeHandle("e", DragMode.E); addResizeHandle("se", DragMode.SE);
        addResizeHandle("s", DragMode.S); addResizeHandle("sw", DragMode.SW); addResizeHandle("w", DragMode.W); addResizeHandle("nw", DragMode.NW);
        overlay.addEventListener("mousemove", this::move);
        overlay.addEventListener("mouseup", event -> dragMode = DragMode.NONE);
        overlay.append(window);
        owner.body.append(overlay);
        // Keep receiving a drag after the pointer leaves the popup itself.
        owner.body.addEventListener("mousemove", this::move);
        owner.body.addEventListener("mouseup", event -> dragMode = DragMode.NONE);
        markDirty();
    }

    private boolean openFrameworkWindow() {
        double screenWidth = owner.getViewport().layoutWidth();
        double screenHeight = owner.getViewport().layoutHeight();
        width = Math.min(Math.max(MIN_WIDTH, screenWidth * 0.8d), screenWidth - 24);
        height = Math.min(Math.max(MIN_HEIGHT, screenHeight * 0.8d), screenHeight - 24);
        DialogWindow.Options options = new DialogWindow.Options(
                sourcePath.toUpperCase(), width, height, true,
                "resource-create-overlay opening resource-preview-overlay",
                "resource-create-dialog resource-preview-window",
                "resource-create-heading resource-preview-header",
                "resource-create-title resource-preview-title",
                "resource-create-close resource-preview-close"
        );
        dialog = DialogWindow.open(owner, options, null);
        viewport = dialog.content();
        viewport.setAttribute("style", "position:relative;flex:1;min-height:0;margin-top:18px;background:#fff;border:1px solid var(--gray-light);overflow:hidden;");
        if (imagePreview) {
            imageView = element("IMG", "resource-preview-image");
            imageView.setAttribute("src", "/" + sourcePath);
            imageView.setAttribute("alt", sourcePath);
            imageView.setAttribute("style", "position:absolute;inset:0;width:100%;height:100%;object-fit:contain;display:block;");
            viewport.append(imageView);
        }
        viewport.addEventListener("mousedown", this::forward);
        viewport.addEventListener("mouseup", this::forward);
        viewport.addEventListener("mousemove", this::forward);
        viewport.addEventListener("wheel", this::forward);
        markDirty();
        return true;
    }

    private void addResizeHandle(String side, DragMode mode) {
        Element handle = element("DIV", "resource-preview-resize resource-preview-resize-" + side);
        String style = switch (side) {
            case "n" -> "top:-5px;left:8px;right:8px;height:10px;cursor:n-resize;";
            case "ne" -> "top:-5px;right:-5px;width:12px;height:12px;cursor:ne-resize;";
            case "e" -> "top:8px;right:-5px;bottom:8px;width:10px;cursor:e-resize;";
            case "se" -> "right:-5px;bottom:-5px;width:12px;height:12px;cursor:se-resize;";
            case "s" -> "left:8px;right:8px;bottom:-5px;height:10px;cursor:s-resize;";
            case "sw" -> "left:-5px;bottom:-5px;width:12px;height:12px;cursor:sw-resize;";
            case "w" -> "top:8px;left:-5px;bottom:8px;width:10px;cursor:w-resize;";
            default -> "top:-5px;left:-5px;width:12px;height:12px;cursor:nw-resize;";
        };
        handle.setAttribute("style", "position:absolute;z-index:2;" + style);
        handle.addEventListener("mousedown", event -> beginDrag(event, mode));
        window.append(handle);
    }

    private void beginDrag(Event event, DragMode mode) {
        if (!(event instanceof MouseEvent mouse)) return;
        dragMode = mode;
        startX = mouse.clientX;
        startY = mouse.clientY;
        startLeft = left;
        startTop = top;
        startWidth = width;
        startHeight = height;
        event.stopPropagation();
    }

    private void move(Event event) {
        if (dragMode == DragMode.NONE || !(event instanceof MouseEvent mouse)) return;
        double dx = mouse.clientX - startX;
        double dy = mouse.clientY - startY;
        if (dragMode.move) { left = startLeft + dx; top = startTop + dy; }
        if (dragMode.east) width = Math.max(MIN_WIDTH, startWidth + dx);
        if (dragMode.south) height = Math.max(MIN_HEIGHT, startHeight + dy);
        if (dragMode.west) { width = Math.max(MIN_WIDTH, startWidth - dx); left = startLeft + startWidth - width; }
        if (dragMode.north) { height = Math.max(MIN_HEIGHT, startHeight - dy); top = startTop + startHeight - height; }
        clampBounds();
        applyWindowBounds();
        markDirty();
        markWindowDirty();
        event.stopPropagation();
    }

    private void clampBounds() {
        double maxWidth = Math.max(MIN_WIDTH, owner.getViewport().layoutWidth() - 12);
        double maxHeight = Math.max(MIN_HEIGHT, owner.getViewport().layoutHeight() - 12);
        width = Math.min(width, maxWidth); height = Math.min(height, maxHeight);
        left = Math.max(6, Math.min(left, owner.getViewport().layoutWidth() - width - 6));
        top = Math.max(6, Math.min(top, owner.getViewport().layoutHeight() - height - 6));
    }

    private void applyWindowBounds() {
        window.setAttribute("style", "position:absolute;left:" + px(left) + ";top:" + px(top)
                + ";width:" + px(width) + ";height:" + px(height)
                + ";box-sizing:border-box;display:flex;flex-direction:column;pointer-events:auto;overflow:visible;");
    }

    private void forward(Event event) {
        if (!(event instanceof MouseEvent mouse) || preview == null || preview.isDisposed()) return;
        MouseEvent.tiggerEvent(mouse, preview);
        event.stopPropagation();
    }

    private void drawPreview(PoseStack poseStack) {
        if (!isOpen() || viewport == null) return;
        if (imagePreview) {
            // The preview image is appended after the manager's normal frame tick.
            // Keep its async texture handle alive for the full dialog lifetime.
            if (imageView != null) imageView.tick();
            return;
        }
        applyImageSource();
        // Manually-rendered documents do not enter the normal top-level tick pass.
        preview.tickElements();
        AABB rect = Rect.of(viewport).getVisualBounds();
        if (!rect.isValid()) return;
        double hostScale = owner.getViewport().renderScale();
        float x = (float) (rect.x() * hostScale);
        float y = (float) (rect.y() * hostScale);
        float w = (float) (rect.width() * hostScale);
        float h = (float) (rect.height() * hostScale);
        double scale = Math.min(w / Math.max(1, preview.getViewport().layoutWidth()), h / Math.max(1, preview.getViewport().layoutHeight()));
        if (!Double.isFinite(scale) || scale <= 0) return;
        double contentWidth = preview.getViewport().layoutWidth() * scale;
        double contentHeight = preview.getViewport().layoutHeight() * scale;
        double contentX = x + Math.max(0, w - contentWidth) * 0.5d;
        double contentY = y + Math.max(0, h - contentHeight) * 0.5d;
        preview.setViewportTransform(scale, scale, contentX, contentY);
        poseStack.pushPose();
        Mask.pushSurfaceClip(preview.getViewport().layoutWidth(), preview.getViewport().layoutHeight(), contentX, contentY, scale, scale);
        try {
            poseStack.translate(contentX, contentY, 0);
            poseStack.scale((float) scale, (float) scale, 1);
            Base.drawDocument(poseStack, preview);
        } finally {
            Mask.popSurfaceClip();
            poseStack.popPose();
        }
    }

    private Element element(String tag, String className) {
        Element element = Element.init(owner.createElement(tag));
        element.setAttribute("class", className);
        return element;
    }
    private Element text(String tag, String value, String className, String style) {
        Element element = element(tag, className); element.setTextContent(value); element.setAttribute("style", style); return element;
    }
    private void markDirty() { if (owner != null && owner.body != null) owner.markDirty(owner.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER); }
    private void markWindowDirty() {
        if (owner == null || window == null) return;
        int dirty = Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER;
        owner.markDirty(window, dirty);
        if (viewport != null) owner.markDirty(viewport, dirty);
        if (imageView != null) owner.markDirty(imageView, dirty);
    }
    private void applyImageSource() {
        if (!imagePreview || preview == null || preview.isDisposed()) return;
        Element image = preview.querySelector("#previewImage");
        if (image == null || ("/" + sourcePath).equals(image.getAttribute("src"))) return;
        image.setAttribute("src", "/" + sourcePath);
        preview.markDirty(Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
    }
    private static boolean isImage(Loader.StaticResourceEntry entry) { String ext = safe(entry.extension()).toLowerCase(java.util.Locale.ROOT); return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("bmp") || ext.equals("gif") || ext.equals("webp"); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String px(double value) { return String.format(java.util.Locale.ROOT, "%.2fpx", value); }

    private enum DragMode {
        NONE(false,false,false,false,false), MOVE(false,false,false,false,true), N(true,false,false,false,false), NE(true,true,false,false,false), E(false,true,false,false,false), SE(false,true,false,true,false), S(false,false,false,true,false), SW(false,false,true,true,false), W(false,false,true,false,false), NW(true,false,true,false,false);
        final boolean north, east, west, south, move;
        DragMode(boolean north, boolean east, boolean west, boolean south, boolean move) { this.north=north; this.east=east; this.west=west; this.south=south; this.move=move; }
    }
}
