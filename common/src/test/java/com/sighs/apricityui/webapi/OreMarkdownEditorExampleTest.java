package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ORE markdown editor example: resource contract, Rhino syntax and two-pane layout. */
class OreMarkdownEditorExampleTest {
    private static final String RESOURCE = "assets/apricityui/apricity/tests/markdown-editor.html";
    private static final String PATH = "apricity/tests/markdown-editor.html";

    @AfterEach
    void cleanup() {
        Size.clearViewportOverride();
    }

    @Test
    void pageUsesOreAndTheBuiltInMarkdownApi() throws Exception {
        String html = readResource();

        assertTrue(html.contains("../apricityui/theme/ore/ore.css"), "ORE public theme is linked");
        assertTrue(html.contains("class=\"ore-theme\""), "ORE scope is present");
        assertTrue(html.contains("id=\"mdInput\""), "source editor exists");
        assertTrue(html.contains("id=\"mdPreview\""), "preview exists");
        assertTrue(html.contains("ApricityUI.renderMarkdownSafe"), "built-in Markdown API is used");
        assertTrue(html.contains("md-split"), "side-by-side split is present");
        assertTrue(html.contains("var(--gold)"), "page consumes ORE tokens");
    }

    @Test
    void inlineScriptCompilesOnTheTargetRhino() throws Exception {
        String script = inlineScript(readResource());
        dev.latvian.mods.rhino.Context context = RhinoTestSupport.enterContext();
        assertNotNull(context.compileString(script, "markdown-editor.html", 1, null),
                "the target Rhino compiler accepts the current editor script");
    }

    @Test
    void previewStillRendersWhenTextAreaSelectionApisAreMissing() throws Exception {
        String html = readResource();
        HTML.putTemple(PATH, html);
        Document document = new Document(PATH, false);
        try {
            document.refresh();

            Context cx = RhinoTestSupport.enterContext();
            Scriptable scope = cx.initStandardObjects();
            // 真实 DOM 绑定为 auiDocument，页面脚本看到的 document 是一层 JS facade：
            // textarea 只保留 value/focus/addEventListener，没有 selectionStart/
            // selectionEnd/setSelectionRange，模拟宿主里 textarea 还没升级成 TextArea 的场景。
            RhinoTestSupport.put(cx, scope, "auiDocument", RhinoTestSupport.wrap(cx, scope, document));
            cx.evaluateString(scope,
                    "var inputStub = {"
                            + "value: '',"
                            + "focus: function () {},"
                            + "addEventListener: function () {}"
                            + "};"
                            + "var document = {"
                            + "getElementById: function (id) {"
                            + "  if (id === 'mdInput') return inputStub;"
                            + "  return auiDocument.getElementById(id);"
                            + "},"
                            + "createElement: function (tag) { return auiDocument.createElement(tag); },"
                            + "querySelector: function (selector) { return auiDocument.querySelector(selector); },"
                            + "querySelectorAll: function (selector) { return auiDocument.querySelectorAll(selector); },"
                            + "addEventListener: function () {}"
                            + "};"
                            + "var ApricityUI = { renderMarkdownSafe: function(md) {"
                            + "return '<h1>Ore Markdown Lab</h1><p>Hello</p>';"
                            + "} };"
                            + "function setTimeout(fn, ms) { fn(); return 1; }"
                            + "function clearTimeout(id) {}",
                    "markdown-editor-stubs", 1, null);
            cx.evaluateString(scope, inlineScript(html), "markdown-editor.html", 1, null);

            Element preview = document.getElementById("mdPreview");
            assertTrue(preview.getTextContent().contains("Ore Markdown Lab"),
                    "preview should render even when the textarea lacks selection APIs: "
                            + preview.getTextContent());
        } finally {
            document.remove();
        }
    }

    @Test
    void oreMarkdownEditorLaysOutAsTwoPanes() throws Exception {
        Size.setViewportOverride(1200, 800);
        HTML.putTemple(PATH, readResource());
        Document document = new Document(PATH, false);
        try {
            document.refresh();

            Element split = document.querySelector(".md-split");
            List<Element> panes = document.querySelectorAll(".md-pane");
            Element input = document.getElementById("mdInput");
            Element preview = document.getElementById("mdPreview");

            assertTrue(split.getBoundingClientRect().width > 1000, "split should fill the viewport");
            assertTrue(split.getBoundingClientRect().height > 500);
            assertEquals(2, panes.size());
            Element left = panes.get(0);
            Element right = panes.get(1);
            assertEquals(left.getBoundingClientRect().y, right.getBoundingClientRect().y, 0.01);
            assertTrue(right.getBoundingClientRect().x > left.getBoundingClientRect().x + 400,
                    "panes must be side by side");
            assertTrue(input.getBoundingClientRect().width > 400);
            assertTrue(input.getBoundingClientRect().height > 400, "editor should fill its pane");
            assertTrue(preview.getBoundingClientRect().width > 400);
            Element scroller = document.querySelector(".md-preview-scroll");
            assertTrue(scroller.getBoundingClientRect().height > 400, "preview scroller should fill the pane");
        } finally {
            document.remove();
        }
    }

    private static String readResource() throws Exception {
        try (InputStream input = OreMarkdownEditorExampleTest.class.getClassLoader()
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
}
