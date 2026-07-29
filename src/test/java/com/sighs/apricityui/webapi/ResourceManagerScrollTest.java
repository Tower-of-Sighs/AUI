package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dev.ResourceManager;
import com.sighs.apricityui.dev.resource.ResourceFontAsset;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Event;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceManagerScrollTest {
    private static final Path TEMPLATE = Path.of("src/main/resources/assets/apricityui/apricity/devtools/resource.html");
    private static final Path LEGACY_TEMPLATE = Path.of("src/main/resources/assets/apricityui/apricity/devtools/resource-manager.html");

    @Test
    void resourceTemplateIsScriptlessAndLegacyTemplateIsGone() throws Exception {
        String html = resourceHtml();
        assertFalse(html.toLowerCase().contains("<script"));
        assertFalse(html.contains("const fs"));
        assertTrue(html.contains(".file-grid .file-card { aspect-ratio: 1 / 1; }"));
        assertTrue(html.contains("line-clamp: 2;"));
        assertTrue(html.contains("text-overflow: ellipsis;"));
        assertFalse(Files.exists(LEGACY_TEMPLATE));
    }

    @Test
    void javaManagerPopulatesTemplateAndHandlesNavigationAndImagePreview() throws Exception {
        Size.setViewportOverride(1463, 843);
        Document document = createManagerDocument("test://resource-manager-java-render", sampleEntries());
        try {
            Element devtools = document.querySelector(".tree-item[data-path=\"devtools\"]");
            assertNotNull(devtools);
            devtools.click();

            assertEquals("DEVTOOLS", document.querySelector("#contentTitle").getTextContent());
            assertEquals(2, document.querySelectorAll(".file-card").size());

            Element imageCard = document.querySelector(".file-card[data-resource-key=\"devtools/bear.png|DEV_FOLDER\"]");
            assertNotNull(imageCard);
            imageCard.click();

            assertNotNull(document.querySelector(".detail-panel.active"));
            Element image = imageCard.querySelector(".file-thumbnail");
            assertNotNull(image);
            assertEquals("/devtools/bear.png", image.getAttribute("src"));
            imageCard.dispatchEvent(new MouseEvent("contextmenu", Position.ZERO, 1, false));
            assertTrue(hasContextAction(document, "COPY PATH"));
            assertTrue(hasContextAction(document, "PREVIEW"));
            assertTrue(hasContextAction(document, "REFERENCE"));
            assertTrue(hasContextAction(document, "COPY SOURCE"));

            Element referenceAction = contextAction(document, "REFERENCE");
            assertNotNull(referenceAction);
            referenceAction.click();
            assertNotNull(document.querySelector(".resource-reference-dialog"));
            assertEquals(2, document.querySelectorAll(".resource-reference-option").size());
            assertEquals("background-image: url(\"/devtools/bear.png\");",
                    document.querySelector(".resource-reference-code").getValue());
            document.querySelectorAll(".resource-reference-option").get(1).click();
            assertEquals("<img src=\"/devtools/bear.png\" alt=\"bear\">",
                    document.querySelector(".resource-reference-code").getValue());
            document.querySelector(".resource-reference-dialog .dialog-close").click();

            Element htmlCard = document.querySelector(
                    ".file-card[data-resource-key=\"devtools/index.html|DEV_FOLDER\"]");
            assertNotNull(htmlCard);
            htmlCard.dispatchEvent(new MouseEvent("contextmenu", Position.ZERO, 1, false));
            assertTrue(hasContextAction(document, "REFERENCE"));
            contextAction(document, "REFERENCE").click();
            assertEquals(4, document.querySelectorAll(".resource-reference-option").size());
            assertEquals("java", document.querySelector(".resource-reference-language-select").getValue());
            assertEquals("ApricityUI.screen(\"devtools/index.html\");",
                    document.querySelector(".resource-reference-code").getValue());
            Element language = document.querySelector(".resource-reference-language-select");
            language.setValue("kjs");
            language.dispatchEvent(new Event(language, "change", true));
            document.querySelectorAll(".resource-reference-option").get(2).click();
            assertEquals("let overlay = ApricityUI.createDocument(\"devtools/index.html\")",
                    document.querySelector(".resource-reference-code").getValue());
            document.querySelector(".resource-reference-dialog .dialog-close").click();

            document.querySelector("#upButton").click();
            assertEquals("ROOT", document.querySelector("#contentTitle").getTextContent());
            document.querySelector("#backButton").click();
            assertEquals("DEVTOOLS", document.querySelector("#contentTitle").getTextContent());
        } finally {
            ResourceManager.close();
            Size.clearViewportOverride();
        }
    }

    @Test
    void populatedFileGridScrollsInsideTemplateContentArea() throws Exception {
        Size.setViewportOverride(900, 500);
        List<Loader.StaticResourceEntry> entries = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            entries.add(entry("asset-" + i + ".json", "json", 1024 + i));
        }
        Document document = createManagerDocument("test://resource-manager-grid-scroll", entries);
        try {
            Element content = document.querySelector(".content");
            assertNotNull(content);
            document.tickFrame();
            assertTrue(content.hasVerticalScrollRange(), "resource grid should create a vertical scroll range");

            Position position = Position.of(content);
            MouseEvent wheel = new MouseEvent("wheel", new Position(position.x + 20, position.y + 80), -1, false);
            wheel.scrollDelta = 80;
            boolean consumed = MouseEvent.tiggerEvent(wheel, document);
            assertTrue(consumed);
            assertTrue(content.getTargetScrollTop() > 0);
        } finally {
            ResourceManager.close();
            Size.clearViewportOverride();
        }
    }

    @Test
    void wrappedFileNamesReserveSpaceBeforeFileMetadata() throws Exception {
        Assumptions.assumeTrue(isClassPresent("net.minecraft.client.renderer.MultiBufferSource"));
        Size.setViewportOverride(1463, 843);
        Document document = createManagerDocument("test://resource-manager-wrapped-file-names", List.of(
                entry("tests/absolute-pseudo-percent-width.html", "html", 2_400),
                entry("tests/container-slot-recipe-test.html", "html", 3_200)
        ));
        try {
            Element tests = document.querySelector(".tree-item[data-path=\"tests\"]");
            assertNotNull(tests);
            tests.click();
            document.tickFrame();

            for (Element card : document.querySelectorAll(".file-card")) {
                Element name = card.querySelector(".file-name");
                Element metadata = card.querySelector(".file-meta");
                assertNotNull(name);
                assertNotNull(metadata);

                Element.DOMRect cardRect = card.getBoundingClientRect();
                Element.DOMRect nameRect = name.getBoundingClientRect();
                Element.DOMRect metadataRect = metadata.getBoundingClientRect();
                Text text = Text.of(name);

                assertTrue(Text.wrap(name).lines().size() > 2);
                assertEquals(2, Text.resolveLineClamp(name));
                assertTrue(nameRect.height >= text.lineHeight * 2 - 0.01);
                assertTrue(metadataRect.y >= nameRect.bottom + 5.9);
                assertTrue(metadataRect.bottom <= cardRect.bottom);
                assertEquals(cardRect.width, cardRect.height, 0.01);
            }
        } finally {
            ResourceManager.close();
            Size.clearViewportOverride();
        }
    }

    @Test
    void newButtonOpensTheJavaOwnedHtmlCreateOverlay() throws Exception {
        Document document = createManagerDocument("test://resource-manager-new-overlay", sampleEntries());
        try {
            Element newButton = document.querySelector("#newButton");
            assertNotNull(newButton);
            newButton.click();

            assertNotNull(document.querySelector(".dialog-overlay.show"));
            assertNotNull(document.querySelector(".dialog-input"));
            assertNotNull(document.querySelector(".resource-create-file-input"));
            assertEquals(3, document.querySelectorAll(".resource-import-card").size());
            assertNotNull(document.querySelector(".dialog-btn-confirm"));
        } finally {
            ResourceManager.close();
        }
    }

    @Test
    void htmlPreviewCreatesDialogOwnedPreviewDocument() throws Exception {
        String path = "test://resource-manager-html-preview";
        HTML.putTemple(path, "<body><div style=\"width:40px;height:20px;background-color:#ffffff;\"></div></body>");
        Loader.StaticResourceEntry entry = new Loader.StaticResourceEntry(
                path,
                "html",
                Loader.ResourceLayer.DEV_FOLDER,
                "",
                "",
                1
        );

        Document owner = TestDocumentFactory.createDocument();
        setViewport(owner, 1280, 720);
        ResourcePreviewDialog previewDialog = new ResourcePreviewDialog();
        Field previewDocumentField = ResourcePreviewDialog.class.getDeclaredField("preview");
        Field previewDocumentPathField = ResourcePreviewDialog.class.getDeclaredField("sourcePath");
        previewDocumentField.setAccessible(true);
        previewDocumentPathField.setAccessible(true);

        previewDialog.open(owner, entry);
        Document previewDocument = (Document) previewDocumentField.get(previewDialog);
        try {
            assertNotNull(previewDocument);
            assertFalse(previewDocument.isReloadPersistent());
            assertTrue(previewDocument.isManuallyRendered());
            assertEquals(path, previewDocumentPathField.get(previewDialog));
        } finally {
            previewDialog.close();
            owner.remove();
        }
    }

    @Test
    void fontCardUsesItsFontAndPreviewOpensEditableTwoLineSample() throws Exception {
        Loader.StaticResourceEntry fontEntry = entry("apricityui/lxgw3500.ttf", "ttf", 654_584);
        Document document = createManagerDocument("test://resource-manager-font-preview", List.of(fontEntry));
        try {
            Element folder = document.querySelector(".file-card[data-path=\"apricityui\"]");
            assertNotNull(folder);
            folder.dispatchEvent(new MouseEvent("dblclick", Position.ZERO, 0, false));

            Element card = document.querySelector(".file-card[data-resource-key=\"apricityui/lxgw3500.ttf|DEV_FOLDER\"]");
            assertNotNull(card);
            Element glyph = card.querySelector(".file-font-glyph");
            assertNotNull(glyph);
            assertEquals("Aa", glyph.getTextContent());
            String family = ResourceFontAsset.familyName(fontEntry);
            assertTrue(Font.isRegistered(family));
            assertTrue(glyph.getAttribute("style").contains("font-family:'" + family + "'"));

            card.dispatchEvent(new MouseEvent("contextmenu", Position.ZERO, 1, false));
            assertTrue(hasContextAction(document, "REFERENCE"));
            Element referenceAction = contextAction(document, "REFERENCE");
            assertNotNull(referenceAction);
            referenceAction.click();
            Element familyInput = document.querySelector(".resource-reference-family-input");
            assertNotNull(familyInput);
            assertEquals("lxgw3500", familyInput.getValue());
            assertEquals(2, document.querySelectorAll(".resource-reference-option").size());
            assertTrue(document.querySelector(".resource-reference-code").getValue().contains("@font-face"));
            familyInput.value = "Custom Display";
            familyInput.dispatchEvent(new Event(familyInput, "input", true));
            assertTrue(document.querySelector(".resource-reference-code").getValue()
                    .contains("font-family: \"Custom Display\""));
            document.querySelectorAll(".resource-reference-option").get(1).click();
            assertEquals("font-family: \"Custom Display\", sans-serif;",
                    document.querySelector(".resource-reference-code").getValue());
            document.querySelector(".resource-reference-dialog .dialog-close").click();

            card.dispatchEvent(new MouseEvent("dblclick", Position.ZERO, 0, false));
            Element sample = document.querySelector(".resource-preview-font-sample");
            assertNotNull(sample);
            assertEquals("中文字体预览\nThe quick brown fox jumps over the lazy dog.", sample.getValue());
            assertTrue(sample.getAttribute("style").contains("font-family:'" + family + "'"));
        } finally {
            ResourceManager.close();
        }
    }

    private static Document createManagerDocument(String path, List<Loader.StaticResourceEntry> entries) throws Exception {
        HTML.putTemple(path, resourceHtml());
        Document document = Document.create(path);
        assertNotNull(document);

        Field documentField = ResourceManager.class.getDeclaredField("toolDocument");
        documentField.setAccessible(true);
        documentField.set(null, document);

        Method render = ResourceManager.class.getDeclaredMethod("render", List.class);
        render.setAccessible(true);
        render.invoke(null, entries);
        return document;
    }

    private static void setViewport(Document document, int width, int height) throws Exception {
        Field viewport = Document.class.getDeclaredField("viewport");
        viewport.setAccessible(true);
        viewport.set(document, new com.sighs.apricityui.instance.ApricityViewport(width, height, 1.0f, 1.0d));
    }

    private static List<Loader.StaticResourceEntry> sampleEntries() {
        return List.of(
                entry("devtools/bear.png", "png", 2048),
                entry("devtools/index.html", "html", 4096),
                entry("global.css", "css", 1024),
                entry("tests/example.json", "json", 512)
        );
    }

    private static Loader.StaticResourceEntry entry(String path, String extension, long size) {
        return new Loader.StaticResourceEntry(
                path,
                extension,
                Loader.ResourceLayer.DEV_FOLDER,
                Path.of("src/main/resources/assets/apricityui/apricity").toAbsolutePath().normalize().toString(),
                "dev-source",
                size
        );
    }

    private static boolean hasContextAction(Document document, String label) {
        return contextAction(document, label) != null;
    }

    private static Element contextAction(Document document, String label) {
        for (Element action : document.querySelectorAll(".ctx-label")) {
            if (label.equals(action.getTextContent())) return action.parentElement;
        }
        return null;
    }

    private static String resourceHtml() throws Exception {
        return Files.readString(TEMPLATE);
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
