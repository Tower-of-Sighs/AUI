package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 五个 flexbox 布局特性的回归测试：order、row-reverse/column-reverse、
 * wrap-reverse、align-content、align-items/align-self 的 baseline 对齐。
 * <p>
 * 期望几何全部来自 CSS Flexbox 规范对容器/行/项的确定行为；所有场景都使用
 * 固定 px 尺寸且 border/padding/margin 为 0，因此期望值是精确的。
 */
class FlexReverseOrderAlignContentTest {

    @Test
    void rowReversePacksItemsFromTheRightEdge() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: row-reverse; align-items: flex-start; width: 200px; height: 100px;");
        document.body.appendChild(parent);

        Element first = fixedBox(document, 60, 40);
        Element second = fixedBox(document, 40, 20);
        parent.appendChild(first);
        parent.appendChild(second);

        // row-reverse 的主轴起点在右边缘：第一个 DOM 子项贴右（x = 200 - 60），
        // 第二个子项紧随其左；交叉轴（垂直）不变，仍从顶部开始。
        assertEquals(140, Position.getOffset(first).x, 0.01);
        assertEquals(0, Position.getOffset(first).y, 0.01);
        assertEquals(100, Position.getOffset(second).x, 0.01);
        assertEquals(0, Position.getOffset(second).y, 0.01);
    }

    @Test
    void rowReverseJustifyContentFlexEndPacksItemsFromTheLeftEdge() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: row-reverse; justify-content: flex-end; align-items: flex-start; width: 200px; height: 100px;");
        document.body.appendChild(parent);

        Element first = fixedBox(document, 60, 40);
        Element second = fixedBox(document, 40, 20);
        parent.appendChild(first);
        parent.appendChild(second);

        // row-reverse 下主轴起点在右边缘、终点在左边缘：justify-content: flex-end
        // 向主轴终点（左）靠拢，但组内顺序仍是第一个 DOM 子项在主轴起点侧（右）。
        // 因此第二个子项贴左边缘（x = 0），第一个子项在其右侧（x = 40）。
        assertEquals(40, Position.getOffset(first).x, 0.01);
        assertEquals(0, Position.getOffset(second).x, 0.01);
    }

    @Test
    void columnReverseStacksItemsFromTheBottomEdge() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column-reverse; align-items: flex-start; width: 100px; height: 200px;");
        document.body.appendChild(parent);

        Element first = fixedBox(document, 60, 40);
        Element second = fixedBox(document, 40, 20);
        parent.appendChild(first);
        parent.appendChild(second);

        // column-reverse 的主轴起点在底边缘：第一个 DOM 子项贴底
        // （底边 = 容器底边，y = 200 - 40），第二个子项紧贴其上（y = 160 - 20）。
        assertEquals(160, Position.getOffset(first).y, 0.01);
        assertEquals(140, Position.getOffset(second).y, 0.01);
        assertEquals(0, Position.getOffset(first).x, 0.01);
        assertEquals(0, Position.getOffset(second).x, 0.01);
        assertEquals(200, Position.getOffset(first).y + Size.of(first).height(), 0.01);
    }

    @Test
    void wrapReverseStacksLinesFromTheBottom() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-wrap: wrap-reverse; align-content: flex-start; align-items: flex-start; width: 150px; height: 100px;");
        document.body.appendChild(parent);

        Element first = fixedBox(document, 60, 40);
        Element second = fixedBox(document, 60, 40);
        Element third = fixedBox(document, 60, 40);
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);

        // wrap-reverse 翻转交叉轴：行从底部向上堆叠。align-content: flex-start
        // 让行保持自然高度 40 并从交叉轴起点（底部）排起，因此第一行
        // （含第一个 DOM 子项）位于 [60, 100]，第二行位于 [20, 60]。
        assertEquals(60, Position.getOffset(first).y, 0.01);
        assertEquals(60, Position.getOffset(second).y, 0.01);
        assertEquals(60, Position.getOffset(second).x, 0.01);
        assertEquals(20, Position.getOffset(third).y, 0.01);
        assertTrue(Position.getOffset(first).y > Position.getOffset(third).y,
                "wrap-reverse 下第一个 DOM 子项所在的行必须位于底部（y 更大）");

        // align-items: flex-start + wrap-reverse：项锚定在该行的物理底边，
        // 即第一行的项底边与容器底边重合。
        assertEquals(100, Position.getOffset(first).y + Size.of(first).height(), 0.01);
    }

    @Test
    void alignContentFlexEndPacksLinesAtTheBottom() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = wrapScenario(document, "flex-end");

        // 两行共 80px，容器 120px：剩余 40px 全部压到交叉轴末端（底部）。
        assertEquals(40, Position.getOffset(parent.getRenderChildren().get(0)).y, 0.01);
        assertEquals(80, Position.getOffset(parent.getRenderChildren().get(2)).y, 0.01);
        assertEquals(120, Position.getOffset(parent.getRenderChildren().get(2)).y
                + Size.of(parent.getRenderChildren().get(2)).height(), 0.01);
    }

    @Test
    void alignContentCenterCentersLines() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = wrapScenario(document, "center");

        // 剩余 40px 均分到两行两侧：(120 - 80) / 2 = 20。
        assertEquals(20, Position.getOffset(parent.getRenderChildren().get(0)).y, 0.01);
        assertEquals(60, Position.getOffset(parent.getRenderChildren().get(2)).y, 0.01);
    }

    @Test
    void alignContentSpaceBetweenPlacesFreeSpaceBetweenLines() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = wrapScenario(document, "space-between");

        // 剩余 40px 全部放进两行之间：第一行贴顶、第二行贴底。
        assertEquals(0, Position.getOffset(parent.getRenderChildren().get(0)).y, 0.01);
        assertEquals(80, Position.getOffset(parent.getRenderChildren().get(2)).y, 0.01);
        assertEquals(40,
                Position.getOffset(parent.getRenderChildren().get(2)).y
                        - Position.getOffset(parent.getRenderChildren().get(0)).y
                        - Size.of(parent.getRenderChildren().get(0)).height(),
                0.01);
    }

    @Test
    void alignContentStretchMakesLinesFillTheContainer() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-wrap: wrap; align-content: stretch; align-items: flex-start; width: 150px; height: 120px;");
        document.body.appendChild(parent);

        Element first = fixedBox(document, 60, 40);
        Element second = fixedBox(document, 60, 40);
        Element third = fixedBox(document, 60, 40);
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);

        // stretch 把剩余 40px 均分给两行：每行 40 + 20 = 60px，两行合计正好填满 120px。
        // 项有确定高度（40px），锚定在各自行的交叉轴起点。
        assertEquals(0, Position.getOffset(first).y, 0.01);
        assertEquals(60, Position.getOffset(third).y, 0.01);
    }

    @Test
    void orderSortsItemsByIntegerValueBeforePainting() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; align-items: flex-start; width: 300px; height: 100px;");
        document.body.appendChild(parent);

        Element first = fixedBox(document, 60, 40);
        first.setAttribute("style", "order: 2; width: 60px; height: 40px;");
        Element second = fixedBox(document, 60, 40);
        second.setAttribute("style", "order: 1; width: 60px; height: 40px;");
        Element third = fixedBox(document, 60, 40);
        third.setAttribute("style", "order: 0; width: 60px; height: 40px;");
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);

        // 视觉顺序按 order 升序排列：order 0 → 1 → 2，与 DOM 顺序无关。
        assertEquals(0, Position.getOffset(third).x, 0.01);
        assertEquals(60, Position.getOffset(second).x, 0.01);
        assertEquals(120, Position.getOffset(first).x, 0.01);
    }

    @Test
    void orderKeepsDomOrderForEqualValues() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; align-items: flex-start; width: 300px; height: 100px;");
        document.body.appendChild(parent);

        Element first = fixedBox(document, 60, 40);
        first.setAttribute("style", "order: 1; width: 60px; height: 40px;");
        Element second = fixedBox(document, 60, 40);
        second.setAttribute("style", "order: 1; width: 60px; height: 40px;");
        Element third = fixedBox(document, 60, 40);
        third.setAttribute("style", "order: 1; width: 60px; height: 40px;");
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);

        // order 相同（含默认 0）时保持 DOM 顺序，排序必须是稳定的。
        assertEquals(0, Position.getOffset(first).x, 0.01);
        assertEquals(60, Position.getOffset(second).x, 0.01);
        assertEquals(120, Position.getOffset(third).x, 0.01);
    }

    @Test
    void rowBaselineAlignsPaintedBaselinesOfDifferentHeightItems() {
        assumeMinecraftClientTextRuntime();
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 300px; height: 200px;");

        Element row = new Element(document, "div");
        row.setAttribute("style", "display: flex; align-items: baseline; width: 200px; height: 80px;");
        document.body.appendChild(row);

        Element small = new Element(document, "div");
        small.setAttribute("style", "width: 60px; height: 40px; font-size: 15px; line-height: 15px;");
        small.innerText = "Alpha";
        Element large = new Element(document, "div");
        large.setAttribute("style", "width: 60px; height: 60px; font-size: 25px; line-height: 25px;");
        large.innerText = "Beta";
        row.appendChild(small);
        row.appendChild(large);

        // 绘制基线 = 项顶（容器坐标）+ margin/border/padding + 首行文本基线偏移。
        // 两项字体不同、高度不同，但绘制基线必须落在同一条线上。
        assertEquals(paintedBaseline(small), paintedBaseline(large), 0.5,
                "row 方向 align-items: baseline 时两项的首行文本基线必须重合");
        // 大号文字基线更深，小项必须被下压才能对齐。
        assertTrue(Position.getOffset(small).y > Position.getOffset(large).y,
                "字号更大的项贴顶，字号更小的项下移以共享基线");
    }

    private static double paintedBaseline(Element element) {
        return Position.getOffset(element).y
                + Box.of(element).getMarginTop()
                + Box.of(element).getBorderTop()
                + Box.of(element).getPaddingTop()
                + Text.baselineOffset(Text.of(element));
    }

    /** 150x120 的 wrap 容器 + 三个 60x40 子项，形成两行（每行一个 40px 的交叉轴高度）。 */
    private static Element wrapScenario(Document document, String alignContent) {
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-wrap: wrap; align-content: " + alignContent
                + "; align-items: flex-start; width: 150px; height: 120px;");
        document.body.appendChild(parent);
        Element first = fixedBox(document, 60, 40);
        Element second = fixedBox(document, 60, 40);
        Element third = fixedBox(document, 60, 40);
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);
        return parent;
    }

    private static Element fixedBox(Document document, int width, int height) {
        Element element = new Element(document, "div");
        element.setAttribute("style", "width: " + width + "px; height: " + height + "px;");
        return element;
    }

    private static void assumeMinecraftClientTextRuntime() {
        // 文本/布局断言使用 headless JVM 测试任务里确定的 AWT 字体回退；
        // Minecraft 字体渲染由客户端集成冒烟套件覆盖。
    }
}
