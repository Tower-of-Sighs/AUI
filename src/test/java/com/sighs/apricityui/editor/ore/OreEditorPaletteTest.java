package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.editor.ore.palette.OreComponentDefinition;
import com.sighs.apricityui.editor.ore.palette.OreComponentRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreEditorPaletteTest {
    private static final Path TEMPLATE = Path.of("src/main/resources/assets/apricityui/apricity/editor/ore/ore-editor.html");

    @Test
    void paletteSeparatesContainersAndComponentsWithoutChangingSelection() throws Exception {
        OreEditorController controller = OreEditorController.INSTANCE;
        controller.close();
        HTML.putTemple(OreEditorController.PATH, Files.readString(TEMPLATE));
        assertTrue(controller.open());
        try {
            Document editor = controller.getDocument();
            UUID selected = controller.getSession().selectedNode();
            Element content = editor.querySelector("#editorSidebarContent");
            assertEquals(2, content.querySelectorAll(".editor-palette-items .editor-palette-item").size());

            Element switcher = content.querySelector(".editor-palette-switcher");
            assertNotNull(switcher);
            Element components = switcher.querySelectorAll("button").get(1);
            components.click();

            assertEquals(3, content.querySelectorAll(".editor-palette-items .editor-palette-item").size());
            assertEquals(selected, controller.getSession().selectedNode());
            Element activeComponents = content.querySelector(".editor-palette-switcher").querySelectorAll("button").get(1);
            assertEquals("true", activeComponents.getAttribute("aria-pressed"));
        } finally {
            if (controller.isOpen()) {
                controller.getSession().setDirty(false);
                controller.close();
            }
        }
    }

    @Test
    void lockedRootStillAcceptsNodesThroughThePaletteDropPath() throws Exception {
        OreEditorController controller = OreEditorController.INSTANCE;
        controller.close();
        HTML.putTemple(OreEditorController.PATH, Files.readString(TEMPLATE));
        assertTrue(controller.open());
        try {
            java.lang.reflect.Field projectField = OreEditorController.class.getDeclaredField("project");
            projectField.setAccessible(true);
            OreEditorProject project = (OreEditorProject) projectField.get(controller);
            int before = project.root().children().size();
            assertTrue(project.root().locked());

            OreComponentDefinition definition = OreComponentRegistry.definitions().get(0);
            java.lang.reflect.Method add = OreEditorController.class.getDeclaredMethod("addPaletteNode",
                    OreComponentDefinition.class, com.sighs.apricityui.editor.ore.model.OreContainerNode.class,
                    com.sighs.apricityui.editor.ore.canvas.OreFlexInsertionResolver.Insertion.class);
            add.setAccessible(true);
            add.invoke(controller, definition, project.root(), null);

            assertEquals(before + 1, project.root().children().size());
            assertEquals(project.root().children().get(before).id(), controller.getSession().selectedNode());
        } finally {
            if (controller.isOpen()) {
                controller.getSession().setDirty(false);
                controller.close();
            }
        }
    }
}
