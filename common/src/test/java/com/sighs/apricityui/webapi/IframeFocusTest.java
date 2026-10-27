package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Iframe;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IframeFocusTest {
    @Test
    void clickingEmbeddedDocumentDoesNotFocusOuterTextSelectionUnit() {
        Document document = TestDocumentFactory.createDocument();
        document.body.appendChild(document.createHTML("<p>Outer selectable text</p>"));
        Iframe iframe = new Iframe(document);
        iframe.setAttribute("style", "display:block;width:320px;height:180px");
        document.body.appendChild(iframe);

        for (int button : new int[]{0, 1}) {
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", new Position(20, 40), button, false),
                    document, iframe);
            assertEquals(iframe, document.getFocusedElement(),
                    "Outer text selection must not steal the embedded document's keyboard focus");
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", new Position(20, 40), button, false),
                    document, iframe);
            assertEquals(iframe, document.getFocusedElement());
            assertFalse(document.getDocumentSelection().isSelecting());
        }
    }
}
