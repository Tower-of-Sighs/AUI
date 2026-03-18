package com.sighs.apricityui.resource.async.network;

import com.sighs.apricityui.init.AbstractAsyncHandler;

public final class NetworkHandle {
    private final String url;
    private volatile long generation;
    private volatile AbstractAsyncHandler.AsyncState state = AbstractAsyncHandler.AsyncState.NEW;
    private volatile Throwable error;
    private volatile long failedAtMs;

    public NetworkHandle(String url, long generation) {
        this.url = url;
        this.generation = generation;
    }

    public String url() {
        return url;
    }

    public long generation() {
        return generation;
    }

    public AbstractAsyncHandler.AsyncState state() {
        return state;
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

    public synchronized void markLoading() {
        state = AbstractAsyncHandler.AsyncState.LOADING;
    }

    public synchronized void markReady() {
        state = AbstractAsyncHandler.AsyncState.READY;
        error = null;
    }

    public synchronized void markFailed(Throwable throwable, long nowMs) {
        state = AbstractAsyncHandler.AsyncState.FAILED;
        error = throwable;
        failedAtMs = nowMs;
    }

    public synchronized void markStale() {
        state = AbstractAsyncHandler.AsyncState.STALE;
    }
}
