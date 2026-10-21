package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryQueryScopeTest {
    @Test
    void nestedScopesKeepLayoutCachingActiveUntilTheOuterScopeCloses() {
        assertFalse(LayoutMeasureCache.isActive());
        try (GeometryQueryScope outer = GeometryQueryScope.open()) {
            assertTrue(LayoutMeasureCache.isActive());
            try (GeometryQueryScope inner = GeometryQueryScope.open()) {
                assertTrue(LayoutMeasureCache.isActive());
            }
            assertTrue(LayoutMeasureCache.isActive());
        }
        assertFalse(LayoutMeasureCache.isActive());
    }

    @Test
    void publicBoundingRectEnablesLayoutCachingForAnUncommittedElement() {
        Document document = TestDocumentFactory.createDocument();
        ProbeElement element = new ProbeElement(document);
        element.setAttribute("style", "width:40px;height:20px;");
        document.body.appendChild(element);

        element.getBoundingClientRect();

        assertTrue(element.observedLayoutScope);
        assertFalse(LayoutMeasureCache.isActive());
    }

    @Test
    void publicSizeQueriesEnableLayoutCachingForUncommittedElements() {
        assertSizeQueryUsesScope(0);
        assertSizeQueryUsesScope(1);
        assertSizeQueryUsesScope(2);
        assertSizeQueryUsesScope(3);
    }

    private static void assertSizeQueryUsesScope(int query) {
        Document document = TestDocumentFactory.createDocument();
        ProbeElement element = new ProbeElement(document);
        element.setAttribute("style", "width:40px;height:20px;padding:3px;border:2px solid;");
        document.body.appendChild(element);

        switch (query) {
            case 0 -> element.getClientWidth();
            case 1 -> element.getClientHeight();
            case 2 -> element.getOffsetWidth();
            case 3 -> element.getOffsetHeight();
            default -> throw new AssertionError("unknown geometry query: " + query);
        }

        assertTrue(element.observedLayoutScope, "size query must enable layout caching");
        assertFalse(LayoutMeasureCache.isActive());
    }

    private static final class ProbeElement extends Element {
        private boolean observedLayoutScope;

        private ProbeElement(Document document) {
            super(document, "probe");
        }

        @Override
        public Style getComputedStyle() {
            observedLayoutScope |= LayoutMeasureCache.isActive();
            return super.getComputedStyle();
        }
    }
}
