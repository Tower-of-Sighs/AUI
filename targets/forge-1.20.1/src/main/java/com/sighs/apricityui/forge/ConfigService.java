package com.sighs.apricityui.forge;

import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.spi.AuiConfigService;

/**
 * Forge implementation of {@link AuiConfigService}, backed by
 * {@link ApricityUIConfig}'s {@code ForgeConfigSpec}.
 */
public final class ConfigService implements AuiConfigService {
    public static final ConfigService INSTANCE = new ConfigService();

    private ConfigService() {
    }

    private static ApricityUIConfig.Client client() {
        return ApricityUIConfig.CLIENT;
    }

    @Override
    public boolean debugAutoReload() {
        return client().debugAutoReload.get();
    }

    @Override
    public void setDebugAutoReload(boolean value) {
        client().debugAutoReload.set(value);
    }

    @Override
    public boolean aiAutoScreenshot() {
        return client().aiAutoScreenshot.get();
    }

    @Override
    public void setAiAutoScreenshot(boolean value) {
        client().aiAutoScreenshot.set(value);
    }

    @Override
    public boolean frameTimingHud() {
        return client().frameTimingHud.get();
    }

    @Override
    public void setFrameTimingHud(boolean value) {
        client().frameTimingHud.set(value);
    }

    @Override
    public boolean remoteDebug() {
        return client().remoteDebug.get();
    }

    @Override
    public void setRemoteDebug(boolean value) {
        client().remoteDebug.set(value);
    }

    @Override
    public boolean resourceManagerWorldWindow() {
        return client().resourceManagerWorldWindow.get();
    }

    @Override
    public void setResourceManagerWorldWindow(boolean value) {
        client().resourceManagerWorldWindow.set(value);
    }

    @Override
    public boolean viewportZoomPassThrough() {
        return client().viewportZoomPassThrough.get();
    }

    @Override
    public void setViewportZoomPassThrough(boolean value) {
        client().viewportZoomPassThrough.set(value);
    }

    @Override
    public float worldWindowDepthOffsetScale() {
        return client().worldWindowDepthOffsetScale();
    }

    @Override
    public void setWorldWindowDepthOffsetScale(double value) {
        client().worldWindowDepthOffsetScale.set(value);
    }

    @Override
    public int worldWindowMaxDisplayDistance() {
        return client().worldWindowMaxDisplayDistance.get();
    }

    @Override
    public void setWorldWindowMaxDisplayDistance(int value) {
        client().worldWindowMaxDisplayDistance.set(value);
    }

    @Override
    public boolean worldWindowLodEnabled() {
        return client().worldWindowLodEnabled.get();
    }

    @Override
    public void setWorldWindowLodEnabled(boolean value) {
        client().worldWindowLodEnabled.set(value);
    }

    @Override
    public int worldWindowFullDetailDistance() {
        return client().worldWindowFullDetailDistance.get();
    }

    @Override
    public void setWorldWindowFullDetailDistance(int value) {
        client().worldWindowFullDetailDistance.set(value);
    }

    @Override
    public int worldWindowReducedDetailDistance() {
        return client().worldWindowReducedDetailDistance.get();
    }

    @Override
    public void setWorldWindowReducedDetailDistance(int value) {
        client().worldWindowReducedDetailDistance.set(value);
    }

    @Override
    public void save() {
        ApricityUIConfig.CLIENT_SPEC.save();
    }

    @Override
    public void markClientReloadPending() {
        ApricityUIConfig.markClientReloadPending();
    }

    @Override
    public boolean consumeClientReloadPending() {
        return ApricityUIConfig.consumeClientReloadPending();
    }
}
