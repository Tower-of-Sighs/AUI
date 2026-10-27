package com.sighs.apricityui.dev.perf;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.util.AuiLog;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded CPU-side opening diagnostics, not GPU presentation or an all-network-resources load event. */
public final class PageOpenTiming {
    public enum Stage {
        REQUEST, REFRESH_BEGIN, EXTRACTION, INITIAL_STYLE, EXPANDERS, FINAL_STYLE, PAINT_LIST,
        GLOBAL_SCRIPT, PAGE_SCRIPTS, LIFECYCLE, REFRESH_COMPLETE, DOCUMENT_CREATED,
        WINDOW_CREATED, SHELL_PAINT, LAYOUT_READY, FIRST_PAINT, CSS_SETTLED, FONTS_SETTLED, READY_PAINT
    }

    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final long TIMEOUT_NS = 10_000_000_000L;
    private static final long QUIET_NS = 150_000_000L;
    private final long id = NEXT_ID.incrementAndGet();
    private final String path;
    private final String source;
    private final LongSupplier clock;
    private final Consumer<String> log;
    private final long startedNs;
    private final long[] stages = new long[Stage.values().length];
    private String documentId = "unbound";
    private long generation;
    private String status;
    private long lastActivityNs;
    private long lastEventNs;
    private Object resourceToken;
    private long resourceRevision = -1;
    private long cssRevision = -1;
    private boolean resourcesObserved;
    private boolean cssSettled;
    private boolean fontsSettled;
    private long lastCssSettledNs = -1;
    private long lastFontsSettledNs = -1;
    private int pendingCss;
    private int pendingFonts;
    private int failures;
    private int resourceRestarts;
    private int paints;
    private int skippedPaints;
    private int layoutCommits;
    private int layoutSlices;
    private long layoutNs;
    private long maxLayoutNs;
    private long maxRenderNs;

    /** Disabled traces are represented by null, avoiding per-frame diagnostic work. */
    public static PageOpenTiming start(String path, String source) {
        if (!Boolean.parseBoolean(System.getProperty("apricityui.pageOpenTiming", "true"))) return null;
        return new PageOpenTiming(path, source, System::nanoTime, line -> ApricityUI.LOGGER.info(line));
    }

    PageOpenTiming(String path, String source, LongSupplier clock, Consumer<String> log) {
        this.path = AuiLog.source(path);
        this.source = AuiLog.source(source);
        this.clock = clock;
        this.log = log;
        this.startedNs = clock.getAsLong();
        this.lastActivityNs = startedNs;
        this.lastEventNs = startedNs;
        Arrays.fill(stages, -1);
        mark(Stage.REQUEST);
    }

    public boolean isActive() {
        return status == null;
    }

    public boolean isBound() {
        return generation != 0;
    }

    public void bind(UUID documentId, long generation) {
        if (!isActive()) return;
        this.documentId = documentId.toString();
        this.generation = generation;
    }

    public void mark(Stage stage) {
        if (!checkDeadline() || stages[stage.ordinal()] >= 0) return;
        long now = clock.getAsLong();
        stages[stage.ordinal()] = now - startedNs;
        log.accept(prefix() + " stage=" + stage + " elapsedMs=" + millis(now - startedNs)
                + " sincePreviousMs=" + millis(now - lastEventNs));
        lastEventNs = now;
        // These stages perform initialization; observation-only milestones must not extend the quiet window.
        if (stage.ordinal() <= Stage.WINDOW_CREATED.ordinal()) lastActivityNs = now;
    }

    public void layoutWork(long elapsedNs, boolean slice) {
        if (!checkDeadline()) return;
        if (slice) layoutSlices++; else layoutCommits++;
        layoutNs += Math.max(0, elapsedNs);
        maxLayoutNs = Math.max(maxLayoutNs, elapsedNs);
        lastActivityNs = clock.getAsLong();
    }

    /** Called only after a successful paint/flush boundary, including initial-layout skipped passes. */
    public void rendered(boolean contentPainted, boolean pendingStyleOrLayout, Object token, long revision, long cssVersion,
                         int cssTasks, int fontTasks, int failedTasks, long renderNs) {
        if (!checkDeadline()) return;
        long now = clock.getAsLong();
        maxRenderNs = Math.max(maxRenderNs, renderNs);
        boolean settled = observeResources(token, revision, cssVersion, cssTasks, fontTasks, failedTasks);
        if (!contentPainted) {
            skippedPaints++;
            return;
        }
        paints++;
        mark(Stage.LAYOUT_READY);
        mark(Stage.FIRST_PAINT);
        if (settled && !pendingStyleOrLayout) tryReady(now, "ready");
    }

    /** Used by the logical tick so manually-rendered/hidden documents terminate instead of timing out. */
    public void tick(boolean pendingStyleOrLayout, Object token, long revision, long cssVersion,
                     int cssTasks, int fontTasks, int failedTasks) {
        if (!checkDeadline()) return;
        boolean settled = observeResources(token, revision, cssVersion, cssTasks, fontTasks, failedTasks);
        if (settled && !pendingStyleOrLayout) tryReady(clock.getAsLong(), "ready-no-paint");
    }

    private boolean observeResources(Object token, long revision, long cssVersion,
                                     int cssTasks, int fontTasks, int failedTasks) {
        long now = clock.getAsLong();
        pendingCss = cssTasks;
        pendingFonts = fontTasks;
        failures = failedTasks;
        if (!resourcesObserved) {
            // First observation only records identity; it is not a resource restart and must not
            // extend the quiet window (otherwise a page with zero external resources never settles).
            resourcesObserved = true;
            resourceToken = token;
            resourceRevision = revision;
            cssRevision = cssVersion;
            fontsSettled = false;
        } else if (resourceToken != token || cssRevision != cssVersion) {
            cssSettled = false;
            cssRevision = cssVersion;
        }
        if (resourcesObserved && (resourceToken != token || resourceRevision != revision)) {
            if (fontsSettled) resourceRestarts++;
            resourceToken = token;
            resourceRevision = revision;
            lastActivityNs = now;
            fontsSettled = false;
        }
        if (cssTasks == 0) {
            if (!cssSettled) lastCssSettledNs = now - startedNs;
            cssSettled = true;
            mark(Stage.CSS_SETTLED);
        } else {
            cssSettled = false;
            lastActivityNs = now;
        }
        // A pending stylesheet can still enqueue fonts. Do not report font settlement before it finishes.
        if (cssTasks == 0 && fontTasks == 0) {
            if (!fontsSettled) lastFontsSettledNs = now - startedNs;
            fontsSettled = true;
            mark(Stage.FONTS_SETTLED);
        } else {
            fontsSettled = false;
            lastActivityNs = now;
        }
        return cssSettled && fontsSettled;
    }

    private void tryReady(long now, String reason) {
        if (stages[Stage.REFRESH_COMPLETE.ordinal()] < 0) return;
        if (now - lastActivityNs < QUIET_NS) return;
        mark(Stage.READY_PAINT);
        finish(failures == 0 ? reason : reason + "-with-resource-errors");
    }

    public void finish(String reason) {
        if (!isActive()) return;
        status = reason;
        long now = clock.getAsLong();
        log.accept(prefix() + " stage=SUMMARY status=" + reason + " totalMs=" + millis(now - startedNs)
                + " createdMs=" + stageMillis(Stage.DOCUMENT_CREATED)
                + " shellPaintMs=" + stageMillis(Stage.SHELL_PAINT)
                + " firstPaintMs=" + stageMillis(Stage.FIRST_PAINT)
                + " readyPaintMs=" + stageMillis(Stage.READY_PAINT)
                + " lastCssSettledMs=" + optionalMillis(lastCssSettledNs)
                + " lastFontsSettledMs=" + optionalMillis(lastFontsSettledNs)
                + " cssPending=" + pendingCss + " fontsPending=" + pendingFonts + " resourceFailures=" + failures
                + " resourceRestarts=" + resourceRestarts + " paints=" + paints + " skippedPaints=" + skippedPaints
                + " layoutCommits=" + layoutCommits + " layoutSlices=" + layoutSlices
                + " layoutWorkMs=" + millis(layoutNs) + " maxLayoutWorkMs=" + millis(maxLayoutNs)
                + " maxRenderMs=" + millis(maxRenderNs)
                + " quietMs=150 paintBoundary=cpu-submit readyScope=css-font-style-layout excludes=images-gpu-presentation");
        resourceToken = null;
    }

    private boolean checkDeadline() {
        if (!isActive()) return false;
        if (clock.getAsLong() - startedNs >= TIMEOUT_NS) {
            finish("timeout");
            return false;
        }
        return true;
    }

    private String prefix() {
        return "[AUI Open] id=" + id + " path=" + path + " source=" + source
                + " document=" + documentId + " generation=" + generation;
    }

    private String stageMillis(Stage stage) {
        return optionalMillis(stages[stage.ordinal()]);
    }

    private static String optionalMillis(long nanos) {
        return nanos < 0 ? "not-observed" : millis(nanos);
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0d);
    }
}
