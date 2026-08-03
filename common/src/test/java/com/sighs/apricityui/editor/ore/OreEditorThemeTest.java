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

class OreEditorThemeTest {
    private static final Path TEMPLATE = Path.of("../../common/src/main/resources/assets/apricityui/apricity/editor/ore/ore-editor.html");

    @Test
    void themeModeGroupsEveryCanvasTokenWithIndependentResetActions() throws Exception {
        OreEditorController controller = OreEditorController.INSTANCE;
        controller.close();
        HTML.putTemple(OreEditorController.PATH, Files.readString(TEMPLATE));
        assertTrue(controller.open());
        try {
            Document editor = controller.getDocument();
            Element themeTab = editor.querySelector(".editor-tab[data-editor-mode=\"theme\"]");
            assertNotNull(themeTab);
            themeTab.click();

            Element content = editor.querySelector("#editorSidebarContent");
            assertEquals(5, content.querySelectorAll(".editor-theme-group").size());
            assertEquals(35, content.querySelectorAll(".form-input").size());
            assertEquals(26, content.querySelectorAll(".editor-theme-color").size());
            assertEquals(26, content.querySelectorAll(".editor-theme-alpha").size());
            assertEquals(5, content.querySelectorAll(".editor-theme-group-header .button-secondary").size());
            assertEquals(35, content.querySelectorAll(".editor-theme-token-reset").size());
            assertTrue(content.querySelector(".editor-theme-token-reset").isDisabled());
            assertEquals(0, editor.querySelectorAll(".editor-document-dirty").size());

            Element firstToken = content.querySelector(".form-input");
            firstToken.setValue("#123456");
            firstToken.dispatchEvent(new Event(firstToken, "change", true));
            Element firstColor = content.querySelector(".editor-theme-color");
            assertEquals("#123456", firstColor.getValue());
            firstColor.setValue("#654321");
            firstColor.dispatchEvent(new Event(firstColor, "change", true));
            assertEquals("#654321", firstToken.getValue());
            Element firstAlpha = content.querySelector(".editor-theme-alpha");
            firstAlpha.setValue("0.5");
            firstAlpha.dispatchEvent(new Event(firstAlpha, "change", true));
            assertEquals("rgba(101, 67, 33, 0.5)", firstToken.getValue());
            Element resetToken = content.querySelector(".editor-theme-token-reset");
            assertTrue(!resetToken.isDisabled());
            resetToken.click();
            assertEquals("#f4f5f7", content.querySelector(".form-input").getValue());
            Element undo = editor.querySelector("#undoButton");
            assertTrue(!undo.isDisabled());
            undo.click();
            assertEquals("rgba(101, 67, 33, 0.5)", content.querySelector(".form-input").getValue());
            Element dirty = editor.querySelector(".editor-document-dirty TRANSLATION");
            assertNotNull(dirty);
            assertEquals("ore_editor.apricityui.document.modified", dirty.getTextContent());
        } finally {
            if (controller.isOpen()) {
                controller.getSession().setDirty(false);
                controller.close();
            }
        }
    }
}
