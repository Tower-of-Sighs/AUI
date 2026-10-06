package com.sighs.apricityui.render;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.spi.AuiServices;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import com.sighs.apricityui.style.Transform;

public final class LayoutCommit {
    private LayoutCommit() {
    }

    // visited 集合池：动画期间 commit 逐帧执行，每帧 new IdentityHashMap 从空表
    // 重新扩容到全文档元素数（内部 Object[] 链，JFR 归因约 81MB）。栈式池
    // 允许理论上的重入（多文档嵌套提交），clear 保容复用。
    private static final java.util.ArrayDeque<Set<Element>> VISITED_POOL = new java.util.ArrayDeque<>();

    private static Set<Element> obtainVisited() {
        Set<Element> set = VISITED_POOL.poll();
        return set != null ? set : Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static void releaseVisited(Set<Element> set) {
        set.clear();
        if (VISITED_POOL.size() < 4) VISITED_POOL.push(set);
    }

    public static void commit(Document document) {
        if (document == null || !document.isActive()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;

        // 首次全量几何提交的**启动权**只属于渲染路径（Base 的门控）：只有分片已经在跑时，
        // 这里才顺带推进一片（每帧最多一片），避免 tick 路径把剩余工作一次性做完又冻一帧。
        // 没有分片在跑时保持原来的同步全量提交，这样 tick / hitTest / 测试等调用方拿到的
        // 几何仍然是完整的 —— 它们假定「commit 返回后几何必然完整」。
        if (document.isInitialCommitSlicing() && document.needsInitialFullCommit()) {
            if (isInitialCommitSlicingEnabled()) {
                Document.InitialCommitSlice slice = document.getInitialCommitSlice();
                if (!slice.advancedThisFrame) {
                    commitInitialSlice(document, initialCommitSliceBudgetNs());
                }
                return;
            }
            // 分片中途把开关关掉：丢弃残留状态，退回同步全量提交。
            document.endInitialCommitSlice();
        }
        commitFull(document, paintList);
    }

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

    /** 首次全量提交每帧的时间预算；属性/配置缺失、非法或非正数时退回内置默认值 8ms。 */
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

    /** 配置服务里的每帧预算（毫秒）；读取失败退回内置默认，绝不返回 0 或负数。 */
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
        Document.InitialCommitSlice slice = document.getInitialCommitSlice();
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
        if (!document.needsInitialFullCommit()) return false;
        if (!isInitialCommitSlicingEnabled()) return false;
        Document.InitialCommitSlice slice = document.getInitialCommitSlice();
        if (slice == null || !slice.advancedThisFrame) {
            commitInitialSlice(document, initialCommitSliceBudgetNs());
        }
        return document.needsInitialFullCommit();
    }

    /**
     * 首次全量几何提交的一片：在 {@code budgetNs} 预算内提交 paintList 上尽可能多的元素，
     * 预算耗尽就停下；游标、visited 集合、起始纳秒都挂在 Document 上，下一帧继续。
     *
     * <p>续跑状态每片都用与同步路径相同的缓存作用域包裹，所以 {@link #commitElement} 的
     * 语义（含 committed fallback 的关闭）与一次性全量提交完全一致。</p>
     *
     * @return {@code true} 表示这一代文档的首次全量提交已经完成（本片可能刚好收尾）
     */
    public static boolean commitInitialSlice(Document document, long budgetNs) {
        if (document == null || !document.isActive()) return true;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return true;

        long generation = document.getRefreshGeneration();
        if (!document.needsInitialFullCommit()) {
            // 这一代已经全量提交过（例如中途把开关关掉），丢弃可能残留的分片状态。
            document.endInitialCommitSlice();
            return true;
        }
        Document.InitialCommitSlice slice = document.getInitialCommitSlice();
        if (slice != null && slice.generation != generation) {
            // refresh() 换代：按新一代重新开始（beginRefreshLifecycle 已经重置过一次，
            // 这里兜底，防止状态被别处保留下来）。
            document.endInitialCommitSlice();
            slice = null;
        }
        if (slice == null) {
            slice = document.beginInitialCommitSlice();
            RenderBatchStats.recordFullLayoutCommit();
        }
        slice.advancedThisFrame = true;

        long deadlineNs = System.nanoTime() + Math.max(0L, budgetNs);
        Set<Element> visited = slice.visited;
        int cursor = slice.cursor;
        RectFrameCache.begin();
        TransformFrameCache.begin();
        RectFrameCache.disableCommittedFallback();
        TransformFrameCache.disableCommittedFallback();
        LayoutMeasureCache.begin();
        try {
            for (; cursor < paintList.size(); cursor++) {
                Element target = RenderNode.getRenderNodeTarget(paintList.get(cursor));
                if (target == null || target.document != document || !visited.add(target)) continue;
                commitElement(target);
                if (System.nanoTime() >= deadlineNs) {
                    cursor++;
                    break;
                }
            }
        } finally {
            LayoutMeasureCache.end();
            TransformFrameCache.enableCommittedFallback();
            RectFrameCache.enableCommittedFallback();
            TransformFrameCache.end();
            RectFrameCache.end();
        }
        slice.cursor = cursor;
        if (cursor < paintList.size()) return false;

        // 完成：滚动条度量 + 与同步路径完全相同的日志（同一代只打一条）。
        for (Element element : visited) {
            if (element.mayRenderScrollbar()) element.commitScrollMetricsAfterLayoutCommit();
        }
        boolean firstLayout = document.markFirstLayoutCommitForTiming();
        if (firstLayout) {
            ApricityUI.LOGGER.info(
                    "[AUI Layout] first commit path={} generation={} elements={} total={}ms",
                    document.getPath(),
                    document.getRefreshGeneration(),
                    visited.size(),
                    (System.nanoTime() - slice.startNs) / 1_000_000L
            );
        }
        // 分片期间几何是半成品，命中缓存可能已经按部分几何重建过：完成时整体失效，
        // 下一次命中测试按完整几何重建，不会留下缺项。
        document.markHitTestDirtyAll();
        document.endInitialCommitSlice();
        return true;
    }

    private static void commitFull(Document document, List<RenderNode> paintList) {
        RenderBatchStats.recordFullLayoutCommit();
        boolean firstLayout = document.markFirstLayoutCommitForTiming();
        long startedNs = firstLayout ? System.nanoTime() : 0L;
        Set<Element> visited = obtainVisited();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        RectFrameCache.disableCommittedFallback();
        TransformFrameCache.disableCommittedFallback();
        LayoutMeasureCache.begin();
        try {
            for (int i = 0; i < paintList.size(); i++) {
                Element target = RenderNode.getRenderNodeTarget(paintList.get(i));
                if (target == null || target.document != document || !visited.add(target)) continue;
                commitElement(target);
            }
            for (Element element : visited) {
                if (element.mayRenderScrollbar()) element.commitScrollMetricsAfterLayoutCommit();
            }
        } finally {
            LayoutMeasureCache.end();
            TransformFrameCache.enableCommittedFallback();
            RectFrameCache.enableCommittedFallback();
            TransformFrameCache.end();
            RectFrameCache.end();
            if (firstLayout) {
                ApricityUI.LOGGER.info(
                        "[AUI Layout] first commit path={} generation={} elements={} total={}ms",
                        document.getPath(),
                        document.getRefreshGeneration(),
                        visited.size(),
                        (System.nanoTime() - startedNs) / 1_000_000L
                );
            }
            releaseVisited(visited);
        }
    }

    public static void commitTransforms(Document document, Set<Element> roots) {
        if (document == null || !document.isActive() || roots == null || roots.isEmpty()) return;

        RenderBatchStats.recordTransformCommit();
        Set<Element> visited = obtainVisited();
        ArrayDeque<Element> pending = new ArrayDeque<>();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        try {
            for (Element root : roots) {
                if (root != null && root.document == document) pending.addLast(root);
            }
            while (!pending.isEmpty()) {
                Element target = pending.removeFirst();
                if (!visited.add(target) || !Interaction.isDisplayed(target)) continue;
                commitTransformElement(target);
                List<Element> children = target.getRenderChildren();
                for (int index = 0; index < children.size(); index++) {
                    Element child = children.get(index);
                    if (child != null && child.document == document) pending.addLast(child);
                }
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

        long rectDependency = target.getRenderer().rectDependency(target.document);
        if (!target.getRenderer().hasCommittedRect(rectDependency)) {
            Rect rect = Rect.createAndCache(target);
            rect.getVisualBounds();
            target.getRenderer().commitRect(rect, rectDependency);
        }

        long transformDependency = target.getRenderer().transformDependency(target.document);
        if (!target.getRenderer().hasCommittedWorldTransform(transformDependency)) {
            try {
                Matrix4f matrix = Base.createAndCacheWorldTransform(target);
                target.getRenderer().commitWorldTransform(matrix, transformDependency);
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

    private static final double[] SHIFT_SCRATCH = new double[2];
    private static final Matrix4f IDENTITY = new Matrix4f();

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

        Rect rect = target.getRenderer().getCommittedRect();
        if (rect == null || !target.getRenderer().hasScrollStableCommittedRect()) {
            // 提交之后发生过滚动以外的几何/样式变化，平移不再等价 —— 完整重建。
            commitElement(target);
            return;
        }

        if (Position.usesDevicePixelSnappedPaint(target)) {
            Position snapped = Position.forRender(target);
            dx = snapped.x - rect.position.x;
            dy = snapped.y - rect.position.y;
        }

        rect.translate(dx, dy);
        target.getRenderer().commitRect(rect, target.getRenderer().rectDependency(target.document));
        RectFrameCache.put(target, rect);

        Matrix4f committed = target.getRenderer().getCommittedWorldTransform();
        if (committed == null) return;
        long transformDependency = target.getRenderer().transformDependency(target.document);
        if (target.getRenderer().hasCommittedWorldTransform(transformDependency)) return;
        if (target.getRenderer().hasScrollStableCommittedWorldTransform() && committed.equals(IDENTITY)) {
            // 平移乘单位阵仍是单位阵，直接重盖戳。
            target.getRenderer().commitWorldTransform(committed, transformDependency);
            TransformFrameCache.put(target, committed);
            return;
        }
        try {
            Matrix4f matrix = Base.createAndCacheWorldTransform(target);
            target.getRenderer().commitWorldTransform(matrix, transformDependency);
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
        long rectDependency = target.getRenderer().rectDependency(target.document);
        if (!target.getRenderer().hasCommittedRect(rectDependency)) {
            commitElement(target);
            return;
        }

        long transformDependency = target.getRenderer().transformDependency(target.document);
        if (target.getRenderer().hasCommittedWorldTransform(transformDependency)) return;
        try {
            Matrix4f matrix = Base.createAndCacheWorldTransform(target);
            target.getRenderer().commitWorldTransform(matrix, transformDependency);
        } catch (NoClassDefFoundError unavailableRenderRuntime) {
            if (!isOptionalRenderDependency(unavailableRenderRuntime)) throw unavailableRenderRuntime;
        }
    }

    private static boolean isOptionalRenderDependency(NoClassDefFoundError error) {
        String missing = error.getMessage();
        return missing != null && (missing.startsWith("org/joml/") || missing.startsWith("com/mojang/blaze3d/"));
    }

}
