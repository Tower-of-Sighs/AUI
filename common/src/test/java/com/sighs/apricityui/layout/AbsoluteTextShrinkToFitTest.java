package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbsoluteTextShrinkToFitTest {
    private Element tooltip(String text, String extra) {
        var document = TestDocumentFactory.createDocument();
        Element anchor = document.createElement("div");
        anchor.setAttribute("style", "position:relative;width:40px;height:30px;");
        document.body.appendChild(anchor);
        Element tooltip = document.createElement("span");
        tooltip.setTextContent(text);
        tooltip.setAttribute("style", "position:absolute;left:50%;bottom:100%;font-size:14px;line-height:20px;padding:6px 8px;border:2px solid black;" + extra);
        anchor.appendChild(tooltip);
        document.commitRenderState();
        return tooltip;
    }

    @Test
    void cjkTooltipShrinksToMinimumContentWhenAvailableSpaceIsNarrow() {
        Element tooltip = tooltip("默认朝上的气泡", "");
        double glyph = Text.measureLine(Text.of(tooltip), "默");
        assertEquals(glyph + 20, Size.of(tooltip).width(), 0.1);
        assertTrue(Size.of(tooltip).height() >= 140);
        assertEquals(7, tooltip.resolveRenderedLines(Text.of(tooltip), glyph, 200).size());
    }

    @Test
    void unbreakableWordDoesNotShrinkBelowItsMinimumContent() {
        Element tooltip = tooltip("unbreakable", "");
        assertEquals(Text.measureLine(Text.of(tooltip), "unbreakable") + 20, Size.of(tooltip).width(), 0.1);
    }

    @Test
    void minimumWidthReflowsHeightInsteadOfKeepingNarrowMeasurement() {
        Element tooltip = tooltip("默认朝上的气泡", "min-width:200px;");
        assertTrue(Size.of(tooltip).width() >= 200);
        assertEquals(36, Size.of(tooltip).height(), 0.1);
        assertEquals(1, tooltip.resolveRenderedLines(Text.of(tooltip), 200, 200).size());
    }

    @Test
    void nowrapTooltipKeepsPreferredWidth() {
        Element tooltip = tooltip("默认朝上的气泡", "white-space:nowrap;");
        assertEquals(Text.measureLine(Text.of(tooltip), "默认朝上的气泡") + 20, Size.of(tooltip).width(), 0.1);
        assertEquals(36, Size.of(tooltip).height(), 0.1);
    }
}
