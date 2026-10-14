package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.dom.RenderElement;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.ShorthandParser;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GridLayoutTest {
    private Element grid(String css) {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:600px;height:400px");
        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display:grid;" + css);
        document.body.appendChild(grid);
        return grid;
    }
    private Element item(Element parent, String css) {
        Element child = new Element(parent.document, "div");
        child.setAttribute("style", css);
        parent.appendChild(child);
        return child;
    }
    private void rect(Element e, double x, double y, double w, double h) {
        assertEquals(x, Position.getOffset(e).x, 0.001);
        assertEquals(y, Position.getOffset(e).y, 0.001);
        assertEquals(w, Size.of(e).width(), 0.001);
        assertEquals(h, Size.of(e).height(), 0.001);
    }
    @Test void keywordCacheRefreshesAndLayoutUsesCurrentValues() {
        Element g = grid("width:100px;height:50px;grid-template:10px 10px / 20px 20px;place-content:start;place-items:start");
        item(g, "width:4px;height:2px");
        Element second = item(g, "width:4px;height:2px");
        Grid cached = Grid.of(g);
        assertSame(cached, Grid.of(g));
        assertTrue(cached.gridAutoFlow.is("row"));
        assertEquals("start", cached.justifyItems.value());
        rect(second, 20, 0, 4, 2);

        String[] properties = {"grid-auto-flow", "align-content", "justify-content", "align-items", "justify-items"};
        double[][] positions = {{0, 10}, {0, 40}, {60, 40}, {60, 48}, {76, 48}};
        for (int i = 0; i < properties.length; i++) {
            g.getStyle().setProperty(properties[i], i == 0 ? "COLUMN DENSE" : "end");
            g.document.flushPendingStyleUpdates();
            Grid current = Grid.of(g);
            assertNotSame(cached, current, properties[i]);
            assertSame(current, Grid.of(g));
            rect(second, positions[i][0], positions[i][1], 4, 2);
            cached = current;
        }
        g.getStyle().setProperty("grid-template-columns", "30px 30px");
        g.document.flushPendingStyleUpdates();
        assertSame(cached, Grid.of(g));
        rect(second, 66, 48, 4, 2);
    }
    @Test void explicitItemsReserveCellsBeforeAutomaticItems() {
        Element g = grid("grid-template:30px / 40px 60px");
        Element a = item(g, ""), b = item(g, "grid-area:1 / 1 / 2 / 2");
        rect(a, 40, 0, 60, 30); rect(b, 0, 0, 40, 30);
    }
    @Test void denseFillsHolesAndSparseKeepsCursor() {
        for (boolean dense : List.of(false, true)) {
            Element g = grid("grid-template-columns:repeat(3,20px);grid-auto-rows:10px;grid-auto-flow:row" + (dense ? " dense" : ""));
            item(g, "grid-column:span 2"); item(g, "grid-column:span 2");
            Element third = item(g, "");
            rect(third, 40, dense ? 0 : 10, 20, 10);
        }
    }
    @Test void columnFlowAndDenseAreTransposed() {
        Element g = grid("grid-template-rows:repeat(3,20px);grid-auto-columns:10px;grid-auto-flow:column dense");
        item(g, "grid-row:span 2"); item(g, "grid-row:span 2");
        rect(item(g, ""), 0, 40, 10, 20);
    }
    @Test void negativeLinesAndPrecedingImplicitTracksUseExplicitGrid() {
        Element g = grid("width:100px;grid-template-columns:20px 30px;grid-auto-columns:10px 15px;grid-auto-rows:10px;justify-content:start");
        Element before = item(g, "grid-column:-5 / -3"), last = item(g, "grid-column:-2 / -1");
        rect(before, 0, 0, 25, 10); rect(last, 45, 0, 30, 10);
    }
    @Test void namedAreasAndRepeatedNamedLinesKeepCase() {
        Element g = grid("grid-template:[Top] 'Head Head' 20px 'Side Main' 30px / 40px 60px");
        rect(item(g, "grid-area:Main"), 40, 20, 60, 30);
        rect(item(g, "grid-area:Head"), 0, 0, 100, 20);
        Element named = grid("grid-template-columns:repeat(3,[Col] 25px) [End];grid-auto-rows:10px");
        rect(item(named, "grid-column:Col 2 / End"), 25, 0, 50, 10);
    }
    @Test void autoFitCollapsesEmptyTracksAndGutters() {
        Element fit = grid("width:100px;gap:10px;grid-template-columns:repeat(auto-fit,minmax(20px,1fr));grid-auto-rows:10px");
        rect(item(fit, ""), 0, 0, 100, 10);
        Element fill = grid("width:100px;gap:10px;grid-template-columns:repeat(auto-fill,minmax(20px,1fr));grid-auto-rows:10px");
        rect(item(fill, ""), 0, 0, 80d / 3, 10);
    }
    @Test void autoRepeatCountsOtherTracksAndMultiTrackPattern() {
        Element g = grid("width:140px;gap:10px;grid-template-columns:20px repeat(auto-fill,10px 20px);grid-auto-rows:10px");
        for (int i = 0; i < 5; i++) item(g, "");
        assertEquals(120, Layout.computeContentSize(g).width(), 0.001);
        assertEquals(10, Layout.computeContentSize(g).height(), 0.001);
    }
    @Test void fractionalTracksSolveWeightsFromTotalSpace() {
        Element g = grid("width:120px;grid-template-columns:20px 1fr 2fr;grid-auto-rows:10px");
        item(g, ""); Element a = item(g, "width:10px"), b = item(g, "width:30px");
        assertEquals(20, Position.getOffset(a).x, 0.001);
        assertEquals(20 + 100d / 3, Position.getOffset(b).x, 0.001);
    }
    @Test void fractionalMinimumFreezesAndRemainingTracksRedistribute() {
        Element g = grid("width:100px;grid-template-columns:1fr 1fr 1fr;grid-auto-rows:10px");
        item(g, "width:80px"); rect(item(g, ""), 80, 0, 10, 10);
        rect(item(g, ""), 90, 0, 10, 10);
    }
    @Test void spanningItemUsesFractionWeightsWhenWidthIsIntrinsic() {
        Element g = grid("display:inline-grid;grid-template-columns:1fr 2fr;grid-auto-rows:10px");
        item(g, "width:90px;grid-column:span 2");
        Element a = item(g, ""), b = item(g, "");
        assertEquals(90, Size.of(g).width(), .001);
        rect(a, 0, 10, 30, 10);
        rect(b, 30, 10, 60, 10);
    }
    @Test void fractionalWeightsBelowOneDoNotConsumeAllSpace() {
        Element g = grid("width:100px;grid-template-columns:minmax(0,.25fr) minmax(0,.25fr);grid-auto-rows:10px");
        rect(item(g, ""), 0, 0, 25, 10); rect(item(g, ""), 25, 0, 25, 10);
    }
    @Test void lengthsPercentagesAndFractionalGapsStayPrecise() {
        Element g = grid("width:100px;grid-template-columns:25% calc(25% + 0.5px) 1fr;column-gap:2.5px;grid-auto-rows:10px");
        item(g, ""); rect(item(g, ""), 27.5, 0, 25.5, 10); rect(item(g, ""), 55.5, 0, 44.5, 10);
    }
    @Test void autoTracksStretchAndContentAlignmentDistributes() {
        Element g = grid("width:100px;height:60px;grid-template:10px / auto auto");
        rect(item(g, ""), 0, 0, 50, 10); rect(item(g, ""), 50, 0, 50, 10);
        Element spaced = grid("width:100px;grid-template:10px / 20px 20px;justify-content:space-between");
        rect(item(spaced, ""), 0, 0, 20, 10); rect(item(spaced, ""), 80, 0, 20, 10);
    }
    @Test void emptyGridRetainsExplicitTrackSizes() {
        Element g = grid("grid-template:20px 30px / 40px 60px;gap:5px");
        assertEquals(105, Layout.computeContentSize(g).width(), 0.001);
        assertEquals(55, Layout.computeContentSize(g).height(), 0.001);
    }
    @Test void marginsStretchConstraintsAndExplicitZero() {
        Element g = grid("grid-template:50px / 100px");
        rect(item(g, "padding:5px;border:1px solid black;max-width:40px;margin:2px;box-sizing:border-box"), 0, 0, 40, 46);
        Element zero = grid("grid-template:50px / 100px");
        rect(item(zero, "width:0;height:0"), 0, 0, 0, 0);
        Element centered = grid("grid-template:50px / 100px");
        rect(item(centered, "width:20px;height:10px;margin:auto"), 40, 20, 20, 10);
    }
    @Test void safeAlignmentFallsBackWhenItemOverflows() {
        Element g = grid("grid-template:20px / 20px");
        rect(item(g, "width:40px;height:40px;place-self:safe center"), 0, 0, 40, 40);
        Element unsafe = grid("grid-template:20px / 20px");
        rect(item(unsafe, "width:40px;height:40px;place-self:unsafe center"), -10, -10, 40, 40);
    }
    @Test void absoluteGridAreaDoesNotCreateTracks() {
        Element g = grid("position:relative;width:100px;height:50px;grid-template:20px 30px / 40px 60px");
        rect(item(g, "position:absolute;grid-area:2 / 2 / 3 / 3;inset:0"), 40, 20, 60, 30);
        item(g, "position:absolute;grid-column:10 / 20;width:5px;height:5px");
        assertEquals(100, Layout.computeContentSize(g).width(), 0.001);
    }
    @Test void directTextIsPlacedWrappedAndSelectable() {
        Element g = grid("width:100px;grid-template-columns:50px 50px;font-size:12px;line-height:14px");
        g.appendChild(g.document.createTextNode("alpha beta gamma"));
        Element child = item(g, "height:10px");
        List<Layout.DirectTextLayout> layouts = Layout.computeDirectTextLayouts(g);
        assertEquals(1, layouts.size()); assertTrue(layouts.get(0).lines().size() > 1);
        assertEquals(50, Position.getOffset(child).x, 0.001);
        assertTrue(com.sighs.apricityui.behavior.SelectionUnits.paintsTextViaRuns(g));
    }
    @Test void minContentRespectsUnbreakableWordsAndNoWrap() {
        Element g = grid("grid-template-columns:min-content max-content;font-size:12px;line-height:14px;justify-content:start");
        Element a = item(g, ""); a.setTextContent("longword short");
        Element b = item(g, ""); b.setTextContent("longword short");
        double min = Text.measureLine(Text.of(a), "longword"), max = Text.measureLine(Text.of(a), "longword short");
        assertEquals(min, Size.of(a).width(), 0.001); assertEquals(max, Size.of(b).width(), 0.001);
    }
    @Test void nestedGridUsesAssignedAreaAndMutationInvalidatesCache() {
        Element g = grid("width:100px;grid-template-columns:repeat(2,minmax(0,1fr))");
        Element nested = item(g, "display:grid;grid-template-columns:repeat(2,minmax(0,1fr))");
        Element first = item(nested, "height:10px"), second = item(nested, "height:10px");
        Size.of(g); rect(first, 0, 0, 25, 10); rect(second, 25, 0, 25, 10);
        g.getStyle().setProperty("width", "200px"); g.document.flushPendingStyleUpdates();
        Size.of(g); rect(second, 50, 0, 50, 10);
    }
    @Test void invalidDeclarationsLeavePreviousValidValue() {
        Element g = grid("grid-template-columns:20px;grid-template-columns:2;grid-template-columns:repeat(-1,30px);grid-template-areas:'A A';grid-template-areas:'A .' '. A'");
        assertEquals("20px", g.getComputedStyle().gridTemplateColumns);
        assertEquals("'A A'", g.getComputedStyle().gridTemplateAreas);
        assertFalse(ShorthandParser.isValidGridDeclaration("grid-template-columns", "2"));
        assertFalse(ShorthandParser.isValidGridDeclaration("grid-row", "0 / 2"));
        assertFalse(ShorthandParser.isValidGridDeclaration("grid-template-columns", "minmax(1fr,2fr)"));
        assertFalse(ShorthandParser.isValidGridDeclaration("grid-template-columns", "repeat(2,repeat(2,20px))"));
    }
    @Test void shorthandSourceOrderAndPriorityReachLonghands() {
        Element g = grid("grid-column-end:4;grid-column:2 / 3;grid-column-start:1 !important;grid-column:3 / 4");
        assertEquals("1", g.getComputedStyle().gridColumnStart);
        assertEquals("4", g.getComputedStyle().gridColumnEnd);
        Style style = new Style(); style.merge("grid:20px / 30px;place-items:center end");
        assertEquals("20px", style.gridTemplateRows); assertEquals("end", style.justifyItems);
    }
    @Test void stylesheetShorthandsCascadeAgainstLonghands() {
        Element g = grid("");
        g.setAttribute("id", "grid");
        g.setAttribute("class", "grid");
        HashMap<String, Map<String, CSS.Declaration>> declarations = new HashMap<>();
        CSS.readCSS("#grid { grid:20px / 30px; place-items:center end; }"
                + ".grid { grid-template-columns:80px; justify-items:start; }"
                + ".grid { grid-template-rows:40px !important; }", declarations, "test://grid.css");
        g.document.CSSCache.putAll(declarations);
        Style style = g.getComputedStyle();
        assertEquals("30px", style.gridTemplateColumns);
        assertEquals("40px", style.gridTemplateRows);
        assertEquals("end", style.justifyItems);
    }
    @Test void placementLonghandMutationRecomputesGeometry() {
        Element g = grid("grid-template:10px / 20px 30px;justify-content:start");
        Element a = item(g, "grid-column:1 / 2");
        rect(a, 0, 0, 20, 10);
        Style before = a.getComputedStyle().clone();
        a.getStyle().setProperty("grid-column-start", "2");
        a.getStyle().setProperty("grid-column-end", "3");
        g.document.flushPendingStyleUpdates();
        assertTrue((RenderElement.observeStyleMask(a, before, a.getComputedStyle()) & Drawer.RELAYOUT) != 0);
        rect(a, 20, 0, 30, 10);
    }
    @Test void hugeIntegersAndRepeatsTerminate() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Element g = grid("grid-template-columns:repeat(999999999999999999999,0px);grid-auto-rows:1px");
            Element e = item(g, "grid-column:999999999999999999999 / span 2");
            assertTrue(Double.isFinite(Position.getOffset(e).x));
        });
    }

    @Test void baselineGroupsIncludeAscentAndDescentAndLastLines() {
        for (String align : List.of("first baseline", "last baseline")) {
            Element g = grid("width:120px;grid-template-columns:60px 60px;align-items:" + align);
            Element a = item(g, "font-size:20px;line-height:24px;padding-bottom:20px"); a.setTextContent("A");
            Element b = item(g, "font-size:12px;line-height:14px;padding-top:25px"); b.setTextContent("B");
            double ba = Position.getOffset(a).y + Text.baselineOffset(Text.of(a));
            double bb = Position.getOffset(b).y + 25 + Text.baselineOffset(Text.of(b));
            assertEquals(ba, bb, .001);
            assertTrue(Size.of(g).height() >= Math.max(Size.of(a).height(), Size.of(b).height()));
        }
    }
    @Test void fitContentCapsGrowthAndPreservesMinimum() {
        Element g = grid("width:200px;grid-template-columns:fit-content(60px) 1fr;font-size:12px;justify-content:start");
        Element a = item(g, ""); a.setTextContent("alpha beta gamma delta");
        Element b = item(g, "height:10px");
        double minimum = Text.minContentWidth(Text.of(a)), cap = minimum + 10;
        g.getStyle().setProperty("grid-template-columns", "fit-content(" + cap + "px) 1fr");
        g.document.flushPendingStyleUpdates();
        assertEquals(cap, Position.getOffset(b).x, .001);
        assertEquals(200 - cap, Size.of(b).width(), .001);
        g.getStyle().setProperty("grid-template-columns", "fit-content(1px) 1fr");
        g.document.flushPendingStyleUpdates();
        assertEquals(minimum, Position.getOffset(b).x, .001);
    }
    @Test void orderAndOutOfFlowItemsDoNotChangeDomOrder() {
        Element g = grid("grid-template:10px/20px 30px");
        Element a = item(g, "order:2"), b = item(g, "order:-1");
        item(g, "display:none;grid-column:50"); item(g, "position:fixed;grid-column:50");
        rect(b, 0, 0, 20, 10); rect(a, 20, 0, 30, 10);
        assertSame(a, g.getRenderChildren().get(0));
    }
    @Test void repeatedLayoutAndCollapsedRowsDoNotKeepStaleSizes() {
        Element g = grid("width:100px;grid-template-rows:0fr");
        Element a = item(g, "overflow:hidden"); item(a, "height:40px");
        assertEquals(0, Size.of(a).height(), .001);
        g.getStyle().setProperty("grid-template-rows", "1fr");
        g.document.flushPendingStyleUpdates();
        for (int i = 0; i < 3; i++) assertEquals(40, Size.of(a).height(), .001);
        item(a, "height:10px");
        assertEquals(50, Size.of(g).height(), .001);
    }
}
