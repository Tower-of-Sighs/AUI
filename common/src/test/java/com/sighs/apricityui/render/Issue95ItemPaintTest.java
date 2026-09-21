package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.spi.AuiItemRenderService;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue#95 回归：每个 {@code <item>} 的绘制节点不再为自身刷新一次加载器共享缓冲。
 *
 * <p>背景：{@code RenderNode.ItemNode.render} 原先在物品后端绘制前调用
 * {@code Base.commitDraws()}，即每画一个物品就 flush 一次平台共享的
 * {@code MultiBufferSource}。平台物品后端自己会在提交前刷新它（例如 1.21.1 的
 * {@code BufferSource.endBatch()} 先刷 shared 再刷 fixed），因此 common 侧这次
 * 刷新是纯冗余的，代价随物品数量线性增长。</p>
 *
 * <p>本测试用代理 {@link AuiRenderService} 数 {@code flushSharedBuffers} 调用次数，
 * 用假 {@link AuiItemRenderService} 数物品绘制次数，并把两边的事件记进同一个序列，
 * 断言三件事：</p>
 * <ul>
 *   <li>纯 item 文档的共享缓冲刷新次数不随 item 数量线性增长（上界 1）；</li>
 *   <li>夹在 item 之间、真正产生几何的节点（overflow:hidden 的 div）仍然刷新，
 *       且它画的几何必须在该 div 之后的 item 绘制之前提交；</li>
 *   <li>{@link RenderBatchStats} 的 shared/item 计数读写语义正确。</li>
 * </ul>
 *
 * <p>物品栈是加载器类型，这里用不透明 token 代替；{@code ItemNode.positioned}
 * 不接受 DOM 元素，正好绕开布局之外的加载器依赖（与 OverflowClipRenderWalkTest
 * 反射访问 MC/JOML 类型的手法一致）。</p>
 */
class Issue95ItemPaintTest {

    private static final int ITEM_COUNT = 8;
    private static final Object ITEM_STACK = new Object();
    private static final String EVENT_ITEM = "item";
    private static final String EVENT_MESH = "mesh";

    // ------------------------------------------------------------------
    // 反射访问 MC/JOML 类型（只存在于测试运行时 classpath）
    // ------------------------------------------------------------------

    private static Class<?> poseStackClass() {
        try {
            return Class.forName("com.mojang.blaze3d.vertex.PoseStack");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Class<?> matrix4fClass() {
        try {
            return Class.forName("org.joml.Matrix4f");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Class<?> matrix4fcClass() {
        try {
            return Class.forName("org.joml.Matrix4fc");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newPoseStack() {
        try {
            return poseStackClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object lastPoseMatrix(Object poseStack) {
        try {
            Object pose = poseStackClass().getMethod("last").invoke(poseStack);
            return pose.getClass().getMethod("pose").invoke(pose);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object copyMatrix(Object matrix) {
        try {
            Object copy = matrix4fClass().getDeclaredConstructor().newInstance();
            setMatrix(copy, matrix);
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setMatrix(Object matrix, Object source) {
        try {
            matrix4fClass().getMethod("set", matrix4fcClass()).invoke(matrix, source);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------
    // 记录用假后端
    // ------------------------------------------------------------------

    private static final class RenderRecorder {
        final List<String> events = new ArrayList<>();
        int sharedFlushes;
        int itemDraws;

        AuiRenderService install() {
            return (AuiRenderService) Proxy.newProxyInstance(
                    AuiRenderService.class.getClassLoader(),
                    new Class<?>[]{AuiRenderService.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "flushSharedBuffers":
                                sharedFlushes++;
                                events.add("shared");
                                return null;
                            case "beginMesh":
                                // 只记录“提交过一次网格”，不解析几何内容
                                return MeshBuilder.of(new Object());
                            case "submitMesh":
                                events.add(EVENT_MESH);
                                return null;
                            case "emitVertex":
                            case "emitVertexUV":
                                return null;
                            case "getProjectionMatrix":
                                return matrix4fClass().getDeclaredConstructor().newInstance();
                            case "beginTextureBatch":
                                return new Object();
                            case "flushTextureBatch":
                                return null;
                            case "getGLVersionString":
                                return "";
                            case "isOnRenderThread":
                                return true;
                            case "recordRenderCall":
                                ((Runnable) args[0]).run();
                                return null;
                            default:
                                Class<?> type = method.getReturnType();
                                if (type == boolean.class) return false;
                                if (type == int.class) return 0;
                                if (type == float.class) return 0f;
                                return null;
                        }
                    });
        }

        /** 第 {@code itemIndex} 个 item（从 1 开始）绘制之前，已经提交了几次网格。 */
        int meshesBeforeItem(int itemIndex) {
            int seen = 0;
            int meshes = 0;
            for (String event : events) {
                if (EVENT_ITEM.equals(event)) {
                    seen++;
                    if (seen == itemIndex) return meshes;
                } else if (EVENT_MESH.equals(event)) {
                    meshes++;
                }
            }
            return meshes;
        }
    }

    /** 假物品后端：物品栈是不透明 token，不需要任何 MC 类型，只数调用次数。 */
    private static AuiItemRenderService recordingItems(RenderRecorder recorder) {
        return request -> {
            recorder.itemDraws++;
            recorder.events.add(EVENT_ITEM);
        };
    }

    // ------------------------------------------------------------------
    // paint list 遍历（复刻 Base.drawDocumentInContext 的逐节点 pose 保存/还原）
    // ------------------------------------------------------------------

    private static final Method SHOULD_SKIP_SUBTREE = findShouldSkipSubtree();
    private static final Method NODE_RENDER = findNodeRender();

    private static Method findShouldSkipSubtree() {
        try {
            Method m = Base.class.getDeclaredMethod("shouldSkipSubtree", Element.class);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Method findNodeRender() {
        try {
            return RenderNode.class.getMethod("render", poseStackClass());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** {@code Base.commitLocalDraws()}：只落地本地批次、不触碰共享缓冲的那一半。 */
    private static Method findCommitLocalDraws() {
        try {
            Method m = Base.class.getDeclaredMethod("commitLocalDraws");
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static void walkPaintList(Document document, Object poseStack) {
        RectFrameCache.begin();
        TransformFrameCache.begin();
        Mask.resetDepth(800, 600);
        try {
            Element skippedSubtree = null;
            Set<Element> enteredSubtrees = new HashSet<>();
            for (RenderNode node : document.getPaintList()) {
                Element target = RenderNode.getRenderNodeTarget(node);
                if (skippedSubtree != null) {
                    if (target != null && RenderNode.isSameOrDescendant(target, skippedSubtree)) {
                        continue;
                    }
                    skippedSubtree = null;
                }
                if (target != null && enteredSubtrees.add(target)) {
                    boolean skip;
                    try {
                        skip = (boolean) SHOULD_SKIP_SUBTREE.invoke(null, target);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                    if (skip) {
                        skippedSubtree = target;
                        continue;
                    }
                }
                Object saved = copyMatrix(lastPoseMatrix(poseStack));
                try {
                    NODE_RENDER.invoke(node, poseStack);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("render failed for " + node, e);
                } finally {
                    setMatrix(lastPoseMatrix(poseStack), saved);
                }
            }
        } finally {
            TransformFrameCache.end();
            RectFrameCache.end();
        }
    }

    // ------------------------------------------------------------------
    // 场景：两份手工 paint list
    // ------------------------------------------------------------------

    private static RenderNode item() {
        return RenderNode.ItemNode.positioned(() -> ITEM_STACK, 8.0, 8.0, 1.0, 0, false);
    }

    /** 纯 item 文档：paint list 就是 N 个连续 ItemNode。 */
    private static Document bareItemDocument() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 800px; height: 600px;");
        List<RenderNode> paintList = document.getPaintList();
        paintList.clear();
        for (int i = 0; i < ITEM_COUNT; i++) {
            paintList.add(item());
        }
        return document;
    }

    /** 一半 item 之后插一个 overflow:hidden 且有背景色的 div（会真的画几何）。 */
    private static Document maskedItemDocument() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 800px; height: 600px;");
        Element box = document.createElement("div");
        box.setAttribute("style", "width: 120px; height: 120px; overflow: hidden;"
                + " background-color: #123456;");
        document.body.appendChild(box);
        document.commitRenderState();

        List<RenderNode> paintList = document.getPaintList();
        paintList.clear();
        int half = ITEM_COUNT / 2;
        for (int i = 0; i < half; i++) {
            paintList.add(item());
        }
        // overflow:hidden 的裁剪边界：push/pop 前后都必须让已画的几何落地
        paintList.add(new RenderNode.MaskPushNode(box));
        paintList.add(new RenderNode.ElementPhaseNode(box, Base.RenderPhase.BODY));
        paintList.add(new RenderNode.MaskPopNode(box));
        for (int i = half; i < ITEM_COUNT; i++) {
            paintList.add(item());
        }
        return document;
    }

    /** 注入假后端遍历一遍，返回统计结果。 */
    private static RenderRecorder runWalk(Document document) {
        return runWalk(document, false);
    }

    /**
     * {@code sinkScissor} 打开 {@link Mask#testScissorSink}：无头 JVM 里
     * {@code Minecraft.getInstance()} 为 null，只有这个测试钩子能让真正的
     * 裁剪遮罩（MaskPush/PopNode）走通，同时不产生任何 GL 调用。
     */
    private static RenderRecorder runWalk(Document document, boolean sinkScissor) {
        RenderRecorder recorder = new RenderRecorder();
        AuiRenderService previousRender = AuiServices.render();
        AuiItemRenderService previousItems = AuiServices.items();
        AuiServices.setRender(recorder.install());
        AuiServices.setItems(recordingItems(recorder));
        if (sinkScissor) Mask.testScissorSink = rect -> {
        };
        try {
            walkPaintList(document, newPoseStack());
        } finally {
            if (sinkScissor) Mask.testScissorSink = null;
            AuiServices.setRender(previousRender);
            AuiServices.setItems(previousItems);
        }
        return recorder;
    }

    // ------------------------------------------------------------------
    // 断言
    // ------------------------------------------------------------------

    @Test
    void sharedBufferFlushDoesNotScaleWithItemCount() {
        RenderRecorder recorder = runWalk(bareItemDocument());

        assertEquals(ITEM_COUNT, recorder.itemDraws,
                "每个 item 都必须真的走到平台物品后端，否则断言没有意义");
        assertTrue(recorder.sharedFlushes <= 1,
                "纯 item 文档的共享缓冲刷新次数必须与 item 数量无关（上界 1），实测 "
                        + recorder.sharedFlushes + " 次 / " + ITEM_COUNT + " 个 item");
    }

    @Test
    void nonItemGeometryStillFlushesAndLandsBeforeLaterItems() {
        RenderRecorder bare = runWalk(bareItemDocument());
        RenderRecorder masked = runWalk(maskedItemDocument(), true);

        assertEquals(ITEM_COUNT, masked.itemDraws, "带 div 的文档同样要画满所有 item");
        assertTrue(masked.sharedFlushes > bare.sharedFlushes,
                "真正产生几何的非 item 节点（overflow:hidden 的裁剪边界）必须仍然刷新共享缓冲："
                        + "纯 item=" + bare.sharedFlushes + " 次，带 div=" + masked.sharedFlushes + " 次");
        assertTrue(masked.meshesBeforeItem(ITEM_COUNT / 2 + 1) >= 1,
                "div 画的几何必须在它之后的 item 绘制之前提交，否则后画的背景会盖住已画好的物品");
        assertEquals(0, bare.meshesBeforeItem(ITEM_COUNT / 2 + 1),
                "纯 item 文档不该产生 AUI 几何");
    }

    @Test
    void commitLocalDrawsSkipsSharedBuffersWhileCommitDrawsFlushesThem() throws Exception {
        Method commitLocalDraws = findCommitLocalDraws();
        assertNotNull(commitLocalDraws,
                "Base.commitLocalDraws() 必须存在：ItemNode 的绘制路径靠它落地本地批次而不动共享缓冲");

        RenderRecorder recorder = new RenderRecorder();
        AuiRenderService previousRender = AuiServices.render();
        AuiServices.setRender(recorder.install());
        try {
            commitLocalDraws.invoke(null);
            assertEquals(0, recorder.sharedFlushes,
                    "commitLocalDraws() 只能落地本地（AUI 几何/贴图）批次，不得触碰共享缓冲");
            Base.commitDraws();
            assertEquals(1, recorder.sharedFlushes,
                    "commitDraws() 仍必须在渲染状态变更前刷新共享缓冲");
        } finally {
            AuiServices.setRender(previousRender);
        }
    }

    @Test
    void batchStatsExposeSharedFlushesAndItemTimings() {
        RenderBatchStats.beginDocument();
        RenderBatchStats.beginFrame();
        RenderBatchStats.recordSharedFlush();
        RenderBatchStats.recordSharedFlush();
        RenderBatchStats.recordItemDraw(1_000_000L);
        RenderBatchStats.recordItemDraw(3_000_000L);
        RenderBatchStats.recordItemDraw(2_000_000L);
        RenderBatchStats.endFrame();

        assertEquals(2, RenderBatchStats.lastSharedFlushes(), "帧内共享缓冲刷新应累加");
        assertEquals(3, RenderBatchStats.lastItemDraws(), "帧内物品绘制次数应累加");
        assertEquals(6_000_000L, RenderBatchStats.lastItemNanos(), "帧内物品耗时应累加");
        assertEquals(3_000_000L, RenderBatchStats.lastItemMaxNanos(), "单物品最大耗时应取帧内最大值");

        // 非正耗时不计入耗时统计，但仍算一次绘制
        RenderBatchStats.beginFrame();
        RenderBatchStats.recordItemDraw(0L);
        RenderBatchStats.recordItemDraw(5_000_000L);
        RenderBatchStats.endFrame();
        assertEquals(2, RenderBatchStats.lastItemDraws(), "非正耗时也是一次绘制");
        assertEquals(5_000_000L, RenderBatchStats.lastItemNanos(), "非正耗时不参与累加");
        assertEquals(5_000_000L, RenderBatchStats.lastItemMaxNanos(), "非正耗时不参与取最大值");

        // last* 是 endFrame 的快照：帧内累加期间仍保持上一帧的值，只有 endFrame 才改写
        assertEquals(0, RenderBatchStats.lastSharedFlushes(), "上一帧没有共享缓冲刷新");
        RenderBatchStats.beginFrame();
        RenderBatchStats.recordItemDraw(1_500_000L);
        RenderBatchStats.recordSharedFlush();
        assertEquals(2, RenderBatchStats.lastItemDraws(), "帧内数据在 endFrame 之前不得改写快照");
        assertEquals(0, RenderBatchStats.lastSharedFlushes(), "帧内数据在 endFrame 之前不得改写快照");
        RenderBatchStats.endFrame();
        assertEquals(1, RenderBatchStats.lastItemDraws());
        assertEquals(1, RenderBatchStats.lastSharedFlushes());
        assertEquals(1_500_000L, RenderBatchStats.lastItemNanos());
        assertEquals(1_500_000L, RenderBatchStats.lastItemMaxNanos());

        // 非帧路径（endDocument）同样产出快照
        RenderBatchStats.beginDocument();
        RenderBatchStats.recordSharedFlush();
        RenderBatchStats.recordItemDraw(900_000L);
        RenderBatchStats.endDocument();
        assertEquals(1, RenderBatchStats.lastSharedFlushes(), "非帧路径由 endDocument 产出快照");
        assertEquals(1, RenderBatchStats.lastItemDraws());
        assertEquals(900_000L, RenderBatchStats.lastItemNanos());
        assertEquals(900_000L, RenderBatchStats.lastItemMaxNanos());
    }
}
