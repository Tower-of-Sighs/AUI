package com.sighs.apricityui.layout;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaxWidthInlineFlowTest {
    private Element paragraph(String style) {
        var document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:1000px;");
        Element p = document.createElement("p");
        p.setAttribute("style", "font-size:16px;line-height:20px;" + style);
        p.appendChild(new TextNode(document, "Some descriptive text before "));
        Element code = document.createElement("code");
        code.appendChild(new TextNode(document, "inline code"));
        p.appendChild(code);
        p.appendChild(new TextNode(document, " and a longer explanation that must wrap inside the paragraph."));
        document.body.appendChild(p);
        return p;
    }

    private void assertRunsFit(Element p, double width) {
        var runs = NormalFlow.computeTextRuns(p);
        assertFalse(runs.isEmpty());
        for (var run : runs) {
            for (int line = 0; line < run.lines().size(); line++) {
                double right = (line == 0 ? run.x() : 0) + Text.measureLine(run.text(), run.lines().get(line));
                assertTrue(right <= width + 0.01, "Text run exceeded its used content width: " + right);
            }
        }
        assertTrue(runs.stream().anyMatch(run -> run.y() > 0 || run.lineCount() > 1));
    }

    @Test
    void autoWidthParagraphUsesMaxWidthForMixedInlineLayout() {
        Element p = paragraph("max-width:180px;");
        assertRunsFit(p, 180);
        p.document.commitRenderState();
        assertEquals(180, Size.of(p).width(), 0.01);
        assertRunsFit(p, 180);
    }

    @Test
    void borderBoxMaxWidthExcludesPaddingAndBorderFromLineWidth() {
        Element p = paragraph("box-sizing:border-box;max-width:220px;padding:10px;border:5px solid black;");
        assertRunsFit(p, 190);
        p.document.commitRenderState();
        assertEquals(220, Size.of(p).width(), 0.01);
        assertRunsFit(p, 190);
    }
}
