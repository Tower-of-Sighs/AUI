package com.sighs.apricityui.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.style.Text;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class FabricClientService implements AuiClientService {
    public static final FabricClientService INSTANCE = new FabricClientService();
    private FabricClientService() { }

    public Size getWindowSize() { return Client.getWindowSize(); }
    public Position getMousePosition() { return Client.getMousePosition(); }
    public Position getMousePositionDirectly() { return Client.getMousePositionDirectly(); }
    public double getWindowWidth() { return Minecraft.getInstance().getWindow().getWidth(); }
    public double getWindowHeight() { return Minecraft.getInstance().getWindow().getHeight(); }
    public int getScaledWidth() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
    public int getScaledHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
    public int getDefaultFontWidth(String text, boolean bold, boolean oblique, double strokeWidth) { return Client.getDefaultFontWidth(text, bold, oblique, strokeWidth); }
    public void drawDefaultFont(PoseStack poseStack, Text text, String content, Position position) { Client.drawDefaultFont(poseStack, text, content, position); }
    public boolean isKeyPressed(String keyName) { return Client.isKeyPressed(keyName); }
    public Position getMousePositionForWorldInteraction() { return Client.getMousePositionForWorldInteraction(); }
    public void openScreen(String templatePath) { Minecraft.getInstance().setScreen(new ApricityScreen(templatePath)); }
    public void closeScreen() { Minecraft.getInstance().setScreen(null); }
    public Path getGameDirectory() { return FabricLoader.getInstance().getGameDir(); }
    public Path getConfigDirectory() { return FabricLoader.getInstance().getConfigDir(); }
    public boolean isProduction() { return !FabricLoader.getInstance().isDevelopmentEnvironment(); }
    public void addScanPackage(String basePackage) { FabricReflectionUtils.addScanPackage(basePackage); }
    public void addScanPackages(String... basePackages) { FabricReflectionUtils.addScanPackages(basePackages); }
    public void scanAnnotationClasses(Class<? extends Annotation> annotationClass, Predicate<Map<String, Object>> predicate, Consumer<Class<?>> consumer, Runnable onFinished) { FabricReflectionUtils.findAnnotationClasses(annotationClass, predicate, consumer, onFinished); }
    public void openUri(URI uri) { net.minecraft.Util.getPlatform().openUri(uri); }
    public void openFile(File file) { net.minecraft.Util.getPlatform().openFile(file); }
    public long getWindowHandle() { return Minecraft.getInstance().getWindow().getWindow(); }
    public Vec3 getCameraPosition() { return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(); }
    public Vector3f getCameraLookVector() { return Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector(); }
}
