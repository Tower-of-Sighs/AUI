package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionedFlexCrossSizeTest {
    private Element child(String align) {
        var document = TestDocumentFactory.createDocument();
        Element frame = document.createElement("div");
        frame.setAttribute("style", "position:relative;width:500px;height:200px;");
        document.body.appendChild(frame);
        Element mask = document.createElement("div");
        mask.setAttribute("style", "position:absolute;inset:0;display:flex;flex-direction:column;align-items:" + align + ";justify-content:center;");
        frame.appendChild(mask);
        Element child = document.createElement("span");
        child.setTextContent("Loading...");
        mask.appendChild(child);
        document.commitRenderState();
        return child;
    }

    @Test
    void centeredChildOfInsetPositionedFlexRemainsContentSized() {
        Element child = child("center");
        double width = Size.of(child).width();
        assertTrue(width > 0 && width < 200);
        assertTrue(Size.natural(child).width() < 200);
        assertEquals((500 - width) / 2, Position.getOffset(child).x, 0.01);
    }

    @Test
    void stretchStillFillsPositionedFlexCrossAxis() {
        Element child = child("stretch");
        assertEquals(500, Size.of(child).width(), 0.01);
        assertEquals(0, Position.getOffset(child).x, 0.01);
    }
}
