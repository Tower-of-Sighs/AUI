package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
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
        PoseStack.Pose last = poseStack.last();
        snapshot.pose.set(last.pose());
        snapshot.normal.set(last.normal());
        return snapshot;
    }

    private static void restorePose(PoseStack poseStack, PoseSnapshot snapshot) {
        PoseStack.Pose last = poseStack.last();
        last.pose().set(snapshot.pose);
        last.normal().set(snapshot.normal);
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
            Mask.pushScissorScale(viewport.scissorScale());
            try {
                poseStack.scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
                drawFlatDocumentInContext(poseStack, document, List.of());
            } finally {
                Mask.popScissorScale();
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
            RectFrameCache.begin();
            TransformFrameCache.begin();
            LayoutMeasureCache.begin();
            StyleFrameCache.begin();
            FilterRenderer.beginFrame();
            poseStack.pushPose();
            FontDrawer.pushDocumentPixelScale(document.getViewport().scissorScale());
            try {
                // 输入/脚本改 DOM 产生的 RELAYOUT dirty 若尚未提交(布局提交在 20Hz tick,
                // 而绘制是每帧),当前帧绘制会读到未提交的旧几何 —— 光标 caretPosition 返回
                // (0,0) 画在左上角。绘制前强制提交一次 pending 布局工作(无 pending 时廉价)。
                // Pointer state can change between client ticks. Commit only the
                // queued style roots before render work so a class/attribute change
                // cannot populate this frame's Rect cache with the previous style.
                boolean styleChanged = document.commitPendingStyleRecalcForRender();
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
                if (styleNeedsGeometryCommit || scrollChanged) {
                    LayoutCommit.commit(document);
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
                            Base.resolvePaintOffset(poseStack, node);
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
                    topLayerDepthScope = null;
                }
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
        return !cachedRect.getVisualBounds().intersects(currentClip);
    }

    /** Flushes every deferred draw backend before a render-state change. */
    public static void commitDraws() {
        Graph.endBatch();
        ImageDrawer.flushBatch();
        AuiServices.render().flushSharedBuffers();
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
        // PoseStack.mulPoseMatrix renamed to mulPose in 1.20.5; the JOML
        // equivalent (last().pose().mul) is stable across both.
        poseStack.last().pose().mul(matrix);
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

        String[] raw = value.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+");
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
