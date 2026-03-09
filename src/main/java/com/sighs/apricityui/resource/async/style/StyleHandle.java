package com.sighs.apricityui.resource.async.style;

import com.sighs.apricityui.init.AbstractAsyncHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StyleHandle {
    private final UUID documentId;
    private final ConcurrentHashMap<Integer, CssEntry> cssEntries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> requestedFonts = new ConcurrentHashMap<>();
    private final AtomicInteger pendingTasks = new AtomicInteger(0);

    private final long generation;
    private volatile AbstractAsyncHandler.AsyncState state = AbstractAsyncHandler.AsyncState.NEW;
    private volatile long failedAtMs = 0L;

    public StyleHandle(UUID documentId, long generation) {
        this.documentId = documentId;
        this.generation = generation;
    }

    public UUID documentId() {
        return documentId;
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

    public int pendingTasks() {
        return pendingTasks.get();
    }

    public void putCssEntry(int order, CssEntry entry) {
        if (entry == null) return;
        cssEntries.put(order, entry);
    }

    public List<Map.Entry<Integer, CssEntry>> snapshotCssEntries() {
        ArrayList<Map.Entry<Integer, CssEntry>> entries = new ArrayList<>(cssEntries.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        return entries;
    }

    public boolean tryRequestFont(String fontKey) {
        if (fontKey == null || fontKey.isBlank()) return false;
        return requestedFonts.putIfAbsent(fontKey, Boolean.TRUE) == null;
    }

    public synchronized void markLoading() {
        if (state == AbstractAsyncHandler.AsyncState.STALE) return;
        state = AbstractAsyncHandler.AsyncState.LOADING;
    }

    public synchronized void markApplying() {
        if (state == AbstractAsyncHandler.AsyncState.STALE) return;
        state = AbstractAsyncHandler.AsyncState.APPLYING;
    }

    public synchronized void markTaskQueued() {
        if (state == AbstractAsyncHandler.AsyncState.STALE) return;
        pendingTasks.incrementAndGet();
        if (state == AbstractAsyncHandler.AsyncState.NEW || state == AbstractAsyncHandler.AsyncState.READY || state == AbstractAsyncHandler.AsyncState.FAILED) {
            state = AbstractAsyncHandler.AsyncState.LOADING;
        }
    }

    public synchronized void markTaskCompleted(boolean failed) {
        if (state == AbstractAsyncHandler.AsyncState.STALE) return;
        if (failed) failedAtMs = System.currentTimeMillis();

        int left = pendingTasks.decrementAndGet();
        if (left < 0) {
            pendingTasks.set(0);
            left = 0;
        }
        if (left > 0) {
            state = AbstractAsyncHandler.AsyncState.LOADING;
            return;
        }

        if (failed && cssEntries.isEmpty()) {
            state = AbstractAsyncHandler.AsyncState.FAILED;
        } else {
            state = AbstractAsyncHandler.AsyncState.READY;
        }
    }

    public synchronized void markReady() {
        if (state == AbstractAsyncHandler.AsyncState.STALE) return;
        state = AbstractAsyncHandler.AsyncState.READY;
    }

    public synchronized void markStale() {
        state = AbstractAsyncHandler.AsyncState.STALE;
        pendingTasks.set(0);
    }

    public record CssEntry(String contextPath, String cssText) {
    }
}
