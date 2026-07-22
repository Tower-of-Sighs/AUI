package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dev.ResourceManager;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
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
            assertTrue(hasContextAction(document, "COPY SOURCE"));

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
    void newButtonOpensTheJavaOwnedHtmlCreateOverlay() throws Exception {
        Document document = createManagerDocument("test://resource-manager-new-overlay", sampleEntries());
        try {
            Element newButton = document.querySelector("#newButton");
            assertNotNull(newButton);
            newButton.click();

            assertNotNull(document.querySelector(".dialog-overlay.show"));
            assertNotNull(document.querySelector(".dialog-input"));
            assertNotNull(document.querySelector(".resource-create-file-input"));
            assertEquals(2, document.querySelectorAll(".resource-import-card").size());
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
        for (Element action : document.querySelectorAll(".ctx-label")) {
            if (label.equals(action.getTextContent())) return true;
        }
        return false;
    }

    private static String resourceHtml() throws Exception {
        return Files.readString(TEMPLATE);
    }
}
