package com.sighs.apricityui.instance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.sighs.apricityui.instance.client.CursorReleaseController;

class CursorReleaseControllerTest {
    private final AtomicInteger releases = new AtomicInteger();
    private final AtomicInteger grabs = new AtomicInteger();

    @AfterEach
    void resetController() {
        CursorReleaseController.resetForTest();
    }

    @Test
    void holdingAndReleasingKeyTransfersMouseCapture() {
        update(true, true, true);

        assertTrue(CursorReleaseController.isActive());
        assertEquals(1, releases.get());
        assertEquals(0, grabs.get());

        update(true, true, false);
        assertEquals(1, releases.get());

        update(false, true, false);

        assertFalse(CursorReleaseController.isActive());
        assertEquals(1, grabs.get());
    }

    @Test
    void releasesMouseAgainIfVanillaRecapturesItWhileHeld() {
        update(true, true, true);
        update(true, true, true);

        assertTrue(CursorReleaseController.isActive());
        assertEquals(2, releases.get());
    }

    @Test
    void openingScreenClearsOwnershipWithoutGrabbingMouse() {
        update(true, true, true);
        update(true, false, false);

        assertFalse(CursorReleaseController.isActive());
        assertEquals(0, grabs.get());
    }

    @Test
    void initiallyReleasedMouseIsNotGrabbedOnKeyRelease() {
        update(true, true, false);
        update(false, true, false);

        assertFalse(CursorReleaseController.isActive());
        assertEquals(0, releases.get());
        assertEquals(0, grabs.get());
    }

    private void update(boolean requested, boolean available, boolean grabbed) {
        CursorReleaseController.update(requested, available, grabbed, releases::incrementAndGet, grabs::incrementAndGet);
    }
}
