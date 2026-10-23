package com.sighs.apricityui.util;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.util.kjs.ApricityUIClientUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownTest {
    @Test
    void rendersHeadingsEmphasisAndLists() {
        String html = Markdown.render("# Hi\n\n**bold** and *em*\n\n- a\n- b");

        assertTrue(html.contains("<h1>Hi</h1>"), html);
        assertTrue(html.contains("<strong>bold</strong>"), html);
        assertTrue(html.contains("<em>em</em>"), html);
        assertTrue(html.contains("<ul>"), html);
        assertTrue(html.contains("<li>a</li>"), html);
    }

    @Test
    void extendedProfileEnablesFencedCodeBlocksWithoutProfileLine() {
        String html = Markdown.render("```\nint x = 1;\n```");

        assertTrue(html.contains("<pre><code>int x = 1;"), html);
    }

    @Test
    void trustedRenderPassesRawHtmlThrough() {
        String html = Markdown.render("<b>x</b>");

        assertTrue(html.contains("<b>x</b>"), html);
    }

    @Test
    void safeModeEscapesDangerousContainerTags() {
        String html = Markdown.renderSafe("<script>alert(1)</script>");

        assertFalse(html.contains("<script>"), html);
        assertTrue(html.contains("&lt;script>"), html);
    }

    @Test
    void bothScriptFacadesExposeMarkdownRendering() throws Exception {
        // Forge/NeoForge 1.21.1 bind ApricityUIClientUtil; NeoForge 26.1 binds the
        // main ApricityUI facade. Both must expose the page-script entry points.
        assertNotNull(ApricityUI.class.getMethod("renderMarkdown", String.class));
        assertNotNull(ApricityUI.class.getMethod("renderMarkdownSafe", String.class));
        assertNotNull(ApricityUIClientUtil.class.getMethod("renderMarkdown", String.class));
        assertNotNull(ApricityUIClientUtil.class.getMethod("renderMarkdownSafe", String.class));
    }

    @Test
    void nullAndEmptyInputRenderToEmptyString() {
        assertEquals("", Markdown.render(null));
        assertEquals("", Markdown.render(""));
        assertEquals("", Markdown.renderSafe(null));
        assertEquals("", Markdown.renderSafe(""));
    }
}
