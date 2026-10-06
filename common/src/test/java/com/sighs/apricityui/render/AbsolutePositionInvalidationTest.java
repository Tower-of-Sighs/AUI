package com.sighs.apricityui.render;

import com.sighs.apricityui.dom.RenderElement;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbsolutePositionInvalidationTest {
    @Test
    void absoluteGeometryChangeKeepsAncestorAndSiblingSizeCaches() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = document.createElement("div");
        Element absolute = document.createElement("div");
        Element sibling = document.createElement("div");
        parent.appendChild(absolute);
        parent.appendChild(sibling);
        document.body.appendChild(parent);

        Size parentSize = new Size(300, 100);
        Size siblingSize = new Size(20, 20);
        absolute.getRenderer().size.set(new Size(10, 8));
        parent.getRenderer().size.set(parentSize);
        sibling.getRenderer().size.set(siblingSize);

        Style origin = new Style();
        origin.update("position", "absolute");
        origin.update("left", "0%");
        origin.update("width", "10%");
        Style current = origin.clone();
        current.update("left", "80%");
        current.update("width", "80%");

        RenderElement.observeStyle(absolute, origin, current);

        assertNull(absolute.getRenderer().size.get());
        assertSame(parentSize, parent.getRenderer().size.get());
        assertSame(siblingSize, sibling.getRenderer().size.get());
        assertTrue(absolute.hasDirtyFlag(Drawer.RELAYOUT));
    }
}
