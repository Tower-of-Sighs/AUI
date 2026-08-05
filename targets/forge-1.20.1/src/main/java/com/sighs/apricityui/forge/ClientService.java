package com.sighs.apricityui.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.style.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Vector3f;

import java.io.File;
import java.lang.annotation.Annotation;
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
            Minecraft.getInstance().setScreen(new ApricityScreen(templatePath));
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public void closeScreen() {
        try {
            Minecraft.getInstance().setScreen(null);
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
        return FMLEnvironment.production;
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
    public void openUri(URI uri) {
        try {
            net.minecraft.Util.getPlatform().openUri(uri);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public void openFile(File file) {
        try {
            net.minecraft.Util.getPlatform().openFile(file);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public long getWindowHandle() {
        try {
            return Minecraft.getInstance().getWindow().getWindow();
        } catch (RuntimeException | LinkageError ignored) {
            return 0L;
        }
    }

    @Override
    public Vec3 getCameraPosition() {
        try {
            return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        } catch (RuntimeException | LinkageError ignored) {
            return new Vec3(0.0, 0.0, 0.0);
        }
    }

    @Override
    public Vector3f getCameraLookVector() {
        try {
            return Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        } catch (RuntimeException | LinkageError ignored) {
            return new Vector3f(0.0F, 0.0F, -1.0F);
        }
    }
}
