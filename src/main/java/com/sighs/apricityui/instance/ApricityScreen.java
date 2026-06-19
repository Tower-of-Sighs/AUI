package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 纯 UI Screen（不带容器交互）。
 */
public class ApricityScreen extends Screen {
    private final String templatePath;
    private Document linkedDocument;

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

        // 窗口 resize 会重新调用 init()，需要先清理旧 Document 避免残留
        if (linkedDocument != null) {
            linkedDocument.remove();
            linkedDocument = null;
        }

        linkedDocument = Document.create(templatePath);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (linkedDocument != null) {
            Base.drawScreenDocument(guiGraphics.pose(), linkedDocument);
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
        Cursor.resetToDefault();
        super.onClose();
    }

    @Override
    public void removed() {
        if (linkedDocument != null) {
            linkedDocument.remove();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
