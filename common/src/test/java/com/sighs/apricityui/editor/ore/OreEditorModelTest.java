package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OreEditorModelTest {
    @Test
    void projectStartsWithAnImmutableRootFlexContainer() {
        OreEditorProject project = new OreEditorProject();

        assertTrue(project.root().isRoot());
        assertTrue(project.root().locked());
        project.root().setLocked(false);
        assertTrue(project.root().locked());
        assertTrue(project.root().acceptsStructuralChildren());
        assertEquals("row", project.root().flex().direction());
        assertEquals("nowrap", project.root().flex().wrap());
        assertEquals("flex-start", project.root().flex().justifyContent());
        assertEquals("stretch", project.root().flex().alignItems());
        assertEquals("stretch", project.root().flex().alignContent());
        assertEquals("0px", project.root().flex().rowGap());
        assertEquals("0px", project.root().flex().columnGap());
    }

    @Test
    void nodesCanNestAndMoveAcrossContainersWithoutDuplicateParents() {
        OreEditorProject project = new OreEditorProject();
        OreContainerNode left = new OreContainerNode(false);
        OreContainerNode right = new OreContainerNode(false);
        OreComponentNode component = new OreComponentNode("button", "Button");
        project.root().add(left);
        project.root().add(right);
        left.add(component);

        right.add(component);

        assertTrue(left.children().isEmpty());
        assertEquals(1, right.children().size());
        assertSame(right, component.parent());
        assertSame(component, project.find(component.id()));
    }

    @Test
    void containersCannotCreateCycles() {
        OreContainerNode parent = new OreContainerNode(false);
        OreContainerNode child = new OreContainerNode(false);
        parent.add(child);

        assertThrows(IllegalArgumentException.class, () -> child.add(parent));
    }

    @Test
    void projectThemeKeepsCanvasOverridesSeparateAndResettable() {
        OreEditorProject project = new OreEditorProject();
        project.theme().set("--ore-purple", "#123456");

        assertEquals("#123456", project.theme().get("--ore-purple"));
        assertEquals("--ore-purple:#123456;", project.theme().toCss());

        project.theme().reset();
        assertTrue(project.theme().overrides().isEmpty());
    }

    @Test
    void nodeLockStateIsIndependentOfParentageAndStyle() {
        OreContainerNode parent = new OreContainerNode(false);
        OreComponentNode component = new OreComponentNode("button", "Build");
        parent.add(component);
        component.style().set("color", "#fff");

        component.setLocked(true);
        parent.setLocked(true);

        assertTrue(component.locked());
        assertTrue(!parent.acceptsStructuralChildren());
        assertSame(parent, component.parent());
        assertEquals("#fff", component.style().get("color"));
    }
}
