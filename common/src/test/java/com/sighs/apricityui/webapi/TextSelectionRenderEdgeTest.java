package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.behavior.TextSelection;
import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本选择的“渲染边界”回归：行数截断（line-clamp）与单行省略（text-overflow:ellipsis）
 * 只影响绘制，不影响选区。覆盖最后两个文本选择修复：
 * <ul>
 *   <li>选中文本保留原始字体颜色（不再强制白色）；高亮背景仍取 selection-color（默认 #0078D7）；</li>
 *   <li>选择高亮与逐行分段跟随“实际渲染的行”（line-clamp 截断 / 文本溢出省略），
 *       高亮矩形只覆盖可见行、止于真实文本（不含合成的 "..."）。</li>
 * </ul>
 * 选区提取类用例只断言公开状态（{@link Element#selectAllInnerText()}、
 * {@link Element#hasInnerTextSelection()}、{@link Element#getSelectedInnerText()}、
 * {@link Element#canSelectInnerText()}），与绘制实现无关。
 * <p>
 * 绘制类用例复用 {@code ProgressBarBackgroundPaintTest} 的手法：安装一个记录
 * {@link AuiRenderService} 的代理，经公开的 {@link Element#drawContentOnly} 触发
 * 元素内容绘制，再 {@link Graph#endBatch()}。高亮矩形经 {@code Graph.drawFillRect}
 * 以 selection-color 的 position-color 顶点进入 mesh，因此高亮的“颜色与几何”
 * 可以无头断言（选中文字的“字形颜色”烘焙在栅格纹理里，见 {@link #newPoseStack()} 上方说明）。
 * <p>
 * 注意：几何类断言面向修复后的行为编写；实现合入前直接运行，clamp/ellipsis 几何用例
 * 会因旧实现按“全部换行行”绘制高亮而失败 —— 这正是它们要回归的缺陷。
 */
class TextSelectionRenderEdgeTest {

    /** 足够长的文本：在 200px 宽盒内必然换行成 3 行以上，且单行省略用例在 100px 宽盒内必然被截断。 */
    private static final String LONG_TEXT =
            "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau";

    @Test
    void blockContainerDoesNotPaintFlattenedDescendantTextAtItsOwnOrigin() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Element tree = new Element(document, "div");
        Element firstRow = new Element(document, "div");
        Element secondRow = new Element(document, "div");
        Element firstLabel = new Element(document, "span");
        Element secondLabel = new Element(document, "span");
        firstRow.setAttribute("style", "display: flex;");
        secondRow.setAttribute("style", "display: flex;");
        firstLabel.setTextContent("CACHE");
        secondLabel.setTextContent("OVERLAYS");
        firstRow.appendChild(firstLabel);
        secondRow.appendChild(secondLabel);
        tree.appendChild(firstRow);
        tree.appendChild(secondRow);
        document.body.appendChild(tree);

        assertEquals("CACHEOVERLAYS", SelectionUnits.flattenedSelectableText(tree),
                "the selection model must still expose the descendant text");
        assertEquals("", leafPaintContent(tree),
                "a block container must leave descendant text to its child boxes");
    }

    @Test
    void leafPaintStillUsesDirectTextNodes() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Element label = new Element(document, "span");
        label.appendChild(new TextNode(document, "CACHE"));
        document.body.appendChild(label);

        assertEquals("CACHE", leafPaintContent(label));
    }

    @Test
    void flexLeafTextIsPaintedExactlyOnce() {
        assertFlexLeafPaint("inline-flex", false, "Pure CSS");
        assertFlexLeafPaint("flex", true, "O");
    }

    // ------------------------------------------------------------------
    // 选区提取：截断/省略只影响绘制，selectAllInnerText 返回底层全文
    // ------------------------------------------------------------------

    @Test
    void selectAllInnerTextOnLineClampedElementReturnsTheFullUnderlyingText() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "line-clamp: 2; width: 200px;", LONG_TEXT);

        // 前置条件：line-clamp 确实被样式解析（引擎的语法是 line-clamp，非 -webkit- 前缀）。
        assertEquals("2", div.getComputedStyle().lineClamp);

        assertTrue(div.canSelectInnerText());
        div.selectAllInnerText();

        // 选区建立在底层文本上，而不是可见的截断行。
        assertTrue(div.hasInnerTextSelection());
        assertEquals(LONG_TEXT, div.getSelectedInnerText());
    }

    @Test
    void selectAllInnerTextOnEllipsizedElementReturnsTheFullUnderlyingText() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document,
                "white-space: nowrap; overflow: hidden; text-overflow: ellipsis; width: 100px;", LONG_TEXT);

        // 前置条件：ellipsis 三个相关样式都已生效。
        assertEquals("ellipsis", div.getComputedStyle().textOverflow);
        assertEquals("hidden", div.getComputedStyle().overflow);

        assertTrue(div.canSelectInnerText());
        div.selectAllInnerText();

        // 可见行被省略号截短，但选区仍然覆盖完整文本。
        assertTrue(div.hasInnerTextSelection());
        assertEquals(LONG_TEXT, div.getSelectedInnerText());
    }

    @Test
    void selectAllInnerTextOnClampedAndEllipsizedElementReturnsTheFullUnderlyingText() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document,
                "line-clamp: 2; overflow: hidden; text-overflow: ellipsis; width: 200px;", LONG_TEXT);

        assertEquals("2", div.getComputedStyle().lineClamp);
        assertEquals("ellipsis", div.getComputedStyle().textOverflow);

        assertTrue(div.canSelectInnerText());
        div.selectAllInnerText();

        assertTrue(div.hasInnerTextSelection());
        assertEquals(LONG_TEXT, div.getSelectedInnerText());
    }

    @Test
    void selectionFlagsStillHoldOnClampedAndEllipsizedElements() {
        Document document = TestDocumentFactory.createDocument();
        Element clamped = unit(document, "line-clamp: 2; width: 200px;", LONG_TEXT);
        Element ellipsized = unit(document,
                "white-space: nowrap; overflow: hidden; text-overflow: ellipsis; width: 100px;", LONG_TEXT);

        // 截断/省略元素仍然是可以选择、可以清空选区的普通文本单元。
        assertTrue(clamped.canSelectInnerText());
        assertTrue(ellipsized.canSelectInnerText());
        assertFalse(clamped.hasInnerTextSelection());
        assertFalse(ellipsized.hasInnerTextSelection());

        // 逐个选中：文档级选区是唯一的，后一次 selectAll 替换前一次，未选中的单元不报选区。
        clamped.selectAllInnerText();
        assertTrue(clamped.hasInnerTextSelection());
        assertFalse(ellipsized.hasInnerTextSelection());
        assertEquals(LONG_TEXT, clamped.getSelectedInnerText());

        ellipsized.selectAllInnerText();
        assertTrue(ellipsized.hasInnerTextSelection());
        assertFalse(clamped.hasInnerTextSelection());
        assertEquals(LONG_TEXT, ellipsized.getSelectedInnerText());

        // 清空非锚点单元是 no-op；清空锚点单元清掉整个文档级选区。
        clamped.clearTextSelection();
        assertTrue(ellipsized.hasInnerTextSelection());
        ellipsized.clearTextSelection();
        assertFalse(clamped.hasInnerTextSelection());
        assertFalse(ellipsized.hasInnerTextSelection());
    }

    // ------------------------------------------------------------------
    // 绘制：高亮跟随渲染行（需要修复 12 落地）
    // ------------------------------------------------------------------

    @Test
    void selectionHighlightOnLineClampedElementOnlyCoversTheVisibleLines() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "line-clamp: 2; width: 200px;", LONG_TEXT);
        div.selectAllInnerText();
        // 先完成布局（使用默认服务），再安装录制代理只捕获绘制。
        document.tickFrame();

        Recording recording = installRecording();
        try {
            // 前置条件：文本确实被截断（渲染行少于底层换行行），且 line-clamp:2 起效。
            Text text = Text.of(div);
            double contentWidth = Box.of(div).innerSize().width();
            double contentHeight = Box.of(div).innerSize().height();
            Text.WrappedText wrapped = Text.wrapCached(div, text);
            List<String> rendered = div.resolveRenderedLines(text, contentWidth, contentHeight);
            assertTrue(wrapped.lines().size() > 2,
                    "precondition: text must wrap beyond 2 lines, got " + wrapped.lines().size());
            assertTrue(rendered.size() < wrapped.lines().size(),
                    "precondition: the rendered lines must be truncated by line-clamp, got "
                            + rendered.size() + " of " + wrapped.lines().size());
            assertTrue(rendered.size() <= 2,
                    "precondition: line-clamp:2 must leave at most 2 rendered lines, got " + rendered.size());

            drawContent(div);

            // 高亮矩形只覆盖可见的两行：所有 selection-color 顶点都必须落在内容盒内。
            List<int[]> highlight = recording.verticesOfColor(0, 120, 215); // #0078D7
            assertFalse(highlight.isEmpty(), "selection highlight must emit selection-color vertices");
            Position contentPos = Rect.of(div).getContentPosition();
            for (int[] v : highlight) {
                assertTrue(v[4] >= contentPos.x - 1 && v[4] <= contentPos.x + contentWidth + 1,
                        "highlight x=" + v[4] + " must stay inside content box ["
                                + contentPos.x + ", " + (contentPos.x + contentWidth) + "]");
                assertTrue(v[5] >= contentPos.y - 1 && v[5] <= contentPos.y + contentHeight + 1,
                        "highlight y=" + v[5] + " must stay inside content box ["
                                + contentPos.y + ", " + (contentPos.y + contentHeight) + "]");
            }
            // 行带数量不超过可见行 + 1（相邻两行共享一条边）；旧实现按全部换行行绘制会多出至少一条。
            long distinctY = highlight.stream().map(v -> v[5]).distinct().count();
            assertTrue(distinctY <= rendered.size() + 1,
                    "highlight must span only the visible lines, distinct y bands=" + distinctY);
        } finally {
            AuiServices.setRender(recording.previous());
        }
    }

    @Test
    void selectionHighlightOnEllipsizedElementStopsAtTheVisibleText() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document,
                "white-space: nowrap; overflow: hidden; text-overflow: ellipsis; width: 100px;", LONG_TEXT);
        div.selectAllInnerText();
        // 先完成布局（使用默认服务），再安装录制代理只捕获绘制。
        document.tickFrame();

        Recording recording = installRecording();
        try {
            // 前置条件：渲染行确实被省略号截短，且以合成的 "..." 结尾。
            Text text = Text.of(div);
            double contentWidth = Box.of(div).innerSize().width();
            double contentHeight = Box.of(div).innerSize().height();
            Text.WrappedText wrapped = Text.wrapCached(div, text);
            List<String> rendered = div.resolveRenderedLines(text, contentWidth, contentHeight);
            assertEquals(1, wrapped.lines().size(), "precondition: nowrap must keep a single wrapped line");
            assertEquals(1, rendered.size());
            assertNotEquals(wrapped.lines().get(0), rendered.get(0),
                    "precondition: the rendered line must be shortened by the ellipsis");
            assertTrue(rendered.get(0).endsWith("..."),
                    "precondition: the rendered line must carry the synthetic ellipsis, got \"" + rendered.get(0) + "\"");

            drawContent(div);

            // 高亮止于真实文本（不含合成的 "..."）：所有 selection-color 顶点都必须在内容盒内。
            List<int[]> highlight = recording.verticesOfColor(0, 120, 215); // #0078D7
            assertFalse(highlight.isEmpty(), "selection highlight must emit selection-color vertices");
            Position contentPos = Rect.of(div).getContentPosition();
            for (int[] v : highlight) {
                assertTrue(v[4] >= contentPos.x - 1 && v[4] <= contentPos.x + contentWidth + 1,
                        "highlight x=" + v[4] + " must stop at the visible text inside content box ["
                                + contentPos.x + ", " + (contentPos.x + contentWidth) + "]");
                assertTrue(v[5] >= contentPos.y - 1 && v[5] <= contentPos.y + contentHeight + 1,
                        "highlight y=" + v[5] + " must stay inside content box ["
                                + contentPos.y + ", " + (contentPos.y + contentHeight) + "]");
            }
        } finally {
            AuiServices.setRender(recording.previous());
        }
    }

    // ------------------------------------------------------------------
    // 绘制：高亮背景来自 selection-color（默认 #0078D7）
    // ------------------------------------------------------------------

    @Test
    void selectionHighlightBackgroundUsesTheSelectionColor() {
        // 默认 selection-color：#0078D7。
        assertHighlightColor("width: 200px; height: 24px;", 0, 120, 215);
        // 自定义 selection-color：高亮背景跟随配置，而不是硬编码。
        assertHighlightColor("width: 200px; height: 24px; selection-color: #112233;", 17, 34, 51);
    }

    private static void assertHighlightColor(String style, int r, int g, int b) {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, style, LONG_TEXT);
        div.selectAllInnerText();
        // 先完成布局（使用默认服务），再安装录制代理只捕获绘制。
        document.tickFrame();

        Recording recording = installRecording();
        try {
            drawContent(div);

            List<int[]> highlight = recording.verticesOfColor(r, g, b);
            assertFalse(highlight.isEmpty(),
                    "selection highlight must be drawn with selection-color #"
                            + String.format("%02X%02X%02X", r, g, b));
        } finally {
            AuiServices.setRender(recording.previous());
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 一个带文本子节点的普通流 div（宽高由样式决定）。 */
    private static Element unit(Document document, String style, String text) {
        Element div = new Element(document, "div");
        div.setAttribute("style", style);
        div.appendChild(new TextNode(document, text));
        document.body.appendChild(div);
        return div;
    }

    private static String leafPaintContent(Element element) throws Exception {
        Field field = Element.class.getDeclaredField("textSelection");
        field.setAccessible(true);
        TextSelection selection = (TextSelection) field.get(element);
        Method method = TextSelection.class.getDeclaredMethod("selectableText");
        method.setAccessible(true);
        Text text = (Text) method.invoke(selection);
        return text.content;
    }

    private static void assertFlexLeafPaint(String display, boolean legacyInnerText, String expected) {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");
        Element element = new Element(document, "span");
        // 宽度必须容得下整段文本（"Pure CSS" 自然宽 ≈122.7px），否则按浏览器标准
        // 直接文本会在容器内容宽处软换行、分两次绘制——本测试断言的是"不重复绘制"，
        // 不是"不换行"。
        element.setAttribute("style", "display: " + display + "; align-items: center; width: 160px; height: 32px;");
        if (legacyInnerText) element.innerText = expected;
        else element.appendChild(new TextNode(document, expected));
        document.body.appendChild(element);

        List<String> painted = new ArrayList<>();
        AuiClientService previous = AuiServices.client();
        AuiClientService recording = (AuiClientService) Proxy.newProxyInstance(
                AuiClientService.class.getClassLoader(),
                new Class<?>[]{AuiClientService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("drawDefaultFont")) {
                        painted.add((String) args[2]);
                        return null;
                    }
                    return method.invoke(previous, args);
                });
        AuiServices.setClient(recording);
        try {
            drawContent(element);
        } finally {
            AuiServices.setClient(previous);
        }

        assertEquals(List.of(expected), painted,
                "a direct-text " + display + " element must paint its text once");
    }

    /**
     * 反射构造 {@code com.mojang.blaze3d.vertex.PoseStack}：Minecraft/JOML 类型只在
     * 测试运行期 classpath 上（同 ProgressBarBackgroundPaintTest 的约定），编译期不引用。
     * <p>
     * 选中文字的“字形颜色”烘焙在 FontDrawer 的栅格纹理里（setImagePixel 写入带色调的
     * ARGB，之后经纹理批次绘制），不会以 position-color 顶点形式出现；无头环境无法断言
     * “选中文字不强制白色”，该点只能由游戏内集成测试/人工验证（见报告）。
     */
    private static Object newPoseStack() {
        try {
            return Class.forName("com.mojang.blaze3d.vertex.PoseStack").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 经公开的 drawContentOnly 触发元素内容绘制（含选择高亮），并冲刷 Graph 批次。 */
    private static void drawContent(Element element) {
        try {
            Class<?> poseStackClass = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
            Method drawContentOnly = Element.class.getMethod("drawContentOnly", poseStackClass);
            drawContentOnly.invoke(element, newPoseStack());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        Graph.endBatch();
    }

    /** 安装记录 position-color 顶点的 AuiRenderService 代理，返回可查询的录制器。 */
    private static Recording installRecording() {
        Recording recording = new Recording();
        AuiServices.setRender(recording.proxy());
        return recording;
    }

    private static Object newMatrix4f() {
        try {
            return Class.forName("org.joml.Matrix4f").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 记录 emitVertex 的颜色/坐标，供高亮几何与颜色断言查询（同 ProgressBarBackgroundPaintTest）。 */
    private static final class Recording {
        private final Map<Object, List<int[]>> meshVertices = new IdentityHashMap<>();
        private final AuiRenderService previous = AuiServices.render();

        AuiRenderService proxy() {
            return (AuiRenderService) Proxy.newProxyInstance(
                    AuiRenderService.class.getClassLoader(),
                    new Class<?>[]{AuiRenderService.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "beginMesh": {
                                Object token = new Object();
                                meshVertices.put(token, new ArrayList<>());
                                return MeshBuilder.of(token);
                            }
                            case "emitVertex": {
                                Object mesh = args[0];
                                float x = (float) args[2];
                                float y = (float) args[3];
                                int r = (int) args[5];
                                int g = (int) args[6];
                                int b = (int) args[7];
                                int a = (int) args[8];
                                List<int[]> verts = meshVertices.get(mesh);
                                if (verts != null) verts.add(new int[]{r, g, b, a, Math.round(x), Math.round(y)});
                                return null;
                            }
                            case "getProjectionMatrix":
                                return newMatrix4f();
                            case "getGLVersionString":
                                return "";
                            case "isOnRenderThread":
                                return true;
                            case "recordRenderCall":
                                ((Runnable) args[0]).run();
                                return null;
                            default:
                                Class<?> rt = method.getReturnType();
                                if (rt == boolean.class) return false;
                                if (rt == int.class) return 0;
                                if (rt == float.class) return 0f;
                                return null;
                        }
                    });
        }

        AuiRenderService previous() {
            return previous;
        }

        List<int[]> verticesOfColor(int r, int g, int b) {
            List<int[]> result = new ArrayList<>();
            for (List<int[]> verts : meshVertices.values()) {
                for (int[] v : verts) {
                    if (v[0] == r && v[1] == g && v[2] == b && v[3] > 0) {
                        result.add(v);
                    }
                }
            }
            return result;
        }
    }
}
