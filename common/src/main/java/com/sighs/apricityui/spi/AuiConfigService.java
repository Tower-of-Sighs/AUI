package com.sighs.apricityui.spi;

/**
 * Loader-side configuration access.
 *
 * <p>The loader owns the config model (Forge {@code ForgeConfigSpec}) and
 * implements this interface so {@code common} DevTools, resource manager and
 * rendering code can read and update settings without referencing the loader
 * config class directly. Registered through {@link AuiServices}.</p>
 */
public interface AuiConfigService {
    boolean debugAutoReload();

    void setDebugAutoReload(boolean value);

    boolean aiAutoScreenshot();

    void setAiAutoScreenshot(boolean value);

    boolean frameTimingHud();

    void setFrameTimingHud(boolean value);

    boolean remoteDebug();

    void setRemoteDebug(boolean value);

    boolean resourceManagerWorldWindow();

    void setResourceManagerWorldWindow(boolean value);

    boolean viewportZoomPassThrough();

    void setViewportZoomPassThrough(boolean value);

    float worldWindowDepthOffsetScale();

    void setWorldWindowDepthOffsetScale(double value);

    int worldWindowMaxDisplayDistance();

    void setWorldWindowMaxDisplayDistance(int value);

    boolean worldWindowLodEnabled();

    void setWorldWindowLodEnabled(boolean value);

    int worldWindowFullDetailDistance();

    void setWorldWindowFullDetailDistance(int value);

    int worldWindowReducedDetailDistance();

    void setWorldWindowReducedDetailDistance(int value);

    /** Persists the current config values to disk. */
    void save();

    void markClientReloadPending();

    boolean consumeClientReloadPending();
}
