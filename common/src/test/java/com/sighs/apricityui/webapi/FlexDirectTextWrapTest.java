package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.TextSelection;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Flex;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * flex 容器匿名文本项（直接文本）软换行的回归测试。
 * 浏览器行为：窄 row 容器里的直接文本按容器内容宽折行；column 方向、
 * white-space: nowrap、无约束自然测量（max-content）都不折行。
 */
class FlexDirectTextWrapTest {

    private static final String LONG_TEXT =
            "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda";

    @Test
    void directTextSoftWrapsAtRowContainerContentWidth() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = flexRow(document, "width: 120px; height: 200px;");
        parent.innerText = LONG_TEXT;

        List<Flex.DirectTextLayout> layouts = Flex.computeDirectTextLayouts(parent);
        assertEquals(1, layouts.size());
        Flex.DirectTextLayout layout = layouts.get(0);
        Text text = layout.text();

        double naturalWidth = Text.measureLine(text, text.content);
        assertTrue(naturalWidth > 120, "前置条件：文本自然宽度必须超过容器宽");

        // 折行：多行、行宽不超过容器内容宽、总高 = 行数 × lineHeight。
        assertTrue(layout.lines().size() > 1, "窄容器中的直接文本必须软换行");
        assertTrue(text.size.width() <= 120.01, "折行后宽度不能超过容器内容宽");
        assertEquals(layout.lines().size() * text.lineHeight, text.size.height(), 0.01);
    }

    @Test
    void autoHeightRowContainerGrowsToWrappedTextHeight() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = flexRow(document, "width: 120px;");
        parent.innerText = LONG_TEXT;

        Flex.DirectTextLayout layout = Flex.computeDirectTextLayouts(parent).get(0);
        assertTrue(layout.lines().size() > 1, "前置条件：文本必须折行");

        // 定宽 auto 高容器：内容高必须按折行后的文本高计算（不能按单行）。
        assertEquals(layout.text().size.height(), Size.of(parent).height(), 0.5,
                "auto 高容器的内容高必须等于折行后文本高度");
    }

    @Test
    void nowrapWhiteSpaceKeepsDirectTextOnOneLine() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = flexRow(document, "white-space: nowrap; width: 120px; height: 200px;");
        parent.innerText = LONG_TEXT;

        Flex.DirectTextLayout layout = Flex.computeDirectTextLayouts(parent).get(0);
        assertEquals(1, layout.lines().size(), "white-space: nowrap 不允许软换行");
        assertEquals(Text.measureLine(layout.text(), layout.text().content),
                layout.text().size.width(), 0.01);
    }

    @Test
    void columnDirectionNeverSoftWrapsDirectText() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; flex-direction: column; align-items: flex-start; width: 120px; height: 300px;");
        document.body.appendChild(parent);
        parent.innerText = LONG_TEXT;

        // column 主轴是高，文本宽度不受主轴约束：不折行（浏览器一致）。
        Flex.DirectTextLayout layout = Flex.computeDirectTextLayouts(parent).get(0);
        assertEquals(1, layout.lines().size());
    }

    @Test
    void unconstrainedNaturalMeasurementStaysMaxContent() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        // inline-flex 无宽度声明：固有宽度 = max-content = 不折行的单行宽。
        // （有约束的自然测量才允许折行，否则文本与容器宽度会互相循环依赖。）
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: inline-flex;");
        document.body.appendChild(parent);
        parent.innerText = LONG_TEXT;

        Size natural = Size.natural(parent);
        assertTrue(natural.width() > 120,
                "无约束自然测量必须保持 max-content 单行宽度，实际: " + natural.width());
    }

    @Test
    void hitTestingFollowsWrappedLines() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 400px;");

        Element parent = flexRow(document, "width: 120px; height: 200px;");
        parent.innerText = LONG_TEXT;

        Flex.DirectTextLayout layout = Flex.computeDirectTextLayouts(parent).get(0);
        assertTrue(layout.lines().size() > 1, "前置条件：文本必须折行");
        Text text = layout.text();

        Position origin = Position.forRender(parent);
        Box box = Box.of(parent);
        double px = origin.x + box.getMarginLeft() + layout.position().x;
        double py = origin.y + box.getMarginTop() + layout.position().y;

        // 第二行内部命中；整块文本下方不命中。
        assertTrue(TextSelection.isPositionOverSelectableText(parent,
                px + 2, py + text.lineHeight * 1.5),
                "折行后第二行的点击必须命中文本");
        assertFalse(TextSelection.isPositionOverSelectableText(parent,
                px + 2, py + text.size.height() + text.lineHeight),
                "折行后文本块下方的点击不得命中");
    }

    private static Element flexRow(Document document, String extraStyle) {
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "display: flex; align-items: flex-start; " + extraStyle);
        document.body.appendChild(parent);
        return parent;
    }
}
