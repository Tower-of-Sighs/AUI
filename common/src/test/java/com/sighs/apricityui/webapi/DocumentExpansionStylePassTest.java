package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dom.DocumentExpander;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentExpansionStylePassTest {
    @Test
    void knownNoOpPreservesInitialComputedStyle() {
        DocumentExpander original = AuiServices.expander();
        Style[] initial = new Style[1];
        AuiServices.setExpander(new DocumentExpander() {
            public void apply(Document document) { fail("Fast path must report the no-op itself"); }
            public boolean applyAndReportChanges(Document document) {
                initial[0] = document.body.getRawComputedStyle();
                return false;
            }
        });
        Document document = null;
        try {
            HTML.putTemple("test://no-op-expansion", "<html><body style='width:120px'>hello</body></html>");
            document = Document.create("test://no-op-expansion");
            assertSame(initial[0], document.body.getRawComputedStyle());
        } finally {
            if (document != null) document.remove();
            AuiServices.setExpander(original);
        }
    }

    @Test
    void legacyExpanderStillGetsFullFinalStylePass() {
        DocumentExpander original = AuiServices.expander();
        Style[] initial = new Style[1];
        AuiServices.setExpander(document -> {
            initial[0] = document.body.getRawComputedStyle();
            document.body.setAttribute("style", "width:240px;color:red");
        });
        Document document = null;
        try {
            HTML.putTemple("test://changed-expansion", "<html><body style='width:120px'>hello</body></html>");
            document = Document.create("test://changed-expansion");
            assertNotSame(initial[0], document.body.getRawComputedStyle());
            assertEquals("240px", document.body.getRawComputedStyle().width);
        } finally {
            if (document != null) document.remove();
            AuiServices.setExpander(original);
        }
    }
}
