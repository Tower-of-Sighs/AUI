package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentEditableUpgradeTest {

    @Test
    void nestedContenteditableElementUpgradesToRichText() {
        Document document = TestDocumentFactory.createDocument();
        Element root = document.createHTML(
                "<div class=\"wrap\"><div class=\"toolbar\"></div>"
                        + "<div id=\"editor\" contenteditable=\"true\" spellcheck=\"false\"></div></div>");
        Element editor = document.getElementById("editor");
        assertTrue(editor instanceof com.sighs.apricityui.element.RichText,
                "nested contenteditable div must upgrade to RichText, got "
                        + (editor == null ? "null" : editor.getClass().getSimpleName()));
    }

    /** The page selection bridge relies on Rhino exposing these raw-node properties. */
    @Test
    void rhinoCanReadParentElementAndNodeType() {
        Document document = TestDocumentFactory.createDocument();
        Element p = document.createElement("p");
        com.sighs.apricityui.dom.TextNode tn = document.createTextNode("hello");
        p.appendChild(tn);
        document.body.appendChild(p);

        dev.latvian.mods.rhino.Context cx = RhinoTestSupport.enterContext();
        dev.latvian.mods.rhino.Scriptable scope = cx.initStandardObjects();
        RhinoTestSupport.put(cx, scope, "TN", tn);
        RhinoTestSupport.put(cx, scope, "P", p);
        Object result = cx.evaluateString(scope,
                "var host = TN.nodeType === 1 ? TN : TN.parentElement;"
                + " host ? (host.tagName + '|' + TN.nodeType) : 'no-host';",
                "raw", 1, null);
        assertTrue("P|3".equals(result), "raw node parentElement/nodeType must resolve, got " + result);
    }

}
