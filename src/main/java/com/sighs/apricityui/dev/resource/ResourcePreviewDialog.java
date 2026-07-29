package com.sighs.apricityui.dev.resource;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ui.ToastManager;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.ui.DialogWindow;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.render.AABB;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.Rect;

/** Owns the resource-browser preview window and renders its document into its content viewport. */
public final class ResourcePreviewDialog {
    private static final double MIN_WIDTH = 360;
    private static final double MIN_HEIGHT = 240;
    private static ResourcePreviewDialog active;

    private Document owner;
    private Document preview;
    private Element viewport;
    private Element imageView;
    private Element fontTextArea;
    private DialogWindow dialog;
    private String sourcePath = "";
    private String fontFamily = "";
    private boolean imagePreview;
    private boolean fontPreview;

    public void open(Document owner, Loader.StaticResourceEntry entry) {
        if (owner == null || owner.body == null || entry == null) return;
        String path = safe(entry.path());
        if (path.isBlank()) return;
        close();
        this.owner = owner;
        this.sourcePath = path;
        this.imagePreview = isImage(entry);
        this.fontPreview = ResourceFontAsset.isFont(entry);
        if (fontPreview) {
            fontFamily = ResourceFontAsset.familyName(entry);
            if (!ResourceFontAsset.ensureLoaded(entry)) {
                ToastManager.show("Font preview unavailable");
                close();
                return;
            }
        }
        this.preview = imagePreview || fontPreview ? null : Document.create(path);
        if (!imagePreview && !fontPreview && preview == null) {
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
        if (preview != null && !preview.isDisposed()) preview.remove();
        owner = null;
        preview = null;
        viewport = null;
        imageView = null;
        fontTextArea = null;
        dialog = null;
        sourcePath = "";
        fontFamily = "";
        imagePreview = false;
        fontPreview = false;
    }

    public boolean isOpen() {
        return dialog != null && dialog.isOpen()
                && (imagePreview || fontPreview || (preview != null && !preview.isDisposed()));
    }

    public static void draw(PoseStack poseStack) {
        if (active != null) active.drawPreview(poseStack);
    }

    private void createWindow() {
        openFrameworkWindow();
    }

    private void openFrameworkWindow() {
        double screenWidth = owner.getViewport().layoutWidth();
        double screenHeight = owner.getViewport().layoutHeight();
        double width = Math.min(Math.max(MIN_WIDTH, screenWidth * 0.8d), screenWidth - 24);
        double height = Math.min(Math.max(MIN_HEIGHT, screenHeight * 0.8d), screenHeight - 24);
        DialogWindow.Options options = new DialogWindow.Options(
                sourcePath.toUpperCase(), width, height, true,
                "dialog-overlay show resource-preview-overlay",
                "dialog resource-preview-window",
                "dialog-header resource-preview-header",
                "dialog-title resource-preview-title",
                "dialog-close resource-preview-close",
                "dialog-body resource-preview-body",
                "dialog-title-icon"
        );
        dialog = DialogWindow.open(owner, options, null);
        Element body = dialog.content();
        body.setAttribute("style", "position:relative;flex:1;min-height:0;display:flex;");
        viewport = element("DIV", "resource-preview-viewport");
        viewport.setAttribute("style", "position:relative;flex:1;min-height:0;background:#fff;border:1px solid var(--gray-light);overflow:hidden;");
        if (imagePreview) {
            imageView = element("IMG", "resource-preview-image");
            imageView.setAttribute("src", "/" + sourcePath);
            imageView.setAttribute("alt", sourcePath);
            imageView.setAttribute("style", "position:absolute;inset:0;width:100%;height:100%;object-fit:contain;display:block;");
            viewport.append(imageView);
        } else if (fontPreview) {
            fontTextArea = element("TEXTAREA", "resource-preview-font-sample");
            fontTextArea.setValue("中文字体预览\nThe quick brown fox jumps over the lazy dog.");
            fontTextArea.setAttribute("spellcheck", "false");
            fontTextArea.setAttribute("style", "position:absolute;inset:0;width:100%;height:100%;box-sizing:border-box;"
                    + "resize:none;border:0;outline:none;padding:32px;background:#fff;color:#1a1a1a;"
                    + "font-family:'" + fontFamily + "',sans-serif;font-size:42px;line-height:1.5;"
                    + "font-weight:400;letter-spacing:0;white-space:pre-wrap;overflow:auto;");
            viewport.append(fontTextArea);
        }
        body.append(viewport);
        viewport.addEventListener("mousedown", this::forward);
        viewport.addEventListener("mouseup", this::forward);
        viewport.addEventListener("mousemove", this::forward);
        viewport.addEventListener("wheel", this::forward);
        markDirty();
    }

    private void forward(Event event) {
        if (!(event instanceof MouseEvent mouse) || preview == null || preview.isDisposed()) return;
        MouseEvent.tiggerEvent(mouse, preview);
        event.stopPropagation();
    }

    private void drawPreview(PoseStack poseStack) {
        if (!isOpen() || viewport == null) return;
        if (imagePreview || fontPreview) {
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
    private void markDirty() { if (owner != null && owner.body != null) owner.markDirty(owner.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER); }
    private void applyImageSource() {
        if (!imagePreview || preview == null || preview.isDisposed()) return;
        Element image = preview.querySelector("#previewImage");
        if (image == null || ("/" + sourcePath).equals(image.getAttribute("src"))) return;
        image.setAttribute("src", "/" + sourcePath);
        preview.markDirty(Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
    }
    private static boolean isImage(Loader.StaticResourceEntry entry) { String ext = safe(entry.extension()).toLowerCase(java.util.Locale.ROOT); return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("bmp") || ext.equals("gif") || ext.equals("webp"); }
    private static String safe(String value) { return value == null ? "" : value; }
}
