package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression for issue 131.
 *
 * <p>Updating the root element's inline {@code transform} runs
 * {@code RenderElement.observeStyle}, which parses the transform and therefore
 * resolves a CSS length. {@code CssLength.resolve} asks for the root font size,
 * and that read goes back to the root element's computed style while the inline
 * update is still in flight — the snapshot has not been refreshed yet — which
 * used to re-enter {@code updateInlineStyle} until the stack overflowed.</p>
 *
 * <p>Needs an active document context: {@code Size.getRootFontSize()} reads the
 * context document's root style; without one the run-time root-font override
 * short-circuits and the chain never reaches the root computed style.</p>
 */
class RootTransformInlineStyleReentrancyTest {
    @Test
    void reupdatingRootTranslateZDoesNotReenterInlineStyleUpdate() {
        String path = "test://root-transform-reentrancy";
        HTML.putTemple(path, "<html><body></body></html>");
        Document document = Document.create(path);
        try {
            Element html = document.documentElement;
            html.setAttribute("style", "transform:translateZ(10px)");
            html.getComputedStyle();
            document.body.setAttribute("style", "transform:translateZ(0px)");
            document.body.getComputedStyle();

            // Second update on the same root node, with the document as the active context so
            // the transform length resolution reaches Size.resolveDocumentRootFontSize().
            Document.runWithContext(document, () ->
                    html.setAttribute("style", "transform:translateZ(20px)"));

            assertEquals(20.0d, DocumentLayerOrder.translateZ(document), 0.0001d);
        } finally {
            document.remove();
        }
    }
}
