package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiServices;

import java.util.Arrays;
import java.util.Locale;

public final class FrameTimingHud {
    private static final int SAMPLE_COUNT = 120;
    private static final long[] SAMPLES = new long[SAMPLE_COUNT];
    private static int sampleIndex = 0;
    private static int sampleSize = 0;
    private static boolean frameActive = false;
    private static long frameElapsedNs = 0L;
    private static volatile boolean profilingActive = false;

    private FrameTimingHud() {
    }

    public static void beginFrame() {
        if (!isEnabled()) {
            clear();
            return;
        }
        profilingActive = true;
        frameActive = true;
        frameElapsedNs = 0L;
        RenderBatchStats.beginFrame();
    }

    public static void record(long elapsedNs) {
        if (!isEnabled()) {
            clear();
            return;
        }
        if (elapsedNs <= 0) return;
        if (frameActive) {
            frameElapsedNs += elapsedNs;
            return;
        }
        pushSample(elapsedNs);
    }

    public static void endFrame() {
        profilingActive = false;
        if (!isEnabled()) {
            clear();
            return;
        }
        if (frameActive) {
            if (frameElapsedNs > 0) {
                pushSample(frameElapsedNs);
            }
            frameActive = false;
            frameElapsedNs = 0L;
            RenderBatchStats.endFrame();
        }
    }

    /**
     * Whether per-draw profiling must run this frame. Resolved once per frame so hot
     * per-paint call sites can read a plain field instead of the configuration.
     */
    public static boolean isProfilingActive() {
        return profilingActive;
    }

    private static void pushSample(long elapsedNs) {
        SAMPLES[sampleIndex] = elapsedNs;
        sampleIndex = (sampleIndex + 1) % SAMPLE_COUNT;
        if (sampleSize < SAMPLE_COUNT) {
            sampleSize++;
        }
    }

    /**
     * Live image-stream status lines registered by texture elements such as
     * {@code <iframe>}, so the capture rate, queue latency and raster size are visible in
     * game rather than only in a debugger.
     */
    private static final java.util.List<java.util.function.Supplier<String>> STREAMS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Registers a status line for the HUD.
     *
     * @return a handle that removes it again; closing twice is harmless
     */
    public static AutoCloseable registerStream(java.util.function.Supplier<String> status) {
        STREAMS.add(status);
        return () -> STREAMS.remove(status);
    }

    /** Returns the formatted frame-timing stats line, or {@code null} when empty. */
    public static String frameStatsText() {
        if (sampleSize == 0) return null;

        long min = Long.MAX_VALUE;
        long max = 0L;
        long sum = 0L;
        for (int i = 0; i < sampleSize; i++) {
            long value = SAMPLES[i];
            if (value <= 0) continue;
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }
        if (min == Long.MAX_VALUE) return null;

        double avg = (double) sum / sampleSize;
        String base = String.format(
                Locale.ROOT,
                "max %.2f ms  min %.2f ms  avg %.2f ms  g %d img %d sb %d  ly %d tf %d",
                toMillis(max),
                toMillis(min),
                toMillis(avg),
                RenderBatchStats.lastGraphFlushes(),
                RenderBatchStats.lastImageFlushes(),
                RenderBatchStats.lastSharedFlushes(),
                RenderBatchStats.lastFullCommits(),
                RenderBatchStats.lastTransformCommits()
        );
        for (java.util.function.Supplier<String> stream : STREAMS) {
            String text = stream.get();
            if (text != null && !text.isEmpty()) {
                base = base + "  " + text;
            }
        }
        int items = RenderBatchStats.lastItemDraws();
        if (items <= 0) return base;
        return base + String.format(
                Locale.ROOT,
                "  item %d x%.2f/%.2f ms",
                items,
                toMillis((double) RenderBatchStats.lastItemNanos() / items),
                toMillis(RenderBatchStats.lastItemMaxNanos())
        );
    }

    private static double toMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private static double toMillis(double nanos) {
        return nanos / 1_000_000.0d;
    }

    public static boolean isEnabled() {
        try {
            return AuiServices.config().frameTimingHud();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static void clear() {
        frameActive = false;
        frameElapsedNs = 0L;
        profilingActive = false;
        if (sampleSize == 0 && sampleIndex == 0) return;
        Arrays.fill(SAMPLES, 0L);
        sampleIndex = 0;
        sampleSize = 0;
    }

}
