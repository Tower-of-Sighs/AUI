package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dev.resource.ResourceCreateDialog;
import com.sighs.apricityui.dev.resource.ResourceMetaDialog;
import com.sighs.apricityui.dev.resource.ResourceReferenceDialog;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.style.Background;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.sighs.apricityui.parser.Color;

class ResourceDialogStyleTest {
    private static final Path TEMPLATE = Path.of(
            "../../common/src/main/resources/assets/apricityui/apricity/devtools/resource.html");
    private static final Path GLOBAL_STYLE = Path.of(
            "../../common/src/main/resources/assets/apricityui/apricity/global.css");

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
            // :root 的 --purple 现在对裸元素生效，var 会被解析为实际色值（浏览器语义）
            assertEquals("#8b5cf6", header.getComputedStyle().backgroundColor);
            assertTrue(usesCssVar(document.CSSCache, "var(--purple)"), "dialog styles reference --purple");
            assertEquals("2px solid #e0e0e0", input.getComputedStyle().border);
            assertTrue(usesCssVar(document.CSSCache, "var(--gray-light)"), "dialog styles reference --gray-light");
            assertEquals("tests/", input.value);
            assertTrue(create.isDisabled());
            assertTrue(create.matches(".dialog-btn:disabled"));
            assertTrue(document.CSSCache.containsKey(".dialog-btn:disabled"));
            assertEquals("0.4", document.CSSCache.get(".dialog-btn:disabled").get("opacity").value());
            assertEquals("0.4", Selector.matchCSS(create).get("opacity").value());
        } finally {
            createDialog.close();
        }

        Path html = tempDir.resolve("dialog-meta.html");
        Files.writeString(html, "<head><meta name=\"aui-viewport\" content=\"mode=browser\"></head><body></body>");
        ResourceMetaDialog metaDialog = new ResourceMetaDialog();
        metaDialog.open(document, "tests/dialog-meta.html", html, null);
        try {
            assertEquals(2, document.querySelectorAll(".dialog-select-wrap").size());
            assertEquals(2, document.querySelectorAll(".dialog-select").size());
            assertEquals(2, document.querySelectorAll(".dialog-select-arrow").size());
            assertEquals(2, document.querySelectorAll(".resource-meta-select[data-tooltip-key]").size());
            assertEquals(8, document.querySelectorAll(".resource-meta-option[data-tooltip-key]").size());

            Element select = document.querySelector(".dialog-select");
            Element arrow = document.querySelector(".dialog-select-arrow");
            assertFalse(select.matches(".tree-icon svg"));
            assertNull(Selector.matchCSS(select).get("height"));
            assertEquals("auto", select.getRawComputedStyle().height);
            assertEquals("14px", select.getRawComputedStyle().paddingLeft);
            assertEquals("#8b5cf6", arrow.getComputedStyle().backgroundColor);
            document.setFocusedElement(select);
            document.flushPendingStyleUpdates();
            assertEquals("var(--purple-dark)", Selector.matchCSS(arrow).get("background").value());
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

    @Test
    void confirmButtonLabelPaintsAboveItsHoverFill() throws Exception {
        Document document = styledDocument();
        ResourceReferenceDialog dialog = new ResourceReferenceDialog();
        dialog.open(document, new Loader.StaticResourceEntry(
                "tests/example.html", "html", Loader.ResourceLayer.DEV_FOLDER, "", "", 1));
        try {
            Element button = document.querySelector(".resource-reference-copy");
            assertNotNull(button);
            button.setHover(true);
            document.flushPendingStyleUpdates();

            List<Element> children = button.getRenderChildren();
            Element fill = children.stream().filter(Element::isPseudoElement).findFirst().orElseThrow();
            Element label = button.querySelector(".dialog-btn-label");
            assertNotNull(label);
            assertEquals("0", fill.getRawComputedStyle().zIndex);
            assertEquals("1", label.getRawComputedStyle().zIndex);

            List<RenderNode> paint = Drawer.createPaintList(document.body);
            int fillIndex = bodyIndex(paint, fill);
            int labelIndex = bodyIndex(paint, label);
            assertTrue(fillIndex < labelIndex, "the z-index:1 label must paint after the z-index:0 fill");
        } finally {
            dialog.close();
        }
    }

    @Test
    void navigationRootReturnsToTransparentAfterHover() throws Exception {
        Document document = styledDocument();
        Element navigation = document.createElement("div");
        navigation.setAttribute("class", "nav-path");
        Element root = document.createElement("span");
        root.setTextContent("ROOT");
        root.setAttribute("style", "transition: all 1ms linear;");
        navigation.appendChild(root);
        document.body.appendChild(navigation);

        assertEquals("transparent", root.getComputedStyle().backgroundColor);

        root.setHover(true);
        document.flushPendingStyleUpdates();
        sampleMotionFrame(document, root);

        root.setHover(false);
        document.flushPendingStyleUpdates();
        sampleMotionFrame(document, root);
        Thread.sleep(10L);

        Background settled = sampleMotionFrame(document, root);
        assertEquals(0, settled.color == null ? 0 : new com.sighs.apricityui.parser.Color(settled.color).getA());
        assertFalse(root.isHover);
    }

    private static Background sampleMotionFrame(Document document, Element element) {
        StyleFrameCache.begin();
        try {
            document.stepMotionRender();
            return Background.of(element);
        } finally {
            StyleFrameCache.end();
        }
    }

    private static int bodyIndex(List<RenderNode> paint, Element target) {
        for (int i = 0; i < paint.size(); i++) {
            RenderNode node = paint.get(i);
            if (node instanceof RenderNode.ElementPhaseNode phase
                    && phase.target() == target
                    && phase.phase() == Base.RenderPhase.BODY) return i;
        }
        throw new AssertionError("BODY render node missing for " + target.tagName);
    }

    private static boolean usesCssVar(java.util.Map<String, java.util.Map<String, CSS.Declaration>> cache, String var) {
        for (java.util.Map<String, CSS.Declaration> props : cache.values()) {
            for (CSS.Declaration declaration : props.values()) {
                if (var.equals(declaration.value())) return true;
            }
        }
        return false;
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
