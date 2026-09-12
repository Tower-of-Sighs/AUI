package com.sighs.apricityui.screen;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.viewport.ApricityViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;

/**
 * Screen hosting a single AUI document.
 *
 * <p>26.1 renders screens in two phases: {@link #extractBackground} /
 * {@link #extractRenderState} only collect render states, and the
 * {@code GuiRenderer} rasterises them afterwards. AUI's immediate-mode
 * document drawing therefore does not happen here — {@code Client.drawScreen}
 * submits a fullscreen Picture-in-Picture state during extraction, and the PIP
 * renderer rasterises every live document (including the linked one) into the
 * overlay texture. This class only manages the document lifecycle and input.</p>
 */
public class ApricityScreen extends Screen implements AuiLinkedScreen {
    private final String templatePath;
    private boolean pauseGame;
    private boolean showDefaultBackground;
    private Document linkedDocument;
    private boolean loggedInitState = false;

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

    static boolean shouldCreateLinkedDocument(boolean hasLinkedDocument, boolean disposed) {
        return !hasLinkedDocument || disposed;
    }

    @Override
    protected void init() {
        super.init();

        boolean hasLinkedDocument = linkedDocument != null;
        boolean disposed = hasLinkedDocument && linkedDocument.isDisposed();
        if (shouldCreateLinkedDocument(hasLinkedDocument, disposed)) {
            linkedDocument = Document.create(templatePath);
            if (linkedDocument != null) {
                linkedDocument.applyViewport(false);
            }
        }
        if (!loggedInitState) {
            loggedInitState = true;
            ApricityViewport viewport = currentViewport();
            ApricityUI.LOGGER.info(
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
     * In 26.1 the render pipeline calls {@link #extractBackground} before
     * {@link #extractRenderState}, so gate the default background here instead
     * of inside the render method.
     */
    @Override
    public void extractBackground(@Nonnull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (showDefaultBackground) {
            super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void extractRenderState(@Nonnull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        // AuiLinkedScreens submit the UI PIP state themselves (above the
        // vanilla background, below extractor-drawn content); Client.drawScreen
        // skips its own submission for them and only adds the pseudo-cursor.
        com.sighs.apricityui.client.gui.ApricityGuiLayers.submitUi(guiGraphics);
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
        removeLinkedDocument();
        Size.clearViewportOverride();
        Cursor.resetToDefault();
        super.onClose();
    }

    @Override
    public void removed() {
        removeLinkedDocument();
        Size.clearViewportOverride();
        super.removed();
    }

    private void removeLinkedDocument() {
        Document document = linkedDocument;
        if (document == null) return;
        try {
            if (!document.isDisposed()) {
                if (document.body != null) {
                    Event.triggerSingle(new Event(document.body, "unload", false));
                }
                document.remove();
            }
        } finally {
            linkedDocument = null;
        }
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
