package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.spi.AuiConfigService;

/**
 * NeoForge implementation of {@link AuiConfigService}, backed by
 * {@link ApricityUIConfig}'s {@code ModConfigSpec}.
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
        return ApricityUIConfig.get(client().debugAutoReload);
    }

    @Override
    public void setDebugAutoReload(boolean value) {
        client().debugAutoReload.set(value);
    }

    @Override
    public boolean aiAutoScreenshot() {
        return ApricityUIConfig.get(client().aiAutoScreenshot);
    }

    @Override
    public void setAiAutoScreenshot(boolean value) {
        client().aiAutoScreenshot.set(value);
    }

    @Override
    public boolean frameTimingHud() {
        return ApricityUIConfig.get(client().frameTimingHud);
    }

    @Override
    public void setFrameTimingHud(boolean value) {
        client().frameTimingHud.set(value);
    }

    @Override
    public boolean remoteDebug() {
        return ApricityUIConfig.get(client().remoteDebug);
    }

    @Override
    public void setRemoteDebug(boolean value) {
        client().remoteDebug.set(value);
    }

    @Override
    public boolean resourceManagerWorldWindow() {
        return ApricityUIConfig.get(client().resourceManagerWorldWindow);
    }

    @Override
    public void setResourceManagerWorldWindow(boolean value) {
        client().resourceManagerWorldWindow.set(value);
    }

    @Override
    public boolean viewportZoomPassThrough() {
        return ApricityUIConfig.get(client().viewportZoomPassThrough);
    }

    @Override
    public void setViewportZoomPassThrough(boolean value) {
        client().viewportZoomPassThrough.set(value);
    }

    @Override
    public boolean blockMouseEventsWhenCursorHidden() {
        return ApricityUIConfig.get(client().blockMouseEventsWhenCursorHidden);
    }

    @Override
    public void setBlockMouseEventsWhenCursorHidden(boolean value) {
        client().blockMouseEventsWhenCursorHidden.set(value);
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
        return ApricityUIConfig.get(client().worldWindowMaxDisplayDistance);
    }

    @Override
    public void setWorldWindowMaxDisplayDistance(int value) {
        client().worldWindowMaxDisplayDistance.set(value);
    }

    @Override
    public boolean worldWindowLodEnabled() {
        return ApricityUIConfig.get(client().worldWindowLodEnabled);
    }

    @Override
    public void setWorldWindowLodEnabled(boolean value) {
        client().worldWindowLodEnabled.set(value);
    }

    @Override
    public int worldWindowFullDetailDistance() {
        return ApricityUIConfig.get(client().worldWindowFullDetailDistance);
    }

    @Override
    public void setWorldWindowFullDetailDistance(int value) {
        client().worldWindowFullDetailDistance.set(value);
    }

    @Override
    public int worldWindowReducedDetailDistance() {
        return ApricityUIConfig.get(client().worldWindowReducedDetailDistance);
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
