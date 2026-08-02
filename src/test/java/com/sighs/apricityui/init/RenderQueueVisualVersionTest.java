package com.sighs.apricityui.init;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderQueueVisualVersionTest {
    @Test
    void hitTestDirtyDoesNotAdvanceVisualVersionOrPendingVisualWork() {
        RenderQueue queue = new RenderQueue(new Document("test://visual-version", false));
        long version = queue.getVisualVersion();

        queue.markDirty(Drawer.HITTEST);

        org.junit.jupiter.api.Assertions.assertEquals(version, queue.getVisualVersion());
        assertFalse(queue.hasPendingVisualWork());
    }

    @Test
    void visualDirtyAdvancesVersionAndClearsAfterCommit() {
        RenderQueue queue = new RenderQueue(new Document("test://visual-version", false));
        long version = queue.getVisualVersion();

        queue.markDirty(Drawer.REPAINT | Drawer.RELAYOUT | Drawer.REORDER);

        assertTrue(queue.getVisualVersion() > version);
        assertTrue(queue.hasPendingVisualWork());
        queue.commit();
        assertFalse(queue.hasPendingVisualWork());
    }

    @Test
    void repaintOnlyCommitDoesNotRequestGeometryCommit() {
        RenderQueue queue = new RenderQueue(new Document("test://visual-version", false));

        queue.markDirty(Drawer.REPAINT);

        assertFalse(queue.commit(false));
    }

    @Test
    void layoutDirtyCommitStillRequestsGeometryCommit() {
        RenderQueue queue = new RenderQueue(new Document("test://visual-version", false));

        queue.markDirty(Drawer.RELAYOUT);

        assertTrue(queue.commit(false));
    }
}
