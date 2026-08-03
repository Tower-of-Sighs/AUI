package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreEditorContainerInspectorTest {
    private static final Path TEMPLATE = Path.of("../../common/src/main/resources/assets/apricityui/apricity/editor/ore/ore-editor.html");

    @Test
    void rootContainerInspectorExposesIndependentOverflowAxes() throws Exception {
        OreEditorController controller = OreEditorController.INSTANCE;
        controller.close();
        HTML.putTemple(OreEditorController.PATH, Files.readString(TEMPLATE));
        assertTrue(controller.open());
        try {
            Document editor = controller.getDocument();
            Element inspect = editor.querySelector(".editor-tab[data-editor-mode=\"inspect\"]");
            assertNotNull(inspect);
            inspect.click();

            Element content = editor.querySelector("#editorSidebarContent");
            assertEquals(2, content.querySelectorAll(".editor-segmented").size());
            assertEquals(7, content.querySelectorAll(".editor-segmented button").size());
            assertEquals(21, content.querySelectorAll(".form-select").size());
            assertEquals(14, content.querySelectorAll(".form-input").size());
            assertEquals(13, content.querySelectorAll("input[type=\"number\"]").size());
            assertEquals(3, content.querySelectorAll(".editor-alignment-preview").size());
            assertEquals(3, content.querySelectorAll(".form-select option[value=\"scroll\"]").size());

            Element justify = content.querySelectorAll(".editor-alignment-field .form-select").get(0);
            Element justifyPreview = content.querySelectorAll(".editor-alignment-preview").get(0);
            justify.setValue("space-between");
            justify.dispatchEvent(new Event(justify, "change", true));
            assertTrue(justifyPreview.getAttribute("class").contains("editor-alignment-value-space-between"));

            Element column = content.querySelector("[data-editor-segmented-value=\"column\"]");
            assertNotNull(column);
            column.click();
            assertTrue(column.getAttribute("class").contains("button-primary"));
            Element root = editor.querySelector("[data-ore-node-id=\"" + controller.getSession().selectedNode() + "\"]");
            assertNotNull(root);
            assertTrue(root.getAttribute("style").contains("flex-direction:column"));

            Element undo = editor.querySelector("#undoButton");
            Element redo = editor.querySelector("#redoButton");
            assertNotNull(undo);
            assertNotNull(redo);
            assertTrue(!undo.isDisabled());
            undo.click();
            assertTrue(root.getAttribute("style").contains("flex-direction:row"));
            assertTrue(!redo.isDisabled());
            redo.click();
            assertTrue(root.getAttribute("style").contains("flex-direction:column"));
        } finally {
            if (controller.isOpen()) {
                controller.getSession().setDirty(false);
                controller.close();
            }
        }
    }
}
