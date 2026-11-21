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
    private long paintListRevision;
    private long targetRevision = -1L;
    private java.util.List<Element> commitTargets = java.util.List.of();
    private Set<Element> styleTransformRoots;

    public long getPaintListRevision() {
        return paintListRevision;
    }

    /** Geometry visits each target once, independently of its clip/stacking paint phases. */
    public java.util.List<Element> getCommitTargets() {
        if (targetRevision != paintListRevision) {
            Set<Element> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            ArrayList<Element> targets = new ArrayList<>();
            for (var node : owner.getPaintList()) {
                Element target = com.sighs.apricityui.render.RenderNode.getRenderNodeTarget(node);
                if (target != null && target.document == owner && seen.add(target)) targets.add(target);
            }
            commitTargets = java.util.List.copyOf(targets);
            targetRevision = paintListRevision;
        }
        return commitTargets;
    }

    public LayoutLifecycle(Document owner) {
        this.owner = owner;
    }

    public void reset() {
        paintListRevision++;
        commitTargets = java.util.List.of();
        paintListDirty = false;
        styleTransformRoots = null;
        endInitialCommitSlice();
    }

    public void paintListChanged(boolean hasElements) {
        paintListRevision++;
        paintListDirty = hasElements;
    }

    /** Versions an in-place paint-list reorder without inventing new layout dirty work. */
    public void paintListRevisionChanged() {
        paintListRevision++;
    }

    public boolean hasPendingCommit() {
        return paintListDirty;
    }

    public void updatesFlushed() {
        paintListDirty = false;
    }

    private FullCommitReason pendingFullReason =
            FullCommitReason.EXPLICIT;
    private Set<Element> styleLayoutRoots;

    public boolean collectUpdates(int globalMask, Set<Element> dirtyElements) {
        boolean full = paintListDirty || (globalMask & (Drawer.RELAYOUT | Drawer.COMMIT_LAYOUT)) != 0;
        pendingFullReason = paintListDirty
                ? FullCommitReason.PAINT_LIST
                : (globalMask & Drawer.RELAYOUT) != 0
                ? FullCommitReason.RELAYOUT
                : FullCommitReason.GLOBAL_TRANSFORM;
        boolean needsCommit = full;
        Set<Element> roots = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Element> layoutRoots = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Element element : dirtyElements) {
            if (element == null || !element.isConnected()) continue;
            if (element.hasDirtyFlag(Drawer.RELAYOUT)) {
                needsCommit = true;
                Element isolated = isolatedLayoutRoot(element);
                if (isolated == null) full = true;
                else layoutRoots.add(isolated);
                if (!paintListDirty) pendingFullReason = FullCommitReason.RELAYOUT;
            } else if (element.hasDirtyFlag(Drawer.COMMIT_LAYOUT)) {
                needsCommit = true;
                roots.add(element);
            }
        }
        styleTransformRoots = full ? null : roots;
        styleLayoutRoots = full ? null : layoutRoots;
        return needsCommit;
    }

    private Element isolatedLayoutRoot(Element element) {
        for (Element current = element; current != null; current = current.parentElement) {
            if (current.getRawComputedStyle().isInFlow()) continue;
            Element parent = current.parentElement;
            if (parent != null && parent.getLayout().getCommittedRectIfValid() != null) return current;
            return null;
        }
        return null;
    }

    private Set<Element> drainStyleLayoutRoots() {
        Set<Element> roots = styleLayoutRoots;
        styleLayoutRoots = null;
        return roots;
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
        Set<Element> layoutRoots = drainStyleLayoutRoots();
        boolean initialPending = needsInitialFullCommit();
        if (initialPending && completeInitialGeometryNow) {
            LayoutCommit.commitNow(owner, source == null ? pendingFullReason : source);
            return true;
        }
        if (initialPending && owner.isInitialCommitDeferred() && LayoutCommit.isInitialCommitSlicingEnabled()) {
            owner.markHitTestDirtyAll();
            return false;
        }
        if (roots != null && !initialPending) {
            if (layoutRoots != null && !layoutRoots.isEmpty()) LayoutCommit.commitSubtrees(owner, layoutRoots);
            LayoutCommit.commitTransforms(owner, roots);
        } else {
            LayoutCommit.commit(owner, source == null ? pendingFullReason : source);
        }
        return true;
    }

    public boolean commitFrame(boolean styleNeedsGeometryCommit, boolean motionNeedsGeometryCommit, boolean scrollChanged) {
        Document document = owner;
        if (needsInitialFullCommit() && LayoutCommit.isInitialCommitSlicingEnabled()) {
            boolean incomplete = LayoutCommit.advanceInitialCommitForRender(document);
            // Initial geometry includes all these sources at their current values.
            drainStyleTransformRoots();
            document.drainMotionLayoutRoots();
            document.drainMotionSubtreeLayoutRoots();
            document.drainMotionGeometryRoots();
            document.discardScrollShifts();
            return !incomplete;
        }
        if (styleNeedsGeometryCommit) {
            Set<Element> transformRoots = drainStyleTransformRoots();
            // A transition can start (or advance) on the same frame as the style
            // change, publishing motion roots of its own. The full commit used to
            // cover them, so they are drained here too.
            Set<Element> motionLayoutRoots = document.drainMotionLayoutRoots();
            Set<Element> motionSubtreeRoots = Collections.newSetFromMap(new IdentityHashMap<>());
            motionSubtreeRoots.addAll(document.drainMotionSubtreeLayoutRoots());
            Set<Element> styleSubtreeRoots = drainStyleLayoutRoots();
            if (styleSubtreeRoots != null) motionSubtreeRoots.addAll(styleSubtreeRoots);
            Set<Element> motionGeometryRoots = document.drainMotionGeometryRoots();
            if (transformRoots == null
                    || !motionLayoutRoots.isEmpty()
                    || (motionNeedsGeometryCommit && motionGeometryRoots.isEmpty() && motionSubtreeRoots.isEmpty())) {
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
                // 子树重建使用当前滚动位置，排在平移之后覆盖重叠部分，避免被平移两次。
                if (!motionSubtreeRoots.isEmpty()) LayoutCommit.commitSubtrees(document, motionSubtreeRoots);
                LayoutCommit.commitTransforms(document, transformRoots);
            }
            document.discardScrollShifts();
            document.commitMotionHitTest();
        } else if (scrollChanged) {
            // 滚动只平移子树几何：走 commitScrollTranslation 快速路径，
            // 避免每帧全量 Rect 重建（JFR 里滚动帧的主要分配/耗时来源）。
            Set<Element> layoutRoots = document.drainMotionLayoutRoots();
            Set<Element> subtreeRoots = document.drainMotionSubtreeLayoutRoots();
            Set<Element> geometryRoots = document.drainMotionGeometryRoots();
            if (!layoutRoots.isEmpty() || (motionNeedsGeometryCommit && geometryRoots.isEmpty() && subtreeRoots.isEmpty())) {
                LayoutCommit.commit(document, !layoutRoots.isEmpty() ? FullCommitReason.MOTION_LAYOUT : FullCommitReason.MOTION_FALLBACK);
                document.discardScrollShifts();
            } else {
                LayoutCommit.commitScrollTranslation(document, document.getScrollShifts());
                if (!subtreeRoots.isEmpty()) LayoutCommit.commitSubtrees(document, subtreeRoots);
                if (!geometryRoots.isEmpty()) LayoutCommit.commitTransforms(document, geometryRoots);
            }
            document.commitMotionHitTest();
        } else if (motionNeedsGeometryCommit) {
            Set<Element> layoutRoots = document.drainMotionLayoutRoots();
            Set<Element> subtreeRoots = document.drainMotionSubtreeLayoutRoots();
            Set<Element> geometryRoots = document.drainMotionGeometryRoots();
            if (!layoutRoots.isEmpty()) {
                LayoutCommit.commit(document, FullCommitReason.MOTION_LAYOUT);
            } else if (!subtreeRoots.isEmpty()) {
                // out-of-flow 的布局动画：只重建这些子树，不整页重排。
                LayoutCommit.commitSubtrees(document, subtreeRoots);
            } else if (!geometryRoots.isEmpty()) {
                LayoutCommit.commitTransforms(document, geometryRoots);
            } else {
                // Keep the correctness fallback for a future motion source
                // that reports geometry work without publishing a root.
                LayoutCommit.commit(document, FullCommitReason.MOTION_FALLBACK);
            }
            document.commitMotionHitTest();
        }
        return true;
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

    public InitialCommitSlice beginInitialCommitSlice() {
        InitialCommitSlice slice = new InitialCommitSlice(getCommitTargets(), paintListRevision);
        slice.generation = owner.getRefreshGeneration();
        initialCommitSlice = slice;
        return slice;
    }

    public void endInitialCommitSlice() {
        initialCommitSlice = null;
    }

    /** Main-thread state bound to a versioned, unique geometry sequence, never a paint cursor. */
    public static final class InitialCommitSlice {
        public final java.util.List<Element> targets;
        public final long paintListRevision;
        public final long[] rectDependencies;
        public final long[] transformDependencies;
        public final boolean[] displayed;
        public final long startNs = System.nanoTime();
        public int cursor;
        public int scrollCursor;
        public int scrollPrepareCursor;
        public int textCursor;
        final ScrollLayout.Pass scrollPass = new ScrollLayout.Pass();
        public boolean gutterChanged;
        java.util.List<Element> scrollers;
        public long generation;
        public boolean advancedThisFrame;

        InitialCommitSlice(java.util.List<Element> targets, long paintListRevision) {
            this.targets = targets;
            this.paintListRevision = paintListRevision;
            rectDependencies = new long[targets.size()];
            transformDependencies = new long[targets.size()];
            displayed = new boolean[targets.size()];
        }
    }

}
