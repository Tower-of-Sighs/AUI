package com.sighs.apricityui.spi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;

import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

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

    /** Opens the loader's WebUI screen for the given template path. */
    void openScreen(String templatePath);

    /** Returns the loader's game directory (e.g. Forge {@code FMLPaths.GAMEDIR}). */
    Path getGameDirectory();

    /** Returns the loader's config directory (e.g. Forge {@code FMLPaths.CONFIGDIR}). */
    Path getConfigDirectory();

    /** Returns whether the loader is running in a production (non-dev) environment. */
    boolean isProduction();

    /** Restricts annotation scanning to the given base packages. */
    void addScanPackage(String basePackage);

    /** Restricts annotation scanning to the given base packages. */
    void addScanPackages(String... basePackages);

    /** Scans loader-registered classes for the given annotation and invokes the consumer for each match. */
    void scanAnnotationClasses(Class<? extends Annotation> annotationClass,
                               Predicate<Map<String, Object>> annotationPredicate,
                               Consumer<Class<?>> consumer,
                               Runnable onFinished);
}
