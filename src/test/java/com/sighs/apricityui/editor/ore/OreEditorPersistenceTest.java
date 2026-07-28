package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import com.sighs.apricityui.editor.ore.persistence.OreEditorHtmlExporter;
import com.sighs.apricityui.editor.ore.persistence.OreEditorProjectCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreEditorPersistenceTest {
    @Test
    void projectRoundTripPreservesTreeStylesFlexAndTheme() {
        OreEditorProject project = new OreEditorProject();
        OreContainerNode column = new OreContainerNode(false);
        column.setLocked(true);
        column.flex().setDirection("column");
        column.flex().setAlignContent("space-between");
        column.flex().setRowGap("12px");
        column.flex().setColumnGap("6px");
        column.style().set("padding", "8px");
        OreComponentNode button = new OreComponentNode("button", "Use <Ore>");
        button.style().set("position", "absolute");
        button.enterAbsolute(0);
        button.stateStyle(OreComponentNode.VisualState.HOVER).set("background", "#654321");
        column.add(button);
        project.root().add(column);
        project.theme().set("--ore-purple", "#123456");

        OreEditorProject restored = new OreEditorProjectCodec().read(new OreEditorProjectCodec().write(project));
        OreContainerNode restoredColumn = (OreContainerNode) restored.root().children().get(0);
        OreComponentNode restoredButton = (OreComponentNode) restoredColumn.children().get(0);

        assertEquals(project.root().id(), restored.root().id());
        assertEquals("column", restoredColumn.flex().direction());
        assertEquals("space-between", restoredColumn.flex().alignContent());
        assertEquals("12px", restoredColumn.flex().rowGap());
        assertEquals("6px", restoredColumn.flex().columnGap());
        assertTrue(restoredColumn.locked());
        assertEquals("8px", restoredColumn.style().get("padding"));
        assertEquals(button.id(), restoredButton.id());
        assertEquals("Use <Ore>", restoredButton.content());
        assertTrue(restoredButton.absolute());
        assertEquals(0, restoredButton.flowIndex());
        assertEquals("#654321", restoredButton.stateStyle(OreComponentNode.VisualState.HOVER).get("background"));
        assertEquals("#123456", restored.theme().get("--ore-purple"));
    }

    @Test
    void exporterProducesCleanEscapedHtmlWithoutEditorArtifacts() {
        OreEditorProject project = new OreEditorProject();
        project.root().flex().setRowGap("12px");
        project.root().flex().setColumnGap("6px");
        OreComponentNode button = new OreComponentNode("button", "<Build & Run>");
        button.stateStyle(OreComponentNode.VisualState.HOVER).set("background", "#654321");
        project.root().add(button);

        String html = new OreEditorHtmlExporter().export(project);

        assertTrue(html.contains("&lt;Build &amp; Run&gt;"));
        assertTrue(html.contains("ore-edit.css"));
        assertTrue(html.contains("row-gap:12px;column-gap:6px;"));
        assertTrue(html.contains(".ore-state-" + button.id().toString().replace("-", "") + ":hover {background:#654321;}"));
        assertFalse(html.contains("data-ore-editor-ui"));
        assertFalse(html.contains("editor-selection-overlay"));
    }

    @Test
    void projectRoundTripPreservesAbsoluteFlowStyleSnapshot() {
        OreEditorProject project = new OreEditorProject();
        OreComponentNode component = new OreComponentNode("button", "Build");
        component.style().set("order", "2");
        component.style().set("flex-grow", "1");
        component.captureFlowStyleSnapshot();
        component.enterAbsolute(0);
        component.style().set("position", "absolute");
        component.style().set("left", "12px");
        project.root().add(component);

        OreEditorProject restored = new OreEditorProjectCodec().read(new OreEditorProjectCodec().write(project));
        OreComponentNode restoredComponent = (OreComponentNode) restored.root().children().get(0);

        assertTrue(restoredComponent.absolute());
        assertTrue(restoredComponent.hasFlowStyleSnapshot());
        assertEquals("2", restoredComponent.flowStyleSnapshot().get("order"));
        assertEquals("1", restoredComponent.flowStyleSnapshot().get("flex-grow"));
        assertNull(restoredComponent.flowStyleSnapshot().get("left"));
    }
}
