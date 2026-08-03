package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.drag.OreDragController;
import com.sighs.apricityui.editor.ore.palette.OreComponentDefinition;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreDragControllerTest {
    @Test
    void dropReportsLatestPointerOnceAndClearsSession() {
        OreDragController drag = new OreDragController();
        OreComponentDefinition payload = new OreComponentDefinition("row", "name", "description", true);
        AtomicInteger drops = new AtomicInteger();
        drag.begin(payload, 1, 2);
        drag.move(30, 40);

        drag.end((definition, point) -> {
            drops.incrementAndGet();
            assertEquals(payload, definition);
            assertEquals(30, point[0]);
            assertEquals(40, point[1]);
        });
        drag.end((definition, point) -> drops.incrementAndGet());

        assertEquals(1, drops.get());
        assertFalse(drag.active());
    }

    @Test
    void beginActivatesOnlyWithPayload() {
        OreDragController drag = new OreDragController();
        drag.begin(null, 0, 0);
        assertFalse(drag.active());
        drag.begin(new OreComponentDefinition("button", "name", "description", false), 0, 0);
        assertTrue(drag.active());
    }
}
