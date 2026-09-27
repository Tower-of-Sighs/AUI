package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.spi.AuiConfigService;

/**
 * Forge implementation of {@link AuiConfigService}, backed by
 * {@link ApricityUIConfig}'s {@code ModConfigSpec}.
 */
public final class ConfigService implements AuiConfigService {
    public static final ConfigService INSTANCE = new ConfigService();

    private ConfigService() {
    }

    private static ApricityUIConfig.Client client() {
        return ApricityUIConfig.CLIENT;
    }
    private static <T> T value(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> configValue) {
        return ApricityUIConfig.get(configValue);
    }


    @Override
    public boolean debugAutoReload() {
        return value(client().debugAutoReload);
    }

    @Override
    public void setDebugAutoReload(boolean value) {
        client().debugAutoReload.set(value);
    }

    @Override
    public boolean aiAutoScreenshot() {
        return value(client().aiAutoScreenshot);
    }

    @Override
    public void setAiAutoScreenshot(boolean value) {
        client().aiAutoScreenshot.set(value);
    }

    @Override
    public boolean frameTimingHud() {
        return value(client().frameTimingHud);
    }

    @Override
    public void setFrameTimingHud(boolean value) {
        client().frameTimingHud.set(value);
    }

    @Override
    public boolean remoteDebug() {
        return value(client().remoteDebug);
    }

    @Override
    public void setRemoteDebug(boolean value) {
        client().remoteDebug.set(value);
    }

    @Override
    public boolean resourceManagerWorldWindow() {
        return value(client().resourceManagerWorldWindow);
    }

    @Override
    public void setResourceManagerWorldWindow(boolean value) {
        client().resourceManagerWorldWindow.set(value);
    }

    @Override
    public boolean viewportZoomPassThrough() {
        return value(client().viewportZoomPassThrough);
    }

    @Override
    public void setViewportZoomPassThrough(boolean value) {
        client().viewportZoomPassThrough.set(value);
    }

    @Override
    public boolean blockMouseEventsWhenCursorHidden() {
        return value(client().blockMouseEventsWhenCursorHidden);
    }

    @Override
    public void setBlockMouseEventsWhenCursorHidden(boolean value) {
        client().blockMouseEventsWhenCursorHidden.set(value);
    }

    @Override
    public float worldWindowDepthOffsetScale() {
        return value(client().worldWindowDepthOffsetScale).floatValue();
    }

    @Override
    public void setWorldWindowDepthOffsetScale(double value) {
        client().worldWindowDepthOffsetScale.set(value);
    }

    @Override
    public int worldWindowMaxDisplayDistance() {
        return value(client().worldWindowMaxDisplayDistance);
    }

    @Override
    public void setWorldWindowMaxDisplayDistance(int value) {
        client().worldWindowMaxDisplayDistance.set(value);
    }

    @Override
    public boolean worldWindowLodEnabled() {
        return value(client().worldWindowLodEnabled);
    }

    @Override
    public void setWorldWindowLodEnabled(boolean value) {
        client().worldWindowLodEnabled.set(value);
    }

    @Override
    public int worldWindowFullDetailDistance() {
        return value(client().worldWindowFullDetailDistance);
    }

    @Override
    public void setWorldWindowFullDetailDistance(int value) {
        client().worldWindowFullDetailDistance.set(value);
    }

    @Override
    public int worldWindowReducedDetailDistance() {
        return value(client().worldWindowReducedDetailDistance);
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
