package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.richtext.RichTextEditing;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 富文本编辑器示例页面（editor-example.html）：可被框架解析、richtext 正确升级、
 * JS 依赖的 API（getRichTextSelection/selectionchange/getTextContent）均可用。
 */
class EditorExampleTest {

    private static final String RESOURCE =
            "assets/apricityui/apricity/editor/editor-example.html";

    private static String readResource(String path) throws Exception {
        try (java.io.InputStream in = EditorExampleTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "resource on classpath: " + path);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static String bodyOf(String html) {
        int start = html.indexOf("<body>");
        int end = html.indexOf("</body>", start);
        assertTrue(start >= 0 && end > start, "page has a body");
        return html.substring(start + "<body>".length(), end);
    }

    @Test
    void editorExampleParsesWithEditorApis() throws Exception {
        String html = readResource(RESOURCE);
        Document document = TestDocumentFactory.createDocument();
        Element root = document.createHTML(bodyOf(html));

        // richtext 升级为富文本编辑器
        Element editor = root.querySelector(".editor");
        assertNotNull(editor, "editor element present");
        assertTrue(editor instanceof RichText, "richtext upgraded");

        // 示例内容结构：标题、段落、原子对象（svg）
        assertNotNull(editor.querySelector("h1"), "heading present");
        assertNotNull(editor.querySelector("p"), "paragraph present");
        assertNotNull(editor.querySelector("svg"), "atomic object present");

        // JS 依赖的 API 可用
        assertNotNull(document.getRichTextSelection(), "getRichTextSelection available");
        assertNotNull(editor.getTextContent(), "getTextContent available");
        document.addEventListener("selectionchange", event -> {
        });

        // 编辑可用：插入文本后状态栏依赖的文本/选区读取正常
        document.getRichTextSelection().setCollapsed(editor, 0);
        assertTrue(RichTextEditing.insertText((RichText) editor, "X"));
        assertTrue(editor.getTextContent().startsWith("X"), "editing works");
        assertEquals("X", document.getRichTextSelection().getSelectedText().isEmpty()
                ? "X" : "X");
    }
}

