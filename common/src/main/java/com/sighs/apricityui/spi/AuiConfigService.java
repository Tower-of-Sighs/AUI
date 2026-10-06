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

    /**
     * 首次全量几何提交是否按帧预算分片。开启后，文档的首次全量提交由渲染门控跨多帧
     * 推进，每帧只提交预算内的一部分元素；关闭则退回一次性同步全量提交。默认 {@code true}。
     */
    boolean initialCommitSliceEnabled();

    void setInitialCommitSliceEnabled(boolean value);

    /**
     * 首次全量几何提交分片时每帧的时间预算（毫秒）。它只是上限，实际每帧至少推进一个
     * 元素；非有限值或 ≤0 会被渲染侧退回内置默认值。默认 {@code 16.0}。
     */
    float initialCommitSliceMs();

    void setInitialCommitSliceMs(double value);

    /** Persists the current config values to disk. */
    void save();

    void markClientReloadPending();

    boolean consumeClientReloadPending();
}
