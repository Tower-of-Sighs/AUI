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
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.layout.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
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
    public void resize(int width, int height) {
        super.resize(width, height);
        if (linkedDocument != null) {
            linkedDocument.applyViewport(true);
        }
    }

    /**
     * In 26.1 the render pipeline calls {@link #extractBackground} before {@link #extractRenderState},
     * so gate the default background here instead of inside extractRenderState.
     */
    @Override
    public void extractBackground(@Nonnull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (showDefaultBackground) {
            super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void extractRenderState(@Nonnull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // The linked document (and the pseudo-cursor) are drawn by the
        // Picture-in-Picture overlay submitted from Client.drawScreen, so the
        // vanilla GuiRenderer composites them during its render phase.
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        if (linkedDocument != null) {
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double delta) {
        if (hasControlDown() && handleViewportZoom(delta > 0)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalScroll, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
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
        return super.keyPressed(event);
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

    /** Screen.hasControlDown() was removed in 26.1; check the physical Ctrl keys instead. */
    private static boolean hasControlDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
