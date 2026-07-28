package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.model.OreAbsoluteConstraints;
import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OreAbsoluteConstraintsTest {
    @Test
    void horizontalAndVerticalAnchorsReplaceTheirOppositeOffsets() {
        OreComponentNode component = new OreComponentNode("div", "");
        OreAbsoluteConstraints.setOffset(component, "left", "12px");
        OreAbsoluteConstraints.setOffset(component, "right", "8px");
        OreAbsoluteConstraints.setOffset(component, "top", "4px");
        OreAbsoluteConstraints.setOffset(component, "bottom", "6px");

        assertEquals("8px", component.style().get("right"));
        assertNull(component.style().get("left"));
        assertEquals("6px", component.style().get("bottom"));
        assertNull(component.style().get("top"));
    }

    @Test
    void clearingAnOffsetDoesNotAlterItsOppositeAnchor() {
        OreComponentNode component = new OreComponentNode("div", "");
        component.style().set("right", "8px");
        OreAbsoluteConstraints.setOffset(component, "left", "");

        assertEquals("8px", component.style().get("right"));
        assertNull(component.style().get("left"));
    }

    @Test
    void restoringFlowSnapshotRemovesAbsoluteOffsetsAndRestoresFlexItemProperties() {
        OreComponentNode component = new OreComponentNode("div", "");
        component.style().set("order", "3");
        component.style().set("flex-grow", "2");
        component.style().set("flex-shrink", "0");
        component.style().set("flex-basis", "40%");
        component.style().set("align-self", "center");
        component.captureFlowStyleSnapshot();
        component.enterAbsolute(1);
        component.style().set("position", "absolute");
        component.style().set("left", "12px");
        component.style().set("top", "8px");
        component.style().set("width", "120px");
        component.style().set("order", "99");

        component.leaveAbsolute();
        component.restoreFlowStyleSnapshot();

        assertNull(component.style().get("position"));
        assertNull(component.style().get("left"));
        assertNull(component.style().get("top"));
        assertNull(component.style().get("width"));
        assertEquals("3", component.style().get("order"));
        assertEquals("2", component.style().get("flex-grow"));
        assertEquals("0", component.style().get("flex-shrink"));
        assertEquals("40%", component.style().get("flex-basis"));
        assertEquals("center", component.style().get("align-self"));
    }
}
