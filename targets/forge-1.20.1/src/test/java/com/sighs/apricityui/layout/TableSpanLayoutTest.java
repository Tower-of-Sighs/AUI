package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class TableSpanLayoutTest {
    private Document document(String body) {
        String path = "test://table-span-mutation";
        HTML.putTemple(path, "<html><head><style>body{width:300px;font-size:0;line-height:0}td{padding:0}</style></head><body>" + body + "</body></html>");
        Document doc = new Document(path, false);
        doc.refresh();
        doc.commitRenderState();
        return doc;
    }

    @Test void spanMutationsInvalidateOtherRowsAndHitTesting() {
        Document doc = document("""
            <table id="t" style="width:300px">
              <tbody><tr><td id="a"><div style="width:30px;height:60px"></div></td><td id="b"><div style="width:30px;height:20px"></div></td></tr>
              <tr id="r2"><td id="c"><div style="width:30px;height:20px"></div></td><td id="d"><div style="width:30px;height:20px"></div></td></tr></tbody>
            </table>
            """);
        try {
            Element a = doc.querySelector("#a"), b = doc.querySelector("#b"), c = doc.querySelector("#c");
            double initialX = b.getBoundingClientRect().x;
            a.setAttribute("colspan", "2"); doc.commitRenderState();
            assertTrue(b.getBoundingClientRect().x > initialX);
            a.removeAttribute("colspan"); doc.commitRenderState();
            assertEquals(initialX, b.getBoundingClientRect().x, .01);
            a.setAttribute("rowspan", "2"); doc.commitRenderState();
            assertTrue(c.getBoundingClientRect().x > a.getBoundingClientRect().x);
            var ar = a.getBoundingClientRect();
            var rr = doc.querySelector("#r2").getBoundingClientRect();
            Element hit = doc.hitTest(new Position(ar.x + ar.width - 2, rr.y + rr.height - 2));
            assertSame(a, hit);
            a.removeAttribute("rowspan"); doc.commitRenderState();
            assertEquals(a.getBoundingClientRect().x, c.getBoundingClientRect().x, .01);
        } finally { doc.remove(); }
    }

    @Test void zeroSpanTracksInsertedRemovedAndHiddenRowsWithoutChangingDomOrder() {
        Document doc = document("""
            <table id="t"><tfoot id="foot"><tr><td style="height:10px">f</td></tr></tfoot>
              <tbody id="body"><tr><td id="a" rowspan="0" style="width:20px;height:60px"></td><td style="width:30px;height:10px"></td></tr>
              <tr id="last"><td style="height:20px"></td></tr></tbody></table>
            """);
        try {
            Element table = doc.querySelector("#t"), body = doc.querySelector("#body"), a = doc.querySelector("#a");
            assertSame(doc.querySelector("#foot"), table.children.get(0));
            Element row = doc.createElement("tr"); row.setInnerHTML("<td id='added' style='height:30px'></td>");
            body.appendChild(row); doc.commitRenderState();
            assertEquals(a.getBoundingClientRect().x + a.getBoundingClientRect().width, doc.querySelector("#added").getBoundingClientRect().x, .01);
            doc.querySelector("#last").setAttribute("style", "display:none"); doc.commitRenderState();
            assertEquals(body.getBoundingClientRect().height, a.getBoundingClientRect().height, .01);
            body.removeChild(row); doc.commitRenderState();
            assertEquals(a.getBoundingClientRect().height, body.getBoundingClientRect().height, .01);
            assertEquals(body.getBoundingClientRect().y + body.getBoundingClientRect().height, doc.querySelector("#foot").getBoundingClientRect().y, .01);
        } finally { doc.remove(); }
    }

    @Test void attributesUseHtmlLimitsAndOnlyApplyToCells() {
        Document doc = document("<table><tr><td id='a'></td></tr></table>");
        try {
            Element a = doc.querySelector("#a");
            String[] inputs = {"", "-2", "bad", "0", "+2tail", " 3.4", "999999999999999999999999"};
            int[] cols = {1, 1, 1, 1, 2, 3, 1000}, rows = {1, 1, 1, 0, 2, 3, 65534};
            for (int i = 0; i < inputs.length; i++) {
                a.setAttribute("colspan", inputs[i]); a.setAttribute("rowspan", inputs[i]);
                assertEquals(cols[i], Table.span(a, "colspan", 1000));
                assertEquals(rows[i], Table.span(a, "rowspan", 65534));
            }
            Element div = doc.createElement("div"); div.setAttribute("colspan", "3");
            assertEquals(1, Table.span(div, "colspan", 1000));
        } finally { doc.remove(); }
    }

    @Test void largeSpansStayBoundedAndStable() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Document doc = document("""
                <table id="t"><tbody><tr><td id="a" colspan="1000" rowspan="65534" style="height:60px;width:100px"></td></tr>
                <tr><td style="width:20px;height:20px"></td></tr></tbody></table>
                """);
            try {
                for (int i = 0; i < 100; i++) assertTrue(Double.isFinite(doc.querySelector("#a").getBoundingClientRect().width));
                assertEquals(60, doc.querySelector("#t").getBoundingClientRect().height, .01);
            } finally { doc.remove(); }
        });
    }

    @Test void spansKeepSelectionAndScrollGeometryInsideFlexAndGrid() {
        for (String display : new String[]{"block", "flex", "grid"}) {
            Document doc = document("""
                <div style="display:%s;width:160px"><div id="wrap" style="width:160px;overflow:auto;scrollbar-width:none">
                <table id="t" style="width:100%%;font-family:sans-serif;font-size:16px;line-height:20px">
                <tbody><tr><td id="a" rowspan="2" colspan="2" style="user-select:text">selectable table text<div style="width:200px;height:60px"></div></td><td style="width:40px;height:10px"></td></tr>
                <tr><td style="width:40px;height:20px"></td></tr>
                <tr><td style="width:30px;height:10px"></td><td style="width:30px"></td><td></td></tr></tbody>
                </table></div></div>
                """.formatted(display));
            try {
                Element a = doc.querySelector("#a"), wrap = doc.querySelector("#wrap");
                var selection = doc.getDocumentSelection();
                selection.collapse(a, 0); selection.extendTo(a, 10);
                a.setAttribute("colspan", "1"); doc.commitRenderState();
                assertEquals("selectable", selection.getSelectedText(doc));
                a.setAttribute("colspan", "2"); doc.commitRenderState();
                wrap.commitScrollMetricsFromLayout();
                assertTrue(wrap.scrollWidth >= 200, display);
                wrap.setScrollLeft(40);
                assertEquals(40, wrap.getTargetScrollLeft(), .01);
                assertEquals("selectable", selection.getSelectedText(doc));
                assertTrue(a.getBoundingClientRect().height >= 60, display);
            } finally { doc.remove(); }
        }
    }

    @Test void footerDisplayBlockifiesInFlexAndGridContainers() {
        for (String display : new String[]{"flex", "grid"}) {
            Document doc = document("<div style='display:" + display + "'><tfoot id='f'></tfoot></div>");
            try { assertEquals("block", doc.querySelector("#f").getComputedStyle().display); }
            finally { doc.remove(); }
        }
    }
}
