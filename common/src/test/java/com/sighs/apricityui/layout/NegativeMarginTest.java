package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for negative margins (CSS2 §8.3 不限制 margin 符号)。
 * 历史上 Box.resolveBoxLength 用 Math.max(0, …) 把负 margin 钳到 0，
 * 导致 ore 主题加载按钮的 ::after  spinner（left/top:50% + margin:-10px 0 0 -10px）
 * 相对宿主向右下偏移。padding 不允许负值，仍必须钳制。
 */
class NegativeMarginTest {
    @Test
    void negativeMarginsSurviveShorthandAndLonghand() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        element.setAttribute("style", "margin:-10px 0 0 -10px;");
        document.body.appendChild(element);

        Box box = Box.of(element);
        assertEquals(-10, box.getMarginTop(), 0.0001);
        assertEquals(0, box.getMarginRight(), 0.0001);
        assertEquals(0, box.getMarginBottom(), 0.0001);
        assertEquals(-10, box.getMarginLeft(), 0.0001);

        Element longhand = document.createElement("div");
        longhand.setAttribute("style", "margin-left:-6px;margin-top:2px;");
        document.body.appendChild(longhand);

        Box longhandBox = Box.of(longhand);
        assertEquals(-6, longhandBox.getMarginLeft(), 0.0001);
        assertEquals(2, longhandBox.getMarginTop(), 0.0001);
    }

    @Test
    void negativePaddingIsStillClampedToZero() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        element.setAttribute("style", "padding:-5px;padding-left:-3px;");
        document.body.appendChild(element);

        Box box = Box.of(element);
        assertEquals(0, box.getPaddingTop(), 0.0001);
        assertEquals(0, box.getPaddingLeft(), 0.0001);
    }

    @Test
    void absoluteChildBorderBoxShiftsByItsNegativeMargin() {
        Size.setViewportOverride(1000, 800);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "margin:0;padding:0;");

            Element parent = document.createElement("div");
            parent.setAttribute("style", "position:relative;width:100px;height:40px;");
            document.body.appendChild(parent);

            // ore.css 加载按钮 ::after 的定位方式：left/top 50% 把 margin 边
            // 对齐到包含块中心，负 margin 再把 border box 拉回真正居中。
            Element child = document.createElement("div");
            child.setAttribute("style",
                    "position:absolute;left:50%;top:50%;width:16px;height:16px;margin:-10px 0 0 -10px;");
            parent.appendChild(child);

            Position parentPos = Position.forRender(parent);
            Position childPos = Position.forRender(child);
            Box childBox = Box.of(child);

            // left/top 定位的是 margin 边（CSS2 §10.3.7/§10.3.8）：
            // margin 边在 50% 处，border box（绘制位置）= margin 边 + 负 margin。
            assertEquals(parentPos.x + 50, childPos.x, 0.0001);
            assertEquals(parentPos.y + 20, childPos.y, 0.0001);
            assertEquals(parentPos.x + 40, childPos.x + childBox.getMarginLeft(), 0.0001);
            assertEquals(parentPos.y + 10, childPos.y + childBox.getMarginTop(), 0.0001);
        } finally {
            Size.clearViewportOverride();
        }
    }
}
