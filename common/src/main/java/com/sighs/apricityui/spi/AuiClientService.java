package com.sighs.apricityui.spi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import net.minecraft.client.gui.GuiGraphics;

import java.nio.file.Path;

/**
 * Loader-side client platform access.
 *
 * <p>Implemented by the Forge client bootstrap and registered through
 * {@link AuiServices}. Common rendering/layout code uses this interface instead
 * of referencing the loader's {@code Client} class directly, so {@code common}
 * remains compilable without the loader source tree.</p>
 */
public interface AuiClientService {
    /** Returns the logical window size (scaled GUI size), falling back to a headless default. */
    Size getWindowSize();

    /** Returns the current mouse position in GUI coordinates. */
    Position getMousePosition();

    /** Returns the raw GLFW cursor position in GUI coordinates, or {@code null} without a window. */
    Position getMousePositionDirectly();

    /** Returns the unscaled window width in physical pixels. */
    double getWindowWidth();

    /** Returns the unscaled window height in physical pixels. */
    double getWindowHeight();

    /** Returns the scaled GUI width. */
    int getScaledWidth();

    /** Returns the scaled GUI height. */
    int getScaledHeight();

    int getDefaultFontWidth(String text, boolean bold, boolean oblique, double strokeWidth);

    void drawDefaultFont(PoseStack poseStack, Text text, String content, Position position);

    boolean isKeyPressed(String keyName);

    /** Returns the pointer position used for world-window picking. */
    Position getMousePositionForWorldInteraction();

    /** Renders persistent screen documents over the current GUI frame. */
    void drawPersistentScreenDocuments(GuiGraphics guiGraphics, Document excludedDocument);

    /** Returns the loader's game directory (e.g. Forge {@code FMLPaths.GAMEDIR}). */
    Path getGameDirectory();
}
