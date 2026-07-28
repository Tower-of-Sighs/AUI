package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.editor.ore.persistence.OreEditorHtmlImporter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OreEditorHtmlImporterTest {
    @Test
    void importsEditableBodyStructureStylesAndThemeVariables() {
        OreEditorProject project = new OreEditorHtmlImporter().read("""
                <!DOCTYPE html><html><body style="--ore-purple:#123456">
                  <div style="display:flex;flex-direction:column;gap:12px;padding:4px">
                    <button style="color:#fff;position:absolute;left:8px">Build</button>
                  </div>
                </body></html>
                """);

        assertEquals("#123456", project.theme().get("--ore-purple"));
        OreContainerNode container = assertInstanceOf(OreContainerNode.class, project.root().children().get(0));
        assertEquals("column", container.flex().direction());
        assertEquals("12px", container.flex().gap());
        assertEquals("4px", container.style().get("padding"));
        OreComponentNode button = assertInstanceOf(OreComponentNode.class, container.children().get(0));
        assertEquals("button", button.type());
        assertEquals("Build", button.content().trim());
        assertEquals("#fff", button.style().get("color"));
        assertEquals("8px", button.style().get("left"));
        assertEquals(true, button.absolute());
    }
}
