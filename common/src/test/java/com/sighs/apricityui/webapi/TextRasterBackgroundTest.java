package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.util.TextMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TextRasterBackgroundTest {
    @Test
    void selectsContainingAncestorBackgroundAndPreservesOwnerOnClone() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "position:relative;width:200px;height:100px;background:#48494A;");

        Element track = new Element(document, "div");
        track.setAttribute("style", "position:relative;width:100px;height:8px;top:20px;background:#8C8D90;");
        Element label = new Element(document, "span");
        label.setAttribute("style", "position:absolute;left:8px;top:-12px;font-size:8px;line-height:8px;");
        label.setTextContent("value");
        track.appendChild(label);
        document.body.appendChild(track);
        document.commitRenderState();

        Text text = Text.of(label);
        assertEquals("#8C8D90", text.rasterBackgroundColor);
        Text clone = TextMetrics.cloneTextForSegment(text, "value", null);
        assertSame(label, clone.owner());

        Position outside = Rect.of(label).position;
        assertEquals("#48494A", TextMetrics.resolveRasterBackgroundColor(text, outside, "value"));

        label.setAttribute("style", "position:absolute;left:8px;top:0px;font-size:8px;line-height:8px;");
        document.flushPendingStyleUpdates();
        document.commitRenderState();
        Position inside = Rect.of(label).position;
        assertEquals("#8C8D90", TextMetrics.resolveRasterBackgroundColor(text, inside, "value"));
    }
}
