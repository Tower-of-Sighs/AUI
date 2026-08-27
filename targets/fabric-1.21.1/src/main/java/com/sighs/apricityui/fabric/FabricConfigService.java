package com.sighs.apricityui.fabric;

import com.sighs.apricityui.spi.AuiConfigService;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FabricConfigService implements AuiConfigService {
    public static final FabricConfigService INSTANCE = new FabricConfigService();
    private final Properties values = new Properties();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("apricityui.properties");
    private final AtomicBoolean reloadPending = new AtomicBoolean();

    private FabricConfigService() {
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        } catch (IOException ignored) {
        }
    }

    private boolean bool(String key, boolean fallback) { return Boolean.parseBoolean(values.getProperty(key, String.valueOf(fallback))); }
    private int integer(String key, int fallback) { try { return Integer.parseInt(values.getProperty(key, String.valueOf(fallback))); } catch (NumberFormatException e) { return fallback; } }
    private float decimal(String key, float fallback) { try { return Float.parseFloat(values.getProperty(key, String.valueOf(fallback))); } catch (NumberFormatException e) { return fallback; } }
    private void set(String key, Object value) { values.setProperty(key, String.valueOf(value)); }

    public boolean debugAutoReload() { return bool("debugAutoReload", false); }
    public void setDebugAutoReload(boolean value) { set("debugAutoReload", value); }
    public boolean aiAutoScreenshot() { return bool("aiAutoScreenshot", false); }
    public void setAiAutoScreenshot(boolean value) { set("aiAutoScreenshot", value); }
    public boolean frameTimingHud() { return bool("frameTimingHud", false); }
    public void setFrameTimingHud(boolean value) { set("frameTimingHud", value); }
    public boolean remoteDebug() { return bool("remoteDebug", FabricLoader.getInstance().isDevelopmentEnvironment()); }
    public void setRemoteDebug(boolean value) { set("remoteDebug", value); }
    public boolean resourceManagerWorldWindow() { return bool("resourceManagerWorldWindow", false); }
    public void setResourceManagerWorldWindow(boolean value) { set("resourceManagerWorldWindow", value); }
    public boolean viewportZoomPassThrough() { return bool("viewportZoomPassThrough", true); }
    public void setViewportZoomPassThrough(boolean value) { set("viewportZoomPassThrough", value); }
    public boolean blockMouseEventsWhenCursorHidden() { return bool("blockMouseEventsWhenCursorHidden", true); }
    public void setBlockMouseEventsWhenCursorHidden(boolean value) { set("blockMouseEventsWhenCursorHidden", value); }
    public float worldWindowDepthOffsetScale() { return decimal("worldWindowDepthOffsetScale", .01f); }
    public void setWorldWindowDepthOffsetScale(double value) { set("worldWindowDepthOffsetScale", value); }
    public int worldWindowMaxDisplayDistance() { return integer("worldWindowMaxDisplayDistance", 128); }
    public void setWorldWindowMaxDisplayDistance(int value) { set("worldWindowMaxDisplayDistance", value); }
    public boolean worldWindowLodEnabled() { return bool("worldWindowLodEnabled", false); }
    public void setWorldWindowLodEnabled(boolean value) { set("worldWindowLodEnabled", value); }
    public int worldWindowFullDetailDistance() { return integer("worldWindowFullDetailDistance", 16); }
    public void setWorldWindowFullDetailDistance(int value) { set("worldWindowFullDetailDistance", value); }
    public int worldWindowReducedDetailDistance() { return integer("worldWindowReducedDetailDistance", 48); }
    public void setWorldWindowReducedDetailDistance(int value) { set("worldWindowReducedDetailDistance", value); }

    public void save() { try { Files.createDirectories(path.getParent()); try (OutputStream output = Files.newOutputStream(path)) { values.store(output, "ApricityUI Fabric configuration"); } } catch (IOException ignored) { } }
    public void markClientReloadPending() { reloadPending.set(true); }
    public boolean consumeClientReloadPending() { return reloadPending.getAndSet(false); }
}
