package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.task.FrameScheduler;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.*;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import com.sighs.apricityui.style.Transform;
import com.sighs.apricityui.parser.CSS;

public class Base {
    public enum RenderPhase {
        SHADOW,
        BODY,
        BORDER
    }

    private static final float DEFAULT_DEPTH_STEP = 0.005f;
    private static final float GLOBAL_DOCUMENT_Z_OFFSET = 1.0f;
    private static final float GUI_ITEM_MODEL_Z_OFFSET = GuiItemDepths.SCREEN_ITEM_MODEL_Z;
    private static final float GUI_ITEM_DECORATION_Z_OFFSET = GuiItemDepths.SCREEN_ITEM_DECORATION_Z;
    private static final float GUI_ITEM_FOREGROUND_Z_OFFSET = GuiItemDepths.SCREEN_ITEM_FOREGROUND_Z;
    private static final float GUI_FLOATING_ITEM_MODEL_Z_OFFSET = GuiItemDepths.SCREEN_FLOATING_ITEM_MODEL_Z;
    private static final float GUI_FLOATING_ITEM_DECORATION_Z_OFFSET = GuiItemDepths.SCREEN_FLOATING_ITEM_DECORATION_Z;
    private static final float FLAT_DOCUMENT_LAYER_STEP = GuiItemDepths.FLAT_DOCUMENT_LAYER_STEP;
    private static final java.util.ArrayDeque<Float> DOCUMENT_Z_OFFSET_STACK = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<GuiItemZ> GUI_ITEM_Z_STACK = new java.util.ArrayDeque<>();

    /**
     * paint 循环里每个 node 每帧一对 pushPose/popPose,各分配 Pose + Matrix4f +
     * Matrix3f(JFR 采样里这是渲染侧最大的分配源)。节点渲染只需"结束后恢复数值",
     * 用池化快照就地保存/恢复 last() 即可,语义与 push/pop 等价——节点内部的
     * push/pop 必须自平衡,这一点两种方案都要求。
     * 渲染限定在 Render thread,静态池无需同步;嵌套文档(PIP/世界窗口)会在
     * 外层 node.render 期间进入内层循环,快照从池里逐个取用,天然支持重入。
     */
    private static final java.util.ArrayDeque<PoseSnapshot> POSE_SNAPSHOT_POOL = new java.util.ArrayDeque<>();

    private static final class PoseSnapshot {
        final Matrix4f pose = new Matrix4f();
        final org.joml.Matrix3f normal = new org.joml.Matrix3f();
    }

    private static PoseSnapshot savePose(PoseStack poseStack) {
        PoseSnapshot snapshot = POSE_SNAPSHOT_POOL.pollLast();
        if (snapshot == null) snapshot = new PoseSnapshot();
        snapshot.pose.set(PoseMatrices.of(poseStack));
        snapshot.normal.set(PoseMatrices.normal(poseStack));
        return snapshot;
    }

    private static void restorePose(PoseStack poseStack, PoseSnapshot snapshot) {
        PoseMatrices.set(poseStack, snapshot.pose);
        PoseMatrices.setNormal(poseStack, snapshot.normal);
        POSE_SNAPSHOT_POOL.addLast(snapshot);
    }
    private static float guiItemModelZ = GUI_ITEM_MODEL_Z_OFFSET;
    private static float guiItemDecorationZ = GUI_ITEM_DECORATION_Z_OFFSET;
    private static final java.util.ArrayDeque<Float> DEPTH_STEP_STACK = new java.util.ArrayDeque<>();
    private static float depthStep = DEFAULT_DEPTH_STEP;
    private static final java.util.ArrayDeque<Boolean> DEPTH_MODE_STACK = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Float> DEPTH_CURSOR_STACK = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Boolean> DEPTH_TEST_STACK = new java.util.ArrayDeque<>();
    private static boolean accumulateDepth = false;
    private static float depthCursor = 0.0f;
    private static boolean depthTestEnabled = true;
    private static float documentZOffset = GLOBAL_DOCUMENT_Z_OFFSET;

    public static void drawOverlayDocument(PoseStack poseStack, Document document) {
        if (document == null) return;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            ApricityViewport viewport = document.getViewport();
            Mask.resetDepth();
            poseStack.pushPose();
            try {
                PoseMatrices.scale2D(poseStack, viewport.renderScale(), viewport.renderScale());
                // 基底必须在 renderScale 应用之后快照：scissor 矩形保持在 CSS
                // 坐标系，只有元素 CSS transform 的局部增量会作用于它。
                Mask.pushScissorScale(viewport.scissorScale(), poseStack);
                try {
                    drawFlatDocumentInContext(poseStack, document, List.of());
                } finally {
                    Mask.popScissorScale();
                }
            } finally {
                poseStack.popPose();
            }
        }
    }

    public static void drawScreenDocument(PoseStack poseStack, Document document) {
        drawScreenDocument(poseStack, document, List.of());
    }

    public static void drawScreenDocument(
            PoseStack poseStack,
            Document document,
            List<? extends RenderNode> overlayNodes
    ) {
        if (document == null) return;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            // screen 直接绘制单个文档时也必须刷新裁剪范围，避免窗口缩放后沿用旧尺寸。
            Mask.resetDepth();
            drawFlatDocumentInContext(poseStack, document, overlayNodes);
        }
    }

    public static void drawDocument(PoseStack poseStack, Document document) {
        if (document == null) return;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            drawDocumentInContext(poseStack, document, List.of());
        }
    }

    /**
     * Draws a manually managed screen document on the same flat layer as its owner.
     * The caller remains responsible for positioning and clipping the embedded surface.
     */
    public static void drawEmbeddedDocument(PoseStack poseStack, Document document, Document ownerDocument) {
        if (document == null) return;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            drawFlatDocumentAtZInContext(
                    poseStack,
                    document,
                    List.of(),
                    resolveEmbeddedDocumentBaseZ(ownerDocument)
            );
        }
    }

    private static void drawFlatDocumentInContext(
            PoseStack poseStack,
            Document document,
            List<? extends RenderNode> overlayNodes
    ) {
        drawFlatDocumentAtZInContext(
                poseStack,
                document,
                overlayNodes,
                resolveFlatDocumentBaseZ(document)
        );
    }

    private static void drawFlatDocumentAtZInContext(
            PoseStack poseStack,
            Document document,
            List<? extends RenderNode> overlayNodes,
            float baseZ
    ) {
        pushDocumentZOffset(baseZ);
        // Item Z values are relative to the already translated document plane.
        // Adding baseZ again would make later documents' items jump two layers.
        pushGuiItemZ(GUI_ITEM_MODEL_Z_OFFSET, GUI_ITEM_DECORATION_Z_OFFSET);
        try {
            drawDocumentInContext(poseStack, document, overlayNodes);
        } finally {
            popGuiItemZ();
            popDocumentZOffset();
        }
    }

    static float resolveFlatDocumentBaseZ(Document document) {
        int layer = 0;
        for (Document candidate : DocumentLayerOrder.backToFront(Document.getAll())) {
            if (!isFlatDocument(candidate)) continue;
            if (candidate == document) {
                return GLOBAL_DOCUMENT_Z_OFFSET + layer * FLAT_DOCUMENT_LAYER_STEP;
            }
            layer++;
        }
        return GLOBAL_DOCUMENT_Z_OFFSET;
    }

    static float resolveEmbeddedDocumentBaseZ(Document ownerDocument) {
        return resolveFlatDocumentBaseZ(ownerDocument);
    }

    public static float getFlatOverlayZ() {
        int layerCount = 0;
        for (Document candidate : Document.getAll()) {
            if (isFlatDocument(candidate)) layerCount++;
        }
        return GLOBAL_DOCUMENT_Z_OFFSET + layerCount * FLAT_DOCUMENT_LAYER_STEP;
    }

    private static boolean isFlatDocument(Document document) {
        return document != null && !document.inWorld && !document.isManuallyRendered();
    }

    /**
     * Draws a document while its document context is active. Keeping this
     * boundary inside Base makes standalone surfaces behave like overlays.
     */
    // enteredSubtrees 集合池：逐帧逐文档 new IdentityHashMap 从空表扩容到全文档
    // 元素数（内部 Object[] 链，JFR 归因约 67MB），池化后 clear 保容复用。
    // 栈式池容许嵌套文档渲染的理论重入。
    private static final java.util.ArrayDeque<Set<Element>> ENTERED_SUBTREES_POOL = new java.util.ArrayDeque<>();

    private static Set<Element> obtainEnteredSubtrees() {
        Set<Element> set = ENTERED_SUBTREES_POOL.poll();
        return set != null ? set : Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static void releaseEnteredSubtrees(Set<Element> set) {
        set.clear();
        if (ENTERED_SUBTREES_POOL.size() < 4) ENTERED_SUBTREES_POOL.push(set);
    }

    private static void drawDocumentInContext(
            PoseStack poseStack,
            Document document,
            List<? extends RenderNode> overlayNodes
    ) {
        long startNs = System.nanoTime();
        AuiRenderService.RenderStateScope renderState = AuiServices.render().pushFilterRenderState();
        if (renderState == null) renderState = AuiRenderService.RenderStateScope.NOOP;
        try {
            // World-window rendering calls drawDocument directly, so drain
            // fenced render tasks (such as texture uploads) at this boundary.
            FrameScheduler.renderBegin();
            RenderBatchStats.beginDocument();
            // 逐字形缓存：在这里重置本帧的光栅预算，并把上一帧的字形存储状态发布给 HUD/日志。
            FontDrawer.beginFrame();
            RectFrameCache.begin();
            TransformFrameCache.begin();
            LayoutMeasureCache.begin();
            StyleFrameCache.begin();
            FilterRenderer.beginFrame();
            poseStack.pushPose();
            FontDrawer.pushDocumentPixelScale(document.getViewport().scissorScale());
            try {
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
                if (styleChanged) {
                    // A newly-created transition must publish its first style before
                    // geometry is committed for this frame.
                    styleNeedsGeometryCommit = document.commitRenderStateForMotion();
                } else if (document.hasPendingRenderState()) {
                    document.commitRenderState();
                }
                // CSS transition/animation time is render-frame time, not Minecraft's 20 Hz logic tick.
                // Layout-affecting motion must also refresh committed bounds before this paint pass.
                boolean motionNeedsGeometryCommit = document.stepMotionRender();
                boolean scrollChanged = document.stepScrollRender();
                if (styleNeedsGeometryCommit) {
                    Set<Element> transformRoots = document.drainStyleTransformRoots();
                    // A transition can start (or advance) on the same frame as the style
                    // change, publishing motion roots of its own. The full commit used to
                    // cover them, so they are drained here too.
                    Set<Element> motionLayoutRoots = document.drainMotionLayoutRoots();
                    Set<Element> motionGeometryRoots = document.drainMotionGeometryRoots();
                    if (transformRoots == null
                            || !motionLayoutRoots.isEmpty()
                            || (motionNeedsGeometryCommit && motionGeometryRoots.isEmpty())) {
                        LayoutCommit.commit(document);
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
                        LayoutCommit.commit(document);
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
                        LayoutCommit.commit(document);
                    } else if (!geometryRoots.isEmpty()) {
                        LayoutCommit.commitTransforms(document, geometryRoots);
                    } else {
                        // Keep the correctness fallback for a future motion source
                        // that reports geometry work without publishing a root.
                        LayoutCommit.commit(document);
                    }
                    document.commitMotionHitTest();
                }
                poseStack.translate(0, 0, documentZOffset);
                // 首次全量几何提交分片尚未完成时，本帧几何是半成品：跳过该文档自身的
                // paint list（上面的提交分支与下面的 finally 清理都照常执行，baseZ 决定的
                // 文档层叠顺序也不受影响）。与文档几何无关的浮空物品 overlay 仍然绘制。
                if (!initialCommitIncomplete) {
                    paintDocumentNodes(poseStack, document);
                }
                // Sample after all render-frame motion/scroll geometry is committed;
                // callbacks are queued and delivered by the next logical tick.
                Window.window.sampleIntersectionObservers(document);
                pushGuiItemZ(GUI_FLOATING_ITEM_MODEL_Z_OFFSET, GUI_FLOATING_ITEM_DECORATION_Z_OFFSET);
                try {
                    for (RenderNode overlayNode : overlayNodes) {
                        if (overlayNode == null) continue;
                        PoseSnapshot snapshot = savePose(poseStack);
                        try {
                            resolvePaintOffset(poseStack, overlayNode);
                            overlayNode.render(poseStack);
                        } finally {
                            restorePose(poseStack, snapshot);
                        }
                    }
                } finally {
                    popGuiItemZ();
                }
            } finally {
                FontDrawer.popDocumentPixelScale();
                poseStack.popPose();
                StyleFrameCache.end();
                LayoutMeasureCache.end();
                TransformFrameCache.end();
                RectFrameCache.end();
                Base.commitDraws();
                FilterRenderer.endFrame();
                RenderBatchStats.endDocument();
                FrameTimingHud.record(System.nanoTime() - startNs);
            }
        } finally {
            renderState.close();
        }
    }

    /**
     * 绘制文档自身的 paint list（含 top-layer 深度隔离与裁剪剔除）。抽成方法是为了让
     * {@link #drawDocumentInContext} 能在首次全量几何提交分片未完成时整块跳过它，
     * 同时保持原有的 enteredSubtrees / topLayerDepthScope 清理路径不变。
     */
    private static void paintDocumentNodes(PoseStack poseStack, Document document) {
        Element skippedSubtree = null;
        Set<Element> enteredSubtrees = obtainEnteredSubtrees();
        Element activeTopLayerRoot = null;
        TopLayerDepthScope topLayerDepthScope = null;
        try {
            List<? extends RenderNode> paintNodes = document.getPaintList();
            for (int pi = 0; pi < paintNodes.size(); pi++) {
                RenderNode node = paintNodes.get(pi);
                Element target = RenderNode.getRenderNodeTarget(node);
                if (skippedSubtree != null) {
                    if (target != null && RenderNode.isSameOrDescendant(target, skippedSubtree)) {
                        continue;
                    }
                    skippedSubtree = null;
                }
                // Clip/filter pushes precede an element's SHADOW node. Cull at
                // the first node so a skipped subtree cannot leave either stack unbalanced.
                if (target != null && enteredSubtrees.add(target) && shouldSkipSubtree(target)) {
                    skippedSubtree = target;
                    continue;
                }

                // A top-layer element is a separate browser surface in both
                // screen/PIP and world-window renders. It must not compete
                // with the depth written by the document beneath it.
                Element topLayerRoot = findTopLayerRoot(target);
                if (topLayerRoot != activeTopLayerRoot) {
                    if (topLayerDepthScope != null) topLayerDepthScope.close();
                    topLayerDepthScope = null;
                    activeTopLayerRoot = topLayerRoot;
                    if (topLayerRoot != null) {
                        topLayerDepthScope = TopLayerDepthScope.open();
                    }
                }

                PoseSnapshot snapshot = savePose(poseStack);
                try {
                    resolvePaintOffset(poseStack, node);
                    node.render(poseStack);
                } finally {
                    restorePose(poseStack, snapshot);
                }
            }
        } finally {
            if (topLayerDepthScope != null) topLayerDepthScope.close();
            releaseEnteredSubtrees(enteredSubtrees);
        }
        if (topLayerDepthScope != null) {
            topLayerDepthScope.close();
        }
    }

    private static Element findTopLayerRoot(Element target) {
        Element current = target;
        while (current != null) {
            if (current.isTopLayer()) return current;
            current = current.parentElement;
        }
        return null;
    }

    /**
     * World-space documents use depth testing to occlude normal content. A
     * top-layer popup is a separate browser surface, however, so leaving the
     * depth test enabled makes it compete with the document plane at far
     * distances. Isolate its batches and paint it in DOM order instead.
     */
    private static final class TopLayerDepthScope {
        private final boolean previousDepthTest;
        private final boolean previousDepthMask;
        private boolean closed;

        private TopLayerDepthScope(boolean previousDepthTest, boolean previousDepthMask) {
            this.previousDepthTest = previousDepthTest;
            this.previousDepthMask = previousDepthMask;
        }

        private static TopLayerDepthScope open() {
            if (!Base.isDepthTestEnabled()) return null;
            Base.commitDraws();

            boolean previousDepthTest = AuiServices.render().isDepthTestEnabled();
            boolean previousDepthMask = AuiServices.render().isDepthMaskEnabled();
            Base.pushDepthTest(false);
            AuiServices.render().disableDepthTest();
            AuiServices.render().setDepthMask(false);
            return new TopLayerDepthScope(previousDepthTest, previousDepthMask);
        }

        private void close() {
            if (closed) return;
            closed = true;
            Base.commitDraws();
            Base.popDepthTest();
            if (previousDepthTest) AuiServices.render().enableDepthTest();
            else AuiServices.render().disableDepthTest();
            AuiServices.render().setDepthMask(previousDepthMask);
        }
    }

    private static boolean shouldSkipSubtree(Element target) {
        if (target == null || target.document == null
                || target == target.document.documentElement
                || target == target.document.body) return false;
        if (RenderNode.shouldSkip(target)) return true;

        AABB currentClip = Mask.getCurrentClip();
        if (!currentClip.isValid()) return false;
        Rect cachedRect = RectFrameCache.get(target);
        if (cachedRect == null) return false;
        return RenderNode.isFullyCulled(cachedRect, currentClip);
    }

    /**
     * Flushes every deferred draw backend before a render-state change, including the
     * loader's shared buffer source. Use this whenever geometry queued after the flush
     * must not be reordered relative to geometry queued before it: clip/mask limits,
     * depth-test toggles and document teardown all depend on that ordering.
     */
    public static void commitDraws() {
        Graph.endBatch();
        ImageDrawer.flushBatch();
        AuiServices.render().flushSharedBuffers();
    }

    /**
     * Flushes only the batches AUI owns (the deferred graph mesh and the texture queue)
     * and leaves the loader's shared buffer source untouched. Callers must guarantee
     * that anything already queued there is either flushed by whatever runs next or
     * carries no ordering requirement. The item paint path is the canonical case: the
     * item backend ends on {@code BufferSource.endBatch()} (shared batches first, then
     * fixed ones), so it preserves paint order without a redundant flush here.
     */
    public static void commitLocalDraws() {
        Graph.endBatch();
        ImageDrawer.flushBatch();
    }

    public static void beginRendering() {
        if (depthTestEnabled) {
            AuiServices.render().enableDepthTest();
            AuiServices.render().setDepthMask(true);
        } else {
            AuiServices.render().disableDepthTest();
            AuiServices.render().setDepthMask(false);
        }
        AuiServices.render().disableCull();
        AuiServices.render().enableBlend();
        AuiServices.render().setBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, // Source Alpha 乘 1
                GL11.GL_ONE_MINUS_SRC_ALPHA // Dest Alpha 乘 (1 - src)
        );
        setPositionColorShader();
    }

    public static void finishRendering() {
        AuiServices.render().enableCull();
        AuiServices.render().disableBlend();
    }

    private static com.sighs.apricityui.spi.MeshBuilder currentMesh;

    /** Returns the active vertex mesh (set by the batch/immediate draw contexts). */
    public static com.sighs.apricityui.spi.MeshBuilder getMesh() {
        return currentMesh;
    }

    public static void setMesh(com.sighs.apricityui.spi.MeshBuilder mesh) {
        currentMesh = mesh;
    }

    public static void applyTransform(PoseStack poseStack, Element element) {
        Matrix4f matrix = prepareWorldTransform(element);
        // 1.20.5 把 PoseStack.mulPoseMatrix 改名成 mulPose，两者的矩阵语义由
        // PoseMatrices 统一，这里不再直接摸 pose。
        PoseMatrices.mulPoseMatrix(poseStack, matrix);
    }

    public static Matrix4f prepareWorldTransform(Element element) {
        // A committed transform may have been produced by a normal screen
        // layout pass. WorldWindow has different translateZ semantics, so it
        // may only reuse transforms computed inside the current flat scope.
        Matrix4f cached = WorldPaintDepth.canReuseCommittedTransforms()
                ? TransformFrameCache.get(element)
                : TransformFrameCache.getFrame(element);
        if (cached != null) return cached;
        Matrix4f matrix = computeWorldTransform(element);
        TransformFrameCache.put(element, matrix);
        return matrix;
    }

    public static Matrix4f createAndCacheWorldTransform(Element element) {
        Matrix4f matrix = computeWorldTransform(element);
        TransformFrameCache.put(element, matrix);
        return matrix;
    }

    /**
     * CSSOM View §7.1.1：{@code getBoundingClientRect()} 返回的是变换后的视觉盒。这里把元素的
     * 未变换边框盒四角经“根 → 自身”的 transform 链映射后取包围盒；链上没有 transform 时返回 null，
     * 调用方直接用未变换的盒，避免给绝大多数元素加上无谓的开销。
     */
    public static double[] visualBounds(Element element, double x, double y, double width, double height) {
        if (!hasVisualTransform(element)) return null;
        // 用绘制路径同一个入口取矩阵：它带逐帧缓存，也保证剔除判定和实际画出来的位置同源。
        Matrix4f matrix = prepareWorldTransform(element);
        Vector3f point = new Vector3f();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            point.set((float) (x + ((corner & 1) == 0 ? 0 : width)),
                    (float) (y + ((corner & 2) == 0 ? 0 : height)), 0f);
            matrix.transformPosition(point);
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        return new double[]{minX, minY, Math.max(0, maxX - minX), Math.max(0, maxY - minY)};
    }

    /** 元素自身或任一祖先是否带 transform。只看已提交的样式快照，不为了量矩形触发样式计算。 */
    public static boolean hasVisualTransform(Element element) {
        for (Element current = element; current != null; current = current.parentElement) {
            List<Transform> cached = current.getRenderer().transform.get();
            if (cached != null) {
                if (!cached.isEmpty()) return true;
                continue;
            }
            Style committed = current.getRenderer().computedStyle.get();
            if (committed == null) continue;
            String raw = committed.transform;
            if (raw == null || raw.isBlank() || "unset".equalsIgnoreCase(raw.trim())
                    || "none".equalsIgnoreCase(raw.trim())) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 把文档坐标点逆变换回元素自己的坐标系（逆掉"根 → 该元素"的 transform 链）。
     *
     * <p>命中测试比的是元素的**布局**盒子（变换前的坐标），光标却是文档坐标；元素落在
     * transform 子树里时两者不在同一坐标系，必须先逆变换回去，否则鼠标停在被缩放/平移过的
     * 画面上会命中别的元素或谁也命不中。链上没有 transform 时直接返回入参，普通元素零开销。</p>
     *
     * @return {@code {x, y}}；矩阵不可逆时返回入参
     */
    public static double[] toLocalPoint(Element element, double x, double y) {
        if (element == null || !hasVisualTransform(element)) return new double[]{x, y};
        Matrix4f inverse = new Matrix4f(prepareWorldTransform(element));
        try {
            inverse.invert();
        } catch (RuntimeException singular) {
            return new double[]{x, y};
        }
        Vector3f point = new Vector3f((float) x, (float) y, 0f);
        inverse.transformPosition(point);
        if (!Float.isFinite(point.x) || !Float.isFinite(point.y)) return new double[]{x, y};
        return new double[]{point.x, point.y};
    }

    private static Matrix4f computeWorldTransform(Element element) {
        Element[] route = element.getRouteArray();
        int routeSize = route.length;

        Matrix4f matrix = new Matrix4f();
        for (int i = routeSize - 1; i >= 0; i--) {
            Element e = route[i];
            Rect rect = Rect.of(e);
            double posX = rect.position.x;
            double posY = rect.position.y;
            Box box = rect.box;
            Size size = rect.getShadowSize();

            double currentAbsX = posX + box.getMarginLeft();
            double currentAbsY = posY + box.getMarginTop();

            List<Transform> functions = prepareTransform(e, size);

            if (!functions.isEmpty()) {
                double w = size.width();
                double h = size.height();
                // transform-origin 默认为中心 (50% 50%)
                float[] origin = resolveTransformOrigin(e.getComputedStyle().transformOrigin, w, h);
                float originX = origin[0];
                float originY = origin[1];

                for (int fi = 0; fi < functions.size(); fi++) {
                    Transform transform = functions.get(fi);
                    if (transform instanceof Transform.Translate t) {
                        // WorldWindow uses the paint-depth cursor for CSS stacking.
                        // Keep translateZ as a stacking-order input, but do not turn
                        // ordinary flat DOM content into physically separated planes.
                        matrix.translate((float) t.x(), (float) t.y(), WorldPaintDepth.effectiveTranslateZ(t.z()));
                    } else if (transform instanceof Transform.Rotate r) {
                        matrix.translate((float) currentAbsX + originX, (float) currentAbsY + originY, 0);
                        if (r.x() != 0) matrix.rotate(new Quaternionf().rotationX((float) Math.toRadians(r.x())));
                        if (r.y() != 0) matrix.rotate(new Quaternionf().rotationY((float) Math.toRadians(r.y())));
                        if (r.z() != 0) matrix.rotate(new Quaternionf().rotationZ((float) Math.toRadians(r.z())));
                        matrix.translate(-((float) currentAbsX + originX), -((float) currentAbsY + originY), 0);
                    } else if (transform instanceof Transform.Scale s) {
                        matrix.translate((float) currentAbsX + originX, (float) currentAbsY + originY, 0);
                        matrix.scale((float) s.x(), (float) s.y(), 1.0f);
                        matrix.translate(-((float) currentAbsX + originX), -((float) currentAbsY + originY), 0);
                    }
                }
            }
        }
        return matrix;
    }

    public static List<Transform> prepareTransform(Element element, Size size) {
        List<Transform> functions = element.getRenderer().transform.get();
        if (functions != null) return functions;
        String cssTransform = element.getComputedStyle().transform;
        functions = Transform.parse(cssTransform, size.width(), size.height());
        element.getRenderer().transform.set(functions);
        return functions;
    }

    private static float[] resolveTransformOrigin(String value, double width, double height) {
        if (value == null || value.isBlank() || "unset".equalsIgnoreCase(value)) {
            return new float[]{(float) (width / 2.0), (float) (height / 2.0)};
        }

        String[] raw = com.sighs.apricityui.layout.Layout.splitTopLevelWhitespace(
                value.trim().toLowerCase(java.util.Locale.ROOT)).toArray(String[]::new);
        String xToken = "50%";
        String yToken = "50%";
        if (raw.length == 1) {
            if (isVerticalOrigin(raw[0])) yToken = raw[0];
            else xToken = raw[0];
        } else {
            xToken = raw[0];
            yToken = raw[1];
            if (isVerticalOrigin(xToken) && !isVerticalOrigin(yToken)) {
                String tmp = xToken;
                xToken = yToken;
                yToken = tmp;
            }
        }

        return new float[]{
                (float) resolveOriginToken(xToken, width, true),
                (float) resolveOriginToken(yToken, height, false)
        };
    }

    private static boolean isVerticalOrigin(String token) {
        return "top".equals(token) || "bottom".equals(token);
    }

    private static double resolveOriginToken(String token, double basis, boolean horizontal) {
        if (token == null || token.isBlank()) return basis / 2.0;
        return switch (token) {
            case "left" -> horizontal ? 0 : basis / 2.0;
            case "right" -> horizontal ? basis : basis / 2.0;
            case "top" -> horizontal ? basis / 2.0 : 0;
            case "bottom" -> horizontal ? basis / 2.0 : basis;
            case "center" -> basis / 2.0;
            default -> Size.resolveLength(token, basis, basis / 2.0);
        };
    }

    public static void resolveOffset(PoseStack poseStack) {
        if (accumulateDepth) {
            // A renderer-internal draw belongs to its enclosing RenderNode.
            // Advancing here would let images and placeholders perturb every
            // subsequent node's CSS paint depth.
            return;
        }
        poseStack.translate(0, 0, depthStep);
    }

    private static void resolvePaintOffset(PoseStack poseStack, RenderNode node) {
        if (!accumulateDepth) {
            poseStack.translate(0, 0, depthStep);
            return;
        }
        poseStack.translate(0, 0, advancePaintDepth(node));
    }

    private static float advancePaintDepth(RenderNode node) {
        depthCursor = WorldPaintDepth.advance(
                depthCursor,
                depthStep,
                node == null || node.advancesPaintDepth()
        );
        return depthCursor;
    }

    /** Moves within the current world paint layer without consuming another paint-list slot. */
    public static void offsetPaintDepth(PoseStack poseStack, float fraction) {
        if (!accumulateDepth || poseStack == null || !Float.isFinite(fraction)) return;
        poseStack.translate(0, 0, depthStep * fraction);
    }

    /**
     * Maps an item z-index into its own paint-node interval rather than treating
     * it as an absolute PoseStack depth. This preserves paint-list ordering
     * while still allowing slots in the same node to opt into a local offset.
     */
    public static void offsetLocalPaintDepth(PoseStack poseStack, int zIndex) {
        if (poseStack == null || zIndex == 0) return;
        float fraction = Math.max(-0.45F, Math.min(0.45F, zIndex / 1000.0F));
        poseStack.translate(0.0F, 0.0F, depthStep * fraction);
    }

    public static void pushDepthStep(float step) {
        DEPTH_STEP_STACK.push(depthStep);
        depthStep = step;
    }

    public static void popDepthStep() {
        if (!DEPTH_STEP_STACK.isEmpty()) {
            depthStep = DEPTH_STEP_STACK.pop();
        } else {
            depthStep = DEFAULT_DEPTH_STEP;
        }
    }

    public static void pushDepthMode(boolean accumulate) {
        DEPTH_MODE_STACK.push(accumulateDepth);
        DEPTH_CURSOR_STACK.push(depthCursor);
        accumulateDepth = accumulate;
        depthCursor = 0.0f;
    }

    /** Overrides the document-level Z offset for a nested render surface. */
    public static void pushDocumentZOffset(float offset) {
        DOCUMENT_Z_OFFSET_STACK.push(documentZOffset);
        documentZOffset = Float.isFinite(offset) ? offset : GLOBAL_DOCUMENT_Z_OFFSET;
    }

    public static void popDocumentZOffset() {
        documentZOffset = DOCUMENT_Z_OFFSET_STACK.isEmpty()
                ? GLOBAL_DOCUMENT_Z_OFFSET
                : DOCUMENT_Z_OFFSET_STACK.pop();
    }

    public static void pushDepthTest(boolean enabled) {
        DEPTH_TEST_STACK.push(depthTestEnabled);
        depthTestEnabled = enabled;
    }

    public static void popDepthTest() {
        depthTestEnabled = DEPTH_TEST_STACK.isEmpty() ? true : DEPTH_TEST_STACK.pop();
    }

    public static boolean isDepthTestEnabled() {
        return depthTestEnabled;
    }

    public static float getGuiItemModelZ() {
        return guiItemModelZ;
    }

    public static float getGuiItemDecorationZ() {
        return guiItemDecorationZ;
    }

    /**
     * Returns the depth reserved for document foreground overlays above item decorations.
     * World documents already place foreground nodes after child item nodes in their
     * accumulated paint interval, so they must not add a screen-space GUI depth bias.
     */
    public static float getGuiItemForegroundZ() {
        return GuiItemDepths.foregroundZ(guiItemDecorationZ, accumulateDepth);
    }

    public static void pushGuiItemZ(float modelZ, float decorationZ) {
        GUI_ITEM_Z_STACK.push(new GuiItemZ(guiItemModelZ, guiItemDecorationZ));
        guiItemModelZ = Float.isFinite(modelZ) ? modelZ : GUI_ITEM_MODEL_Z_OFFSET;
        guiItemDecorationZ = Float.isFinite(decorationZ) ? decorationZ : GUI_ITEM_DECORATION_Z_OFFSET;
    }

    public static void popGuiItemZ() {
        GuiItemZ previous = GUI_ITEM_Z_STACK.poll();
        guiItemModelZ = previous == null ? GUI_ITEM_MODEL_Z_OFFSET : previous.modelZ();
        guiItemDecorationZ = previous == null ? GUI_ITEM_DECORATION_Z_OFFSET : previous.decorationZ();
    }

    private record GuiItemZ(float modelZ, float decorationZ) {
    }

    public static void popDepthMode() {
        if (!DEPTH_MODE_STACK.isEmpty()) {
            accumulateDepth = DEPTH_MODE_STACK.pop();
        } else {
            accumulateDepth = false;
        }
        if (!DEPTH_CURSOR_STACK.isEmpty()) {
            depthCursor = DEPTH_CURSOR_STACK.pop();
        } else {
            depthCursor = 0.0f;
        }
    }

    public static void setProjectionMatrix(Matrix4f matrix) {
        com.sighs.apricityui.spi.AuiServices.render().setProjectionMatrix(matrix);
    }

    public static Matrix4f getProjectionMatrix() {
        return com.sighs.apricityui.spi.AuiServices.render().getProjectionMatrix();
    }

    /** Binds the given shader program (loader's ShaderInstance/ShaderProgram). */
    public static void setShader(Object shader) {
        com.sighs.apricityui.spi.AuiServices.render().setShader(shader);
    }

    public static void setPositionColorShader() {
        com.sighs.apricityui.spi.AuiServices.render().setPositionColorShader();
    }

    public static void setShaderColor(float a, float r, float g, float b) {
        com.sighs.apricityui.spi.AuiServices.render().setShaderColor(a, r, g, b);
    }

    /** Returns the system clipboard text, or an empty string when unavailable. */
    public static String getClipboardText() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.keyboardHandler == null) return "";
        String text = minecraft.keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }

    /** Copies text to the system clipboard (no-op when unavailable). */
    public static void setClipboardText(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.keyboardHandler == null) return;
        minecraft.keyboardHandler.setClipboard(text == null ? "" : text);
    }

}
