package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.image.ImageHandle;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.task.AbstractAsyncHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 遮罩层画不上时的浏览器语义（CSS Masking 1 §6.1：mask 图像不可用/无效按
 * transparent black 处理，元素整体被遮住）。
 *
 * <p>mask 的实现是把遮罩层画进离屏画布 M，再以 dst-in 把 M 的遮罩值乘回内容层 C
 * （{@link FilterRenderer#popMaskImage}）。"一层都没画上"有两种完全不同的原因，
 * 只有第二种该按规范遮挡：</p>
 * <ul>
 *   <li><b>纹理仍在异步加载</b>（NEW/LOADING/APPLYING）：结果未知。保持内容可见
 *       （fail-open），加载完成会触发 requester 重绘——这样首帧不会先把元素藏起来
 *       再突然出现。</li>
 *   <li><b>解码失败 / 图像无效 / 没有 mask 层</b>：终态。规范要求按 transparent black
 *       处理，也就是元素整体不可见——浏览器里 {@code mask-image:url(坏图)} 就是这个表现。</li>
 * </ul>
 *
 * <p>用桩渲染服务记录"内容合成"与"dst-in 遮罩 blit"用的是哪支着色器，从而在不碰 GL 的
 * 前提下断言内容有没有被合成回父目标。</p>
 */
class MaskUnavailableSemanticsTest {
    private static final int GUI_WIDTH = 800;
    private static final int GUI_HEIGHT = 600;

    /** t11 主题 .card::after / .panel::after 用的 notebook 横线遮罩（t11-hand-drawn-doodle.css）。 */
    private static final String NOTEBOOK_MASK_PATH =
            "apricityui/theme/t11-hand-drawn-doodle/notebook-lines.svg";

    /** 与主题声明同形，只把宿主换成 #card 以便观察：遮罩 url + mask-size + mask-repeat。 */
    private static final String T11_CARD_HTML =
            "<html><head><style>"
                    + "#card{position:relative;width:240px;height:120px;background:#fffef5;}"
                    + "#card::after{content:\"\";position:absolute;top:12px;right:10px;bottom:10px;left:12px;"
                    + "background-color:#dbece5;"
                    + "mask-image:url(\"/" + NOTEBOOK_MASK_PATH + "\");"
                    + "mask-size:256px 28px;mask-repeat:repeat;}"
                    + "</style></head><body><div id='card'></div></body></html>";

    /** 解析期就无效的 mask 图像：url() 没有路径，永远没有可画的层。 */
    private static final String INVALID_MASK_HTML =
            "<html><head><style>"
                    + "#box{width:200px;height:80px;background:#3f6d60;mask-image:url();}"
                    + "</style></head><body><div id='box'></div></body></html>";

    /** mask-image 的初始值不是遮罩：没有 mask 层，也绝不能把元素遮没。 */
    private static final String MASK_NONE_HTML =
            "<html><head><style>"
                    + "#box{width:200px;height:80px;background:#3f6d60;mask-image:none;}"
                    + "</style></head><body><div id='box'></div></body></html>";

    /** 能画出来的遮罩层（渐变不需要图像加载）：正常路径必须保持不变。 */
    private static final String GRADIENT_MASK_HTML =
            "<html><head><style>"
                    + "#box{width:200px;height:80px;background:#3f6d60;"
                    + "mask-image:linear-gradient(#000000,#000000);}"
                    + "</style></head><body><div id='box'></div></body></html>";

    private static final Method NODE_RENDER = nodeRender();
    private static final List<String> INSTALLED_PATHS = new ArrayList<>();

    @AfterEach
    void dropInstalledImageHandles() {
        for (String path : INSTALLED_PATHS) {
            imageHandles().remove(path);
        }
        INSTALLED_PATHS.clear();
    }

    @Test
    void t11DecorationStaysVisibleWhileItsMaskImageIsStillLoading() {
        Document document = document("t11-loading", T11_CARD_HTML);
        try {
            installMaskHandle(NOTEBOOK_MASK_PATH, AbstractAsyncHandler.AsyncState.APPLYING);

            RenderTrace trace = paint(document);

            assertEquals(2, maskNodeCount(document), "t11 的 ::after 装饰仍然拥有一对 mask 节点");
            assertTrue(trace.events.contains("content-composite"),
                    "纹理还在加载只说明结果未知：保持内容可见，等加载完成再重绘");
            assertFalse(trace.events.contains("mask-blit"), "没有画上的 mask 内容可以相乘");
        } finally {
            document.remove();
        }
    }

    @Test
    void t11DecorationDisappearsWhenItsMaskImageFailsToDecode() {
        Document document = document("t11-decode-failed", T11_CARD_HTML);
        try {
            installMaskHandle(NOTEBOOK_MASK_PATH, AbstractAsyncHandler.AsyncState.FAILED);

            RenderTrace trace = paint(document);

            assertEquals(2, maskNodeCount(document), "t11 的 ::after 装饰仍然拥有一对 mask 节点");
            assertFalse(trace.events.contains("mask-blit"), "解码失败的层没有内容可画");
            assertFalse(trace.events.contains("content-composite"),
                    "规范：mask 图像不可用按 transparent black → 装饰整体不可见，而不是露出未遮罩的实心块");
        } finally {
            document.remove();
        }
    }

    @Test
    void invalidMaskImageHidesTheElementLikeTransparentBlack() {
        Document document = document("invalid-mask-image", INVALID_MASK_HTML);
        try {
            RenderTrace trace = paint(document);

            assertEquals(2, maskNodeCount(document), "url() 无路径也是一层 mask，只是永远画不出来");
            assertFalse(trace.events.contains("mask-blit"), "没有画上的 mask 内容可以相乘");
            assertFalse(trace.events.contains("content-composite"),
                    "规范：mask 图像无效按 transparent black → 元素整体不可见");
        } finally {
            document.remove();
        }
    }

    @Test
    void maskImageNoneIsNotAMaskLayerAndKeepsTheElementVisible() {
        Document document = document("mask-image-none", MASK_NONE_HTML);
        try {
            RenderTrace trace = paint(document);

            assertEquals(0, maskNodeCount(document),
                    "mask-image:none 是初始值，不该被当成「没有 mask 层」而生成遮罩合成节点");
            assertFalse(trace.events.contains("content-composite"),
                    "没有遮罩节点时内容直接画进父目标，不需要任何离屏合成");
        } finally {
            document.remove();
        }
    }

    @Test
    void aPaintableMaskLayerStillAppliesDstInAndComposites() {
        Document document = document("gradient-mask", GRADIENT_MASK_HTML);
        try {
            RenderTrace trace = paint(document);

            assertEquals(2, maskNodeCount(document));
            assertTrue(trace.events.contains("mask-blit"), "画得出来的 mask 层照旧 dst-in");
            assertTrue(trace.events.contains("content-composite"), "内容照旧合成回父目标");
        } finally {
            document.remove();
        }
    }

    private static Document document(String name, String html) {
        String path = "probe://mask-unavailable/" + name;
        HTML.putTemple(path, html);
        Document document = new Document(path, false);
        document.refresh();
        return document;
    }

    private static int maskNodeCount(Document document) {
        int count = 0;
        for (RenderNode node : document.getPaintList()) {
            if (node instanceof RenderNode.MaskImagePushNode || node instanceof RenderNode.MaskImagePopNode) {
                count++;
            }
        }
        return count;
    }

    /**
     * 装一个指定状态的异步句柄。{@link ImageAsyncHandler#request} 只在 generation
     * 不匹配或句柄 STALE 时重建，因此装进去的状态就是绘制期看到的状态。
     */
    private static void installMaskHandle(String path, AbstractAsyncHandler.AsyncState state) {
        ImageHandle handle = new ImageHandle(path, currentGeneration());
        switch (state) {
            case APPLYING -> {
                handle.tryEnterLoading();
                handle.tryEnterApplying();
            }
            // failedAtMs 放到 1 小时后：句柄的 5s 失败重试窗口不会在用例运行期间把它重置回 NEW。
            case FAILED -> handle.markFailed(new IllegalStateException("mask decode failed"),
                    System.currentTimeMillis() + 3_600_000L);
            default -> throw new IllegalArgumentException("unexpected mask handle state: " + state);
        }
        imageHandles().put(path, handle);
        INSTALLED_PATHS.add(path);
    }

    private static long currentGeneration() {
        try {
            Method method = AbstractAsyncHandler.class.getDeclaredMethod("currentGeneration");
            method.setAccessible(true);
            return (long) method.invoke(ImageAsyncHandler.INSTANCE);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ImageHandle> imageHandles() {
        try {
            Field field = ImageAsyncHandler.class.getDeclaredField("HANDLES");
            field.setAccessible(true);
            return (Map<String, ImageHandle>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 走一遍 paint list，桩渲染服务记录每支着色器的使用。 */
    private static RenderTrace paint(Document document) {
        RenderTrace trace = new RenderTrace();
        AuiRenderService previousRender = AuiServices.render();
        AuiClientService previousClient = AuiServices.client();
        Consumer<AABB> previousSink = Mask.testScissorSink;
        Object poseStack = newPoseStack();
        AuiServices.setRender(trace.install());
        AuiServices.setClient(stubClient());
        Mask.testScissorSink = ignored -> { };
        RectFrameCache.begin();
        TransformFrameCache.begin();
        LayoutMeasureCache.begin();
        StyleFrameCache.begin();
        Mask.resetDepth(GUI_WIDTH, GUI_HEIGHT);
        FilterRenderer.beginFrame();
        try {
            for (RenderNode node : List.copyOf(document.getPaintList())) {
                try {
                    NODE_RENDER.invoke(node, poseStack);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("render failed for " + node, e);
                }
            }
        } finally {
            FilterRenderer.endFrame();
            StyleFrameCache.end();
            LayoutMeasureCache.end();
            TransformFrameCache.end();
            RectFrameCache.end();
            Mask.testScissorSink = previousSink;
            AuiServices.setRender(previousRender);
            AuiServices.setClient(previousClient);
        }
        return trace;
    }

    /**
     * 桩渲染服务：三支滤镜着色器各给一个哨兵对象。内容合成（filter 着色器）与
     * dst-in 遮罩 blit（mask 着色器）都是全窗四边形提交，只有着色器身份能区分。
     */
    private static final class RenderTrace {
        private static final Object FILTER_SHADER = new Object();
        private static final Object MASK_SHADER = new Object();
        private static final Object MERGE_SHADER = new Object();

        final List<String> events = new ArrayList<>();
        final FboHandle mainTarget = FboHandle.of(new Object(), GUI_WIDTH, GUI_HEIGHT);

        AuiRenderService install() {
            return (AuiRenderService) Proxy.newProxyInstance(AuiRenderService.class.getClassLoader(),
                    new Class<?>[]{AuiRenderService.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getMainRenderTarget" -> mainTarget;
                        case "createOffscreenTarget" -> FboHandle.of(new Object(), (int) args[0], (int) args[1]);
                        case "getFilterShader" -> FILTER_SHADER;
                        case "getFilterMaskShader" -> MASK_SHADER;
                        case "getFilterMaskMergeShader" -> MERGE_SHADER;
                        case "setShader" -> {
                            events.add(args[0] == FILTER_SHADER ? "content-composite"
                                    : args[0] == MASK_SHADER ? "mask-blit" : "other-shader");
                            yield null;
                        }
                        case "beginMesh" -> MeshBuilder.of(new Object());
                        case "submitMesh" -> {
                            events.add("submit");
                            yield null;
                        }
                        case "pushFilterRenderState" -> (AuiRenderService.RenderStateScope) () -> { };
                        case "getProjectionMatrix" -> newMatrix4f();
                        // 保持 stencil 能力判定停在默认值（true），别让本用例污染 JVM 级缓存；
                        // currentTargetHasStencil=false 会让 Mask 走 scissor 分支，落进 testScissorSink。
                        case "supportsStencil" -> true;
                        case "currentTargetHasStencil" -> false;
                        case "getGLVersionString" -> "";
                        case "isOnRenderThread" -> true;
                        case "recordRenderCall" -> {
                            ((Runnable) args[0]).run();
                            yield null;
                        }
                        default -> primitiveDefault(method);
                    });
        }

        private static Object primitiveDefault(Method method) {
            Class<?> type = method.getReturnType();
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == float.class) return 0f;
            if (type == double.class) return 0.0d;
            if (type == long.class) return 0L;
            return null;
        }
    }

    private static AuiClientService stubClient() {
        return (AuiClientService) Proxy.newProxyInstance(AuiClientService.class.getClassLoader(),
                new Class<?>[]{AuiClientService.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWindowWidth" -> (double) GUI_WIDTH;
                    case "getWindowHeight" -> (double) GUI_HEIGHT;
                    case "getScaledWidth", "getScaledHeight" -> GUI_WIDTH;
                    case "getScaledWidthF", "getScaledHeightF" -> (float) GUI_WIDTH;
                    default -> null;
                });
    }

    private static Method nodeRender() {
        try {
            return RenderNode.class.getMethod("render",
                    Class.forName("com.mojang.blaze3d.vertex.PoseStack"));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newPoseStack() {
        try {
            return Class.forName("com.mojang.blaze3d.vertex.PoseStack")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newMatrix4f() {
        try {
            return Class.forName("org.joml.Matrix4f").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
