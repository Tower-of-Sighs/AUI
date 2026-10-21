package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Drawer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for high-frequency textContent updates. */
class TextContentDirtyScopeTest {
    @Test
    void directInnerTextUpdateDoesNotRequestPaintListReorder() {
        Document document = TestDocumentFactory.createDocument();
        Element label = document.createElement("span");
        document.body.appendChild(label);
        clearPendingWork(document, label);

        label.setTextContent("0:00.03");

        assertTrue(label.hasDirtyFlag(Drawer.RELAYOUT));
        assertTrue(label.hasDirtyFlag(Drawer.REPAINT));
        assertTrue(label.hasDirtyFlag(Drawer.HITTEST));
        assertFalse(label.hasDirtyFlag(Drawer.REORDER),
                "updating the existing direct innerText must not rebuild the paint list");
    }

    @Test
    void replacingExistingChildNodesStillRequestsPaintListReorder() {
        Document document = TestDocumentFactory.createDocument();
        Element label = document.createElement("span");
        label.appendChild(document.createTextNode("old"));
        document.body.appendChild(label);
        clearPendingWork(document, label);

        label.setTextContent("new");

        assertTrue(label.hasDirtyFlag(Drawer.REORDER),
                "replacing actual child nodes must preserve structural invalidation");
    }

    private static void clearPendingWork(Document document, Element element) {
        document.getDirtyElements().clear();
        element.clearDirtyFlags();
    }
}
