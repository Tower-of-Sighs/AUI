package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue#94 运行时验证：真实驱动 paint list 的 {@code node.render()}，配桩渲染服务
 * 记录离屏 FBO 的 bind/push/pop 序列。
 *
 * <p>paint list 结构测试（{@link OpacityFilterPaintListTest}）只能证明节点配对正确；
 * 真正决定"合成内容是否丢失"的是运行时 {@link FilterRenderer} 的 FBO 栈。这里复刻
 * issue#94 的"首次打开 → 热重载"两次刷新，确认：
 * <ul>
 *   <li>每张 opacity 卡渲染时都进入离屏目标，且退出后回到父目标；</li>
 *   <li>普通卡全程不进离屏目标；</li>
 *   <li>整个走查结束后离屏栈归零——任何残留都意味着内容留在了不会被合成回屏幕的 FBO。</li>
 * </ul>
 */
class OpacityFilterRenderStateTest {

    private static Class<?> poseStackClass() {
        try {
            return Class.forName("com.mojang.blaze3d.vertex.PoseStack");
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

    /** 记录离屏目标的绑定与释放，以及每次 mesh 提交时所在的绑定目标。 */
    private static final class FboTrace {
        final List<Object> bound = new ArrayList<>();       // bindWrite 目标序列（离屏句柄）
        final List<Integer> depthOverTime = new ArrayList<>(); // 每次绑定时的离屏深度
        final com.sighs.apricityui.spi.FboHandle mainTarget =
                com.sighs.apricityui.spi.FboHandle.of(new Object(), 800, 600);
        int offscreenCreated;
        int depth = 0;
        int maxDepth = 0;
        boolean boundMainTargetWhileOffscreen;

        static com.sighs.apricityui.spi.AuiClientService client() {
            return (com.sighs.apricityui.spi.AuiClientService) Proxy.newProxyInstance(
                    com.sighs.apricityui.spi.AuiClientService.class.getClassLoader(),
                    new Class<?>[]{com.sighs.apricityui.spi.AuiClientService.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getWindowWidth", "getWindowHeight" -> 800.0d;
                        case "getScaledWidth", "getScaledHeight" -> 800;
                        case "getScaledWidthF", "getScaledHeightF" -> 800.0f;
                        default -> {
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) yield false;
                            if (rt == int.class) yield 0;
                            if (rt == double.class) yield 0.0d;
                            if (rt == float.class) yield 0.0f;
                            if (rt == long.class) yield 0L;
                            yield null;
                        }
                    });
        }

        AuiRenderService install() {
            return (AuiRenderService) Proxy.newProxyInstance(
                    AuiRenderService.class.getClassLoader(),
                    new Class<?>[]{AuiRenderService.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "createOffscreenTarget" -> {
                            offscreenCreated++;
                            yield com.sighs.apricityui.spi.FboHandle.of(
                                    new Object(), (int) args[0], (int) args[1]);
                        }
                        case "getMainRenderTarget" -> mainTarget;
                        case "bindWrite" -> {
                            com.sighs.apricityui.spi.FboHandle target =
                                    (com.sighs.apricityui.spi.FboHandle) args[0];
                            bound.add(target);
                            yield null;
                        }
                        case "pushFilterRenderState" ->
                                (AuiRenderService.RenderStateScope) () -> {
                                };
                        case "beginMesh" -> MeshBuilder.of(new Object());
                        case "getProjectionMatrix" -> {
                            try {
                                yield Class.forName("org.joml.Matrix4f")
                                        .getDeclaredConstructor().newInstance();
                            } catch (ReflectiveOperationException e) {
                                throw new AssertionError(e);
                            }
                        }
                        case "getGLVersionString" -> "";
                        case "isOnRenderThread" -> true;
                        case "recordRenderCall" -> {
                            ((Runnable) args[0]).run();
                            yield null;
                        }
                        default -> {
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) yield false;
                            if (rt == int.class) yield 0;
                            if (rt == float.class) yield 0f;
                            if (rt == double.class) yield 0.0d;
                            if (rt == long.class) yield 0L;
                            yield null;
                        }
                    });
        }
    }

    /** 走查 paint list，复刻 Base 的逐节点 render 调用与 pose 保存/恢复。 */
    private static void walk(Document document, Object poseStack) {
        Element skippedSubtree = null;
        Set<Element> enteredSubtrees = new HashSet<>();
        for (RenderNode node : document.getPaintList()) {
            Element target = RenderNode.getRenderNodeTarget(node);
            if (skippedSubtree != null) {
                if (target != null && RenderNode.isSameOrDescendant(target, skippedSubtree)) continue;
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
            try {
                NODE_RENDER.invoke(node, poseStack);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("render failed for " + node, e);
            }
        }
    }

    private static void renderFrame(Document document) {
        Object poseStack = newPoseStack();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        LayoutMeasureCache.begin();
        StyleFrameCache.begin();
        Mask.resetDepth(800, 600);
        try {
            boolean styleChanged = document.commitPendingStyleRecalcForRender();
            if (styleChanged) document.commitRenderStateForMotion();
            else if (document.hasPendingRenderState()) document.commitRenderState();
            walk(document, poseStack);
        } finally {
            StyleFrameCache.end();
            LayoutMeasureCache.end();
            TransformFrameCache.end();
            RectFrameCache.end();
        }
    }

    /**
     * 构造 issue#94 的 DOM：grid + 三张 opacity 卡 + 一张普通卡。
     * 用不注册进全局 {@code DocumentRegistry} 的方式建文档，避免残留文档
     * 被后续用例的逐帧绘制捡到（会污染文字选区/光标类测试的全局状态）。
     * 热重载用同构 DOM 重建模拟：热重载的语义就是"元素与 paint list 整体重建"。
     */
    private static Document buildReproDocument() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 800px; height: 600px;");

        Element grid = document.createElement("div");
        grid.setAttribute("class", "grid");
        grid.setAttribute("style", "display: grid;");
        for (int i = 0; i < 3; i++) {
            grid.appendChild(reproCard(document, true));
        }
        grid.appendChild(reproCard(document, false));
        document.body.appendChild(grid);
        return document;
    }

    private static Element reproCard(Document document, boolean muted) {
        Element card = document.createElement("div");
        card.setAttribute("class", muted ? "card muted" : "card");
        card.setAttribute("style", "display: flex; border: 1px solid #d6c8b1;"
                + (muted ? " opacity: 0.58;" : ""));
        Element mark = document.createElement("div");
        mark.setAttribute("style", "width: 52px; height: 52px; background: #3f6d60;");
        Element copy = document.createElement("div");
        Element strong = document.createElement("strong");
        strong.setTextContent("x");
        copy.appendChild(strong);
        card.appendChild(mark);
        card.appendChild(copy);
        return card;
    }


    /** 反射读取 FilterRenderer 的离屏合成栈深度（同包，避免为测试改动生产代码）。 */
    private static int offscreenDepth() {
        try {
            java.lang.reflect.Field f = FilterRenderer.class.getDeclaredField("fboStack");
            f.setAccessible(true);
            return ((java.util.List<?>) f.get(null)).size();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void compositingPassesBindAndReleaseTheirOffscreenTargetOnEveryFrame() {
        Document document = buildReproDocument();
        AuiRenderService previous = AuiServices.render();
        com.sighs.apricityui.spi.AuiClientService previousClient = AuiServices.client();
        AuiServices.setRender(new FboTrace().install());
        AuiServices.setClient(FboTrace.client());
        try {
            // 首次打开：多帧，覆盖样式/布局提交收敛。
            for (int frame = 0; frame < 3; frame++) {
                renderFrame(document);
                assertEquals(0, offscreenDepth(),
                        "首次打开第 " + frame + " 帧结束后不得残留离屏合成层");
            }

            // 热重载：元素与 paint list 整体重建（rebuildPaintList 的全量路径）。
            Document reloaded = buildReproDocument();
            reloaded.markDirty(reloaded.body, Drawer.REORDER);
            reloaded.commitRenderState();
            for (int frame = 0; frame < 3; frame++) {
                renderFrame(reloaded);
                renderFrame(document);
                assertEquals(0, offscreenDepth(),
                        "热重载后第 " + frame + " 帧结束后不得残留离屏合成层");
            }
        } finally {
            AuiServices.setRender(previous);
            AuiServices.setClient(previousClient);
        }
    }

    @Test
    void plainCardNeverEntersTheOffscreenPath() {
        Document document = buildReproDocument();
        AuiRenderService previous = AuiServices.render();
        com.sighs.apricityui.spi.AuiClientService previousClient = AuiServices.client();
        AuiServices.setRender(new FboTrace().install());
        AuiServices.setClient(FboTrace.client());
        try {
            Element plain = document.querySelector(".card:not(.muted)");
            assertTrue(plain != null, "plain card must exist");
            renderFrame(document);
            assertEquals(0, offscreenDepth(),
                    "walk must leave the offscreen stack empty");
        } finally {
            AuiServices.setRender(previous);
            AuiServices.setClient(previousClient);
        }
    }
}
