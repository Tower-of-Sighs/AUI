package com.sighs.apricityui.media;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.resource.async.audio.AudioAsyncHandler;
import com.sighs.apricityui.resource.async.audio.AudioHandle;
import com.sighs.apricityui.spi.AuiAudioService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.task.AbstractAsyncHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * HTMLAudioElement 语义的播放器状态机（纯 Java，不依赖 DOM/Element）。
 * <p>
 * 只依赖 {@link AuiAudioService} 后端接口：headless 下用 AuiServices
 * 默认的时钟模拟后端即可完整测试 play/pause/seek/ended/loop 语义。
 * 事件经 {@link EventSink} 外发——element/Audio 把它桥成 DOM 事件，
 * 测试直接挂 lambda 收集。
 * <p>
 * 每帧由 {@link AudioEngine#tick()} 驱动：轮询加载句柄状态、推进
 * ended/loop 检测、节流派发 timeupdate、推送有效音量。
 */
public final class AudioPlayer {
    // readyState
    public static final int HAVE_NOTHING = 0;
    public static final int HAVE_METADATA = 1;
    public static final int HAVE_ENOUGH_DATA = 4;
    // networkState
    public static final int NETWORK_EMPTY = 0;
    public static final int NETWORK_IDLE = 1;
    public static final int NETWORK_LOADING = 2;
    public static final int NETWORK_NO_SOURCE = 3;

    private static final long TIMEUPDATE_INTERVAL_MS = 250L;

    /** 媒体事件外发口；type 为浏览器事件名（"play"/"ended"/"canplay"…）。 */
    public interface EventSink {
        void onMediaEvent(String type);
    }

    private final EventSink eventSink;

    private String resolvedSrc = "";
    private String preload = "auto";
    private boolean autoplay;

    private volatile int readyState = HAVE_NOTHING;
    // 无 src 的初始态即 NO_SOURCE（浏览器一致）
    private volatile int networkState = NETWORK_NO_SOURCE;

    private double volume = 1.0;
    private boolean muted;
    private boolean loop;

    private AudioHandle handle;
    private AuiAudioService.AudioChannel channel;
    private double duration = Double.NaN;

    private boolean playRequested;
    private AudioPlayPromise pendingPlayPromise;

    private boolean ended;
    private boolean seeking;
    private long lastTimeupdateMs;
    private boolean destroyed;

    public AudioPlayer(EventSink eventSink) {
        this.eventSink = eventSink == null ? type -> {
        } : eventSink;
    }

    // -------------------------------------------------------------- 属性

    public String getSrc() {
        return resolvedSrc;
    }

    /** 设置已解析的 src（调用方负责 Loader.resolve）。等同浏览器 src 反射：触发资源选择。 */
    public void setSrc(String resolved) {
        String next = resolved == null ? "" : resolved;
        if (next.equals(resolvedSrc)) return;
        stopInternal();
        handle = null;
        resolvedSrc = next;
        duration = Double.NaN;
        ended = false;
        seeking = false;
        readyState = HAVE_NOTHING;
        networkState = next.isEmpty() ? NETWORK_NO_SOURCE : NETWORK_IDLE;
        if (next.isEmpty()) {
            dispatch("emptied");
            return;
        }
        if (!"none".equals(preload)) startLoading();
    }

    public String getPreload() {
        return preload;
    }

    public void setPreload(String value) {
        preload = value == null ? "auto" : value;
    }

    public boolean isAutoplay() {
        return autoplay;
    }

    public void setAutoplay(boolean value) {
        autoplay = value;
    }

    public int getReadyState() {
        return readyState;
    }

    public int getNetworkState() {
        return networkState;
    }

    public double getDuration() {
        return duration;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double value) {
        double clamped = Math.max(0, Math.min(1, value));
        if (clamped == volume) return;
        volume = clamped;
        pushEffectiveVolume();
        dispatch("volumechange");
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean value) {
        if (value == muted) return;
        muted = value;
        pushEffectiveVolume();
        dispatch("volumechange");
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean value) {
        loop = value;
    }

    public boolean isPaused() {
        return channel == null || !channelPlaying;
    }

    public boolean isEnded() {
        return ended;
    }

    public boolean isSeeking() {
        return seeking;
    }

    public double getCurrentTime() {
        if (channel == null) return 0;
        return channel.positionSeconds();
    }

    /** seek：钳到 [0, duration]，派发 seeking/seeked。 */
    public void setCurrentTime(double seconds) {
        if (channel == null || Double.isNaN(duration)) return;
        double target = Math.max(0, Math.min(seconds, duration));
        seeking = true;
        dispatch("seeking");
        channel.seekSeconds(target);
        if (target < duration) ended = false;
        seeking = false;
        dispatch("seeked");
    }

    // -------------------------------------------------------------- 方法

    /** 显式 load()：即使 preload=none 也开始加载。 */
    public void load() {
        if (resolvedSrc.isEmpty()) {
            networkState = NETWORK_NO_SOURCE;
            return;
        }
        if (handle != null && handle.state() == AbstractAsyncHandler.AsyncState.READY) return;
        startLoading();
    }

    /** play()：未加载先触发加载，就绪后开播；返回 Promise 风格句柄。 */
    public AudioPlayPromise play() {
        AudioPlayPromise promise = new AudioPlayPromise();
        if (destroyed) {
            promise.reject("player destroyed");
            return promise;
        }
        if (resolvedSrc.isEmpty()) {
            promise.reject("no source");
            return promise;
        }
        playRequested = true;
        pendingPlayPromise = promise;
        if (readyState >= HAVE_ENOUGH_DATA && channel != null) {
            beginPlayback();
        } else {
            load();
        }
        return promise;
    }

    public void pause() {
        playRequested = false;
        if (channel == null || !channelPlaying) return;
        channel.pause();
        channelPlaying = false;
        dispatch("pause");
    }

    /** 释放后端资源；之后本 player 不可用。 */
    public void destroy() {
        destroyed = true;
        stopInternal();
        handle = null;
    }

    // -------------------------------------------------------------- 帧驱动

    private boolean channelPlaying;

    /** 每帧由 AudioEngine 调用。 */
    public void tick() {
        if (destroyed) return;
        pollHandle();
        if (channel != null && channelPlaying) {
            double position = channel.positionSeconds();
            if (!Double.isNaN(duration) && position >= duration) {
                reachEnded();
            } else {
                long now = System.currentTimeMillis();
                if (now - lastTimeupdateMs >= TIMEUPDATE_INTERVAL_MS) {
                    lastTimeupdateMs = now;
                    dispatch("timeupdate");
                }
            }
        }
    }

    private void pollHandle() {
        if (handle == null) return;
        AbstractAsyncHandler.AsyncState state = handle.state();
        if (state == AbstractAsyncHandler.AsyncState.READY && readyState < HAVE_ENOUGH_DATA) {
            duration = handle.durationSeconds();
            dispatch("durationchange");
            readyState = HAVE_METADATA;
            dispatch("loadedmetadata");

            channel = AuiServices.audio().openChannel(handle.buffer());
            if (channel == null) {
                failPlayback("audio backend unavailable");
                return;
            }
            pushEffectiveVolume();
            readyState = HAVE_ENOUGH_DATA;
            networkState = NETWORK_IDLE;
            dispatch("canplay");
            dispatch("canplaythrough");
            if (autoplay || playRequested) beginPlayback();
            return;
        }
        if (state == AbstractAsyncHandler.AsyncState.FAILED) {
            networkState = NETWORK_IDLE;
            readyState = HAVE_NOTHING;
            failPlayback(handle.error() == null ? "audio load failed" : String.valueOf(handle.error().getMessage()));
        }
    }

    private void reachEnded() {
        if (loop) {
            // 播放器侧回卷（不依赖后端 loop），保证 loop/非 loop 路径一致可测。
            channel.seekSeconds(0);
            dispatch("timeupdate");
            return;
        }
        channelPlaying = false;
        channel.pause();
        ended = true;
        dispatch("ended");
    }

    private void beginPlayback() {
        if (channel == null) return;
        if (ended) {
            ended = false;
            channel.seekSeconds(0);
        }
        channel.play();
        channelPlaying = true;
        lastTimeupdateMs = System.currentTimeMillis();
        dispatch("play");
        dispatch("playing");
        if (pendingPlayPromise != null) {
            pendingPlayPromise.resolve();
            pendingPlayPromise = null;
        }
    }

    private void startLoading() {
        networkState = NETWORK_LOADING;
        dispatch("loadstart");
        handle = AudioAsyncHandler.INSTANCE.request(resolvedSrc);
        if (handle == null) {
            networkState = NETWORK_IDLE;
            failPlayback("invalid source");
        }
    }

    private void failPlayback(String message) {
        dispatch("error");
        if (pendingPlayPromise != null) {
            pendingPlayPromise.reject(message);
            pendingPlayPromise = null;
        }
        playRequested = false;
    }

    private void stopInternal() {
        if (channel != null) {
            channel.stop();
            channel.destroy();
            channel = null;
        }
        channelPlaying = false;
        playRequested = false;
        if (pendingPlayPromise != null) {
            pendingPlayPromise.reject("interrupted");
            pendingPlayPromise = null;
        }
    }

    /** reload 清资源时由 AudioEngine 调用：停 channel 但保留 src，可重新 load。 */
    void stopForResourceClear() {
        stopInternal();
        readyState = HAVE_NOTHING;
        networkState = resolvedSrc.isEmpty() ? NETWORK_NO_SOURCE : NETWORK_IDLE;
        handle = null;
        duration = Double.NaN;
    }

    private void pushEffectiveVolume() {
        if (channel == null) return;
        float effective = (float) (volume * (muted ? 0 : 1) * AuiServices.audio().masterVolume());
        channel.setVolume(Math.max(0f, Math.min(1f, effective)));
    }

    private void dispatch(String type) {
        eventSink.onMediaEvent(type);
    }

    // -------------------------------------------------------------- Promise

    /** play() 的 Promise 风格返回（仿 Window.FetchPromise：then/catchError）。 */
    public static final class AudioPlayPromise {
        private final List<Runnable> fulfillCallbacks = new CopyOnWriteArrayList<>();
        private final List<Consumer<Object>> rejectCallbacks = new CopyOnWriteArrayList<>();
        private volatile boolean settled;
        private volatile boolean rejected;
        private volatile Object reason;

        public synchronized void resolve() {
            if (settled) return;
            settled = true;
            for (Runnable callback : fulfillCallbacks) callback.run();
        }

        public synchronized void reject(Object reason) {
            if (settled) return;
            settled = true;
            rejected = true;
            this.reason = reason;
            for (Consumer<Object> callback : rejectCallbacks) callback.accept(reason);
        }

        public AudioPlayPromise then(Runnable onFulfilled) {
            if (onFulfilled == null) return this;
            boolean runNow;
            synchronized (this) {
                runNow = settled && !rejected;
                if (!runNow) fulfillCallbacks.add(onFulfilled);
            }
            if (runNow) onFulfilled.run();
            return this;
        }

        public AudioPlayPromise catchError(Consumer<Object> onRejected) {
            if (onRejected == null) return this;
            Object existing;
            synchronized (this) {
                if (!settled || !rejected) {
                    rejectCallbacks.add(onRejected);
                    return this;
                }
                existing = reason;
            }
            onRejected.accept(existing);
            return this;
        }

        public boolean isSettled() {
            return settled;
        }
    }
}
