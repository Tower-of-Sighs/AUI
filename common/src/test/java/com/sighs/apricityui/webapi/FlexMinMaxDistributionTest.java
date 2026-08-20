package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * flex 分配遵守 min/max 的回归测试（CSS Flexbox §9.2.3 hypothetical main
 * size + §9.9 冻结重分配），以及 flex-basis: content 关键字。
 * <p>
 * 期望几何来自规范的确定行为：被 max 钳住的项冻结并把多余空间重分给兄弟，
 * 主轴不留缝隙；min/max 冲突时 min 胜出。
 */
class FlexMinMaxDistributionTest {

    @Test
    void maxClampedGrowItemFreezesAndRedistributesToSiblings() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element parent = flexRow(document, "width: 300px; height: 60px;");
        Element a = growItem(document, "max-width: 100px;");
        Element b = growItem(document, null);
        parent.appendChild(a);
        parent.appendChild(b);

        // 无 max 时各分 150；A 冻结在 100 后，剩余 200 全部归 B（§9.9 重分配），
        // 主轴不留缝隙：B 的 x 必须紧跟 A 的右边。
        assertEquals(100, Box.of(a).size().width(), 0.01);
        assertEquals(200, Box.of(b).size().width(), 0.01);
        assertEquals(0, Position.getOffset(a).x, 0.01);
        assertEquals(100, Position.getOffset(b).x, 0.01);
    }

    @Test
    void leftoverAfterAllItemsMaxedGoesToJustifyContent() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element parent = flexRow(document, "justify-content: flex-end; width: 300px; height: 60px;");
        Element a = growItem(document, "max-width: 50px;");
        Element b = growItem(document, "max-width: 50px;");
        parent.appendChild(a);
        parent.appendChild(b);

        // 两项都冻结在 50，剩余 200 由 justify-content: flex-end 推到主轴末端。
        assertEquals(50, Box.of(a).size().width(), 0.01);
        assertEquals(50, Box.of(b).size().width(), 0.01);
        assertEquals(200, Position.getOffset(a).x, 0.01);
        assertEquals(250, Position.getOffset(b).x, 0.01);
    }

    @Test
    void flexBasisAboveMaxIsClampedBeforeDistribution() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element parent = flexRow(document, "width: 300px; height: 60px;");
        Element a = new Element(document, "div");
        a.setAttribute("style", "flex-basis: 200px; max-width: 100px; height: 40px;");
        parent.appendChild(a);

        // §9.2.3：hypothetical main size 先钳到 100，容器有剩余也不发生 grow。
        assertEquals(100, Box.of(a).size().width(), 0.01);
        assertEquals(0, Position.getOffset(a).x, 0.01);
    }

    @Test
    void maxClampedItemStaysAtMaxDuringShrink() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element parent = flexRow(document, "width: 200px; height: 60px;");
        Element a = new Element(document, "div");
        a.setAttribute("style", "flex: 0 1 150px; max-width: 80px; height: 40px;");
        Element b = new Element(document, "div");
        b.setAttribute("style", "flex: 0 1 150px; height: 40px;");
        parent.appendChild(a);
        parent.appendChild(b);

        // §9.9：A 的 base(150) 超过 max(80)，收缩时冻结在 80；
        // 20px 缺额全部由 B 承担（150 - 20 = 130）。
        assertEquals(80, Box.of(a).size().width(), 0.01);
        assertEquals(120, Box.of(b).size().width(), 0.01);
        assertEquals(80, Position.getOffset(b).x, 0.01);
    }

    @Test
    void minWinsOverMaxWhenTheyConflict() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element parent = flexRow(document, "width: 300px; height: 60px;");
        Element a = new Element(document, "div");
        a.setAttribute("style", "flex: 0 1 150px; min-width: 120px; max-width: 100px; height: 40px;");
        parent.appendChild(a);

        // CSS 冲突规则：min 胜出，used = 120。
        assertEquals(120, Box.of(a).size().width(), 0.01);
    }

    @Test
    void maxHeightClampsColumnGrowItem() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; align-items: flex-start; width: 100px; height: 300px;");
        document.body.appendChild(parent);
        Element a = new Element(document, "div");
        a.setAttribute("style", "flex: 1; width: 60px; max-height: 100px;");
        Element b = new Element(document, "div");
        b.setAttribute("style", "flex: 1; width: 60px;");
        parent.appendChild(a);
        parent.appendChild(b);

        // column 镜像：A 冻结在 100，B 分到 200，B 紧跟 A 下方。
        assertEquals(100, Box.of(a).size().height(), 0.01);
        assertEquals(200, Box.of(b).size().height(), 0.01);
        assertEquals(100, Position.getOffset(b).y, 0.01);
    }

    @Test
    void borderBoxMaxWidthLeavesNoDoubleClampGap() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element parent = flexRow(document, "width: 300px; height: 60px;");
        Element a = new Element(document, "div");
        a.setAttribute("style", "flex: 1; box-sizing: border-box; max-width: 120px; padding-left: 10px; padding-right: 10px; height: 40px;");
        Element b = growItem(document, null);
        parent.appendChild(a);
        parent.appendChild(b);

        // max 按 border-box 总量解释（引擎既有口径）：A 的 border-box 宽 = 120，
        // B = 180 且紧跟 A，不出现 padding 宽度的缝隙。
        assertEquals(120, Box.of(a).size().width(), 0.01);
        assertEquals(180, Box.of(b).size().width(), 0.01);
        assertEquals(120, Position.getOffset(b).x, 0.01);
    }

    @Test
    void flexBasisContentUsesNaturalSizeInsteadOfCollapsing() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element parent = flexRow(document, "width: 300px; height: 60px;");
        Element a = new Element(document, "div");
        a.setAttribute("style", "flex-basis: content; height: 40px;");
        Element inner = new Element(document, "div");
        inner.setAttribute("style", "width: 70px; height: 20px;");
        a.appendChild(inner);
        parent.appendChild(a);

        // flex-basis: content 取内容尺寸（70px 子项），不能塌缩成 0。
        assertEquals(70, Box.of(a).size().width(), 0.01);
    }

    private static Element flexRow(Document document, String style) {
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; align-items: flex-start; " + style);
        document.body.appendChild(parent);
        return parent;
    }

    private static Element growItem(Document document, String extraStyle) {
        Element element = new Element(document, "div");
        element.setAttribute("style", "flex: 1; height: 40px;"
                + (extraStyle == null ? "" : " " + extraStyle));
        return element;
    }
}
