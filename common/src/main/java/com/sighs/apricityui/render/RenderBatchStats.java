package com.sighs.apricityui.render;

public final class RenderBatchStats {
    private static int graphFlushes;
    private static int imageFlushes;
    private static int sharedFlushes;
    private static int itemDraws;
    private static long itemNanos;
    private static long itemMaxNanos;
    private static boolean frameActive;
    private static int frameGraphFlushes;
    private static int frameImageFlushes;
    private static int frameSharedFlushes;
    private static int frameItemDraws;
    private static long frameItemNanos;
    private static long frameItemMaxNanos;
    private static int lastGraphFlushes;
    private static int lastImageFlushes;
    private static int lastSharedFlushes;
    private static int lastItemDraws;
    private static long lastItemNanos;
    private static long lastItemMaxNanos;

    private RenderBatchStats() {
    }

    public static void beginDocument() {
        graphFlushes = 0;
        imageFlushes = 0;
        sharedFlushes = 0;
        itemDraws = 0;
        itemNanos = 0L;
        itemMaxNanos = 0L;
    }

    public static void beginFrame() {
        frameActive = true;
        frameGraphFlushes = 0;
        frameImageFlushes = 0;
        frameSharedFlushes = 0;
        frameItemDraws = 0;
        frameItemNanos = 0L;
        frameItemMaxNanos = 0L;
    }

    public static void recordGraphFlush() {
        graphFlushes++;
        if (frameActive) frameGraphFlushes++;
    }

    public static void recordImageFlush() {
        imageFlushes++;
        if (frameActive) frameImageFlushes++;
    }

    /**
     * Counts a flush of the loader's shared {@code MultiBufferSource}. This is the
     * metric the per-item paint path was reported against: every deferred batch
     * that reaches the shared source must be submitted before a render-state
     * change, so the count tracks how fragmented a document's painting is.
     */
    public static void recordSharedFlush() {
        sharedFlushes++;
        if (frameActive) frameSharedFlushes++;
    }

    /** Counts one item painted by the loader's item backend, with its wall time. */
    public static void recordItemDraw(long elapsedNs) {
        itemDraws++;
        if (elapsedNs > 0L) {
            itemNanos += elapsedNs;
            if (elapsedNs > itemMaxNanos) itemMaxNanos = elapsedNs;
        }
        if (frameActive) {
            frameItemDraws++;
            if (elapsedNs > 0L) {
                frameItemNanos += elapsedNs;
                if (elapsedNs > frameItemMaxNanos) frameItemMaxNanos = elapsedNs;
            }
        }
    }

    public static void endDocument() {
        if (frameActive) return;
        lastGraphFlushes = graphFlushes;
        lastImageFlushes = imageFlushes;
        lastSharedFlushes = sharedFlushes;
        lastItemDraws = itemDraws;
        lastItemNanos = itemNanos;
        lastItemMaxNanos = itemMaxNanos;
    }

    public static void endFrame() {
        if (!frameActive) return;
        lastGraphFlushes = frameGraphFlushes;
        lastImageFlushes = frameImageFlushes;
        lastSharedFlushes = frameSharedFlushes;
        lastItemDraws = frameItemDraws;
        lastItemNanos = frameItemNanos;
        lastItemMaxNanos = frameItemMaxNanos;
        frameActive = false;
    }

    public static int lastGraphFlushes() {
        return lastGraphFlushes;
    }

    public static int lastImageFlushes() {
        return lastImageFlushes;
    }

    public static int lastSharedFlushes() {
        return lastSharedFlushes;
    }

    public static int lastItemDraws() {
        return lastItemDraws;
    }

    public static long lastItemNanos() {
        return lastItemNanos;
    }

    public static long lastItemMaxNanos() {
        return lastItemMaxNanos;
    }
}
