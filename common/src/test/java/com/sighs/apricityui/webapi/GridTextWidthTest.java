package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridTextWidthTest {
    private static final String LONG_TEXT = "Select an existing record to enable or disable adaptation for that creature.";

    @Test
    void percentageTextUsesItsGridAreaForBothBoxWidthAndWrapping() {
        Element grid = grid();
        Element paragraph = item(grid, "width:100%;box-sizing:border-box;padding:10px;");

        assertEquals(200, Size.of(paragraph).width(), 0.01);
        var wrapped = Text.wrap(paragraph);
        assertTrue(wrapped.lines().size() > 1);
        assertTrue(wrapped.lines().stream().allMatch(line -> Text.measureLine(Text.of(paragraph), line) <= 180.01));
    }

    @Test
    void percentageWidthWorksWithoutStretchAndIncludesContentBoxPadding() {
        Element paragraph = item(grid(), "width:50%;padding:10px;justify-self:start;align-self:start;");

        assertEquals(120, Size.of(paragraph).width(), 0.01);
        assertTrue(Text.wrap(paragraph).lines().size() > 1);
    }

    @Test
    void spanningPercentageUsesTheWholeAreaIncludingItsGap() {
        Element paragraph = item(grid(), "width:50%;grid-column:span 2;box-sizing:border-box;");

        assertEquals(210, Size.of(paragraph).width(), 0.01);
    }

    @Test
    void percentageCalcHonorsWidthBounds() {
        Element paragraph = item(grid(), "width:calc(100% - 20px);max-width:150px;box-sizing:border-box;");

        assertEquals(150, Size.of(paragraph).width(), 0.01);
    }

    @Test
    void nowrapAndFixedWidthKeepTheirExistingSemantics() {
        Element paragraph = item(grid(), "width:100%;white-space:nowrap;");
        assertEquals(200, Size.of(paragraph).width(), 0.01);
        assertEquals(1, Text.wrap(paragraph).lines().size());

        Element fixed = item(grid(), "width:140px;box-sizing:border-box;");
        assertEquals(140, Size.of(fixed).width(), 0.01);
    }

    private static Element grid() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:800px;");
        Element grid = new Element(document, "div");
        grid.setAttribute("style", "display:grid;width:420px;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px;");
        document.body.appendChild(grid);
        return grid;
    }

    private static Element item(Element grid, String style) {
        Element item = new Element(grid.document, "p");
        item.setAttribute("style", "font-size:16px;line-height:1.5;min-width:0;white-space:normal;" + style);
        item.setTextContent(LONG_TEXT);
        grid.appendChild(item);
        return item;
    }
}
