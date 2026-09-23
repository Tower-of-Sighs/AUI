package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FontDrawerEffectiveOpacityTest {
    @Test
    void multipliesOpacityAcrossTheTextStackingContext() {
        Document document = new Document("test://font-opacity", false);
        Element parent = new Element(document, "div");
        Element child = new Element(document, "span");
        parent.appendChild(child);

        assertEquals(1.0d, FontDrawer.effectiveOpacity(child));

        parent.setAttribute("style", "opacity: 0.5");
        assertEquals(0.5d, FontDrawer.effectiveOpacity(child));

        child.setAttribute("style", "filter: opacity(50%)");
        assertEquals(0.25d, FontDrawer.effectiveOpacity(child));
    }
}
