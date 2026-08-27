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

    /**
     * 游戏未显示鼠标（准星模式，mouse grabbed）时，overlay/screen 文档不接收任何
     * 鼠标事件；世界窗口（inWorld）不受影响。默认开启。
     */
    boolean blockMouseEventsWhenCursorHidden();

    void setBlockMouseEventsWhenCursorHidden(boolean value);

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
