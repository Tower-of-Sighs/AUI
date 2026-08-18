package com.sighs.apricityui.resource.async.audio;

import com.sighs.apricityui.spi.AuiAudioService;
import com.sighs.apricityui.task.AbstractAsyncHandler;

/**
 * 一个已解析路径的共享音频句柄：解码结果与后端 buffer 按路径共享
 * （多个 AudioPlayer 播同一文件时共用一份 PCM/buffer，各自独占 channel）。
 * 状态机与 ImageHandle 对齐：NEW → LOADING → APPLYING → READY / FAILED / STALE。
 */
public final class AudioHandle {
    private final String path;
    private volatile long generation;
    private volatile AbstractAsyncHandler.AsyncState state = AbstractAsyncHandler.AsyncState.NEW;
    private volatile AuiAudioService.AudioBufferHandle buffer;
    private volatile double durationSeconds;
    private volatile Throwable error;
    private volatile long failedAtMs;

    public AudioHandle(String path, long generation) {
        this.path = path;
        this.generation = generation;
    }

    public String path() {
        return path;
    }

    public long generation() {
        return generation;
    }

    public AbstractAsyncHandler.AsyncState state() {
        return state;
    }

    public AuiAudioService.AudioBufferHandle buffer() {
        return buffer;
    }

    public double durationSeconds() {
        return durationSeconds;
    }

    public Throwable error() {
        return error;
    }

    public long failedAtMs() {
        return failedAtMs;
    }

    public synchronized void reset(long newGeneration) {
        generation = newGeneration;
        state = AbstractAsyncHandler.AsyncState.NEW;
        error = null;
        failedAtMs = 0L;
    }

    public synchronized boolean tryEnterLoading() {
        if (state != AbstractAsyncHandler.AsyncState.NEW) return false;
        state = AbstractAsyncHandler.AsyncState.LOADING;
        return true;
    }

    public synchronized boolean tryEnterApplying() {
        if (state != AbstractAsyncHandler.AsyncState.LOADING) return false;
        state = AbstractAsyncHandler.AsyncState.APPLYING;
        return true;
    }

    public synchronized void markReady(AuiAudioService.AudioBufferHandle readyBuffer, double readyDurationSeconds) {
        buffer = readyBuffer;
        durationSeconds = readyDurationSeconds;
        error = null;
        state = AbstractAsyncHandler.AsyncState.READY;
    }

    public synchronized void markFailed(Throwable throwable, long nowMs) {
        error = throwable;
        failedAtMs = nowMs;
        state = AbstractAsyncHandler.AsyncState.FAILED;
    }

    public synchronized void markStale() {
        state = AbstractAsyncHandler.AsyncState.STALE;
    }

    public synchronized void destroyBufferIfPresent() {
        if (buffer == null) return;
        buffer.destroy();
        buffer = null;
    }
}
