package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageDrawerBackgroundClipTest {
    @Test
    void skipsClipWhenNoRepeatImageFitsInsideBackgroundBox() {
        assertFalse(ImageDrawer.requiresBackgroundClip(
                18.0F, 18.0F,
                0.0F, 0.0F,
                18.0F, 18.0F,
                false, false
        ));
        assertFalse(ImageDrawer.requiresBackgroundClip(
                18.0F, 18.0F,
                1.0F, 2.0F,
                16.0F, 14.0F,
                false, false
        ));
    }

    @Test
    void keepsClipForRepeatingOrOverflowingBackgrounds() {
        assertTrue(ImageDrawer.requiresBackgroundClip(
                18.0F, 18.0F,
                0.0F, 0.0F,
                18.0F, 18.0F,
                true, false
        ));
        assertTrue(ImageDrawer.requiresBackgroundClip(
                18.0F, 18.0F,
                -1.0F, 0.0F,
                18.0F, 18.0F,
                false, false
        ));
        assertTrue(ImageDrawer.requiresBackgroundClip(
                18.0F, 18.0F,
                0.0F, 0.0F,
                19.0F, 18.0F,
                false, false
        ));
    }
}
