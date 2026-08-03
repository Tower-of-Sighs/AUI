package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CSS2 §10.1：absolute 的包含块是最近 positioned 祖先的 padding box，
 * 没有 positioned 祖先时是初始包含块（视口，随内容滚动）。
 */
class AbsoluteContainingBlockTest {

    @Test
    void absoluteUsesNearestPositionedAncestorInsteadOfDirectParent() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 300px;");

        Element outer = new Element(document, "div");
        outer.setAttribute("style", "position: relative; box-sizing: border-box;"
                + " width: 200px; height: 150px; padding: 10px; border: 2px solid #000; margin-left: 7px;");
        document.body.appendChild(outer);

        Element middle = new Element(document, "div");
        middle.setAttribute("style", "margin-left: 30px;");
        outer.appendChild(middle);

        Element inner = new Element(document, "div");
        inner.setAttribute("style", "margin-left: 25px;");
        middle.appendChild(inner);

        Element abs = new Element(document, "div");
        abs.setAttribute("style", "position: absolute; left: 10px; top: 5px; width: 20px; height: 10px;");
        inner.appendChild(abs);

        // 包含块是 outer 的 padding box：文档位置 = outer 边框原点 + border 2 + (10, 5)
        Position outerOrigin = Position.of(outer);
        Position absPosition = Position.of(abs);
        assertEquals(outerOrigin.x + 2 + 10, absPosition.x, 0.01);
        assertEquals(outerOrigin.y + 2 + 5, absPosition.y, 0.01);

        // 中间祖先的 margin 不影响 absolute 的最终位置（旧实现会锚到直接父元素 inner）
        Position innerOrigin = Position.of(inner);
        assertEquals(1, Math.abs(absPosition.x - (innerOrigin.x + 10)) > 1 ? 1 : 0);
    }

    @Test
    void absolutePercentageSizeAndOffsetResolveAgainstPositionedAncestor() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 300px;");

        Element outer = new Element(document, "div");
        outer.setAttribute("style", "position: relative; box-sizing: border-box;"
                + " width: 200px; height: 150px; padding: 10px; border: 2px solid #000;");
        document.body.appendChild(outer);

        Element middle = new Element(document, "div");
        middle.setAttribute("style", "width: 50px; height: 40px;");
        outer.appendChild(middle);

        Element abs = new Element(document, "div");
        abs.setAttribute("style", "position: absolute; left: 25%; top: 10%; width: 50%; height: 20%;");
        middle.appendChild(abs);

        // outer 的 padding box：200-4=196 宽，150-4=146 高
        assertEquals(98, Size.of(abs).width(), 0.01);
        assertEquals(29.2, Size.of(abs).height(), 0.01);
        Position outerOrigin = Position.of(outer);
        Position absPosition = Position.of(abs);
        assertEquals(outerOrigin.x + 2 + 49, absPosition.x, 0.01);
        assertEquals(outerOrigin.y + 2 + 14.6, absPosition.y, 0.01);
    }

    @Test
    void absoluteWithoutPositionedAncestorUsesInitialContainingBlock() {
        Size.setViewportOverride(640, 360);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "width: 300px; height: 200px;");

            Element wrapper = new Element(document, "div");
            wrapper.setAttribute("style", "margin-left: 50px; margin-top: 40px;");
            document.body.appendChild(wrapper);

            Element abs = new Element(document, "div");
            abs.setAttribute("style", "position: absolute; left: 0; top: 0; width: 30px; height: 20px;");
            wrapper.appendChild(abs);

            // 初始包含块原点是文档视口原点，不是直接父元素
            Position absPosition = Position.of(abs);
            assertEquals(0, absPosition.x, 0.01);
            assertEquals(0, absPosition.y, 0.01);

            // 百分比相对视口解析
            Element percent = new Element(document, "div");
            percent.setAttribute("style", "position: absolute; left: 10%; top: 25%; width: 50%; height: 50%;");
            wrapper.appendChild(percent);
            Position percentPosition = Position.of(percent);
            assertEquals(64, percentPosition.x, 0.01);
            assertEquals(90, percentPosition.y, 0.01);
            assertEquals(320, Size.of(percent).width(), 0.01);
            assertEquals(180, Size.of(percent).height(), 0.01);
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void absoluteContainingBlockUpdatesWhenAncestorBecomesPositioned() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 300px;");

        Element outer = new Element(document, "div");
        outer.setAttribute("style", "width: 200px; height: 150px; margin-left: 40px;");
        document.body.appendChild(outer);

        Element middle = new Element(document, "div");
        middle.setAttribute("style", "margin-left: 30px; width: 100px; height: 80px;");
        outer.appendChild(middle);

        Element abs = new Element(document, "div");
        abs.setAttribute("style", "position: absolute; left: 0; top: 0; width: 20px; height: 20px;");
        middle.appendChild(abs);

        // 没有 positioned 祖先：锚定初始包含块（视口原点）
        Position before = Position.of(abs);
        assertEquals(0, before.x, 0.01);
        assertEquals(0, before.y, 0.01);

        // 中间祖先变为 relative 后，包含块变为它的 padding box（border 内缘，不含 padding）
        middle.setAttribute("style", "position: relative; margin-left: 30px; width: 100px; height: 80px; border: 5px solid #000;");
        Position middleOrigin = Position.of(middle);
        Position after = Position.of(abs);
        assertEquals(middleOrigin.x + 5, after.x, 0.01);
        assertEquals(middleOrigin.y + 5, after.y, 0.01);
    }

    @Test
    void absoluteInsideAbsoluteUsesAbsoluteParentAsContainingBlock() {
        Size.setViewportOverride(640, 360);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "width: 400px; height: 300px;");

            Element outerAbs = new Element(document, "div");
            outerAbs.setAttribute("style", "position: absolute; left: 50px; top: 40px; width: 200px; height: 100px;");
            document.body.appendChild(outerAbs);

            Element childAbs = new Element(document, "div");
            childAbs.setAttribute("style", "position: absolute; left: 10px; top: 5px; width: 20px; height: 20px;");
            outerAbs.appendChild(childAbs);

            Position childPosition = Position.of(childAbs);
            assertEquals(60, childPosition.x, 0.01);
            assertEquals(45, childPosition.y, 0.01);

            // 百分比相对 absolute 父元素的 padding box
            Element percent = new Element(document, "div");
            percent.setAttribute("style", "position: absolute; right: 0; bottom: 0; width: 50%; height: 10%;");
            outerAbs.appendChild(percent);
            assertEquals(100, Size.of(percent).width(), 0.01);
            assertEquals(10, Size.of(percent).height(), 0.01);
            Position percentPosition = Position.of(percent);
            assertEquals(50 + 200 - 100, percentPosition.x, 0.01);
            assertEquals(40 + 100 - 10, percentPosition.y, 0.01);
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void absolutePositionIsStableUnderIntermediateSiblingGrowth() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 300px;");

        Element outer = new Element(document, "div");
        outer.setAttribute("style", "position: relative; width: 200px; height: 200px;");
        document.body.appendChild(outer);

        Element spacer = new Element(document, "div");
        spacer.setAttribute("style", "height: 20px;");
        outer.appendChild(spacer);

        Element middle = new Element(document, "div");
        middle.setAttribute("style", "margin-left: 30px;");
        outer.appendChild(middle);

        Element abs = new Element(document, "div");
        abs.setAttribute("style", "position: absolute; left: 10px; top: 10px; width: 20px; height: 20px;");
        middle.appendChild(abs);

        Position before = Position.of(abs);

        // 中间兄弟节点长高会移动 middle，但 absolute 锚定 outer，不应移动
        spacer.setAttribute("style", "height: 80px;");
        Position middleAfter = Position.of(middle);
        assertEquals(80, middleAfter.y - Position.of(outer).y, 0.01);

        Position after = Position.of(abs);
        assertEquals(before.x, after.x, 0.01);
        assertEquals(before.y, after.y, 0.01);
    }

    @Test
    void absoluteOffsetsIgnoreContainingBlockPadding() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 300px;");

        Element outer = new Element(document, "div");
        outer.setAttribute("style", "position: relative; box-sizing: border-box;"
                + " width: 200px; height: 100px; padding: 15px; border: 5px solid #000;");
        document.body.appendChild(outer);

        Element abs = new Element(document, "div");
        abs.setAttribute("style", "position: absolute; right: 0; bottom: 0; width: 20px; height: 20px;");
        outer.appendChild(abs);

        // padding box 为 190x90（border-box 减双边 border）；right/bottom:0 锚定 padding box 右下角
        Position outerOrigin = Position.of(outer);
        Position absPosition = Position.of(abs);
        assertEquals(outerOrigin.x + 5 + (190 - 20), absPosition.x, 0.01);
        assertEquals(outerOrigin.y + 5 + (90 - 20), absPosition.y, 0.01);
    }
}
