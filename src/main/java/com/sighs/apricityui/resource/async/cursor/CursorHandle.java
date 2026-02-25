package com.sighs.apricityui.resource.async.cursor;

import com.sighs.apricityui.init.AbstractAsyncHandler;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 异步加载得到的 GLFW Cursor 句柄。
 */
public final class CursorHandle {
    private final String key;
    private final String path;
    private final int hotspotX;
    private final int hotspotY;
    private final long generation;

    private final AtomicReference<AbstractAsyncHandler.AsyncState> state = new AtomicReference<>(AbstractAsyncHandler.AsyncState.NEW);

    private volatile long glfwCursorHandle = 0L;
    private volatile long failedAtMs = 0L;
    private volatile Throwable error;

    public CursorHandle(String key, String path, int hotspotX, int hotspotY, long generation) {
        this.key = key;
        this.path = path;
        this.hotspotX = hotspotX;
        this.hotspotY = hotspotY;
        this.generation = generation;
    }

    public String key() {
        return key;
    }

    public String path() {
        return path;
    }

    public int hotspotX() {
        return hotspotX;
    }

    public int hotspotY() {
        return hotspotY;
    }

    public long generation() {
        return generation;
    }

    public AbstractAsyncHandler.AsyncState state() {
        return state.get();
    }

    public long glfwCursorHandle() {
        return glfwCursorHandle;
    }

    public long failedAtMs() {
        return failedAtMs;
    }

    public Throwable error() {
        return error;
    }

    public boolean transition(AbstractAsyncHandler.AsyncState expect, AbstractAsyncHandler.AsyncState next) {
        return state.compareAndSet(expect, next);
    }

    public void markStale() {
        state.set(AbstractAsyncHandler.AsyncState.STALE);
    }

    public void markFailed(Throwable ex, long nowMs) {
        this.error = ex;
        this.failedAtMs = nowMs;
        state.set(AbstractAsyncHandler.AsyncState.FAILED);
    }

    public boolean markReady(long glfwCursorHandle) {
        if (glfwCursorHandle == 0L) return false;
        this.glfwCursorHandle = glfwCursorHandle;
        return state.compareAndSet(AbstractAsyncHandler.AsyncState.APPLYING, AbstractAsyncHandler.AsyncState.READY);
    }

    public void resetForRetry(long generation) {
        // generation 变化在 map 层会新建 handle，这里只重置状态
        this.glfwCursorHandle = 0L;
        this.error = null;
        this.failedAtMs = 0L;
        state.set(AbstractAsyncHandler.AsyncState.NEW);
    }

    public void destroyCursorIfPresent() {
        long handle = glfwCursorHandle;
        glfwCursorHandle = 0L;
        if (handle != 0L) {
            try {
                org.lwjgl.glfw.GLFW.glfwDestroyCursor(handle);
            } catch (Throwable ignored) {
            }
        }
    }
}
