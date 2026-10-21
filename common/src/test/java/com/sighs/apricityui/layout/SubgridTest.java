package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.ShorthandParser;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubgridTest {
    private Element child(Element parent, String style) {
        Element child = new Element(parent.document, "div");
        child.setAttribute("style", style);
        parent.appendChild(child);
        return child;
    }

    @Test void validatesSubgridGrammarWithoutAdmittingItInsideTrackRepeats() {
        for (String value : new String[]{"subgrid", "SUBGRID [A] [] [b]", "subgrid repeat(2,[a] [])",
                "subgrid [a] repeat(auto-fill,[b]) [c]"}) {
            assertTrue(ShorthandParser.isValidGridTrackList(value, true), value);
            assertFalse(ShorthandParser.isValidGridTrackList(value, false), value);
        }
        for (String value : new String[]{"subgrid 1fr", "subgrid repeat(auto-fit,[a])", "subgrid repeat(0,[a])",
                "subgrid repeat(auto-fill,[a]) repeat(auto-fill,[b])", "subgrid [span]",
                "repeat(2,subgrid)", "subgrid repeat(2,1fr)", "subgrid repeat(2,repeat(2,[a]))"}) {
            assertFalse(ShorthandParser.isValidGridTrackList(value, true), value);
        }
    }

    @Test void parentTracksAndGrandchildContributionsInvalidateCachedLayout() {
        var document = TestDocumentFactory.createDocument();
        Element grid = child(document.body, "display:grid;width:150px;grid-template-columns:40px 100px;gap:10px");
        Element sub = child(grid, "display:grid;grid-column:span 2;grid-template-columns:subgrid");
        Element first = child(sub, "height:20px");
        Element second = child(sub, "height:30px");
        LayoutMeasureCache.begin();
        try {
            assertEquals(40, Size.of(first).width(), .001);
            assertEquals(50, Position.getOffset(second).x, .001);
            grid.getStyle().setProperty("grid-template-columns", "70px 70px");
            document.flushPendingStyleUpdates();
            assertEquals(70, Size.of(first).width(), .001);
            assertEquals(80, Position.getOffset(second).x, .001);
            grid.getStyle().setProperty("grid-template-columns", "auto auto");
            grid.getStyle().setProperty("width", "auto");
            grid.getStyle().setProperty("display", "inline-grid");
            first.getStyle().setProperty("width", "40px");
            second.getStyle().setProperty("width", "90px");
            document.flushPendingStyleUpdates();
            assertEquals(140, Size.of(grid).width(), .001);
            second.getStyle().setProperty("width", "120px");
            document.flushPendingStyleUpdates();
            assertEquals(170, Size.of(grid).width(), .001);
            sub.removeChild(second);
            document.flushPendingStyleUpdates();
            assertEquals(50, Size.of(grid).width(), .001);
        } finally { LayoutMeasureCache.end(); }
    }

    @Test void variablesAndShorthandCascadePreserveSubgrid() {
        var document = TestDocumentFactory.createDocument();
        Element grid = child(document.body, "display:grid;grid-template:30px/40px 60px");
        Element sub = child(grid, "display:grid;grid-column:span 2;--tracks:subgrid [A] [B] [C];"
                + "grid-template:10px / var(--tracks);grid-template-rows:30px");
        Element item = child(sub, "grid-column:B/C");
        assertEquals(40, Position.getOffset(item).x, .001);
        assertEquals(60, Size.of(item).width(), .001);
        assertEquals(30, Size.of(item).height(), .001);
    }

    @Test void hitTestingAndSelectionFollowSubgridGeometryAfterResize() {
        var document = TestDocumentFactory.createDocument();
        Element grid = child(document.body, "display:grid;width:200px;grid-template:40px/80px 110px;gap:10px");
        Element sub = child(grid, "display:grid;grid-column:span 2;grid-template-columns:subgrid;overflow:hidden");
        child(sub, "");
        Element text = child(sub, "user-select:text;font-size:12px;line-height:14px");
        text.innerText = "selectable subgrid text";
        document.commitRenderState();
        var rect = text.getBoundingClientRect();
        assertSame(text, document.hitTest(new Position(rect.x + 2, rect.y + 2)));
        var selection = document.getDocumentSelection();
        selection.collapse(text, 0);
        selection.extendTo(text, 10);
        grid.getStyle().setProperty("grid-template-columns", "110px 80px");
        document.flushPendingStyleUpdates();
        document.commitRenderState();
        rect = text.getBoundingClientRect();
        assertEquals(120, rect.x - grid.getBoundingClientRect().x, .001);
        assertSame(text, document.hitTest(new Position(rect.x + 2, rect.y + 2)));
        assertEquals("selectable", selection.getSelectedText(document));
    }
}
