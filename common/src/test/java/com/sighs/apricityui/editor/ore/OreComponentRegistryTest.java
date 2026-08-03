package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreContainerNode;
import com.sighs.apricityui.editor.ore.palette.OreComponentDefinition;
import com.sighs.apricityui.editor.ore.palette.OreComponentRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OreComponentRegistryTest {
    @Test
    void paletteDefinitionsCreateExpectedCanvasNodes() {
        assertEquals(5, OreComponentRegistry.definitions().size());
        for (OreComponentDefinition definition : OreComponentRegistry.definitions()) {
            if (definition.container()) {
                OreContainerNode node = assertInstanceOf(OreContainerNode.class, definition.createNode());
                assertEquals(definition.id(), node.flex().direction());
                assertEquals("64px", node.style().get("min-height"));
            } else {
                OreComponentNode node = assertInstanceOf(OreComponentNode.class, definition.createNode());
                assertEquals(definition.tagName(), node.type());
                assertEquals(definition.defaultContent(), node.content());
            }
        }
    }
}
