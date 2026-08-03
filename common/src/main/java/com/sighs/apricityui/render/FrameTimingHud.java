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

    private FrameTimingHud() {
    }

    public static void beginFrame() {
        if (!isEnabled()) {
            clear();
            return;
        }
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

    private static void pushSample(long elapsedNs) {
        SAMPLES[sampleIndex] = elapsedNs;
        sampleIndex = (sampleIndex + 1) % SAMPLE_COUNT;
        if (sampleSize < SAMPLE_COUNT) {
            sampleSize++;
        }
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
        return String.format(
                Locale.ROOT,
                "max %.2f ms  min %.2f ms  avg %.2f ms  g %d img %d imm %d",
                toMillis(max),
                toMillis(min),
                toMillis(avg),
                RenderBatchStats.lastGraphFlushes(),
                RenderBatchStats.lastImageFlushes(),
                RenderBatchStats.lastImmediateImageFlushes()
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
        if (sampleSize == 0 && sampleIndex == 0) return;
        Arrays.fill(SAMPLES, 0L);
        sampleIndex = 0;
        sampleSize = 0;
    }

}
