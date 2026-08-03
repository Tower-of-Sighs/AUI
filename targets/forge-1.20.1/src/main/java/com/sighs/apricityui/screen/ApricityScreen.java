package com.sighs.apricityui.screen;

import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FrameTimingHud;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.layout.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import com.sighs.apricityui.viewport.ApricityViewport;

public class ApricityScreen extends Screen implements AuiLinkedScreen {
    private final String templatePath;
    private boolean pauseGame;
    private boolean showDefaultBackground;
    private Document linkedDocument;
    private boolean loggedInitState = false;
    private boolean loggedRenderState = false;

    public ApricityScreen(String templatePath) {
        super(Component.empty());
        this.templatePath = templatePath;
    }

    /** Sets whether Minecraft should pause while this screen is open. */
    public ApricityScreen setPauseGame(boolean pauseGame) {
        this.pauseGame = pauseGame;
        return this;
    }

    /** Sets whether Minecraft's standard screen background should be drawn first. */
    public ApricityScreen setShowDefaultBackground(boolean showDefaultBackground) {
        this.showDefaultBackground = showDefaultBackground;
        return this;
    }

    public boolean isPauseGame() {
        return pauseGame;
    }

    public boolean isShowDefaultBackground() {
        return showDefaultBackground;
    }

    public Document getLinkedDocument() {
        return linkedDocument;
    }

    @Override
    protected void init() {
        super.init();

        if (linkedDocument != null) {
            linkedDocument.remove();
            linkedDocument = null;
        }

        linkedDocument = Document.create(templatePath);
        if (linkedDocument != null) {
            linkedDocument.applyViewport(false);
        }
        if (!loggedInitState) {
            loggedInitState = true;
            ApricityViewport viewport = currentViewport();
            com.sighs.apricityui.ApricityUI.LOGGER.info(
                    "[AUI Screen] init path={} viewport={}x{} doc={} body={} paintList={}",
                    templatePath,
                    viewport.layoutWidth(),
                    viewport.layoutHeight(),
                    linkedDocument == null ? "<null>" : linkedDocument.getUuid(),
                    linkedDocument == null || linkedDocument.body == null ? "<null>" : linkedDocument.body.tagName,
                    linkedDocument == null ? -1 : linkedDocument.getPaintList().size()
            );
        }
    }

    @Override
    public void resize(@Nonnull Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        if (linkedDocument != null) {
            linkedDocument.applyViewport(true);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        FrameTimingHud.beginFrame();
        try {
            if (showDefaultBackground) {
                renderBackground(guiGraphics);
            }
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
                ApricityViewport viewport = currentViewport();
                guiGraphics.pose().pushPose();
                Mask.pushScissorScale(viewport.scissorScale());
                try {
                    guiGraphics.pose().scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
                    Base.drawScreenDocument(guiGraphics.pose(), linkedDocument);
                } finally {
                    Mask.popScissorScale();
                    guiGraphics.pose().popPose();
                }
                Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
            }
            Client.drawPersistentScreenDocuments(guiGraphics, linkedDocument);
            com.sighs.apricityui.dev.resource.ResourcePreviewDialog.draw(guiGraphics.pose());
            guiGraphics.flush();
            Cursor.drawPseudoCursor(guiGraphics.pose());
            guiGraphics.flush();
        } finally {
            FrameTimingHud.endFrame();
            Client.drawFrameTimingHud(guiGraphics);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (hasControlDown() && handleViewportZoom(delta > 0)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == AuiServices.keys().reloadKey()) {
            ClientLoader.reload();
            return true;
        }
        if (isControlModifier(modifiers)) {
            if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
                return handleViewportZoom(true);
            }
            if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
                return handleViewportZoom(false);
            }
            if (keyCode == GLFW.GLFW_KEY_0 || keyCode == GLFW.GLFW_KEY_KP_0) {
                return resetViewportZoom();
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        return pauseGame;
    }

    public boolean handleViewportZoom(boolean zoomIn) {
        return linkedDocument != null && linkedDocument.handleViewportZoom(zoomIn);
    }

    public boolean resetViewportZoom() {
        return linkedDocument != null && linkedDocument.resetViewportZoom();
    }

    private ApricityViewport currentViewport() {
        return linkedDocument == null ? new ApricityViewport(1, 1, 1.0f, 1.0d) : linkedDocument.getViewport();
    }

    private static boolean isControlModifier(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }
}
