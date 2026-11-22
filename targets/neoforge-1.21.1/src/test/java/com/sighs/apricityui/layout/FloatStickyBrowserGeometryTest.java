package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.viewport.ApricityViewport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FloatStickyBrowserGeometryTest {
    private static final double EPS = 0.25;
    // Chromium/Edge 155.0.4283.62, 800x600, DPR 1. Scrollbars are hidden for repeatable bounds.
    private static final String HTML_FIXTURE = """
            <!doctype html><html><head><style>
            body {margin:0} * {box-sizing:content-box} section { width:200px; margin:0 }
            #floatbox { display:flow-root; width:120px }
            #left { float:left; width:30px; height:50px }
            #right { float:right; width:40px; height:30px }
            #next { float:right; width:75px; height:20px }
            #line { margin:0; line-height:10px; font:10px/10px monospace }
            #line i { display:inline-block; width:20px; height:10px; vertical-align:top }
            #clear { clear:both; height:5px }
            #auto { display:flow-root; width:100px } #auto span { float:left } #auto i { display:block; width:44px; height:15px }
            .port { width:100px; height:80px; overflow:auto; scrollbar-width:none }
            .area { width:220px; height:160px } .before { height:25px } .after {width:220px;height:180px}
            .stick {position:sticky;top:10px;left:10%;width:30px;height:20px;margin-left:20px}
            #outer { width:100px;height:80px;overflow:auto;scrollbar-width:none }
            #clip { overflow:clip } #hidden {overflow:hidden;height:100px}
            #fc { display:flex; flex-direction:column; width:100px;height:170px } #fc>* {flex:none}
            #gc {display:grid;width:100px;grid-template-rows:25px 145px} #gs {align-self:start}
            table {border-spacing:0; width:100px} th,td {padding:0; height:20px} tbody td {height:200px}
            </style></head><body>
            <div id="floatbox">
            <div id="left"></div>
            <div id="right"></div>
            <div id="next"></div><p id="line"><i id="i1"></i><i id="i2"></i><i id="i3"></i><i id="i4"></i></p>
            <div id="clear"></div></div>
            <div id="auto"><span id="autoFloat"><i></i></span></div>
            <div id="scroll" class="port"><div class="area"><div class="before"></div>
            <div id="sticky" class="stick"></div>
            <div id="sibling"></div></div><div class="after"></div></div>
            <div id="outer">
            <div id="clip"><div class="before"></div>
            <div id="clipSticky" class="stick"></div><div class="after"></div></div></div>
            <div id="outHidden" class="port">
            <div id="hidden"><div class="before"></div>
            <div id="hiddenSticky" class="stick"></div><div class="after"></div></div><div class="after"></div></div>
            <div id="flex" class="port">
            <div id="fc"><div class="before"></div>
            <div id="fs" class="stick"></div></div><div class="after"></div></div>
            <div id="grid" class="port">
            <div id="gc"><div class="before"></div>
            <div id="gs" class="stick"></div></div><div class="after"></div></div>
            <div id="table" class="port"><table><thead id="thead" style="position:sticky;top:0"><tr><th></th></tr></thead><tbody><tr><td></td></tr></tbody></table><div class="after"></div></div>
            </body></html>
            """;

    private static final String EXTRA_FIXTURE = """
            <!doctype html><html><head><style>
            body { margin:0; scrollbar-width:none }
            .port { overflow:auto; scrollbar-width:none; width:100px; height:80px }
            .tall { width:200px; height:200px }
            #rootArea { height:150px } #rootSticky { position:sticky;top:5px; height:20px;width:35px }
            #wrap { display:flow-root; width:100px; font-size:10px;line-height:10px;font-family:"Courier New",monospace }
            #wrap > .image { float:left; width:38px; height:20px }
            #isolation { width:100px; display:flow-root }
            #escapes { height:5px } #escapes i { display:block;float:left;width:35px;height:20px }
            #near { display:flow-root; height:10px }
            #clears { clear:left; height:10px }
            #late { width:100px; display:flow-root; font-size:10px;line-height:10px;font-family:"Courier New",monospace }
            #late b { display:inline-block; width:30px; height:10px; vertical-align:top }
            #late i { float:left; width:30px; height:20px }
            #opposed { position:sticky; inset:7px 0 12px auto; width:30px; height:100px }
            #rowport table,#cellport table {border-spacing:0;width:100px} th,td {padding:0;height:20px} tbody td {height:180px}
            </style></head><body>
            <div id="rootArea"><div id="rootSticky"></div></div>
            <div id="wrap"><div class="image"></div><div id="text">alphase bravofo charlie deltake echotan foxgolf</div></div>
            <div id="isolation"><div id="escapes"><i></i></div><div id="near"></div><div id="clears"></div></div>
            <div id="late"><b id="inlineBefore"></b><i id="lateFloat"></i><b id="inlineAfter"></b></div>
            <div id="opposedPort" class="port"><div class="tall"><div style="height:20px"></div><div id="opposed"></div></div><div class="tall"></div></div>
            <div id="rowport" class="port"><table><thead><tr id="row" style="position:sticky;top:0"><th></th></tr></thead><tbody><tr><td></td></tr></tbody></table><div class="tall"></div></div>
            <div id="cellport" class="port"><table><thead><tr><th id="cell" style="position:sticky;top:0"></th></tr></thead><tbody><tr><td></td></tr></tbody></table><div class="tall"></div></div>
            <div style="height:600px"></div>
            </body></html>
            """;

    @Test
    void floatsMatchBrowserPlacementAcrossNormalBlocks() throws Exception {
        withDocument("float", doc -> {
            rect(doc, "left", null, 0, 0, 30, 50);
            rect(doc, "right", null, 80, 0, 40, 30);
            rect(doc, "next", null, 45, 30, 75, 20);
            rect(doc, "line", null, 0, 0, 120, 20);
            rect(doc, "i1", null, 30, 0, 20, 10);
            rect(doc, "i2", null, 50, 0, 20, 10);
            rect(doc, "i3", null, 30, 10, 20, 10);
            rect(doc, "i4", null, 50, 10, 20, 10);
            rect(doc, "clear", null, 0, 50, 120, 5);
            rect(doc, "floatbox", null, 0, 0, 120, 55);
            rect(doc, "autoFloat", "auto", 0, 0, 44, 15);
            rect(doc, "auto", null, 0, 55, 100, 15);
            assertEquals("left", get(doc, "autoFloat").getComputedStyle().get("float"));
            assertEquals("block", get(doc, "autoFloat").getComputedStyle().display);
            get(doc, "left").setAttribute("style", "float:none");
            assertEquals("none", get(doc, "left").getComputedStyle().get("float"));
            rect(doc, "i1", "line", 0, 20, 20, 10);
        });
    }

    @Test
    void stickyKeepsSpaceClampsAndReleasesOnBothAxes() throws Exception {
        withDocument("sticky", doc -> {
            rect(doc, "sticky", "scroll", 20, 25, 30, 20);
            double sibling = Position.getOffset(get(doc, "sibling")).y;
            Element port = get(doc, "scroll");
            try (var probe = LayoutMeasureCache.probeMeasurements()) {
                scroll(port, 60, 60);
                assertEquals(0, probe.count());
            }
            rect(doc, "sticky", "scroll", 10, 10, 30, 20);
            assertSame(get(doc, "sticky"), doc.hitTest(new Position(15, port.getBoundingClientRect().y + 15)));
            assertEquals(sibling, Position.getOffset(get(doc, "sibling")).y);
            scroll(port, 120, 155);
            rect(doc, "sticky", "scroll", 10, -15, 30, 20);
            scroll(port, 15, 15);
            rect(doc, "sticky", "scroll", 10, 10, 30, 20);
            get(doc, "sticky").setAttribute("style", "top: 18px");
            rect(doc, "sticky", "scroll", 10, 18, 30, 20);
        });
    }

    @Test
    void clipAndHiddenSelectDifferentScrollports() throws Exception {
        withDocument("overflow", doc -> {
            scroll(get(doc, "outer"), 0, 60);
            rect(doc, "clipSticky", "outer", 20, 10, 30, 20);
            scroll(get(doc, "outHidden"), 60, 60);
            rect(doc, "hiddenSticky", "outHidden", -40, -35, 30, 20);
            scroll(get(doc, "outHidden"), 15, 0);
            scroll(get(doc, "hidden"), 0, 60);
            rect(doc, "hiddenSticky", "outHidden", 5, 10, 30, 20);
        });
    }

    @Test
    void stickyWorksWithFlexGridAndTableAreas() throws Exception {
        withDocument("formats", doc -> {
            for (String port : new String[]{"flex", "grid", "table"}) scroll(get(doc, port), 60, 60);
            rect(doc, "fs", "flex", 10, 10, 30, 20);
            rect(doc, "gs", "grid", 10, 10, 30, 20);
            rect(doc, "thead", "table", -60, 0, 100, 20);
            scroll(get(doc, "flex"), 120, 155);
            scroll(get(doc, "grid"), 120, 155);
            rect(doc, "fs", "flex", -50, -5, 30, 20);
            rect(doc, "gs", "grid", -50, -5, 30, 20);
        });
    }

    @Test
    void textReflowsAndFlowRootIsolatesFloats() throws Exception {
        withDocument("text", EXTRA_FIXTURE, doc -> {
            rect(doc, "escapes", "isolation", 0, 0, 100, 5);
            rect(doc, "near", "isolation", 35, 5, 65, 10);
            rect(doc, "clears", "isolation", 0, 20, 100, 10);
            rect(doc, "inlineBefore", "late", 30, 0, 30, 10);
            rect(doc, "lateFloat", "late", 0, 0, 30, 20);
            rect(doc, "inlineAfter", "late", 60, 0, 30, 10);
            get(doc, "late").setAttribute("style", "text-align:center");
            rect(doc, "inlineBefore", "late", 35, 0, 30, 10);
            rect(doc, "inlineAfter", "late", 65, 0, 30, 10);
            var lines = TextFlowGeometry.of(get(doc, "text")).lines();
            assertEquals(4, lines.size(), () -> lines.stream().map(TextFlowGeometry.Line::content).toList().toString());
            // Compare line spacing, excluding the font-specific leading above the glyph bounds.
            double firstY = lines.get(0).y();
            for (int i = 0; i < lines.size(); i++) {
                assertEquals(i < 2 ? 38 : 0, lines.get(i).x(), EPS);
                assertEquals(i * 10, lines.get(i).y() - firstY, EPS);
            }
        });
    }

    @Test
    void stickyUsesViewportTableAndOversizedViewRectangle() throws Exception {
        withDocument("extra-sticky", EXTRA_FIXTURE, doc -> {
            rect(doc, "rootSticky", null, 0, 5, 35, 20);
            scroll(doc.documentElement, 0, 40);
            rect(doc, "rootSticky", null, 0, 5, 35, 20);
            scroll(doc.documentElement, 0, 140);
            rect(doc, "rootSticky", null, 0, -10, 35, 20);
            scroll(doc.documentElement, 0, 0);
            rect(doc, "rootSticky", null, 0, 5, 35, 20);
            scroll(get(doc, "opposedPort"), 0, 85);
            rect(doc, "opposed", "opposedPort", 0, 7, 30, 100);
            scroll(get(doc, "opposedPort"), 0, 140);
            rect(doc, "opposed", "opposedPort", 0, -40, 30, 100);
            for (String id : new String[]{"row", "cell"}) {
                Element port = get(doc, id + "port");
                scroll(port, 0, 65);
                rect(doc, id, id + "port", 0, 0, 100, 20);
                scroll(port, 0, 215);
                rect(doc, id, id + "port", 0, -35, 100, 20);
            }
        });
    }

    @Test
    void stickyKeepsItsStackingContextAndAdaptsToResize() throws Exception {
        withDocument("layers", """
                <html><body style="margin:0"><div id="port" style="position:relative;height:80px;width:100px;overflow:auto;scrollbar-width:none">
                  <div style="width:200px;height:200px">
                    <div style="height:30px"></div>
                    <div id="pinned" style="position:sticky;top:0;left:10%;width:40px;height:20px">
                      <div id="child" style="position:absolute;inset:0;z-index:10"></div>
                    </div>
                  </div>
                  <div id="cover" style="position:absolute;left:70px;top:50px;width:40px;height:20px;z-index:1"></div>
                </div></body></html>
                """, doc -> {
            Element port = get(doc, "port");
            scroll(port, 60, 50);
            rect(doc, "pinned", "port", 10, 0, 40, 20);
            rect(doc, "child", "pinned", 0, 0, 40, 20);
            rect(doc, "cover", "port", 10, 0, 40, 20);
            assertEquals("cover", doc.hitTest(new Position(15, 10)).getAttribute("id"));
            get(doc, "cover").setAttribute("style", "display:none");
            assertEquals("child", doc.hitTest(new Position(15, 10)).getAttribute("id"));
            port.setAttribute("style", "position:relative;width:140px;height:80px;overflow:auto;scrollbar-width:none");
            rect(doc, "pinned", "port", 14, 0, 40, 20);
        });
    }

    private static void rect(Document doc, String id, String relativeTo,
                             double x, double y, double width, double height) {
        var actual = get(doc, id).getBoundingClientRect();
        var base = relativeTo == null ? null : get(doc, relativeTo).getBoundingClientRect();
        assertEquals(x, actual.x - (base == null ? 0 : base.x), EPS, id + " x");
        assertEquals(y, actual.y - (base == null ? 0 : base.y), EPS, id + " y");
        assertEquals(width, actual.width, EPS, id + " width");
        assertEquals(height, actual.height, EPS, id + " height");
    }

    private static void scroll(Element port, double x, double y) {
        double dx = x - port.scrollLeft;
        double dy = y - port.scrollTop;
        port.scrollLeft = port.targetScrollLeft = x;
        port.scrollTop = port.targetScrollTop = y;
        port.document.commitImmediateScroll(port, dx, dy);
    }

    private static Element get(Document doc, String id) {
        return doc.querySelector("#" + id);
    }

    private static void withDocument(String name, CheckedDocument consumer) throws Exception {
        withDocument(name, HTML_FIXTURE, consumer);
    }

    private static void withDocument(String name, String fixture, CheckedDocument consumer) throws Exception {
        String path = "test://float-sticky-browser-" + name;
        HTML.putTemple(path, fixture);
        Document doc = Document.create(path);
        try {
            Field viewport = Document.class.getDeclaredField("viewport");
            viewport.setAccessible(true);
            viewport.set(doc, new ApricityViewport(800, 600, 1.0f, 1.0d));
            doc.tickFrame();
            consumer.accept(doc);
        } finally {
            doc.remove();
        }
    }

    private interface CheckedDocument {
        void accept(Document document) throws Exception;
    }
}
