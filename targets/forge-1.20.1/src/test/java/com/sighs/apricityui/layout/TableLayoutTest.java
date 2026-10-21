package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TableLayoutTest {
    private Element table(String css) {
        Document doc = TestDocumentFactory.createDocument();
        doc.body.setAttribute("style", "width:300px;height:400px;font-family:sans-serif;font-size:16px;line-height:20px");
        return node(doc.body, "table", css);
    }

    private Element node(Element parent, String tag, String css) {
        Element result = new Element(parent.document, tag);
        result.setAttribute("style", css);
        parent.appendChild(result);
        return result;
    }

    private Element cell(Element row, double width, double height) {
        Element cell = node(row, "td", "");
        node(cell, "div", "width:" + width + "px;height:" + height + "px");
        return cell;
    }

    private void rect(Element element, double x, double y, double width, double height) {
        var r = element.getBoundingClientRect();
        assertEquals(x, r.x, .01, element.tagName + " x");
        assertEquals(y, r.y, .01, element.tagName + " y");
        assertEquals(width, r.width, .01, element.tagName + " width");
        assertEquals(height, r.height, .01, element.tagName + " height");
    }

    @Test void headerAndBodyShareColumnsAndRowHeights() {
        Element table = table("padding:5px;border:2px solid black");
        Element head = node(table, "thead", ""), body = node(table, "tbody", "");
        Element first = node(head, "tr", ""), second = node(body, "tr", "");
        Element a = cell(first, 30, 10), b = cell(first, 20, 20);
        Element c = cell(second, 10, 30), d = cell(second, 70, 10);
        rect(table, 0, 0, 114, 64);
        rect(head, 7, 7, 100, 20); rect(body, 7, 27, 100, 30);
        rect(a, 7, 7, 30, 20); rect(b, 37, 7, 70, 20);
        rect(c, 7, 27, 30, 30); rect(d, 37, 27, 70, 30);
    }

    @Test void directRowsWhitespaceAndRaggedRowsDoNotChangeDom() {
        Element table = table("");
        table.appendChild(new TextNode(table.document, "\n  "));
        Element first = node(table, "tr", ""), second = node(table, "tr", "");
        Element a = cell(first, 20, 10);
        cell(second, 10, 20); Element b = cell(second, 40, 20); cell(second, 30, 20);
        rect(table, 0, 0, 90, 30); rect(a, 0, 0, 20, 10); rect(b, 20, 10, 40, 20);
        assertEquals(3, table.childNodes.size()); assertEquals(1, first.children.size());
    }

    @Test void explicitWidthsExpandAndMinimumCanOverflow() {
        Element table = table("width:200px");
        Element row = node(table, "tr", "");
        Element a = cell(row, 20, 10), b = cell(row, 80, 10);
        rect(a, 0, 0, 40, 10); rect(b, 40, 0, 160, 10);
        table.setAttribute("style", "width:30px"); table.document.flushPendingStyleUpdates();
        rect(table, 0, 0, 100, 10); rect(b, 20, 0, 80, 10);
    }

    @Test void arbitraryColumnCountsAndEmptyCellsStayFinite() {
        for (int count : List.of(2, 3, 5)) {
            Element table = table("width:100px");
            Element row = node(table, "tr", "");
            for (int i = 0; i < count; i++) node(row, "td", "padding:2px");
            rect(table, 0, 0, 100, 4);
            for (int i = 0; i < count; i++) rect(row.children.get(i), i * 100d / count, 0, 100d / count, 4);
        }
        rect(table(""), 0, 0, 0, 0);
        rect(table("width:100px"), 0, 0, 100, 0);
    }

    @Test void textWrapsAtAssignedColumnWidthAndStaysTopAligned() {
        Element table = table("width:160px");
        Element row = node(table, "tr", "");
        Element a = node(row, "td", "padding:4px;border:1px solid black;text-align:right");
        a.appendChild(new TextNode(table.document, "alpha beta gamma delta epsilon zeta"));
        Element b = node(row, "td", "padding:4px");
        b.appendChild(new TextNode(table.document, "中文内容换行测试"));
        double height = Size.of(table).height();
        assertTrue(height > 30);
        assertEquals(height, Size.of(a).height(), .01);
        assertEquals(height, Size.of(b).height(), .01);
        var lines = Text.wrap(a, Text.of(a));
        assertTrue(lines.lines().size() > 1);
        assertEquals(lines.height(20) + 10, Size.naturalAtContentWidth(a, Size.of(a).width() - 10).height(), .01);
        assertEquals(0, NormalFlow.computeTextRuns(a).get(0).y(), .01);
    }

    @Test void inlineContentAndImagesParticipateInMeasurement() {
        Element table = table("");
        Element row = node(table, "tr", "");
        Element a = node(row, "td", "");
        node(a, "span", "font-weight:700").setTextContent("Name");
        Element b = node(row, "td", "");
        node(b, "img", "width:40px;height:24px");
        assertTrue(Size.of(a).width() > 0);
        assertEquals(40, Size.of(b).width(), .01);
        assertTrue(Size.of(table).height() >= 24);
    }

    @Test void mutationsInvalidateOtherRowsAndCommittedGeometry() {
        Element table = table("");
        Element r1 = node(table, "tr", ""), r2 = node(table, "tr", "");
        cell(r1, 20, 10); Element b = cell(r1, 30, 10);
        Element c = cell(r2, 10, 10); cell(r2, 10, 10);
        table.document.commitRenderState();
        assertEquals(20, b.getBoundingClientRect().x, .01);
        c.children.get(0).setAttribute("style", "width:60px;height:10px");
        table.document.commitRenderState();
        rect(b, 60, 0, 30, 10);
        table.removeChild(r2); table.document.commitRenderState();
        rect(b, 20, 0, 30, 10);
        table.setAttribute("style", "width:100%"); table.document.commitRenderState();
        assertEquals(300, Size.of(table).width(), .01);
        table.document.body.setAttribute("style", "width:200px"); table.document.commitRenderState();
        assertEquals(200, Size.of(table).width(), .01);
    }

    @Test void explicitDisplayAndHiddenRowsAreHonored() {
        Element table = table(""); Element row = node(table, "tr", "");
        cell(row, 20, 10); cell(row, 30, 10);
        Element hidden = node(table, "tr", "display:none"); cell(hidden, 400, 80);
        rect(table, 0, 0, 50, 10);
        table.setAttribute("style", "display:block"); row.setAttribute("style", "display:flex");
        table.document.flushPendingStyleUpdates();
        assertEquals("block", row.children.get(0).getComputedStyle().display);
        assertEquals(300, Size.of(table).width(), .01);
    }

    @Test void repeatedLayoutIsBoundedAndStable() {
        Element table = table("width:100%");
        for (int r = 0; r < 60; r++) {
            Element row = node(table, "tr", "");
            for (int c = 0; c < 5; c++) cell(row, 20 + c, 10);
        }
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            table.document.commitRenderState();
            for (Element row : table.children) for (Element cell : row.children) assertTrue(cell.getBoundingClientRect().width > 0);
            assertEquals(600, Size.of(table).height(), .01);
        });
    }

    @Test void contentAndFontChangesResizeOtherCells() {
        Element table = table(""); Element row = node(table, "tr", "");
        Element a = node(row, "td", ""), b = node(row, "td", "");
        a.setTextContent("a"); b.setTextContent("b");
        table.document.commitRenderState();
        double before = b.getBoundingClientRect().x;
        a.setTextContent("substantiallylonger"); table.document.commitRenderState();
        double changed = b.getBoundingClientRect().x;
        assertTrue(changed > before);
        a.setAttribute("style", "font-size:32px"); table.document.commitRenderState();
        assertTrue(b.getBoundingClientRect().x > changed);
    }

    @Test void markdownAlignmentAttributesYieldToAuthorCss() {
        Element table = table("width:200px"); Element row = node(table, "tr", "");
        Element a = node(row, "th", ""), b = node(row, "td", "text-align:left");
        a.setAttribute("align", "right"); b.setAttribute("align", "center");
        table.document.flushPendingStyleUpdates();
        assertEquals("right", a.getComputedStyle().textAlign);
        assertEquals("left", b.getComputedStyle().textAlign);
    }

    @Test void nestedTableAndFlexGridParentsKeepAssignedColumns() {
        for (String display : List.of("block", "flex", "grid")) {
            Element table = table("width:100%");
            table.document.body.setAttribute("style", "display:" + display + ";width:200px;align-items:start");
            Element row = node(table, "tr", "");
            Element a = cell(row, 20, 10), b = node(row, "td", "");
            Element nested = node(b, "table", ""); Element inner = node(nested, "tr", "");
            cell(inner, 30, 10); cell(inner, 50, 10);
            table.document.commitRenderState();
            assertEquals(200, Size.of(table).width(), .01, display);
            assertEquals(Size.of(table).width(), Size.of(a).width() + Size.of(b).width(), .01, display);
            assertEquals(80, Size.of(nested).width(), .01, display);
        }
    }

    @Test void overflowingTableContributesToScrollExtent() {
        Element table = table("width:100%"); Element row = node(table, "tr", "");
        cell(row, 200, 20); cell(row, 400, 20);
        Element wrapper = node(table.document.body, "div", "width:100px;height:100px;overflow:auto;scrollbar-width:none");
        wrapper.appendChild(table);
        table.document.commitRenderState();
        wrapper.commitScrollMetricsFromLayout();
        assertTrue(wrapper.scrollWidth >= 600);
        wrapper.setScrollLeft(50);
        assertEquals(50, wrapper.getTargetScrollLeft(), .01);
    }

    @Test void orphanCellDoesNotReenterSizeMeasurement() {
        Element table = table("");
        Element orphan = node(table.document.body, "td", "");
        orphan.setTextContent("orphan");
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertTrue(Size.of(orphan).width() > 0));
    }

    @Test void selectionAndHitTestingFollowColumnsAfterResize() {
        Element table = table("width:200px"); Element row = node(table, "tr", "");
        cell(row, 20, 10);
        Element text = node(row, "td", "user-select:text"); text.setTextContent("selectable table text");
        table.document.commitRenderState();
        var rect = text.getBoundingClientRect();
        assertSame(text, table.document.hitTest(new Position(rect.x + 2, rect.y + 2)));
        var selection = table.document.getDocumentSelection();
        selection.collapse(text, 0); selection.extendTo(text, 10);
        table.setAttribute("style", "width:300px"); table.document.commitRenderState();
        var resized = text.getBoundingClientRect();
        assertTrue(resized.x > rect.x);
        assertSame(text, table.document.hitTest(new Position(resized.x + 2, resized.y + 2)));
        assertEquals("selectable", selection.getSelectedText(table.document));
    }
}
