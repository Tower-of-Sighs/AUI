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
