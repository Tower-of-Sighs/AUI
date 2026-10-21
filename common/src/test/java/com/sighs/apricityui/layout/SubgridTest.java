package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.ShorthandParser;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"Courier New", "Dialog", "AUI Missing Test Font"})
    void mixedFontSizesShareBaselinesAcrossSubgrids(String family) {
        // Mixed-size baseline offsets depend on installed fonts, so assert alignment rather than fixed pixels.
        for (String align : new String[]{"first baseline", "last baseline"}) {
            var document = TestDocumentFactory.createDocument();
            Element grid = child(document.body, "display:grid;width:160px;grid-template-columns:80px 80px;"
                    + "font-family:'" + family + "'");
            String subgridStyle = "display:grid;grid-template-rows:subgrid;grid-row:span 2;align-items:" + align;
            Element left = child(grid, subgridStyle);
            Element right = child(grid, subgridStyle);
            Element a = child(left, "font-size:20px;line-height:24px"); a.setTextContent("A");
            Element b = child(left, "font-size:10px;line-height:12px"); b.setTextContent("B");
            Element c = child(right, "font-size:12px;line-height:16px;padding-top:12px"); c.setTextContent("C");
            Element d = child(right, "font-size:20px;line-height:24px"); d.setTextContent("D");
            var ar = a.getBoundingClientRect();
            var br = b.getBoundingClientRect();
            var cr = c.getBoundingClientRect();
            var dr = d.getBoundingClientRect();
            var gr = grid.getBoundingClientRect();
            assertAll(family + " / " + align,
                    () -> assertEquals(ar.y + Text.baselineOffset(Text.of(a)),
                            cr.y + 12 + Text.baselineOffset(Text.of(c)), .001, "first row baseline"),
                    () -> assertEquals(br.y + Text.baselineOffset(Text.of(b)),
                            dr.y + Text.baselineOffset(Text.of(d)), .001, "second row baseline"),
                    () -> assertEquals(gr.y, Math.min(ar.y, cr.y), .001, "first row start"),
                    () -> assertEquals(Math.max(ar.y + ar.height, cr.y + cr.height),
                            Math.min(br.y, dr.y), .001, "shared auto row boundary"),
                    () -> assertEquals(gr.y + gr.height, Math.max(br.y + br.height, dr.y + dr.height),
                            .001, "auto grid height"),
                    () -> assertEquals(gr.height, left.getBoundingClientRect().height, .001),
                    () -> assertEquals(gr.height, right.getBoundingClientRect().height, .001));
        }
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
