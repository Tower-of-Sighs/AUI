package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotInteractionTest {
    @Test
    void unboundSlotAllowsTooltipButNotInventoryOperations() {
        Document document = new Document("test://display-slot", false);
        document.body = new Body(document);
        Slot slot = new Slot(document);
        document.body.appendChild(slot);

        assertTrue(slot.canShowItemTooltip());
        assertFalse(slot.canOperateBoundMenuSlot());
    }
}
