package com.sighs.apricityui.resource.async.network;

import com.sighs.apricityui.init.AbstractAsyncHandler;

public class NetworkHandle {
    private final String url;
    private volatile long generation;
    private volatile AbstractAsyncHandler.AsyncState state = AbstractAsyncHandler.AsyncState.NEW;
    private volatile long failedAtMs = 0L;

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

    public long failedAtMs() {
        return failedAtMs;
    }

    public synchronized void markReady() {
        this.state = AbstractAsyncHandler.AsyncState.READY;
    }

    public synchronized void markFailed(long nowMs) {
        this.state = AbstractAsyncHandler.AsyncState.FAILED;
        this.failedAtMs = nowMs;
    }

    public synchronized void resetForRetry(long newGeneration) {
        this.generation = newGeneration;
        this.state = AbstractAsyncHandler.AsyncState.NEW;
        this.failedAtMs = 0L;
    }

    public synchronized void markStale() {
        this.state = AbstractAsyncHandler.AsyncState.STALE;
    }
}
