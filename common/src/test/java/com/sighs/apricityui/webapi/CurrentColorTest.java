package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.Gradient;
import com.sighs.apricityui.style.Background;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * issue#145 回归：CSS {@code currentColor} 在边框、背景和渐变色标中必须取元素自身
 * 计算出的 {@code color}（含继承），不能落到颜色名称表里变成透明。
 */
class CurrentColorTest {
    @Test
    void borderUsesElementColor() {
        Element element = inlineStyled("color:#a4ff80;border:3px solid currentColor;");

        assertEquals(0xFFA4FF80, Box.of(element).getBorderTopSide().color().getValue());
    }

    @Test
    void inheritedColorFeedsCurrentColorBorder() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "color:#ffc16b;");
        document.body.appendChild(parent);
        Element child = new Element(document, "div");
        child.setAttribute("style", "border:3px solid currentColor;");
        parent.appendChild(child);

        assertEquals(0xFFFFC16B, Box.of(child).getBorderTopSide().color().getValue());
    }

    @Test
    void backgroundColorUsesElementColor() {
        Element element = inlineStyled("color:#a4ff80;background-color:currentColor;");

        assertEquals(0xFFA4FF80, backgroundArgb(element));
    }

    @Test
    void backgroundShorthandTreatsCurrentColorAsColor() {
        Element element = inlineStyled("color:#a4ff80;background:currentColor;");

        assertEquals(0xFFA4FF80, backgroundArgb(element));
        assertEquals("none", element.getComputedStyle().backgroundImage, "currentColor 不能被当成背景位置");
    }

    @Test
    void gradientStopUsesElementColor() {
        Element element = inlineStyled(
                "color:#a4ff80;background-image:linear-gradient(90deg, currentColor 50%, transparent 50%);");

        Gradient gradient = Background.of(element).getLayers().get(0).gradient;
        assertEquals(0xFFA4FF80, gradient.stops().get(0).color);
        assertEquals(0, gradient.stops().get(1).color);
    }

    @Test
    void colorChangeRebuildsCachedCurrentColorBackground() {
        Element element = inlineStyled("color:#112233;background-color:currentColor;");
        assertEquals(0xFF112233, backgroundArgb(element));

        element.setAttribute("style", "color:#445566;background-color:currentColor;");
        element.document.flushPendingStyleUpdates();

        assertEquals(0xFF445566, backgroundArgb(element),
                "文字色变化后，背景里烘焙的 currentColor 必须按新颜色重建");
    }

    private static int backgroundArgb(Element element) {
        return new Color(Background.of(element).color).getValue();
    }

    private static Element inlineStyled(String css) {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");
        element.setAttribute("style", css);
        document.body.appendChild(element);
        return element;
    }
}
