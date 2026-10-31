package com.sighs.apricityui.layout;

import com.sighs.apricityui.render.*;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.spi.AuiServices;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.sighs.apricityui.style.Transform;

public final class LayoutCommit {
    // visited 集合池：动画期间 commit 逐帧执行，每帧 new IdentityHashMap 从空表
    // 重新扩容到全文档元素数（内部 Object[] 链，JFR 归因约 81MB）。栈式池
    // 允许理论上的重入（多文档嵌套提交），clear 保容复用。
    private static final java.util.ArrayDeque<Set<Element>> VISITED_POOL = new java.util.ArrayDeque<>();
    /**
     * 开关：首次全量提交分片。优先级：显式系统属性
     * {@code -Dapricityui.layout.sliceInitialCommit}（调试/测试覆盖）> 配置服务
     * {@code AuiConfigService.initialCommitSliceEnabled()} > 内置默认（开启）。
     */
    private static final String SLICE_INITIAL_COMMIT_PROPERTY = "apricityui.layout.sliceInitialCommit";
    /**
     * 首次全量提交每帧的时间预算（毫秒）。优先级：显式系统属性
     * {@code -Dapricityui.layout.initialCommitSliceMs} > 配置服务
     * {@code AuiConfigService.initialCommitSliceMs()} > 内置默认 16ms。
     */
    private static final String INITIAL_COMMIT_SLICE_MS_PROPERTY = "apricityui.layout.initialCommitSliceMs";
    private static final double DEFAULT_INITIAL_COMMIT_SLICE_MS = 16.0d;
    private static final double[] SHIFT_SCRATCH = new double[2];
    private static final Matrix4f IDENTITY = new Matrix4f();
    private LayoutCommit() {
    }

    private static Set<Element> obtainVisited() {
        Set<Element> set = VISITED_POOL.poll();
        return set != null ? set : Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static void releaseVisited(Set<Element> set) {
        set.clear();
        if (VISITED_POOL.size() < 4) VISITED_POOL.push(set);
    }

    public static void commit(Document document) {
        commit(document, RenderBatchStats.FullCommitReason.EXPLICIT);
    }

    public static void commit(Document document, RenderBatchStats.FullCommitReason reason) {
        if (document == null || !document.isActive()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;

        // 首次全量几何提交的**启动权**只属于渲染路径（LayoutLifecycle 的门控）：只有分片已经在跑时，
        // 这里才顺带推进一片（每帧最多一片），避免 tick 路径把剩余工作一次性做完又冻一帧。
        // 没有分片在跑时保持原来的同步全量提交，这样 tick / hitTest / 测试等调用方拿到的
        // 几何仍然是完整的 —— 它们假定「commit 返回后几何必然完整」。
        if (document.getLayout().isInitialCommitSlicing() && document.getLayout().needsInitialFullCommit()) {
            if (isInitialCommitSlicingEnabled()) {
                LayoutLifecycle.InitialCommitSlice slice = document.getLayout().getInitialCommitSlice();
                if (!slice.advancedThisFrame) {
                    commitInitialSlice(document, initialCommitSliceBudgetNs());
                }
                return;
            }
            // 分片中途把开关关掉：丢弃残留状态，退回同步全量提交。
            document.getLayout().endInitialCommitSlice();
        }
        commitFull(document, paintList, reason);
    }

    /**
     * Completes the current generation's geometry synchronously, discarding any running slice.
     * Used by paths that must answer from complete geometry immediately (hit testing).
     */
    public static void commitNow(Document document, RenderBatchStats.FullCommitReason reason) {
        if (document == null || !document.isActive()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;
        if (document.getLayout().isInitialCommitSlicing()) document.getLayout().endInitialCommitSlice();
        commitFull(document, paintList, reason);
        document.markHitTestDirtyAll();
    }

    /**
     * 首次全量提交是否分片。默认开启；系统属性写坏（既不是 true 也不是 false）按开启处理，
     * 配置读取失败也退回内置默认（开启）。
     */
    public static boolean isInitialCommitSlicingEnabled() {
        String raw = System.getProperty(SLICE_INITIAL_COMMIT_PROPERTY);
        if (raw != null) return !"false".equalsIgnoreCase(raw.trim());
        try {
            return AuiServices.config().initialCommitSliceEnabled();
        } catch (RuntimeException | LinkageError unavailableConfig) {
            return true;
        }
    }

    /**
     * 首次全量提交每帧的时间预算；属性/配置缺失、非法或非正数时退回内置默认值 16ms。
     */
    public static long initialCommitSliceBudgetNs() {
        String raw = System.getProperty(INITIAL_COMMIT_SLICE_MS_PROPERTY);
        double ms = Double.NaN;
        if (raw != null && !raw.isBlank()) {
            try {
                ms = Double.parseDouble(raw.trim());
            } catch (NumberFormatException ignored) {
                // 属性写坏只影响预算大小，不影响正确性；退回内置默认。
            }
        } else {
            ms = configuredInitialCommitSliceMs();
        }
        if (!Double.isFinite(ms) || ms <= 0.0d) ms = DEFAULT_INITIAL_COMMIT_SLICE_MS;
        return (long) (ms * 1_000_000.0d);
    }

    /**
     * 配置服务里的每帧预算（毫秒）；读取失败退回内置默认，绝不返回 0 或负数。
     */
    private static double configuredInitialCommitSliceMs() {
        try {
            return AuiServices.config().initialCommitSliceMs();
        } catch (RuntimeException | LinkageError unavailableConfig) {
            return DEFAULT_INITIAL_COMMIT_SLICE_MS;
        }
    }

    /**
     * 绘制侧每帧开始前重置「已推进」标记：每个渲染帧最多推进一片，避免同一帧内 tick 路径
     * 与渲染路径各推进一片、把预算翻倍。
     */
    public static void beginInitialCommitFrame(Document document) {
        if (document == null) return;
        LayoutLifecycle.InitialCommitSlice slice = document.getLayout().getInitialCommitSlice();
        if (slice != null) slice.advancedThisFrame = false;
    }

    /**
     * 渲染帧入口（首次全量提交分片的唯一启动点）：该文档的首次全量提交尚未完成时，
     * 本帧推进一片（若本帧尚未推进过），并在未完成时要求调用方跳过本帧的绘制。
     *
     * @return {@code true} 表示本帧该文档的几何仍是半成品，调用方必须跳过它的绘制
     */
    public static boolean advanceInitialCommitForRender(Document document) {
        if (document == null || !document.isActive()) return false;
        if (!document.getLayout().needsInitialFullCommit()) return false;
        if (!isInitialCommitSlicingEnabled()) return false;
        LayoutLifecycle.InitialCommitSlice slice = document.getLayout().getInitialCommitSlice();
        if (slice == null || !slice.advancedThisFrame) {
            commitInitialSlice(document, initialCommitSliceBudgetNs());
        }
        return document.getLayout().needsInitialFullCommit();
    }

    /**
     * 首次全量几何提交的一片：按版本化的唯一目标序列推进几何与滚动度量。
     * 预算在元素边界检查（单元素的递归测量不可抢占）；完成前重验全部依赖。
     *
     * <p>帧缓存作用域及 committed fallback 门控与同步路径一致，不会跨帧泄漏。
     * 所有目标与滚动度量完成、依赖稳定后，调用方才能绘制完整 paint list。</p>
     *
     * @return {@code true} 表示这一代文档的首次全量提交已经完成（本片可能刚好收尾）
     */
    public static boolean commitInitialSlice(Document document, long budgetNs) {
        if (document == null || !document.isActive()) return true;
        // Include target construction, dependency validation and scrollbar tail work.
        long sliceStartNs = System.nanoTime();
        var opening = document.activePageOpenTiming();
        LayoutLifecycle lifecycle = document.getLayout();
        if (!lifecycle.needsInitialFullCommit()) {
            lifecycle.endInitialCommitSlice();
            return true;
        }
        LayoutLifecycle.InitialCommitSlice slice = lifecycle.getInitialCommitSlice();
        if (slice != null && (slice.generation != document.getRefreshGeneration()
                || slice.paintListRevision != lifecycle.getPaintListRevision())) {
            lifecycle.endInitialCommitSlice();
            slice = null;
        }
        if (slice == null) {
            slice = lifecycle.beginInitialCommitSlice();
            RenderBatchStats.recordFullLayoutCommit(RenderBatchStats.FullCommitReason.INITIAL);
        }
        slice.advancedThisFrame = true;
        RectFrameCache.begin();
        TransformFrameCache.begin();
        RectFrameCache.disableCommittedFallback();
        TransformFrameCache.disableCommittedFallback();
        LayoutMeasureCache.begin();
        try {
            while (slice.cursor < slice.targets.size()) {
                int index = slice.cursor++;
                Element target = slice.targets.get(index);
                commitElement(target);
                slice.displayed[index] = Interaction.isDisplayed(target);
                slice.rectDependencies[index] = target.getLayout().rectDependency(document);
                slice.transformDependencies[index] = target.getLayout().transformDependency(document);
                if (System.nanoTime() - sliceStartNs >= Math.max(0L, budgetNs)) return false;
            }
            // Verify all geometry before the scrollbar-metrics tail, matching synchronous commit order.
            // Metrics may invalidate their own rectangle; the synchronous path leaves the same
            // dirty result for the next normal commit, so do not silently recommit it here.
            for (int i = 0; i < slice.targets.size(); i++) {
                Element target = slice.targets.get(i);
                boolean displayed = Interaction.isDisplayed(target);
                if (target.document != document || displayed != slice.displayed[i]
                        || slice.rectDependencies[i] != target.getLayout().rectDependency(document)
                        || slice.transformDependencies[i] != target.getLayout().transformDependency(document)
                        || (displayed && !target.getLayout().hasCommittedRect(slice.rectDependencies[i]))) {
                    slice.cursor = 0;
                    slice.scrollCursor = 0;
                    return false;
                }
            }
            if (slice.paintListRevision != lifecycle.getPaintListRevision()) return false;
            // Keep the synchronous order: all target geometry first, then scrollbar metrics.
            // Metrics can invalidate a rectangle; that invalidation remains pending for the next commit.
            while (slice.scrollCursor < slice.targets.size()) {
                Element target = slice.targets.get(slice.scrollCursor++);
                if (target.mayRenderScrollbar()) target.commitScrollMetricsAfterLayoutCommit();
                if (System.nanoTime() - sliceStartNs >= Math.max(0L, budgetNs)) return false;
            }
            // Scrollbar visibility can change the gutter, invalidating the scroller and
            // descendants after their first geometry pass. Do not release the paint gate
            // until that geometry has been rebuilt against the committed metrics.
            for (int i = 0; i < slice.targets.size(); i++) {
                Element target = slice.targets.get(i);
                boolean displayed = Interaction.isDisplayed(target);
                if (displayed && !target.getLayout().hasCommittedRect(target.getLayout().rectDependency(document))) {
                    slice.cursor = 0;
                    slice.scrollCursor = 0;
                    return false;
                }
            }
            boolean firstLayout = lifecycle.markFirstLayoutCommitForTiming();
            if (firstLayout) {
                ApricityUI.LOGGER.info("[AUI Layout] first commit path={} generation={} elements={} total={}ms",
                        document.getPath(), document.getRefreshGeneration(), slice.targets.size(),
                        (System.nanoTime() - slice.startNs) / 1_000_000L);
            }
            document.markHitTestDirtyAll();
            lifecycle.endInitialCommitSlice();
            return true;
        } finally {
            LayoutMeasureCache.end();
            TransformFrameCache.enableCommittedFallback();
            RectFrameCache.enableCommittedFallback();
            TransformFrameCache.end();
            RectFrameCache.end();
            if (opening != null) {
                opening.initialLayoutCursor(slice.cursor);
                opening.layoutWork(System.nanoTime() - sliceStartNs, true);
            }
        }
    }

    private static void commitFull(Document document, List<RenderNode> paintList, RenderBatchStats.FullCommitReason reason) {
        boolean firstLayout = document.getLayout().markFirstLayoutCommitForTiming();
        RenderBatchStats.FullCommitReason actualReason = firstLayout ? RenderBatchStats.FullCommitReason.INITIAL : reason;
        RenderBatchStats.recordFullLayoutCommit(actualReason);
        // Opt-in diagnostics; normal frames neither log nor measure this path.
        boolean trace = Boolean.getBoolean("apricityui.layout.traceCommits");
        var opening = document.activePageOpenTiming();
        long startedNs = firstLayout || trace || opening != null ? System.nanoTime() : 0L;
        List<Element> targets = document.getLayout().getCommitTargets();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        RectFrameCache.disableCommittedFallback();
        TransformFrameCache.disableCommittedFallback();
        LayoutMeasureCache.begin();
        try {
            for (Element target : targets) commitElement(target);
            for (Element element : targets) {
                if (element.mayRenderScrollbar()) element.commitScrollMetricsAfterLayoutCommit();
            }
            // Metrics can change scrollbar gutters and invalidate the geometry just
            // committed above. Rebuild affected targets before returning complete geometry.
            boolean geometryInvalidated = false;
            for (Element target : targets) {
                if (Interaction.isDisplayed(target)
                        && !target.getLayout().hasCommittedRect(target.getLayout().rectDependency(document))) {
                    commitElement(target);
                    geometryInvalidated = true;
                }
            }
            if (geometryInvalidated) {
                for (Element element : targets) {
                    if (element.mayRenderScrollbar()) element.commitScrollMetricsAfterLayoutCommit();
                }
            }
        } finally {
            LayoutMeasureCache.end();
            TransformFrameCache.enableCommittedFallback();
            RectFrameCache.enableCommittedFallback();
            TransformFrameCache.end();
            RectFrameCache.end();
            long elapsedNs = startedNs == 0L ? 0L : System.nanoTime() - startedNs;
            if (opening != null) opening.layoutWork(elapsedNs, false);
            if (firstLayout) {
                ApricityUI.LOGGER.info(
                        "[AUI Layout] first commit path={} generation={} elements={} total={}ms",
                        document.getPath(),
                        document.getRefreshGeneration(),
                        targets.size(),
                        (System.nanoTime() - startedNs) / 1_000_000L
                );
            }
            if (trace) {
                ApricityUI.LOGGER.info("[AUI Layout] commit path={} reason={} elements={} elapsed={}ms",
                        document.getPath(), actualReason, targets.size(), (System.nanoTime() - startedNs) / 1_000_000.0d);
            }
        }
    }

    /**
     * 只重建给定根子树的几何。
     *
     * <p>用于 out-of-flow（absolute/fixed）元素的布局动画：它不影响祖先流与兄弟位置，
     * 因此没有必要整页重建。调用方必须保证这些根在 {@code LayoutInvalidation.motionChanged}
     * 里走的是「不影响 flow」的分支（子树缓存已失效、祖先未被失效）。</p>
     */
    public static void commitSubtrees(Document document, Set<Element> roots) {
        if (document == null || !document.isActive() || roots == null || roots.isEmpty()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;

        RenderBatchStats.recordSubtreeCommit();
        Set<Element> visited = obtainVisited();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        try {
            for (int i = 0; i < paintList.size(); i++) {
                Element target = RenderNode.getRenderNodeTarget(paintList.get(i));
                if (target == null || target.document != document || !visited.add(target)) continue;
                if (!isInSubtreeOf(target, roots)) continue;
                commitElement(target);
                if (target.mayRenderScrollbar()) target.commitScrollMetricsAfterLayoutCommit();
            }
        } finally {
            TransformFrameCache.end();
            RectFrameCache.end();
            releaseVisited(visited);
        }
    }

    public static void commitTransforms(Document document, Set<Element> roots) {
        if (document == null || !document.isActive() || roots == null || roots.isEmpty()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;

        RenderBatchStats.recordTransformCommit();
        Set<Element> visited = obtainVisited();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        try {
            for (int i = 0; i < paintList.size(); i++) {
                Element target = RenderNode.getRenderNodeTarget(paintList.get(i));
                if (target == null || target.document != document || !visited.add(target)) continue;
                if (!isInSubtreeOf(target, roots)) continue;
                commitTransformElement(target);
            }
        } finally {
            TransformFrameCache.end();
            RectFrameCache.end();
            releaseVisited(visited);
        }
    }

    private static void commitElement(Element target) {
        RenderNode.ensureRendererLoaded(target);
        if (!Interaction.isDisplayed(target)) return;

        long rectDependency = target.getLayout().rectDependency(target.document);
        if (!target.getLayout().hasCommittedRect(rectDependency)) {
            Rect rect = Rect.createAndCache(target);
            rect.getVisualBounds();
            target.getLayout().commitRect(rect, rectDependency);
        }

        long transformDependency = target.getLayout().transformDependency(target.document);
        if (!target.getLayout().hasCommittedWorldTransform(transformDependency)) {
            try {
                Matrix4f matrix = Base.createAndCacheWorldTransform(target);
                target.getLayout().commitWorldTransform(matrix, transformDependency);
            } catch (NoClassDefFoundError unavailableRenderRuntime) {
                if (!isOptionalRenderDependency(unavailableRenderRuntime)) throw unavailableRenderRuntime;
            }
        }
    }

    /**
     * 滚动帧的几何快速路径：Position.forRender 把每个祖先的 scrollLeft/scrollTop
     * 以 "-= scroll" 形式烘焙进绘制坐标，所以滚动只会让受影响子树的 committed
     * rect 整体平移。这里按各容器的本帧位移平移 rect 并重盖依赖戳，替代全量
     * Rect 重建（Position.forRender + Box.of + Background.of 及配套分配）。
     *
     * <p>安全性由 scroll-free 依赖戳保证：只有"除滚动外其它几何输入全部未变"
     * 的元素才走平移，任何一个 layout/style/transform/viewport 变化都会让
     * 该元素回退到 {@link #commitElement} 完整重建。滚动的内容尺寸不变，
     * 因此不触发 commitScrollMetricsFromLayout。
     */
    public static void commitScrollTranslation(Document document, List<Document.ScrollShift> shifts) {
        if (document == null || !document.isActive() || shifts == null || shifts.isEmpty()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;

        IdentityHashMap<Element, double[]> deltas = new IdentityHashMap<>();
        for (Document.ScrollShift shift : shifts) {
            if (shift.element() == null) continue;
            double[] existing = deltas.get(shift.element());
            if (existing == null) {
                deltas.put(shift.element(), new double[]{shift.dx(), shift.dy()});
            } else {
                existing[0] += shift.dx();
                existing[1] += shift.dy();
            }
        }
        if (deltas.isEmpty()) return;

        Set<Element> visited = obtainVisited();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        try {
            // 容器自身也要按祖先位移平移（嵌套滚动时内层容器随外层内容一起移动）；
            // forRender 跳过自身滚动，所以自身 delta 天然不计入。
            for (Element container : deltas.keySet()) {
                if (container.document != document || !visited.add(container)) continue;
                double[] shift = accumulateShift(container, deltas);
                commitScrollTranslatedElement(container, shift[0], shift[1]);
            }
            for (int i = 0; i < paintList.size(); i++) {
                Element target = RenderNode.getRenderNodeTarget(paintList.get(i));
                if (target == null || target.document != document || !visited.add(target)) continue;
                double[] shift = accumulateShift(target, deltas);
                if (shift[0] == 0 && shift[1] == 0) continue;
                commitScrollTranslatedElement(target, shift[0], shift[1]);
            }
        } finally {
            TransformFrameCache.end();
            RectFrameCache.end();
            releaseVisited(visited);
        }
    }

    /**
     * 沿 route 累加本帧滚动位移，与 Position.forRender 的滚动烘焙一一对应：
     * 每个祖先减去其 scroll（forRender: x -= scrollLeft，滚动增大坐标减小），
     * 遇到 fixed 元素时自身计入、再向上断开。
     */
    private static double[] accumulateShift(Element target, IdentityHashMap<Element, double[]> deltas) {
        double shiftX = 0;
        double shiftY = 0;
        for (Element routeElement : target.getRouteArray()) {
            if (routeElement != target) {
                double[] delta = deltas.get(routeElement);
                if (delta != null) {
                    shiftX -= delta[0];
                    shiftY -= delta[1];
                }
            }
            if ("fixed".equals(routeElement.getComputedStyle().position)) break;
        }
        SHIFT_SCRATCH[0] = shiftX;
        SHIFT_SCRATCH[1] = shiftY;
        return SHIFT_SCRATCH;
    }

    private static void commitScrollTranslatedElement(Element target, double dx, double dy) {
        if (!Interaction.isDisplayed(target)) return;

        Rect rect = target.getLayout().getCommittedRect();
        if (rect == null || !target.getLayout().hasScrollStableCommittedRect()) {
            // 提交之后发生过滚动以外的几何/样式变化，平移不再等价 —— 完整重建。
            commitElement(target);
            return;
        }

        rect.translate(dx, dy);
        target.getLayout().commitRect(rect, target.getLayout().rectDependency(target.document));
        RectFrameCache.put(target, rect);

        Matrix4f committed = target.getLayout().getCommittedWorldTransform();
        if (committed == null) return;
        long transformDependency = target.getLayout().transformDependency(target.document);
        if (target.getLayout().hasCommittedWorldTransform(transformDependency)) return;
        if (target.getLayout().hasScrollStableCommittedWorldTransform() && committed.equals(IDENTITY)) {
            // 平移乘单位阵仍是单位阵，直接重盖戳。
            target.getLayout().commitWorldTransform(committed, transformDependency);
            TransformFrameCache.put(target, committed);
            return;
        }
        try {
            Matrix4f matrix = Base.createAndCacheWorldTransform(target);
            target.getLayout().commitWorldTransform(matrix, transformDependency);
        } catch (NoClassDefFoundError unavailableRenderRuntime) {
            if (!isOptionalRenderDependency(unavailableRenderRuntime)) throw unavailableRenderRuntime;
        }
    }

    private static void commitTransformElement(Element target) {
        RenderNode.ensureRendererLoaded(target);
        if (!Interaction.isDisplayed(target)) return;

        // rectDependency does not mix in transformVersion, so a transform-only change
        // leaves the committed rect valid. If the stamp is stale anyway, some style or
        // layout input changed with it: rebuild the rect here as well instead of
        // letting hit-testing and culling read the old committed rect.
        long rectDependency = target.getLayout().rectDependency(target.document);
        if (!target.getLayout().hasCommittedRect(rectDependency)) {
            commitElement(target);
            return;
        }

        long transformDependency = target.getLayout().transformDependency(target.document);
        if (target.getLayout().hasCommittedWorldTransform(transformDependency)) return;
        try {
            Matrix4f matrix = Base.createAndCacheWorldTransform(target);
            target.getLayout().commitWorldTransform(matrix, transformDependency);
        } catch (NoClassDefFoundError unavailableRenderRuntime) {
            if (!isOptionalRenderDependency(unavailableRenderRuntime)) throw unavailableRenderRuntime;
        }
    }

    private static boolean isInSubtreeOf(Element target, Set<Element> roots) {
        Element current = target;
        while (current != null) {
            if (roots.contains(current)) return true;
            current = current.parentElement;
        }
        return false;
    }

    private static boolean isOptionalRenderDependency(NoClassDefFoundError error) {
        String missing = error.getMessage();
        return missing != null && (missing.startsWith("org/joml/") || missing.startsWith("com/mojang/blaze3d/"));
    }

}
