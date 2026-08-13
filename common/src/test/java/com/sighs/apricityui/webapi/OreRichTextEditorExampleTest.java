package com.sighs.apricityui.webapi;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Browser-first ORE rich-text editor resource and Rhino syntax contract. */
class OreRichTextEditorExampleTest {

    private static final String RESOURCE = "assets/apricityui/apricity/tests/richtext-editor.html";

    private static String readResource() throws Exception {
        try (InputStream input = OreRichTextEditorExampleTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            assertNotNull(input, "resource on classpath: " + RESOURCE);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String inlineScript(String html) {
        int start = html.indexOf("<script>");
        int end = html.indexOf("</script>", start);
        assertTrue(start >= 0 && end > start, "page contains an inline editor script");
        return html.substring(start + "<script>".length(), end);
    }

    @Test
    void pageUsesOreAndExposesTheCompleteEditorWorkspace() throws Exception {
        String html = readResource();

        assertTrue(html.contains("../apricityui/theme/ore/ore.css"), "ORE public theme is linked");
        assertTrue(html.contains("class=\"ore-theme\""), "ORE scope is present");
        assertTrue(html.contains("id=\"toolbar\""));
        assertTrue(html.contains("id=\"editor\""));
        assertTrue(html.contains("contenteditable=\"true\""), "browser editing surface is used");
        assertTrue(html.contains("var(--ore-canvas)"), "page consumes ORE tokens");
        assertTrue(html.contains(".toolbar"));
        assertTrue(html.contains(".document-surface"));
        assertTrue(html.contains("#editor u"), "underline styling is explicit");
        assertTrue(html.contains("#editor s { color:inherit"), "strike styling preserves text color");
    }

    @Test
    void inlineScriptCompilesOnTheTargetRhino() throws Exception {
        String script = inlineScript(readResource());
        dev.latvian.mods.rhino.Context context = RhinoTestSupport.enterContext();
        assertNotNull(context.compileString(script, "richtext-editor.html", 1, null),
                "the target Rhino compiler accepts the current editor script");
    }

    @Test
    void scriptUsesInterceptionAndDataDrivenMvc() throws Exception {
        String script = inlineScript(readResource());

        assertTrue(script.contains("function DocumentModel("));
        assertTrue(script.contains("function EditorView(root)"));
        assertTrue(script.contains("function EditorController(model, view)"));
        assertTrue(script.contains("DocumentModel.prototype.insertText"));
        assertTrue(script.contains("EditorView.prototype.render"));
        assertTrue(script.contains("EditorController.prototype.apply"));

        assertTrue(script.contains("addEventListener('beforeinput'"));
        assertTrue(script.contains("addEventListener('compositionstart'"));
        assertTrue(script.contains("addEventListener('paste'"));
        assertTrue(script.contains("addEventListener('cut'"));
        assertTrue(script.contains("addEventListener('drop'"));
        assertTrue(script.contains("preventDefault()"));

        assertTrue(script.contains("undoStack"));
        assertTrue(script.contains("toggleMark"));
        assertTrue(script.contains("setBlockType"));
        assertTrue(script.contains("readSelection"));
        assertTrue(script.contains("writeSelection"));
    }
}
