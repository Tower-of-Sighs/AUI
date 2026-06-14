package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

/**
 * 纯 UI Screen（不带容器交互）。
 */
public class ApricityScreen extends Screen {
    private static final double MAX_DOCUMENT_GUI_SCALE = 5.0d;
    private final String templatePath;
    private Document linkedDocument;
    private boolean loggedInitState = false;
    private boolean loggedRenderState = false;
    private float documentRenderScale = 1.0f;
    private double documentGuiScale = 1.0d;

    public ApricityScreen(String templatePath) {
        super(Component.empty());
        this.templatePath = templatePath;
    }

    public Document getLinkedDocument() {
        return linkedDocument;
    }

    @Override
    protected void init() {
        super.init();
        var window = Minecraft.getInstance().getWindow();
        double actualGuiScale = Math.max(1.0d, window.getGuiScale());
        documentGuiScale = Math.min(actualGuiScale, MAX_DOCUMENT_GUI_SCALE);
        documentRenderScale = (float) (documentGuiScale / actualGuiScale);
        int layoutWidth = Math.max(1, (int) Math.round(window.getScreenWidth() / documentGuiScale));
        int layoutHeight = Math.max(1, (int) Math.round(window.getScreenHeight() / documentGuiScale));
        Size.setViewportOverride(layoutWidth, layoutHeight);

        // 窗口 resize 会重新调用 init()，需要先清理旧 Document 避免残留
        if (linkedDocument != null) {
            linkedDocument.remove();
            linkedDocument = null;
        }

        linkedDocument = Document.create(templatePath);
        if (!loggedInitState) {
            loggedInitState = true;
            com.sighs.apricityui.ApricityUI.LOGGER.info(
                    "[AUI Screen] init path={} viewport={}x{} doc={} body={} paintList={}",
                    templatePath,
                    layoutWidth,
                    layoutHeight,
                    linkedDocument == null ? "<null>" : linkedDocument.getUuid(),
                    linkedDocument == null || linkedDocument.body == null ? "<null>" : linkedDocument.body.tagName,
                    linkedDocument == null ? -1 : linkedDocument.getPaintList().size()
            );
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (linkedDocument != null) {
            if (!loggedRenderState) {
                loggedRenderState = true;
                com.sighs.apricityui.ApricityUI.LOGGER.info(
                        "[AUI Screen] render path={} doc={} body={} paintList={} dirty={}",
                        templatePath,
                        linkedDocument.getUuid(),
                        linkedDocument.body == null ? "<null>" : linkedDocument.body.tagName,
                        linkedDocument.getPaintList().size(),
                        linkedDocument.getDirtyElements().size()
                );
            }
            guiGraphics.pose().pushPose();
            Mask.pushScissorScale(documentGuiScale);
            try {
                guiGraphics.pose().scale(documentRenderScale, documentRenderScale, 1.0f);
                Base.drawScreenDocument(guiGraphics.pose(), linkedDocument);
            } finally {
                Mask.popScissorScale();
                guiGraphics.pose().popPose();
            }
            // 默认字体使用 Minecraft 的 BufferSource，文档绘制结束后立即提交，避免文本延迟到后续阶段才显示。
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        }
        Cursor.drawPseudoCursor(guiGraphics);
    }

    @Override
    public void onClose() {
        if (linkedDocument != null) {
            if (linkedDocument.body != null) {
                Event.triggerSingle(new Event(linkedDocument.body, "unload", false));
            }
            linkedDocument.remove();
        }
        Size.clearViewportOverride();
        Cursor.resetToDefault();
        super.onClose();
    }

    @Override
    public void removed() {
        if (linkedDocument != null) {
            linkedDocument.remove();
        }
        Size.clearViewportOverride();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
