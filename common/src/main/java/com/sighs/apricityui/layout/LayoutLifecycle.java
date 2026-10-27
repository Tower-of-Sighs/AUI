package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.RenderBatchStats.FullCommitReason;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Owns document geometry updates; render callers only need to know whether a frame is ready.
 */
public final class LayoutLifecycle {
    private final Document owner;
    private volatile long timedLayoutGeneration = -1L;
    private volatile InitialCommitSlice initialCommitSlice;
    private boolean paintListDirty;
    private Set<Element> styleTransformRoots;

    public LayoutLifecycle(Document owner) {
        this.owner = owner;
    }

    public void reset() {
        paintListDirty = false;
        styleTransformRoots = null;
        endInitialCommitSlice();
    }

    public void paintListChanged(boolean hasElements) {
        paintListDirty = hasElements;
    }

    public boolean hasPendingCommit() {
        return paintListDirty;
    }

    public void updatesFlushed() {
        paintListDirty = false;
    }

    private FullCommitReason pendingFullReason =
            FullCommitReason.EXPLICIT;

    public boolean collectUpdates(int globalMask, Set<Element> dirtyElements) {
        boolean full = paintListDirty || (globalMask & (Drawer.RELAYOUT | Drawer.COMMIT_LAYOUT)) != 0;
        pendingFullReason = paintListDirty
                ? FullCommitReason.PAINT_LIST
                : (globalMask & Drawer.RELAYOUT) != 0
                ? FullCommitReason.RELAYOUT
                : FullCommitReason.GLOBAL_TRANSFORM;
        boolean needsCommit = full;
        Set<Element> roots = null;
        for (Element element : dirtyElements) {
            if (element == null || !element.isConnected()) continue;
            if (element.hasDirtyFlag(Drawer.RELAYOUT)) {
                needsCommit = true;
                full = true;
                if (!paintListDirty) pendingFullReason = FullCommitReason.RELAYOUT;
            } else if (!full && element.hasDirtyFlag(Drawer.COMMIT_LAYOUT)) {
                needsCommit = true;
                if (roots == null) roots = Collections.newSetFromMap(new IdentityHashMap<>());
                roots.add(element);
            }
        }
        styleTransformRoots = full ? null : roots;
        return needsCommit;
    }

    public void invalidatePendingLayouts(Set<Element> dirtyElements) {
        ArrayList<Element> sorted = null;
        for (Element element : dirtyElements) {
            if (element != null && element.isConnected() && element.hasDirtyFlag(Drawer.RELAYOUT)) {
                if (sorted == null) sorted = new ArrayList<>();
                sorted.add(element);
            }
        }
        if (sorted == null) return;
        sorted.sort(Comparator.comparingInt(Element::getDepth));
        for (Element element : sorted) {
            element.getLayout().invalidateLayoutAncestors();
            element.addDirtyFlags(Drawer.REPAINT);
        }
    }

    public Set<Element> drainStyleTransformRoots() {
        Set<Element> roots = styleTransformRoots;
        styleTransformRoots = null;
        return roots;
    }

    public void commit() {
        commit((FullCommitReason) null);
    }

    /**
     * Tick-path geometry commit.
     *
     * @return {@code false} when the initial full commit was left to the render-frame slicer,
     *         so callers must not refresh hit-test entries from the still-incomplete geometry
     */
    public boolean commit(FullCommitReason source) {
        return commit(source, false);
    }

    /** Hit testing needs complete geometry now; it may bypass the initial-commit slicer. */
    public void commitForHitTest(FullCommitReason source) {
        commit(source, true);
    }

    private boolean commit(FullCommitReason source, boolean completeInitialGeometryNow) {
        Set<Element> roots = drainStyleTransformRoots();
        boolean initialPending = needsInitialFullCommit();
        if (initialPending && completeInitialGeometryNow) {
            // The slicer may be mid-way; finish it in one synchronous commit so a hit test
            // never runs against half-built rects.
            LayoutCommit.commitNow(owner, source == null ? pendingFullReason : source);
            return true;
        }
        if (initialPending && owner.isInitialCommitDeferred() && LayoutCommit.isInitialCommitSlicingEnabled()) {
            // UI opening paths opt in: a tick must not synchronously rebuild the whole document
            // (100ms+ on large pages). Leave the generation pending; prepareRender() advances it
            // one bounded slice per frame. Documents that require geometry right after a tick
            // (tests, headless callers) keep the previous synchronous behaviour.
            owner.markHitTestDirtyAll();
            return false;
        }
        // Tick and render commits need the same targeted path for transform-only work.
        if (roots != null && !initialPending) {
            LayoutCommit.commitTransforms(owner, roots);
        } else {
            LayoutCommit.commit(owner, source == null ? pendingFullReason : source);
        }
        return true;
    }

    public boolean prepareRender() {
        Document document = owner;
        // 首次全量几何提交分片：每个渲染帧开始先重置「已推进」标记，保证本帧最多推进一片。
        LayoutCommit.beginInitialCommitFrame(document);
        // 输入/脚本改 DOM 产生的 RELAYOUT dirty 若尚未提交(布局提交在 20Hz tick,
        // 而绘制是每帧),当前帧绘制会读到未提交的旧几何 —— 光标 caretPosition 返回
        // (0,0) 画在左上角。绘制前强制提交一次 pending 布局工作(无 pending 时廉价)。
        // Pointer state can change between client ticks. Commit only the
        // queued style roots before render work so a class/attribute change
        // cannot populate this frame's Rect cache with the previous style.
        boolean styleChanged = document.commitPendingStyleRecalcForRender();
        // 首次全量几何提交按帧预算分片：样式刷新之后推进一片，并且排在所有布局提交
        // 分支之前 —— 分片的**启动点只有这里**，否则本帧的 commitRenderState() 会先
        // 退回一次性全量提交，那正是要避免的单帧冻结。未完成时下面跳过本帧绘制
        // （几何是半成品），但 finally 清理照常执行。
        boolean initialCommitIncomplete = LayoutCommit.advanceInitialCommitForRender(document);
        boolean styleNeedsGeometryCommit = false;
        if (styleChanged || document.hasPendingRenderState()) {
            // Publish queued changes without committing geometry yet. Motion/scroll below may
            // invalidate it again, so combine both sources into a single geometry commit.
            styleNeedsGeometryCommit = document.commitRenderStateForMotion();
        }
        // CSS transition/animation time is render-frame time, not Minecraft's 20 Hz logic tick.
        // Layout-affecting motion must also refresh committed bounds before this paint pass.
        boolean motionNeedsGeometryCommit = document.stepMotionRender();
        boolean scrollChanged = document.stepScrollRender();
        if (styleNeedsGeometryCommit) {
            Set<Element> transformRoots = drainStyleTransformRoots();
            // A transition can start (or advance) on the same frame as the style
            // change, publishing motion roots of its own. The full commit used to
            // cover them, so they are drained here too.
            Set<Element> motionLayoutRoots = document.drainMotionLayoutRoots();
            Set<Element> motionGeometryRoots = document.drainMotionGeometryRoots();
            if (transformRoots == null
                    || !motionLayoutRoots.isEmpty()
                    || (motionNeedsGeometryCommit && motionGeometryRoots.isEmpty())) {
                LayoutCommit.commit(document, transformRoots == null ? pendingFullReason
                        : !motionLayoutRoots.isEmpty() ? FullCommitReason.MOTION_LAYOUT : FullCommitReason.MOTION_FALLBACK);
            } else {
                // COMMIT_LAYOUT only invalidates committed world transforms, and a
                // transform is computed from the element's own route, so committing
                // the affected subtrees is enough. Transform does not affect layout.
                if (!motionGeometryRoots.isEmpty()) transformRoots.addAll(motionGeometryRoots);
                if (scrollChanged) {
                    // Same split as the scroll branch below: the scroll shifts only
                    // translate the affected subtrees.
                    LayoutCommit.commitScrollTranslation(document, document.getScrollShifts());
                }
                LayoutCommit.commitTransforms(document, transformRoots);
            }
            document.discardScrollShifts();
            document.commitMotionHitTest();
        } else if (scrollChanged) {
            // 滚动只平移子树几何：走 commitScrollTranslation 快速路径，
            // 避免每帧全量 Rect 重建（JFR 里滚动帧的主要分配/耗时来源）。
            Set<Element> layoutRoots = document.drainMotionLayoutRoots();
            Set<Element> geometryRoots = document.drainMotionGeometryRoots();
            if (!layoutRoots.isEmpty() || (motionNeedsGeometryCommit && geometryRoots.isEmpty())) {
                LayoutCommit.commit(document, !layoutRoots.isEmpty() ? FullCommitReason.MOTION_LAYOUT : FullCommitReason.MOTION_FALLBACK);
                document.discardScrollShifts();
            } else {
                LayoutCommit.commitScrollTranslation(document, document.getScrollShifts());
                if (!geometryRoots.isEmpty()) LayoutCommit.commitTransforms(document, geometryRoots);
            }
            document.commitMotionHitTest();
        } else if (motionNeedsGeometryCommit) {
            Set<Element> layoutRoots = document.drainMotionLayoutRoots();
            Set<Element> geometryRoots = document.drainMotionGeometryRoots();
            if (!layoutRoots.isEmpty()) {
                LayoutCommit.commit(document, FullCommitReason.MOTION_LAYOUT);
            } else if (!geometryRoots.isEmpty()) {
                LayoutCommit.commitTransforms(document, geometryRoots);
            } else {
                // Keep the correctness fallback for a future motion source
                // that reports geometry work without publishing a root.
                LayoutCommit.commit(document, FullCommitReason.MOTION_FALLBACK);
            }
            document.commitMotionHitTest();
        }
        return !initialCommitIncomplete;
    }

    public boolean markFirstLayoutCommitForTiming() {
        long generation = owner.getRefreshGeneration();
        if (timedLayoutGeneration == generation) return false;
        timedLayoutGeneration = generation;
        return true;
    }

    /**
     * 当前 refreshGeneration 是否还没做过全量几何提交。
     *
     * <p>与 {@link #markFirstLayoutCommitForTiming()} 不同，这是纯查询：分片路径靠它判断
     * 「这一代还欠一次全量提交」，而不会像后者那样顺手把状态改掉。</p>
     */
    public boolean needsInitialFullCommit() {
        return owner.getRefreshGeneration() != timedLayoutGeneration;
    }

    /**
     * 首次全量提交是否正在分片（已开始、尚未完成）。
     */
    public boolean isInitialCommitSlicing() {
        return initialCommitSlice != null;
    }

    /**
     * 当前分片续跑状态；null 表示没有进行中的分片。
     */
    public InitialCommitSlice getInitialCommitSlice() {
        return initialCommitSlice;
    }

    /**
     * 开始（或换代后重新开始）一代文档的首次全量提交分片。
     */
    public InitialCommitSlice beginInitialCommitSlice() {
        InitialCommitSlice slice = new InitialCommitSlice();
        slice.generation = owner.getRefreshGeneration();
        initialCommitSlice = slice;
        return slice;
    }

    /**
     * 结束分片（完成或换代丢弃），并释放累计的 visited 集合。
     */
    public void endInitialCommitSlice() {
        InitialCommitSlice slice = initialCommitSlice;
        initialCommitSlice = null;
        if (slice != null) slice.visited.clear();
    }

    /**
     * 一次「首次全量几何提交」跨帧分片的续跑状态：游标 + 累计 visited 集合 + 起始纳秒。
     * 每个字段都只在渲染/主线程被 LayoutCommit 读写。
     */
    public static final class InitialCommitSlice {
        /**
         * 分片过程中累计访问过的元素（去重），也是日志里 elements 的来源。
         */
        public final Set<Element> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        /**
         * 第一片开始的纳秒时间戳：日志里的 total 从它算起，是跨帧墙钟时间。
         */
        public final long startNs = System.nanoTime();
        /**
         * paintList 中下一个待提交的下标。
         */
        public int cursor;
        /**
         * 本片所属的 refreshGeneration，换代后不再匹配即丢弃重来。
         */
        public long generation;
        /**
         * 自上一次渲染帧开始（{@link com.sighs.apricityui.layout.LayoutCommit#beginInitialCommitFrame}）
         * 以来是否已推进过一片。渲染门控每帧都会重置它，因此分片实际上是「每个渲染帧一片」。
         */
        public boolean advancedThisFrame;
    }

}
