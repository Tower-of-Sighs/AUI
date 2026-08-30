package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LayoutHotspotOptimizationTest {
    @Test
    void reusesBoxInnerSizeObjectsUntilInputsChange() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        element.setAttribute("style", "width:120px;height:40px;padding:4px;border:2px solid;");
        document.body.appendChild(element);

        Box box = Box.of(element);
        assertSame(box.rawInnerSize(), box.rawInnerSize());
        assertSame(box.innerSize(), box.innerSize());

        Size before = box.innerSize();
        element.getRenderer().size.clear();
        Size after = box.innerSize();
        assertEquals(before, after);
    }

    @Test
    void reusesBoxOuterSizeUntilElementSizeChanges() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        element.setAttribute("style", "width:120px;height:40px;margin:3px 5px;");
        document.body.appendChild(element);

        Box box = Box.of(element);
        assertSame(box.size(), box.size());

        Size before = box.size();
        element.getRenderer().size.clear();
        Size after = box.size();
        assertEquals(before, after);
        assertSame(after, box.size());
    }

    @Test
    void resolvesScaleWidthIterativelyThroughPercentageAncestors() {
        Size.setViewportOverride(1000, 800);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "width:800px;height:400px;");

            Element parent = document.createElement("div");
            parent.setAttribute("style", "width:50%;");
            document.body.appendChild(parent);

            Element child = document.createElement("div");
            parent.appendChild(child);

            assertEquals(400, Size.getScaleWidth(child), 0.0001);
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void resolvesScaleHeightIterativelyThroughPercentageAncestors() {
        Size.setViewportOverride(1000, 800);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "height:400px;");

            Element parent = document.createElement("div");
            parent.setAttribute("style", "height:50%;");
            document.body.appendChild(parent);

            Element child = document.createElement("div");
            parent.appendChild(child);

            assertEquals(200, Size.getScaleHeight(child), 0.0001);
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void resolvesScaleHeightWithoutGrowingTheCallStackForDeepAutoAncestors() {
        Size.setViewportOverride(1000, 800);
        try {
            Document document = TestDocumentFactory.createDocument();
            Element deepest = document.createElement("div");
            Element leaf = deepest;
            for (int i = 0; i < 4096; i++) {
                Element parent = document.createElement("div");
                parent.getRenderer().computedStyle.set(new Style());
                leaf.parentNode = parent;
                leaf.parentElement = parent;
                leaf = parent;
            }

            assertEquals(800, Size.getScaleHeight(deepest), 0.0001);
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void invalidatesStyleDerivedMeasureObjectsWhenTextStyleChanges() {
        Document document = TestDocumentFactory.createDocument();
        Element owner = document.createElement("div");
        Element textElement = document.createElement("span");
        textElement.setAttribute("style", "color: #000000;");
        owner.appendChild(textElement);
        document.body.appendChild(owner);
        Object cached = new Object();

        LayoutMeasureCache.begin();
        try {
            LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_NORMAL_FLOW,
                    owner, 100, Double.NaN, false, cached);
            assertSame(cached, LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_NORMAL_FLOW,
                    owner, 100, Double.NaN, false));
        } finally {
            LayoutMeasureCache.end();
        }

        LayoutMeasureCache.begin();
        try {
            assertSame(cached, LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_NORMAL_FLOW,
                    owner, 100, Double.NaN, false));
            textElement.setAttribute("style", "color: #ffffff;");
            assertNull(LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_NORMAL_FLOW,
                    owner, 100, Double.NaN, false));
        } finally {
            LayoutMeasureCache.end();
        }
    }

    @Test
    void inheritedMotionColorRefreshKeepsTextLayoutDependencyStable() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = document.createElement("div");
        parent.setAttribute("style", "color: #000000;");
        Element child = document.createElement("span");
        child.setTextContent("animated label");
        parent.appendChild(child);
        document.body.appendChild(parent);

        StyleFrameCache.begin();
        LayoutMeasureCache.begin();
        try {
            parent.getComputedStyle();
            child.getComputedStyle();
            Object cached = new Object();
            LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_NORMAL_FLOW,
                    parent, 100, Double.NaN, false, cached);
            long dependency = parent.getRenderer().textDependency();

            Style animated = parent.getComputedStyle().clone();
            animated.color = "#ffffff";
            StyleFrameCache.put(parent, animated);
            child.refreshInheritedStyleForMotion();

            assertEquals(0xFFFFFFFF, Text.getFontColor(child));
            assertEquals(dependency, parent.getRenderer().textDependency());
            assertSame(cached, LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_NORMAL_FLOW,
                    parent, 100, Double.NaN, false));
        } finally {
            LayoutMeasureCache.end();
            StyleFrameCache.end();
        }
    }

    @Test
    void colorTransitionDoesNotBumpTextDependencyPerFrame() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        element.setAttribute("style", "color: #000000; transition: color 1000ms linear;");
        Element child = document.createElement("span");
        child.setTextContent("animated label");
        element.appendChild(child);
        document.body.appendChild(element);
        document.flushPendingStyleUpdates();

        element.setAttribute("style", "color: #ffffff; transition: color 1000ms linear;");
        document.flushPendingStyleUpdates();
        long elementDependency = element.getRenderer().textDependency();
        long childDependency = child.getRenderer().textDependency();

        StyleFrameCache.begin();
        try {
            document.stepMotionRender();
            assertEquals(elementDependency, element.getRenderer().textDependency());
            assertEquals(childDependency, child.getRenderer().textDependency());
        } finally {
            StyleFrameCache.end();
        }

        element.setAttribute("style", "color: #ffffff; transition: none;");
        document.flushPendingStyleUpdates();
    }
}
