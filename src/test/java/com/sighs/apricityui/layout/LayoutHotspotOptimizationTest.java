package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LayoutHotspotOptimizationTest {
    @Test
    void reusesBoxInnerSizeObjectsUntilInputsChange() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        element.setAttribute("style", "width:120px;height:40px;padding:4px;border:2px solid;");
        document.body.appendChild(element);

        Box box = Box.of(element);
        assertSame(box.rawInnerSize(), box.rawInnerSize());
        assertSame(box.innerSize(), box.innerSize());

        Size before = box.innerSize();
        element.getRenderer().size.clear();
        Size after = box.innerSize();
        assertEquals(before, after);
    }

    @Test
    void reusesBoxOuterSizeUntilElementSizeChanges() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        element.setAttribute("style", "width:120px;height:40px;margin:3px 5px;");
        document.body.appendChild(element);

        Box box = Box.of(element);
        assertSame(box.size(), box.size());

        Size before = box.size();
        element.getRenderer().size.clear();
        Size after = box.size();
        assertEquals(before, after);
        assertSame(after, box.size());
    }

    @Test
    void resolvesScaleWidthIterativelyThroughPercentageAncestors() {
        Size.setViewportOverride(1000, 800);
        try {
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "width:800px;height:400px;");

            Element parent = document.createElement("div");
            parent.setAttribute("style", "width:50%;");
            document.body.appendChild(parent);

            Element child = document.createElement("div");
            parent.appendChild(child);

            assertEquals(400, Size.getScaleWidth(child), 0.0001);
        } finally {
            Size.clearViewportOverride();
        }
    }
}
