package com.sighs.apricityui.webapi;

import dev.latvian.mods.rhino.Context;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Browser-first ORE rich-text editor resource and Rhino syntax contract. */
class OreRichTextEditorExampleTest {

    private static final String ROOT = "assets/apricityui/apricity/editor/";

    private static String readResource(String name) throws Exception {
        try (InputStream input = OreRichTextEditorExampleTest.class.getClassLoader()
                .getResourceAsStream(ROOT + name)) {
            assertNotNull(input, "resource on classpath: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void pageUsesOreAndExposesTheCompleteEditorWorkspace() throws Exception {
        String html = readResource("ore-richtext-editor.html");
        String css = readResource("ore-richtext-editor.css");

        assertTrue(html.contains("../apricityui/theme/ore/ore.css"), "ORE public theme is linked");
        assertTrue(html.contains("class=\"ore-theme ore-rich-editor\""), "ORE scope is present");
        assertTrue(html.contains("id=\"editorSurface\""));
        assertTrue(html.contains("contenteditable=\"true\""), "browser editing surface is used");
        assertTrue(html.contains("id=\"previewPane\""));
        assertTrue(html.contains("id=\"sourcePane\""));
        assertTrue(html.contains("id=\"documentOutline\""));
        assertTrue(html.contains("id=\"editorDialogBackdrop\""));
        assertTrue(html.contains("ore-richtext-editor.js"));

        assertTrue(css.contains("var(--ore-canvas)"), "business CSS consumes ORE tokens");
        assertTrue(css.contains(".editor-toolbar"));
        assertTrue(css.contains(".editor-surface"));
        assertTrue(css.contains(".editor-dialog-backdrop"));
    }

    @Test
    void scriptIsRhinoFriendlyAndUsesOnlyLetDeclarations() throws Exception {
        String script = readResource("ore-richtext-editor.js");
        Pattern forbiddenDeclarations = Pattern.compile("\\b(?:var|const)\\s+[A-Za-z_$]");
        Pattern classSyntax = Pattern.compile("\\bclass\\s+[A-Za-z_$]");

        assertTrue(script.contains("let model = new EditorModel()"));
        assertFalse(forbiddenDeclarations.matcher(script).find(), "only let declarations are allowed");
        assertFalse(classSyntax.matcher(script).find(), "prototype constructors keep Rhino compatibility");
        assertFalse(script.contains("=>"), "arrow functions are not used");
        assertFalse(script.contains("exec" + "Command"), "native formatting commands are not used");
        assertFalse(script.contains("?."), "optional chaining is not used");
        assertFalse(script.contains("..."), "spread syntax is not used");

        Context context = Context.enter();
        assertNotNull(context.compileString(script, "ore-richtext-editor.js", 1, null),
                "the bundled KubeJS Rhino compiler accepts the complete script");
    }

    @Test
    void scriptUsesInterceptionAndDataDrivenMvc() throws Exception {
        String script = readResource("ore-richtext-editor.js");

        assertTrue(script.contains("function EditorModel()"));
        assertTrue(script.contains("function EditorView(model)"));
        assertTrue(script.contains("function EditorController(model, view)"));
        assertTrue(script.contains("EditorModel.prototype.insertText"));
        assertTrue(script.contains("EditorView.prototype.renderDocument"));
        assertTrue(script.contains("EditorController.prototype.commit"));

        assertTrue(script.contains("addEventListener(\"beforeinput\""));
        assertTrue(script.contains("addEventListener(\"compositionstart\""));
        assertTrue(script.contains("addEventListener(\"paste\""));
        assertTrue(script.contains("addEventListener(\"cut\""));
        assertTrue(script.contains("addEventListener(\"drop\""));
        assertTrue(script.contains("event.preventDefault()"));

        assertTrue(script.contains("undoStack"));
        assertTrue(script.contains("parseHtmlBlocks"));
        assertTrue(script.contains("blocksToHtml"));
        assertTrue(script.contains("window.localStorage"));
        assertTrue(script.contains("new Blob"));

        assertFalse(script.contains("getRichTextSelection"), "the first implementation uses browser APIs only");
        assertFalse(script.contains("RichTextEditing"), "no module-specific editing API is assumed");
    }
}
