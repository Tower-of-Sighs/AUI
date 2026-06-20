package com.sighs.apricityui.webapi;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.dev.ResourceManager;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Layout;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceManagerScrollTest {
    @Test
    void realResourceManagerTemplateRowsReceiveWheelAndAdvanceScroll() throws Exception {
        Size.setViewportOverride(320, 220);
        try {
            String path = "test://resource-manager-investigation";
            HTML.putTemple(path, resourceManagerHtml());
            Document document = Document.create(path);
            assertNotNull(document);
            try {
                Element rows = document.querySelector(".rows");
                assertNotNull(rows);

                for (int i = 0; i < 80; i++) {
                    Element row = Element.init(document.createElement("div"));
                    row.setAttribute("class", "row");
                    row.appendChild(cell(document, "cell c-index", String.valueOf(i + 1)));
                    row.appendChild(cell(document, "cell c-path", "assets/demo/" + i + ".png"));
                    row.appendChild(cell(document, "cell c-ext", "png"));
                    row.appendChild(cell(document, "cell c-layer", "DEV"));
                    row.appendChild(cell(document, "cell c-size", "1 KB"));
                    rows.appendChild(row);
                }

                document.markDirty(com.sighs.apricityui.init.Drawer.RELAYOUT | com.sighs.apricityui.init.Drawer.REPAINT | com.sighs.apricityui.init.Drawer.REORDER);
                document.commitRenderState();

                assertTrue(rows.hasVerticalScrollRange(), debugSizes(document, rows));
                Position rowsPos = Position.of(rows);
                MouseEvent wheel = new MouseEvent("wheel", new Position(rowsPos.x + 8, rowsPos.y + 8), -1, false);
                wheel.scrollDelta = 50;
                boolean consumed = MouseEvent.tiggerEvent(wheel, document);
                assertTrue(rows.getTargetScrollTop() > 0, "wheel should move .rows target scrollTop consumed=" + consumed);
                assertTrue(consumed, "wheel should be consumed by the scroller targetScrollTop=" + rows.getTargetScrollTop());

                document.stepScrollRender();
                assertTrue(rows.getScrollTop() > 0, "render step should advance visible scrollTop");
            } finally {
                document.remove();
            }
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void realResourceManagerPreviewPanelDoesNotOverlapImageAndInfo() throws Exception {
        Size.setViewportOverride(320, 220);
        try {
            String path = "test://resource-manager-preview-layout";
            HTML.putTemple(path, resourceManagerHtml());
            Document document = Document.create(path);
            assertNotNull(document);
            try {
                Element previewPanel = document.querySelector(".preview-panel");
                Element imageWrap = document.querySelector(".preview-image-wrap");
                Element image = document.querySelector(".preview-image");
                Element info = document.querySelector(".preview-info");
                assertNotNull(previewPanel);
                assertNotNull(imageWrap);
                assertNotNull(image);
                assertNotNull(info);

                previewPanel.setAttribute("class", "preview-panel");
                document.markDirty(com.sighs.apricityui.init.Drawer.RELAYOUT | com.sighs.apricityui.init.Drawer.REPAINT | com.sighs.apricityui.init.Drawer.REORDER);
                document.tickFrame();

                double imageBottom = Position.of(imageWrap).y + Size.of(imageWrap).height();
                double infoTop = Position.of(info).y;
                double panelBottom = Position.of(previewPanel).y + Size.of(previewPanel).height();
                double infoBottom = infoTop + Size.of(info).height();

                assertTrue(infoTop >= imageBottom, "preview info should sit below image wrap imageBottom=" + imageBottom + " infoTop=" + infoTop);
                assertTrue(infoBottom <= panelBottom, "preview info should stay inside preview panel infoBottom=" + infoBottom + " panelBottom=" + panelBottom);
                assertTrue(Size.of(image).width() > 0, "preview image element should have positive width");
                assertTrue(Size.of(image).height() > 0, "preview image element should have positive height");
            } finally {
                document.remove();
            }
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void htmlPreviewCreatesVisiblePreviewDocumentState() throws Exception {
        String path = "test://resource-manager-html-preview";
        HTML.putTemple(path, """
                <body>
                  <div style="width: 40px; height: 20px; background-color: #ffffff;"></div>
                </body>
                """);
        Loader.StaticResourceEntry entry = new Loader.StaticResourceEntry(path, "html", Loader.ResourceLayer.DEV_FOLDER, "", "", 1);

        Method openHtmlPreview = ResourceManager.class.getDeclaredMethod("openHtmlPreview", Loader.StaticResourceEntry.class);
        openHtmlPreview.setAccessible(true);
        Field previewDocumentField = ResourceManager.class.getDeclaredField("previewDocument");
        Field previewDocumentPathField = ResourceManager.class.getDeclaredField("previewDocumentPath");
        previewDocumentField.setAccessible(true);
        previewDocumentPathField.setAccessible(true);

        openHtmlPreview.invoke(null, entry);
        Document previewDocument = (Document) previewDocumentField.get(null);
        try {
            assertNotNull(previewDocument);
            assertTrue(previewDocument.isReloadPersistent(), "html preview document should stay visible over screen rendering");
            assertTrue(path.equals(previewDocumentPathField.get(null)));
        } finally {
            Method closePreviewDocument = ResourceManager.class.getDeclaredMethod("closePreviewDocument");
            closePreviewDocument.setAccessible(true);
            closePreviewDocument.invoke(null);
        }
    }

    private static Element cell(Document document, String className, String text) {
        Element span = Element.init(document.createElement("span"));
        span.setAttribute("class", className);
        span.innerText = text;
        return span;
    }

    private static String resourceManagerHtml() throws Exception {
        return Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/devtools/resource-manager.html"));
    }

    private static String debugSizes(Document document, Element rows) {
        Element manager = document.querySelector(".manager");
        return "real .rows should have positive vertical scroll range"
                + " body=" + Size.of(document.body)
                + " manager=" + Size.of(manager)
                + " rowsSize=" + Size.of(rows)
                + " rowsInner=" + Box.of(rows).innerSize()
                + " rowsContent=" + Size.getContentSize(rows)
                + " rowsLayoutContent=" + Layout.computeContentSize(rows)
                + " scrollHeight=" + rows.scrollHeight
                + " overflowY=" + rows.getComputedStyle().overflowY
                + " display=" + rows.getComputedStyle().display
                + " childCount=" + rows.children.size();
    }
}
