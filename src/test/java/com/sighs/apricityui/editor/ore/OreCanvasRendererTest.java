package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.canvas.OreCanvasRenderer;
import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.HTML;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OreCanvasRendererTest {
    @Test
    void emptyContainersProjectAnEditorOnlyTranslationHint() {
        String path = "test://ore-canvas-empty-" + UUID.randomUUID();
        HTML.putTemple(path, "<html><body><div id=\"canvas\"></div></body></html>");
        Document document = Document.create(path);
        assertNotNull(document);
        try {
            OreEditorProject project = new OreEditorProject();
            Element canvas = document.querySelector("#canvas");
            OreCanvasRenderer renderer = new OreCanvasRenderer(document, canvas, ignored -> { });

            renderer.render(project, project.root().id());

            Element hint = canvas.querySelector("[data-ore-editor-ui=\"empty-container\"]");
            assertNotNull(hint);
            assertEquals("TRANSLATION", hint.tagName);
            assertEquals("ore_editor.apricityui.empty.container", hint.getTextContent());

            project.root().add(new OreComponentNode("div", "Content"));
            renderer.render(project, project.root().id());
            assertFalse(canvas.querySelectorAll("[data-ore-editor-ui=\"empty-container\"]").size() > 0);
        } finally {
            document.remove();
        }
    }

    @Test
    void absoluteSelectionExposesResizeHandleForTheSelectedNode() {
        String path = "test://ore-canvas-renderer-" + UUID.randomUUID();
        HTML.putTemple(path, "<html><body><div id=\"canvas\"></div></body></html>");
        Document document = Document.create(path);
        assertNotNull(document);
        try {
            OreEditorProject project = new OreEditorProject();
            OreComponentNode component = new OreComponentNode("div", "");
            component.enterAbsolute(0);
            component.style().set("position", "absolute");
            component.style().set("width", "80px");
            component.style().set("height", "32px");
            project.root().add(component);

            Element canvas = document.querySelector("#canvas");
            OreCanvasRenderer renderer = new OreCanvasRenderer(document, canvas, ignored -> { }, null, null);
            renderer.render(project, component.id());

            Element handle = canvas.querySelector(".editor-absolute-resize-handle");
            assertNotNull(handle);
            assertEquals(component.id().toString(), handle.getAttribute("data-ore-node-id"));

            renderer.render(project, project.root().id());
            assertNull(canvas.querySelector(".editor-absolute-resize-handle"));
            assertNotNull(canvas.querySelector(".editor-flex-overlay"));
            assertEquals(1, canvas.querySelectorAll(".editor-flex-main-axis").size());
            assertEquals(1, canvas.querySelectorAll(".editor-flex-cross-axis").size());

            renderer.render(project, component.id());
            assertNull(canvas.querySelector(".editor-flex-overlay"));
        } finally {
            document.remove();
        }
    }
}
