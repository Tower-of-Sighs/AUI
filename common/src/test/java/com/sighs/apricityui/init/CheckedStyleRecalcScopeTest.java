package com.sighs.apricityui.init;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * :checked 变化只请求「父节点子树」重算后的正确性与影响范围断言。
 * <p>
 * 依据：组合器（+ / ~）只向后看、选择器不支持 :has，因此影响集不会外溢到父节点子树之外。
 */
class CheckedStyleRecalcScopeTest {

    @Test
    void adjacentAndGeneralSiblingRulesFollowCheckedState() {
        Document document = documentWith("""
                .adjacent { display: none; }
                .general { display: none; }
                input:checked + .adjacent { display: block; }
                input:checked ~ .general { display: block; }
                """);
        Input toggle = new Input(document);
        toggle.setAttribute("type", "checkbox");
        Element adjacent = new Element(document, "section");
        adjacent.setAttribute("class", "adjacent");
        Element spacer = new Element(document, "section");
        Element general = new Element(document, "section");
        general.setAttribute("class", "general");
        document.body.appendChild(toggle);
        document.body.appendChild(adjacent);
        document.body.appendChild(spacer);
        document.body.appendChild(general);

        document.flushPendingStyleUpdates();
        assertEquals("none", adjacent.getComputedStyle().display);
        assertEquals("none", general.getComputedStyle().display);

        toggle.setChecked(true);
        document.flushPendingStyleUpdates();
        assertEquals("block", adjacent.getComputedStyle().display);
        assertEquals("block", general.getComputedStyle().display);

        toggle.setChecked(false);
        document.flushPendingStyleUpdates();
        assertEquals("none", adjacent.getComputedStyle().display);
        assertEquals("none", general.getComputedStyle().display);
    }

    @Test
    void radioGroupUncheckRefreshesTheDeselectedRadioFollowingSibling() {
        Document document = documentWith("""
                .indicator { display: none; }
                input:checked ~ .indicator { display: block; }
                """);
        Element sectionA = new Element(document, "section");
        Input radioA = radio(document, "pick", "a");
        Element indicatorA = new Element(document, "span");
        indicatorA.setAttribute("class", "indicator");
        sectionA.appendChild(radioA);
        sectionA.appendChild(indicatorA);

        // 第二个 radio 故意放在文档另一处的嵌套容器里，验证「按各自的父节点」请求重算。
        Element wrapper = new Element(document, "div");
        Element sectionB = new Element(document, "section");
        Input radioB = radio(document, "pick", "b");
        Element indicatorB = new Element(document, "span");
        indicatorB.setAttribute("class", "indicator");
        sectionB.appendChild(radioB);
        sectionB.appendChild(indicatorB);
        wrapper.appendChild(sectionB);

        document.body.appendChild(sectionA);
        document.body.appendChild(wrapper);

        radioA.setChecked(true);
        document.flushPendingStyleUpdates();
        assertTrue(radioA.isChecked());
        assertEquals("block", indicatorA.getComputedStyle().display);

        radioB.setChecked(true);
        document.flushPendingStyleUpdates();
        assertTrue(radioB.isChecked());
        assertFalse(radioA.isChecked());
        assertEquals("block", indicatorB.getComputedStyle().display);
        // 被取消选中的 radioA 的后续兄弟必须跟着更新为未选中态。
        assertEquals("none", indicatorA.getComputedStyle().display);
    }

    @Test
    void checkedToggleDoesNotRecalculateUnrelatedSubtree() {
        Document document = documentWith("""
                .panel { display: none; }
                input:checked ~ .panel { display: block; }
                """);
        Element left = new Element(document, "div");
        Input toggle = new Input(document);
        toggle.setAttribute("type", "checkbox");
        Element panel = new Element(document, "section");
        panel.setAttribute("class", "panel");
        left.appendChild(toggle);
        left.appendChild(panel);

        // 与 toggle 无关的另一棵子树，直接挂在 body 下：若还按整棵文档树重算，它一定会被扫到。
        CountingElement outside = new CountingElement(document, "div");
        document.body.appendChild(left);
        document.body.appendChild(outside);

        document.flushPendingStyleUpdates();
        assertEquals("none", panel.getComputedStyle().display);
        Style outsideBefore = outside.getComputedStyle();
        outside.recomputeCount = 0;

        toggle.setChecked(true);
        document.flushPendingStyleUpdates();

        assertEquals("block", panel.getComputedStyle().display);
        assertEquals(0, outside.recomputeCount, "无关子树不应被重算");
        assertSame(outsideBefore, outside.getComputedStyle(), "无关子树的 computed style 缓存不应被清空");
    }

    private static Document documentWith(String css) {
        Document document = TestDocumentFactory.createDocument();
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(css, cache, "test://checked-scope.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        return document;
    }

    private static Input radio(Document document, String name, String value) {
        Input input = new Input(document);
        input.setAttribute("type", "radio");
        input.setAttribute("name", name);
        input.setDefaultValue(value);
        return input;
    }

    /** 记录 recomputeStyleSelf 调用次数，用于观测增量重算实际覆盖了哪些元素。 */
    private static final class CountingElement extends Element {
        private int recomputeCount;

        private CountingElement(Document document, String tagName) {
            super(document, tagName);
        }

        @Override
        public boolean recomputeStyleSelf() {
            recomputeCount++;
            return super.recomputeStyleSelf();
        }
    }
}
