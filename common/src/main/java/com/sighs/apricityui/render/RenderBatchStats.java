package com.sighs.apricityui.render;

import java.util.Locale;

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
    private static int fullCommits;
    private static int transformCommits;
    private static int frameFullCommits;
    private static int frameTransformCommits;
    private static int lastFullCommits;
    private static int lastTransformCommits;
    // 文字光栅化（整行路径）跑在工作线程上：任务数与耗时是速率，走帧级双缓冲。
    private static int rasterTasks;
    private static long rasterNanos;
    private static int frameRasterTasks;
    private static long frameRasterNanos;
    private static int lastRasterTasks;
    private static long lastRasterNanos;
    // 字体图集页数/占用、缓存条目数、是否已装不下：都是状态量（gauge），不做帧级双缓冲。
    private static int atlasPages;
    private static int atlasUsedPercent;
    private static int cacheEntries;
    private static boolean atlasExhausted;

    private RenderBatchStats() {
    }

    public static void beginDocument() {
        graphFlushes = 0;
        imageFlushes = 0;
        sharedFlushes = 0;
        itemDraws = 0;
        itemNanos = 0L;
        itemMaxNanos = 0L;
        fullCommits = 0;
        transformCommits = 0;
        rasterTasks = 0;
        rasterNanos = 0L;
    }

    public static void beginFrame() {
        frameActive = true;
        frameGraphFlushes = 0;
        frameImageFlushes = 0;
        frameSharedFlushes = 0;
        frameItemDraws = 0;
        frameItemNanos = 0L;
        frameItemMaxNanos = 0L;
        frameFullCommits = 0;
        frameTransformCommits = 0;
        frameRasterTasks = 0;
        frameRasterNanos = 0L;
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

    /**
     * Counts one full-document geometry commit ({@code LayoutCommit.commit}). This is
     * the expensive path: it rebuilds every element's rect and re-runs layout
     * measurement for the whole document. A relayout or a paint-list rebuild forces
     * it; a transform-only change does not.
     */
    public static void recordFullLayoutCommit() {
        fullCommits++;
        if (frameActive) frameFullCommits++;
    }

    /**
     * Counts one targeted geometry commit ({@code LayoutCommit.commitTransforms}),
     * which only refreshes the committed world transforms of the affected subtrees.
     */
    public static void recordTransformCommit() {
        transformCommits++;
        if (frameActive) frameTransformCommits++;
    }

    /** Counts one text rasterization performed on a worker thread, with its wall time. */
    public static void recordRaster(long elapsedNs) {
        rasterTasks++;
        if (elapsedNs > 0L) rasterNanos += elapsedNs;
        if (frameActive) {
            frameRasterTasks++;
            if (elapsedNs > 0L) frameRasterNanos += elapsedNs;
        }
    }

    /**
     * Publishes the current font texture storage state. Gauges are not double-buffered:
     * the snapshot always reflects the most recent publish. {@code usedPercent} is clamped
     * so a caller mistake cannot produce a nonsensical HUD line.
     */
    public static void setFontStorageState(int pages, int usedPercent, int entries, boolean exhausted) {
        atlasPages = Math.max(0, pages);
        atlasUsedPercent = Math.max(0, Math.min(100, usedPercent));
        cacheEntries = Math.max(0, entries);
        atlasExhausted = exhausted;
    }

    /** 留白绘制的累计次数与日志预算（去掉原版字体回退后，这是唯一的降级形态）。 */
    private static final java.util.concurrent.atomic.AtomicLong blankTextTotal =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong blankTextLogBudget =
            new java.util.concurrent.atomic.AtomicLong(8);

    /** 记一次"字体未就绪且没有可维持画面"的留白绘制。 */
    public static void recordBlankText() {
        blankTextTotal.incrementAndGet();
    }

    /** 累计留白次数（诊断用）。 */
    public static long blankTexts() {
        return blankTextTotal.get();
    }

    /** 是否还允许打一条留白日志：总额限流，避免刷屏。 */
    public static boolean claimBlankTextLog() {
        return blankTextLogBudget.getAndDecrement() > 0;
    }

    public static void endDocument() {
        if (frameActive) return;
        lastGraphFlushes = graphFlushes;
        lastImageFlushes = imageFlushes;
        lastSharedFlushes = sharedFlushes;
        lastItemDraws = itemDraws;
        lastItemNanos = itemNanos;
        lastItemMaxNanos = itemMaxNanos;
        lastFullCommits = fullCommits;
        lastTransformCommits = transformCommits;
        lastRasterTasks = rasterTasks;
        lastRasterNanos = rasterNanos;
    }

    public static void endFrame() {
        if (!frameActive) return;
        lastGraphFlushes = frameGraphFlushes;
        lastImageFlushes = frameImageFlushes;
        lastSharedFlushes = frameSharedFlushes;
        lastItemDraws = frameItemDraws;
        lastItemNanos = frameItemNanos;
        lastItemMaxNanos = frameItemMaxNanos;
        lastFullCommits = frameFullCommits;
        lastTransformCommits = frameTransformCommits;
        lastRasterTasks = frameRasterTasks;
        lastRasterNanos = frameRasterNanos;
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

    /** Full-document geometry commits in the last completed frame. */
    public static int lastFullCommits() {
        return lastFullCommits;
    }

    /** Targeted transform commits in the last completed frame. */
    public static int lastTransformCommits() {
        return lastTransformCommits;
    }

    /** Live font atlas page count (gauge, not per-frame). */
    public static int atlasPages() {
        return atlasPages;
    }

    /** Live font atlas fill ratio in percent, 0..100 (gauge, not per-frame). */
    public static int atlasUsedPercent() {
        return atlasUsedPercent;
    }

    /** Live entry count of the text raster cache (gauge, not per-frame). */
    public static int cacheEntries() {
        return cacheEntries;
    }

    /** Whether the font atlas can no longer take new entries (gauge, not per-frame). */
    public static boolean atlasExhausted() {
        return atlasExhausted;
    }

    /** Worker-thread text rasterizations finished in the last completed frame. */
    public static int lastRasterTasks() {
        return lastRasterTasks;
    }

    /** Wall time spent in worker-thread text rasterization in the last completed frame, in nanos. */
    public static long lastRasterNanos() {
        return lastRasterNanos;
    }

    /**
     * One-line, grep-friendly snapshot used by the per-stage verification runs.
     * Pure formatting: no logging and no state change, so it can be called from
     * anywhere without affecting the counters.
     */
    public static String fontStatsLine(double avgFrameMillis, double avgWallFrameMillis) {
        return String.format(
                Locale.ROOT,
                "[AUI FontStats] avgFrame=%.2fms avgWallFrame=%.2fms imageFlushes=%d graphFlushes=%d"
                        + " sharedFlushes=%d itemDraws=%d fullCommits=%d"
                        + " rasterTasks=%d rasterMs=%.2f atlasPages=%d atlasUsed=%d%% cacheSize=%d atlasFull=%b",
                avgFrameMillis,
                avgWallFrameMillis,
                lastImageFlushes,
                lastGraphFlushes,
                lastSharedFlushes,
                lastItemDraws,
                lastFullCommits,
                lastRasterTasks,
                lastRasterNanos / 1_000_000.0d,
                atlasPages,
                atlasUsedPercent,
                cacheEntries,
                atlasExhausted
        );
    }
}
