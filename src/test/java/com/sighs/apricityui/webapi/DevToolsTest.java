package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.dev.devtools.DevToolsController;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevToolsTest {
    private static final Path REFERENCE = Path.of("src/main/resources/assets/apricityui/apricity/devtools/devtools0.html");
    private static final Path TEMPLATE = Path.of("src/main/resources/assets/apricityui/apricity/devtools/devtools.html");
    private static final Path LEGACY = Path.of("src/main/resources/assets/apricityui/apricity/devtools/index.html");

    @Test
    void templateKeepsReferenceStyleButContainsNoRuntimeJavascript() throws Exception {
        String reference = Files.readString(REFERENCE);
        String template = Files.readString(TEMPLATE);

        assertEquals(styleBlock(reference), styleBlock(template));
        assertFalse(template.toLowerCase().contains("<script"));
        assertFalse(template.contains("onclick="));
        assertTrue(template.contains("<meta name=\"aui-font-mode\" content=\"web\">"));
        assertTrue(template.contains("<meta name=\"aui-viewport\" content=\"mode=browser\">"));
        assertTrue(template.contains("<meta name=\"aui-mouse-events\" content=\"intercept\">"));
        assertFalse(Files.exists(LEGACY));
    }

    @Test
    void javaControllerPopulatesAndEditsTheInspectedDocument() throws Exception {
        Size.setViewportOverride(1600, 900);
        String targetPath = "test://devtools-target";
        String alternatePath = "test://devtools-alternate";
        HTML.putTemple(DevToolsController.PATH, Files.readString(TEMPLATE));
        HTML.putTemple(targetPath, """
                <html><head><meta name="description" content="test"></head><body>
                <main id="app" class="page"><h1 style="color:#111111">Title</h1><p>Body</p><p>Before <strong>Middle</strong> After</p></main>
                </body></html>
                <style>.page { padding: 12px; } h1 { font-size: 24px; }</style>
                """);
        HTML.putTemple(alternatePath, "<html><body><section id=\"alternate-document\">Alternate</section></body></html>");
        Document target = Document.create(targetPath);
        Document alternate = Document.create(alternatePath);
        Document duplicate = Document.create(targetPath);
        assertNotNull(target);
        assertNotNull(alternate);
        assertNotNull(duplicate);

        try {
            assertTrue(DevTools.selectDocument(target));
            Document tool = DevTools.getToolDocument();
            assertNotNull(tool);
            assertTrue(tool.isReloadPersistent());
            Element sidePanel = tool.querySelector(".side-panel");
            Element topbar = tool.querySelector(".topbar");
            assertNotNull(sidePanel);
            assertNotNull(topbar);
            assertEquals(tool.body, sidePanel.parentElement);
            assertEquals(sidePanel, topbar.parentElement);
            assertEquals(topbar, sidePanel.children.get(0));
            assertTrue(tool.querySelectorAll(".main").isEmpty());
            assertTrue(tool.querySelectorAll(".preview-panel").isEmpty());
            assertTrue(tool.querySelectorAll(".device-btn").isEmpty());
            assertEquals("none", tool.body.getComputedStyle().pointerEvents);
            assertEquals("auto", sidePanel.getComputedStyle().pointerEvents);
            Element documentSelect = tool.querySelector("#documentSelect");
            assertNotNull(documentSelect);
            assertEquals(sidePanel, documentSelect.parentElement.parentElement);
            assertEquals(documentSelect.parentElement, sidePanel.children.get(1));
            assertEquals("▾", tool.querySelector(".document-select-arrow").getTextContent());
            assertEquals(target.getUuid().toString(), documentSelect.getValue());
            assertEquals(documentLabel(target), documentSelect.querySelector("option[value=\""
                    + target.getUuid() + "\"]").getTextContent());
            assertEquals(documentLabel(alternate), documentSelect.querySelector("option[value=\""
                    + alternate.getUuid() + "\"]").getTextContent());
            assertEquals(documentLabel(duplicate), documentSelect.querySelector("option[value=\""
                    + duplicate.getUuid() + "\"]").getTextContent());
            documentSelect.setValue(duplicate.getUuid().toString());
            documentSelect.dispatchEvent(new Event(documentSelect, "change", true));
            assertNotNull(tool.querySelector("#domTree [data-node-id=\""
                    + duplicate.querySelector("#app").uuid + "\"]"));
            assertEquals(duplicate.getUuid().toString(), documentSelect.getValue());
            documentSelect.setValue(alternate.getUuid().toString());
            documentSelect.dispatchEvent(new Event(documentSelect, "change", true));
            assertNotNull(tool.querySelector("#domTree [data-node-id=\""
                    + alternate.querySelector("#alternate-document").uuid + "\"]"));
            assertEquals(alternate.getUuid().toString(), documentSelect.getValue());
            documentSelect.setValue(target.getUuid().toString());
            documentSelect.dispatchEvent(new Event(documentSelect, "change", true));
            assertEquals("DEVTOOLS", topbar.querySelector(".logo").getTextContent());
            assertTrue(tool.querySelector("#pickBtn").getTextContent().isBlank());
            assertTrue(tool.querySelector(".console-btn").getTextContent().isBlank());
            assertEquals("tooltip.apricityui.devtools.inspect",
                    tool.querySelector("#pickBtn").getAttribute("data-tooltip-key"));
            assertEquals("tooltip.apricityui.devtools.console",
                    tool.querySelector(".console-btn").getAttribute("data-tooltip-key"));
            Element dragHandle = tool.querySelector("#panelDragHandle");
            assertNotNull(dragHandle);
            assertEquals("tooltip.apricityui.devtools.move", dragHandle.getAttribute("data-tooltip-key"));
            Element.DOMRect panelBeforeDrag = sidePanel.getBoundingClientRect();
            dragHorizontally(tool, dragHandle, -160);
            Element.DOMRect panelAfterDrag = sidePanel.getBoundingClientRect();
            assertEquals(panelBeforeDrag.x - 160, panelAfterDrag.x, 0.01);
            assertEquals(panelBeforeDrag.y, panelAfterDrag.y, 0.01);
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", new Position(200, 300), -1, false),
                    tool, tool.body);
            assertEquals(panelAfterDrag.x, sidePanel.getBoundingClientRect().x, 0.01,
                    "releasing outside the panel must end the drag");
            dragHorizontally(tool, dragHandle, -2000);
            assertEquals(0, sidePanel.getBoundingClientRect().x, 0.01);
            assertEquals(0, sidePanel.getBoundingClientRect().y, 0.01);
            dragHorizontally(tool, dragHandle, 3000);
            assertEquals(1600 - sidePanel.getBoundingClientRect().width,
                    sidePanel.getBoundingClientRect().x, 0.01);
            assertEquals(0, sidePanel.getBoundingClientRect().y, 0.01);
            Element pick = tool.querySelector("#pickBtn");
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", new Position(1500, 20), -1, false), tool, pick);
            Element tooltip = tool.querySelector(".aui-tooltip");
            assertNotNull(tooltip);
            assertFalse(tooltip.getTextContent().isBlank());
            MouseEvent.dispatchToTarget(new MouseEvent("mousemove", new Position(20, 20), -1, false), tool, tool.body);
            assertTrue(tool.querySelectorAll(".aui-tooltip").isEmpty());
            assertNotNull(tool.querySelector("#domTree .dom-node"));
            assertTrue(tool.querySelector("#nodeCount").getTextContent().endsWith("nodes"));
            assertTrue(tool.querySelector("#domTree").getTextContent().contains("\"Title\""));
            assertTrue(tool.querySelector("#domTree").getTextContent().contains("</h1>"));
            assertTrue(tool.querySelector("#domTree").getTextContent().contains("\"Body\""));
            assertTrue(tool.querySelector("#domTree").getTextContent().contains("</p>"));
            String treeText = tool.querySelector("#domTree").getTextContent();
            int beforeIndex = treeText.indexOf("\"Before\"");
            int strongIndex = treeText.indexOf("<strong");
            int middleIndex = treeText.indexOf("\"Middle\"");
            int closeStrongIndex = treeText.indexOf("</strong>");
            int afterIndex = treeText.indexOf("\"After\"");
            assertTrue(beforeIndex >= 0 && beforeIndex < strongIndex);
            assertTrue(strongIndex < middleIndex && middleIndex < closeStrongIndex);
            assertTrue(closeStrongIndex < afterIndex);

            Element heading = target.querySelector("h1");
            assertNotNull(heading);
            assertTrue(DevTools.selectElement(heading));
            assertTrue(tool.querySelector(".attr-block-header").getTextContent().startsWith("H1"));

            tool.querySelector(".inspector-tab[data-tab=\"styles\"]").click();
            tool.querySelector(".style-add").click();
            Element property = tool.querySelector("input[placeholder=\"property\"]");
            Element value = tool.querySelector("input[placeholder=\"value\"]");
            assertNotNull(property);
            assertNotNull(value);
            property.value = "background-color";
            value.value = "#123456";
            Element save = findByText(tool, ".style-prop-delete", "+");
            assertNotNull(save);
            save.click();
            assertTrue(heading.getAttribute("style").contains("background-color: #123456;"));

            tool.querySelector(".inspector-tab[data-tab=\"boxmodel\"]").click();
            assertNotNull(tool.querySelector(".boxmodel-visual"));
            assertNotNull(tool.querySelector(".bx-content"));

            assertTrue(pick.getClassNames().contains("active"));
            pick.click();
            assertFalse(pick.getClassNames().contains("active"));
        } finally {
            if (DevTools.isOpen()) DevTools.toggle();
            duplicate.remove();
            alternate.remove();
            target.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void automaticTargetSelectionSkipsInternalCursorOverlay() throws Exception {
        Size.setViewportOverride(1600, 900);
        String targetPath = "test://devtools-preferred-target";
        String cursorPath = "test://internal-cursor-overlay";
        HTML.putTemple(DevToolsController.PATH, Files.readString(TEMPLATE));
        HTML.putTemple(targetPath, "<html><body><main id=\"preferred-target\">Target</main></body></html>");
        HTML.putTemple(cursorPath, """
                <html><body class="cursor-overlay-body">
                <div id="baeffect-cursor-layer" class="cursor-layer cursor-normal"></div>
                </body></html>
                """);
        Document target = Document.create(targetPath);
        Document cursor = Document.create(cursorPath);
        assertNotNull(target);
        assertNotNull(cursor);

        try {
            if (DevTools.isOpen()) DevTools.toggle();
            assertTrue(DevTools.ensureOpen());
            Document tool = DevTools.getToolDocument();
            assertNotNull(tool);
            assertNotNull(tool.querySelector("#domTree [data-node-id=\""
                    + target.querySelector("#preferred-target").uuid + "\"]"));
            assertTrue(tool.querySelector("#domTree").getTextContent().contains("preferred-target"));
            assertFalse(tool.querySelector("#domTree").getTextContent().contains("baeffect-cursor-layer"));
        } finally {
            if (DevTools.isOpen()) DevTools.toggle();
            cursor.remove();
            target.remove();
            Size.clearViewportOverride();
        }
    }

    private static Element findByText(Document document, String selector, String expected) {
        for (Element element : document.querySelectorAll(selector)) {
            if (expected.equals(element.getTextContent())) return element;
        }
        return null;
    }

    private static void dragHorizontally(Document document, Element handle, double deltaX) {
        Element.DOMRect rect = handle.getBoundingClientRect();
        Position start = new Position(rect.x + rect.width / 2, rect.y + rect.height / 2);
        Position end = new Position(start.x + deltaX, start.y + 120);
        Element pressTarget = handle.querySelector("path");
        MouseEvent.dispatchToTarget(new MouseEvent("mousedown", start, 0, false), document,
                pressTarget == null ? handle : pressTarget);
        MouseEvent.dispatchToTarget(new MouseEvent("mousemove", end, -1, false), document, document.body);
        MouseEvent.dispatchToTarget(new MouseEvent("mouseup", end, 0, false), document, document.body);
    }

    private static String documentLabel(Document document) {
        return document.getPath() + " [" + document.getUuid().toString().substring(0, 4) + "]";
    }

    private static String styleBlock(String html) {
        int start = html.indexOf("<style>");
        int end = html.indexOf("</style>", start);
        return html.substring(start, end + "</style>".length()).replace("\r\n", "\n");
    }
}
