package com.sighs.apricityui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.dev.perf.PageOpenTiming;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.spi.AuiKeyService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.viewport.ApricityViewport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;

/**
 * Screen hosting a single AUI document.
 *
 * <p>Screens only collect render states ({@link #extractBackground} /
 * {@link #extractRenderState}); the {@code GuiRenderer} rasterises them
 * afterwards. AUI's immediate-mode document drawing therefore does not happen
 * here — {@code Client.drawScreen} submits a fullscreen Picture-in-Picture state
 * during extraction, and the PIP renderer rasterises every live document
 * (including the linked one) into the overlay texture. This class only manages
 * the document lifecycle and input.</p>
 */
public class ApricityScreen extends Screen implements AuiLinkedScreen {
    /** {@code SDL_SCANCODE_KP_MINUS}; 26.3's {@link InputConstants} has no named constant for it. */
    private static final int KEY_NUMPAD_SUBTRACT = 86;

    private final String templatePath;
    private boolean pauseGame;
    private boolean showDefaultBackground;
    private Document linkedDocument;
    private boolean loggedInitState = false;

    private PageOpenTiming openTiming;

    public ApricityScreen(String templatePath) {
        super(Component.empty());
        this.templatePath = templatePath;
        this.openTiming = PageOpenTiming.start(templatePath, "screen");
    }

    private static boolean isControlModifier(int modifiers) {
        // SDL_KMOD_CTRL (left|right) since 26.3; the old GLFW value (2) is now MOD_SHIFT's bit.
        return (modifiers & InputConstants.MOD_CONTROL) != 0;
    }

    /**
     * Screen.hasControlDown() was removed in 26.1; check the physical Ctrl keys instead.
     * 26.3 reads SDL keyboard state by scancode through {@link InputConstants#isKeyDown(int)}.
     */
    private static boolean hasControlDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
    }

    public boolean isPauseGame() {
        return pauseGame;
    }

    /**
     * Sets whether Minecraft should pause while this screen is open.
     */
    public ApricityScreen setPauseGame(boolean pauseGame) {
        this.pauseGame = pauseGame;
        return this;
    }

    public boolean isShowDefaultBackground() {
        return showDefaultBackground;
    }

    /**
     * Sets whether Minecraft's standard screen background should be drawn first.
     */
    public ApricityScreen setShowDefaultBackground(boolean showDefaultBackground) {
        this.showDefaultBackground = showDefaultBackground;
        return this;
    }

    public Document getLinkedDocument() {
        return linkedDocument;
    }

    @Override
    protected void init() {
        super.init();

        // Screen.resize() calls init(); recreating the Document here would turn a
        // window resize into a full page reload and wipe form state.
        if (linkedDocument == null || !linkedDocument.isActive()) {
            linkedDocument = Document.create(templatePath, openTiming);
            if (linkedDocument == null && openTiming != null) openTiming.finish("screen-create-failed");
        } else if (openTiming != null) {
            openTiming.finish("screen-reused-document");
        }
        if (linkedDocument != null) {
            linkedDocument.applyViewport(false);
        }
        if (!loggedInitState) {
            loggedInitState = true;
            ApricityViewport viewport = currentViewport();
            ApricityUI.LOGGER.debug(
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
     * The screen pipeline calls {@link #extractBackground} before
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
        // 26.3 {@code event.key()} is the SDL scancode, which is what
        // {@link InputConstants#KEY_*} and the keybinding registry store.
        int keyCode = event.key();
        if (AuiKeyService.matches(keyCode, AuiServices.keys().reloadKey())) {
            ClientLoader.reload();
            return true;
        }
        if (isControlModifier(event.modifiers())) {
            if (keyCode == InputConstants.KEY_EQUALS || keyCode == InputConstants.KEY_ADD) {
                return handleViewportZoom(true);
            }
            if (keyCode == InputConstants.KEY_MINUS || keyCode == KEY_NUMPAD_SUBTRACT) {
                return handleViewportZoom(false);
            }
            if (keyCode == InputConstants.KEY_0 || keyCode == InputConstants.KEY_NUMPAD0) {
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
            linkedDocument = null;
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
}
