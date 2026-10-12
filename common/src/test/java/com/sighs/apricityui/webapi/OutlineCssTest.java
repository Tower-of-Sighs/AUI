package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dom.RenderElement;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.render.AABB;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Style;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSS outline：简写展开、初始值、颜色回落、绘制参数，以及"不参与布局"这条契约
 * （脏位只有重绘、视觉包围盒按焦点框外扩）。
 */
class OutlineCssTest {
    @Test
    void shorthandExpandsIntoLonghandsAndBoxPaintParameters() {
        Document document = TestDocumentFactory.createDocument();
        applyStylesheet(document, ".ring { outline: 2px solid #ff0000; }");
        Element ring = appendClass(document, "ring");

        assertEquals("2px", ring.getComputedStyle().outlineWidth);
        assertEquals("solid", ring.getComputedStyle().outlineStyle);
        assertEquals("#ff0000", ring.getComputedStyle().outlineColor);
        assertEquals("2px solid #ff0000", ring.getComputedStyle().outline);

        Box.Outline outline = Box.of(ring).outline;
        assertTrue(outline.isVisible());
        assertEquals(2, outline.width(), 0.001);
        assertEquals(0xFFFF0000, outline.color());
        assertEquals(0, outline.offset(), 0.001);
    }

    @Test
    void omittedComponentsFallBackToTheirInitialValues() {
        Document document = TestDocumentFactory.createDocument();
        applyStylesheet(document, ".width-only { outline: 2px #ff0000; } .none { outline: none; }");
        Element widthOnly = appendClass(document, "width-only");
        Element none = appendClass(document, "none");

        // 简写省略线型时线型取初始值 none：有宽度有颜色但什么都不画。
        assertEquals("2px", widthOnly.getComputedStyle().outlineWidth);
        assertEquals("#ff0000", widthOnly.getComputedStyle().outlineColor);
        assertEquals("none", widthOnly.getComputedStyle().outlineStyle);
        assertFalse(Box.of(widthOnly).outline.isVisible());

        assertEquals("none", none.getComputedStyle().outlineStyle);
        assertFalse(Box.of(none).outline.isVisible());

        // 只写长写、不写线型也一样不画（outline-style 的初始值就是 none）。
        Style longhandOnly = new Style();
        longhandOnly.merge("outline-width: 4px; outline-color: #00ff00;");
        longhandOnly.finalizeComputedValues(null);
        assertEquals("4px", longhandOnly.outlineWidth);
        assertEquals("none", longhandOnly.outlineStyle);
        assertFalse(Box.parseOutline(longhandOnly).isVisible());
    }

    @Test
    void outlineColorFallsBackToCurrentColor() {
        Document document = TestDocumentFactory.createDocument();
        applyStylesheet(document, ".ring { color: #123456; outline: 2px solid; }");
        Element ring = appendClass(document, "ring");

        assertEquals("auto", ring.getComputedStyle().outlineColor);
        assertEquals(0xFF123456, Box.of(ring).outline.color());
    }

    @Test
    void widthKeywordsAndNegativeOffsetMatchBrowserValues() {
        Style style = new Style();
        style.merge("outline: thick double currentColor; outline-offset: -2px; color: #00ff00;");
        style.finalizeComputedValues(null);

        Box.Outline outline = Box.parseOutline(style);
        assertEquals(5, outline.width(), 0.001);
        assertEquals(-2, outline.offset(), 0.001);
        assertEquals(0xFF00FF00, outline.color());
    }

    @Test
    void moreSpecificShorthandOutranksLessSpecificLonghand() {
        Document document = TestDocumentFactory.createDocument();
        // 简写必须在解析期展开成三个长写，否则 div 的 outline-width 会反过来盖掉 .ring 的简写。
        applyStylesheet(document, "div { outline-width: 1px; } .ring { outline: 4px solid #ffffff; }");
        Element ring = appendClass(document, "ring");

        assertEquals("4px", ring.getComputedStyle().outlineWidth);
        assertEquals("solid", ring.getComputedStyle().outlineStyle);
        assertEquals("#ffffff", ring.getComputedStyle().outlineColor);
        assertEquals(4, Box.of(ring).outline.width(), 0.001);
    }

    @Test
    void outlineChangesRepaintWithoutRelayout() {
        Document document = TestDocumentFactory.createDocument();
        Element ring = appendClass(document, "ring");

        Style origin = ring.getComputedStyle().clone();
        Style current = origin.clone();
        current.merge("outline: 3px solid #00ff00; outline-offset: 2px;");

        int dirtyMask = RenderElement.observeStyleMask(ring, origin, current);

        assertEquals(0, dirtyMask & Drawer.RELAYOUT, "outline 不占布局，不该触发重排");
        assertTrue((dirtyMask & Drawer.REPAINT) != 0, "outline 变化必须重绘");
    }

    @Test
    void outlineReachGrowsTheVisualBounds() {
        Document document = TestDocumentFactory.createDocument();
        Element plain = inlineStyled(document, "width:40px;height:20px;");
        Element ring = inlineStyled(document, "width:40px;height:20px;outline:2px solid #ff0000;outline-offset:3px;");
        Element insetRing = inlineStyled(document, "width:40px;height:20px;outline:2px solid #ff0000;outline-offset:-3px;");
        document.commitRenderState();

        AABB plainBounds = Rect.of(plain).getVisualBounds();
        AABB ringBounds = Rect.of(ring).getVisualBounds();
        AABB insetBounds = Rect.of(insetRing).getVisualBounds();

        // 焦点框画在盒外、且不占布局：相邻元素的视觉包围盒必须跟着外扩 offset + width。
        assertEquals(plainBounds.width() + 2 * (3 + 2), ringBounds.width(), 0.001);
        assertEquals(plainBounds.height() + 2 * (3 + 2), ringBounds.height(), 0.001);
        // 负 offset 的焦点框落在盒内，包围盒不外扩。
        assertEquals(plainBounds.width(), insetBounds.width(), 0.001);
    }

    private static Element inlineStyled(Document document, String css) {
        Element element = new Element(document, "div");
        element.setAttribute("style", css);
        document.body.appendChild(element);
        return element;
    }

    private static Element appendClass(Document document, String className) {
        Element element = new Element(document, "div");
        element.setAttribute("class", className);
        document.body.appendChild(element);
        document.flushPendingStyleUpdates();
        return element;
    }

    private static void applyStylesheet(Document document, String css) {
        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS(css, cache, "test://outline.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
    }
}
