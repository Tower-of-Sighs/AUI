package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 替换元素处于 {@code display:inline} 时，CSS 2.1 §10.3.1 的
 * "width/height 不适用于 non-replaced inline" 豁免不成立：指定尺寸必须生效。
 *
 * <p>回归点：{@code <texture>} 的默认 display 是 inline，但它的标签没进替换元素名单，
 * 被当成非替换行内元素后 width/height 归零，{@code Texture.drawTexture} 里
 * {@code size.width() <= 0} 直接静默 return，表现为 1.2.8 起只写行内 width/height 不出图。</p>
 */
class ReplacedInlineSizingTest {
    @Test
    void inlineTextureKeepsSpecifiedWidthAndHeight() {
        assertInlineSized("texture", "width: 32px; height: 24px;");
    }

    @Test
    void inlineSpriteKeepsSpecifiedWidthAndHeight() {
        // <sprite> 默认 display:block，这里显式 inline，覆盖与 <texture> 相同的判定路径。
        assertInlineSized("sprite", "display: inline; width: 32px; height: 24px;");
    }

    @Test
    void inlineImgStillKeepsSpecifiedWidthAndHeight() {
        assertInlineSized("img", "width: 32px; height: 24px;");
    }

    private static void assertInlineSized(String tag, String style) {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 400px; height: 200px;");

        Element row = document.createElement("div");
        row.setAttribute("style", "width: 300px;");
        document.body.appendChild(row);

        Element element = document.createElement(tag);
        element.setAttribute("style", style);
        row.appendChild(element);

        assertEquals("inline", element.getComputedStyle().display,
                tag + " 的 display 必须是 inline，用例才覆盖得到这条路径");
        assertEquals(32, Size.of(element).width(), 0.01, tag + " 的 width 必须生效");
        assertEquals(24, Size.of(element).height(), 0.01, tag + " 的 height 必须生效");
    }
}
