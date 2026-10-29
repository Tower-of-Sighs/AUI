package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.style.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import org.joml.Vector3f;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Forge implementation of {@link AuiClientService}, delegating to the loader's
 * {@link Client} static facade. Headless environments (no Minecraft instance)
 * fall back to deterministic defaults so common tests behave identically.
 */
public final class ClientService implements AuiClientService {
    public static final ClientService INSTANCE = new ClientService();

    private ClientService() {
    }

    /**
     * The camera state for the current frame.
     *
     * <p>26.3 extracts render state before drawing, so the level frame's camera
     * is already available on {@code GameRenderer.gameRenderState()}; reuse it
     * when the level was rendered this frame. GUI-only frames (title screen,
     * screen-only ticks) leave it uninitialised, so those fall back to
     * extracting the main camera with the current {@code DeltaTracker}
     * ({@code Camera.extractRenderState} takes a tracker, not a partial tick).</p>
     */
    private static net.minecraft.client.renderer.state.level.CameraRenderState cameraState() {
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.renderer.state.level.LevelRenderState levelState =
                minecraft.gameRenderer.gameRenderState().levelRenderState;
        net.minecraft.client.renderer.state.level.CameraRenderState extracted =
                levelState == null ? null : levelState.cameraRenderState;
        if (extracted != null && extracted.initialized) {
            return extracted;
        }
        net.minecraft.client.Camera camera = minecraft.gameRenderer.mainCamera();
        net.minecraft.client.renderer.state.level.CameraRenderState state =
                new net.minecraft.client.renderer.state.level.CameraRenderState();
        camera.extractRenderState(state, minecraft.getDeltaTracker());
        return state;
    }

    @Override
    public Size getWindowSize() {
        try {
            return Client.getWindowSize();
        } catch (RuntimeException | LinkageError ignored) {
            return new Size(1920, 1080);
        }
    }

    @Override
    public Position getMousePosition() {
        try {
            return Client.getMousePosition();
        } catch (RuntimeException | LinkageError ignored) {
            return new Position(0, 0);
        }
    }

    @Override
    public Position getMousePositionDirectly() {
        try {
            return Client.getMousePositionDirectly();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Override
    public double getWindowWidth() {
        try {
            return Client.getWindow().getWidth();
        } catch (RuntimeException | LinkageError ignored) {
            return 1920;
        }
    }

    @Override
    public double getWindowHeight() {
        try {
            return Client.getWindow().getHeight();
        } catch (RuntimeException | LinkageError ignored) {
            return 1080;
        }
    }

    @Override
    public int getScaledWidth() {
        try {
            return Client.getWindow().getGuiScaledWidth();
        } catch (RuntimeException | LinkageError ignored) {
            return 1920;
        }
    }

    @Override
    public int getScaledHeight() {
        try {
            return Client.getWindow().getGuiScaledHeight();
        } catch (RuntimeException | LinkageError ignored) {
            return 1080;
        }
    }

    @Override
    public int getDefaultFontWidth(String text, boolean bold, boolean oblique, double strokeWidth) {
        try {
            return Client.getDefaultFontWidth(text, bold, oblique, strokeWidth);
        } catch (RuntimeException | LinkageError ignored) {
            double stroke = Math.max(0, strokeWidth) * 2;
            int fontStyle = java.awt.Font.PLAIN;
            if (bold) fontStyle |= java.awt.Font.BOLD;
            if (oblique) fontStyle |= java.awt.Font.ITALIC;
            java.awt.Font fallbackFont = new java.awt.Font("Microsoft YaHei", fontStyle, 16);
            int width = new java.awt.Canvas().getFontMetrics(fallbackFont).stringWidth(text == null ? "" : text);
            return (int) Math.ceil(width + stroke);
        }
    }

    @Override
    public void drawDefaultFont(PoseStack poseStack, Text text, String content, Position position) {
        try {
            Client.drawDefaultFont(poseStack, text, content, position);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public boolean isKeyPressed(String keyName) {
        try {
            return Client.isKeyPressed(keyName);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public boolean isMouseGrabbed() {
        try {
            return Minecraft.getInstance().mouseHandler.isMouseGrabbed();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public Position getMousePositionForWorldInteraction() {
        try {
            return Client.getMousePositionForWorldInteraction();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Override
    public void openScreen(String templatePath) {
        try {
            // 26.3: Minecraft.screen/setScreen moved onto Gui.
            Minecraft.getInstance().gui.setScreen(new com.sighs.apricityui.screen.ApricityScreen(templatePath));
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public void closeScreen() {
        try {
            Minecraft.getInstance().gui.setScreen(null);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public Path getGameDirectory() {
        try {
            return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        } catch (RuntimeException | LinkageError ignored) {
            return Path.of("").toAbsolutePath().normalize();
        }
    }

    @Override
    public Path getConfigDirectory() {
        try {
            return FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Override
    public boolean isProduction() {
        // FMLLoader.isProduction() became an instance method in NeoForge 26.1; keep it simple.
        return false;
    }

    @Override
    public void addScanPackage(String basePackage) {
        ReflectionUtils.addScanPackage(basePackage);
    }

    @Override
    public void addScanPackages(String... basePackages) {
        ReflectionUtils.addScanPackages(basePackages);
    }

    @Override
    public void scanAnnotationClasses(Class<? extends Annotation> annotationClass,
                                      Predicate<Map<String, Object>> annotationPredicate,
                                      Consumer<Class<?>> consumer,
                                      Runnable onFinished) {
        try {
            ReflectionUtils.findAnnotationClasses(annotationClass, annotationPredicate, consumer, onFinished);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public void scanAnnotationStaticMethods(Class<? extends Annotation> annotationClass,
                                            Consumer<Method> consumer,
                                            Runnable onFinished) {
        try {
            ReflectionUtils.findAnnotationStaticMethod(annotationClass, null, consumer, onFinished);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public void openUri(URI uri) {
        try {
            // 26.3 dropped Util.getPlatform(); Blaze3D owns the desktop open helpers.
            com.mojang.blaze3d.Blaze3D.openUri(uri);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public void openFile(File file) {
        try {
            com.mojang.blaze3d.Blaze3D.openPath(file.toPath());
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    /**
     * The game window's native handle: an {@code SDL_Window*} on 26.3 (it was a
     * {@code GLFWwindow*} on 26.1). Only pass it to SDL-shaped code.
     */
    @Override
    public long getWindowHandle() {
        try {
            return Minecraft.getInstance().getWindow().handle();
        } catch (RuntimeException | LinkageError ignored) {
            return 0L;
        }
    }

    @Override
    public Vec3 getCameraPosition() {
        try {
            return cameraState().pos;
        } catch (RuntimeException | LinkageError ignored) {
            return new Vec3(0.0, 0.0, 0.0);
        }
    }

    @Override
    public Vector3f getCameraLookVector() {
        try {
            return new Vector3f(0.0F, 0.0F, -1.0F).rotate(cameraState().orientation);
        } catch (RuntimeException | LinkageError ignored) {
            return new Vector3f(0.0F, 0.0F, -1.0F);
        }
    }
}
