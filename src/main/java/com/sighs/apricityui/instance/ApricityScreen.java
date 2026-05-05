package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

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
        linkedDocument = Document.create(templatePath);
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (linkedDocument != null) {
            Base.drawScreenDocument(guiGraphics.pose(), linkedDocument);
        }
        Cursor.drawPseudoCursor(guiGraphics);
    }

    @Override
    public void onClose() {
        if (linkedDocument != null) {
            if (linkedDocument.body != null) {
                linkedDocument.body.triggerEvent(currentEvent -> {
                    if ("unload".equals(currentEvent.type) && currentEvent.listener != null) {
                        try {
                            currentEvent.listener.accept(currentEvent);
                        } catch (Exception ignored) {
                        }
                    }
                });
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
