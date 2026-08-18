package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Flex;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * column 方向 flex-wrap 的回归测试（镜像 row wrap 三件套旋转 90°）。
 * 容器一律显式 align-content: flex-start 关掉默认 stretch 的列拉伸，
 * 让期望几何精确；所有场景固定 px 尺寸。
 */
class FlexColumnWrapTest {

    @Test
    void columnWrapBreaksIntoColumnsWhenExceedingContainerHeight() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = columnWrap(document, "height: 200px;");
        Element first = fixedBox(document, 60, 80);
        Element second = fixedBox(document, 60, 80);
        Element third = fixedBox(document, 60, 80);
        Element fourth = fixedBox(document, 60, 80);
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);
        parent.appendChild(fourth);

        // 200px 高的列只能装两项（80+80=160，再加 80 超高切列）：
        // 第一列 [first, second]，第二列 [third, fourth]，列宽 60。
        assertEquals(0, Position.getOffset(first).x, 0.01);
        assertEquals(0, Position.getOffset(first).y, 0.01);
        assertEquals(0, Position.getOffset(second).x, 0.01);
        assertEquals(80, Position.getOffset(second).y, 0.01);
        assertEquals(60, Position.getOffset(third).x, 0.01);
        assertEquals(0, Position.getOffset(third).y, 0.01);
        assertEquals(60, Position.getOffset(fourth).x, 0.01);
        assertEquals(80, Position.getOffset(fourth).y, 0.01);
    }

    @Test
    void autoHeightColumnWrapContainerStaysSingleColumn() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        // 高度 auto：按规范（与浏览器一致）单列不换行。
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; flex-wrap: wrap; align-items: flex-start; width: 100px;");
        document.body.appendChild(parent);
        Element first = fixedBox(document, 60, 80);
        Element second = fixedBox(document, 60, 80);
        parent.appendChild(first);
        parent.appendChild(second);

        assertEquals(0, Position.getOffset(first).x, 0.01);
        assertEquals(0, Position.getOffset(first).y, 0.01);
        assertEquals(0, Position.getOffset(second).x, 0.01);
        assertEquals(80, Position.getOffset(second).y, 0.01);
        // 单列内容尺寸：宽取最大项宽，高为各项之和。
        assertEquals(60, Flex.computeContentSize(parent).width(), 0.01);
        assertEquals(160, Flex.computeContentSize(parent).height(), 0.01);
    }

    @Test
    void columnReverseMirrorsItemsWithinEachColumn() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column-reverse; flex-wrap: wrap; align-content: flex-start; align-items: flex-start; width: 200px; height: 200px;");
        document.body.appendChild(parent);
        Element first = fixedBox(document, 60, 80);
        Element second = fixedBox(document, 60, 80);
        Element third = fixedBox(document, 60, 80);
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);

        // 分行按原始顺序（第一列 [first, second]），列内主轴镜像：
        // first 贴列底（y = 200-80），second 在其上。
        assertEquals(120, Position.getOffset(first).y, 0.01);
        assertEquals(40, Position.getOffset(second).y, 0.01);
        assertEquals(60, Position.getOffset(third).x, 0.01);
        assertEquals(120, Position.getOffset(third).y, 0.01);
    }

    @Test
    void wrapReverseStacksColumnsFromTheRight() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; flex-wrap: wrap-reverse; align-content: flex-start; align-items: flex-start; width: 200px; height: 200px;");
        document.body.appendChild(parent);
        Element first = fixedBox(document, 60, 80);
        Element second = fixedBox(document, 60, 80);
        Element third = fixedBox(document, 60, 80);
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);

        // wrap-reverse 翻转交叉轴：列从右往左排。第一列（含 first）贴右，
        // 第二列（含 third）在其左。
        assertEquals(140, Position.getOffset(first).x, 0.01);
        assertEquals(80, Position.getOffset(third).x, 0.01);
        assertTrue(Position.getOffset(first).x > Position.getOffset(third).x,
                "wrap-reverse 下第一个 DOM 子项所在的列必须位于右侧（x 更大）");
    }

    @Test
    void justifyContentDistributesFreeSpaceWithinEachColumn() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; flex-wrap: wrap; align-content: flex-start; align-items: flex-start; justify-content: space-between; width: 200px; height: 200px;");
        document.body.appendChild(parent);
        Element first = fixedBox(document, 60, 80);
        Element second = fixedBox(document, 60, 80);
        parent.appendChild(first);
        parent.appendChild(second);

        // 单列两项 160px，剩余 40px 放进项间：first 贴顶，second 贴底。
        assertEquals(0, Position.getOffset(first).y, 0.01);
        assertEquals(120, Position.getOffset(second).y, 0.01);
    }

    @Test
    void alignContentDistributesFreeSpaceBetweenColumns() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; flex-wrap: wrap; align-content: space-between; align-items: flex-start; width: 200px; height: 200px;");
        document.body.appendChild(parent);
        Element first = fixedBox(document, 60, 80);
        Element second = fixedBox(document, 60, 80);
        Element third = fixedBox(document, 60, 80);
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);

        // 两列各 60 宽，容器 200：space-between 把第二列推到右边缘（x = 140）。
        assertEquals(0, Position.getOffset(first).x, 0.01);
        assertEquals(140, Position.getOffset(third).x, 0.01);
    }

    @Test
    void alignItemsCentersItemsWithinEachColumnWidth() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; flex-wrap: wrap; align-content: flex-start; align-items: center; width: 200px; height: 100px;");
        document.body.appendChild(parent);
        Element narrow = fixedBox(document, 40, 80);
        Element wide = fixedBox(document, 60, 80);
        parent.appendChild(narrow);
        parent.appendChild(wide);

        // 超高切列：两列各自宽 40/60，项在自己的列宽内水平居中（居中偏移 0）。
        assertEquals(0, Position.getOffset(narrow).x, 0.01);
        assertEquals(40, Position.getOffset(wide).x, 0.01);
    }

    @Test
    void rowGapAppliesWithinColumnAndColumnGapBetweenColumns() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; flex-wrap: wrap; align-content: flex-start; align-items: flex-start; row-gap: 10px; column-gap: 20px; width: 300px; height: 170px;");
        document.body.appendChild(parent);
        Element first = fixedBox(document, 60, 80);
        Element second = fixedBox(document, 60, 80);
        Element third = fixedBox(document, 60, 80);
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(third);

        // 列内：80 + 10(rowGap) + 80 = 170 恰好放下两项，third 切到第二列。
        assertEquals(90, Position.getOffset(second).y, 0.01);
        // 列间：第一列宽 60 + 20(columnGap) → 第二列 x = 80。
        assertEquals(80, Position.getOffset(third).x, 0.01);
    }

    @Test
    void wrappedColumnContentSizeSumsColumnWidths() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; flex-wrap: wrap; align-content: flex-start; align-items: flex-start; column-gap: 20px; width: 300px; height: 170px;");
        document.body.appendChild(parent);
        parent.appendChild(fixedBox(document, 60, 80));
        parent.appendChild(fixedBox(document, 60, 80));
        parent.appendChild(fixedBox(document, 40, 80));

        // 固有尺寸：宽 = 两列宽 + 列间 gap = 60 + 20 + 40 = 120，
        // 高 = 最高列的内容高（80+80=160，不是容器高 170）。
        Size content = Flex.computeContentSize(parent);
        assertEquals(120, content.width(), 0.01);
        assertEquals(160, content.height(), 0.01);
    }

    private static Element columnWrap(Document document, String extraStyle) {
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; flex-wrap: wrap; align-content: flex-start; align-items: flex-start; width: 200px; " + extraStyle);
        document.body.appendChild(parent);
        return parent;
    }

    private static Element fixedBox(Document document, int width, int height) {
        Element element = new Element(document, "div");
        element.setAttribute("style", "width: " + width + "px; height: " + height + "px;");
        return element;
    }
}
