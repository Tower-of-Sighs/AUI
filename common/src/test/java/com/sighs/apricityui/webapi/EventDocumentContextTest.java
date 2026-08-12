package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.richtext.SelectionBridge;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventDocumentContextTest {

    @Test
    void domEventUsesTargetDocumentForWindowSelectionAndRestoresPreviousContext() {
        Document targetDocument = TestDocumentFactory.createDocument();
        Document otherDocument = TestDocumentFactory.createDocument();
        RichText editor = (RichText) targetDocument.createHTML(
                "<div contenteditable><p>abc</p></div>");
        targetDocument.body.appendChild(editor);
        Element oldParagraph = (Element) editor.getChildNodes().get(0);
        TextNode oldText = (TextNode) oldParagraph.getChildNodes().get(0);
        targetDocument.getRichTextSelection().setCollapsed(oldParagraph, 1);
        AtomicReference<Document> callbackContext = new AtomicReference<>();
        AtomicReference<Element> newParagraph = new AtomicReference<>();

        editor.addEventListener("beforeinput", event -> {
            callbackContext.set(Document.getContextDocument());
            Element paragraph = targetDocument.createElement("p");
            TextNode text = targetDocument.createTextNode("abxc");
            paragraph.appendChild(text);
            editor.replaceChildren(paragraph);
            newParagraph.set(paragraph);
            SelectionBridge selection = Window.window.getSelection();
            selection.setBaseAndExtent(text, 3, text, 3);
        });

        try (Document.ContextScope ignored = Document.withContext(otherDocument)) {
            Event.tiggerEvent(new Event.InputEvent(editor, "beforeinput", true, "insertText", "x"));

            assertSame(targetDocument, callbackContext.get());
            assertSame(otherDocument, Document.getContextDocument());
        }

        assertTrue(targetDocument.getRichTextSelection().hasAnchor());
        assertSame(newParagraph.get(), targetDocument.getRichTextSelection().getAnchorUnit());
        assertFalse(otherDocument.getRichTextSelection().hasAnchor());
    }
}
