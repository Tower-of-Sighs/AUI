package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dev.resource.ResourceCreateDialog;
import com.sighs.apricityui.dev.resource.ResourceMetaDialog;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Selector;
import com.sighs.apricityui.instance.ApricityViewport;
import com.sighs.apricityui.resource.CSS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceDialogStyleTest {
    private static final Path TEMPLATE = Path.of(
            "src/main/resources/assets/apricityui/apricity/devtools/resource.html");
    private static final Path GLOBAL_STYLE = Path.of(
            "src/main/resources/assets/apricityui/apricity/global.css");

    @TempDir
    Path tempDir;

    @Test
    void createAndMetaDialogsUseResource3StructureAndStates() throws Exception {
        Document document = styledDocument();
        ResourceCreateDialog createDialog = new ResourceCreateDialog();
        createDialog.open(document, "tests", null);
        try {
            document.flushPendingStyleUpdates();
            assertNotNull(document.querySelector(".dialog-overlay.show"));
            assertNotNull(document.querySelector(".dialog-header"));
            assertNotNull(document.querySelector(".dialog-title-icon"));
            assertNotNull(document.querySelector(".dialog-body"));
            assertNotNull(document.querySelector(".dialog-input"));
            assertNotNull(document.querySelector(".dialog-footer"));
            assertNull(document.querySelector(".resource-create-input"));

            Element header = document.querySelector(".dialog-header");
            Element input = document.querySelector(".dialog-input");
            Element create = document.querySelector(".dialog-btn-confirm");
            assertEquals("var(--purple)", header.getComputedStyle().backgroundColor);
            assertEquals("2px solid var(--gray-light)", input.getComputedStyle().border);
            assertEquals("tests/", input.value);
            assertTrue(create.isDisabled());
            assertTrue(create.matches(".dialog-btn:disabled"));
            assertTrue(document.CSSCache.containsKey(".dialog-btn:disabled"));
            assertEquals("0.4", document.CSSCache.get(".dialog-btn:disabled").get("opacity").value());
            assertEquals("0.4", Selector.matchCSS(create).get("opacity"));
        } finally {
            createDialog.close();
        }

        Path html = tempDir.resolve("dialog-meta.html");
        Files.writeString(html, "<head><meta name=\"aui-viewport\" content=\"mode=browser\"></head><body></body>");
        ResourceMetaDialog metaDialog = new ResourceMetaDialog();
        metaDialog.open(document, "tests/dialog-meta.html", html, null);
        try {
            assertEquals(3, document.querySelectorAll(".dialog-select-wrap").size());
            assertEquals(3, document.querySelectorAll(".dialog-select").size());
            assertEquals(3, document.querySelectorAll(".dialog-select-arrow").size());
            assertEquals(3, document.querySelectorAll(".resource-meta-select[data-tooltip-key]").size());
            assertEquals(12, document.querySelectorAll(".resource-meta-option[data-tooltip-key]").size());

            Element select = document.querySelector(".dialog-select");
            Element arrow = document.querySelector(".dialog-select-arrow");
            assertFalse(select.matches(".tree-icon svg"));
            assertNull(Selector.matchCSS(select).get("height"));
            assertEquals("auto", select.getRawComputedStyle().height);
            assertEquals("14px", select.getRawComputedStyle().paddingLeft);
            assertEquals("var(--purple)", arrow.getComputedStyle().backgroundColor);
            document.setFocusedElement(select);
            document.flushPendingStyleUpdates();
            assertEquals("var(--purple-dark)", Selector.matchCSS(arrow).get("background"));
        } finally {
            metaDialog.close();
        }
    }

    @Test
    void createDialogDefaultsToResourceRootPath() throws Exception {
        Document document = styledDocument();
        ResourceCreateDialog createDialog = new ResourceCreateDialog();
        createDialog.open(document, "", null);
        try {
            assertEquals("/", document.querySelector(".dialog-input").value);
        } finally {
            createDialog.close();
        }
    }

    private static Document styledDocument() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Field viewport = Document.class.getDeclaredField("viewport");
        viewport.setAccessible(true);
        viewport.set(document, new ApricityViewport(1920, 1080, 1.0f, 1.0d));

        String html = Files.readString(TEMPLATE);
        int styleStart = html.indexOf("<style>");
        int styleEnd = html.indexOf("</style>", styleStart);
        String css = html.substring(styleStart + "<style>".length(), styleEnd);
        Map<String, Map<String, CSS.Declaration>> cache = new java.util.LinkedHashMap<>();
        CSS.readCSS(Files.readString(GLOBAL_STYLE), cache, GLOBAL_STYLE.toString());
        CSS.readCSS(css, cache, TEMPLATE.toString());
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        return document;
    }
}
