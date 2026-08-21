package com.sighs.apricityui.canvas;

import org.junit.jupiter.api.Test;

import java.awt.CompositeContext;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlendCompositeTest {
    private static final int SRC = 191; // ~0.75
    private static final int DST = 64;  // ~0.25

    @Test
    void overlayDoublesForDarkBackdrop() {
        // dst <= 0.5 -> 2 * src * dst = 0.376 -> 96
        assertChannel(96, BlendComposite.Mode.OVERLAY, SRC, DST);
    }

    @Test
    void hardLightScreensForLightSource() {
        // src > 0.5 -> 1 - 2 * (1 - src) * (1 - dst) = 0.624 -> 159
        assertChannel(159, BlendComposite.Mode.HARD_LIGHT, SRC, DST);
    }

    @Test
    void softLightUsesW3cCurve() {
        // src > 0.5, dst > 0.25 -> dst + (2src - 1) * (sqrt(dst) - dst) = 0.375 -> 96
        assertChannel(96, BlendComposite.Mode.SOFT_LIGHT, SRC, DST);
    }

    @Test
    void differenceIsAbsoluteDistance() {
        // |dst - src| = 0.498 -> 127
        assertChannel(127, BlendComposite.Mode.DIFFERENCE, SRC, DST);
    }

    @Test
    void exclusionSoftensDifference() {
        // dst + src - 2 * dst * src = 0.624 -> 159
        assertChannel(159, BlendComposite.Mode.EXCLUSION, SRC, DST);
    }

    @Test
    void colorDodgeBrightensBySource() {
        // src=0.5, dst=0.25 -> dst / (1 - src) = 0.504 -> 128
        assertChannel(128, BlendComposite.Mode.COLOR_DODGE, 128, DST);
    }

    @Test
    void colorDodgeWhiteIsPureWhite() {
        assertChannel(255, BlendComposite.Mode.COLOR_DODGE, 255, DST);
    }

    @Test
    void colorBurnDarkensBySource() {
        // 1 - min(1, (1 - dst) / src) = 0
        assertChannel(0, BlendComposite.Mode.COLOR_BURN, SRC, DST);
    }

    @Test
    void colorBurnBlackIsPureBlack() {
        assertChannel(0, BlendComposite.Mode.COLOR_BURN, 0, DST);
    }

    @Test
    void transparentSourceLeavesBackdropUntouched() {
        // srcA = 0 -> out = dst, regardless of blend mode
        assertChannel(DST, BlendComposite.Mode.DIFFERENCE, 0, DST, 0);
    }

    @Test
    void sourceAlphaScalesBlendContribution() {
        // multiply by white at half alpha: out = 0.5 * dst + 0.5 * (white * dst) = dst
        assertChannel(DST, BlendComposite.Mode.MULTIPLY, 255, DST, 128);
    }

    private static void assertChannel(int expected, BlendComposite.Mode mode, int src, int dst) {
        assertChannel(expected, mode, src, dst, 255);
    }

    private static void assertChannel(int expected, BlendComposite.Mode mode, int src, int dst, int srcAlpha) {
        int channel = apply(mode, src, dst, srcAlpha);
        assertTrue(Math.abs(channel - expected) <= 1,
                mode + " of src=" + src + " dst=" + dst + " a=" + srcAlpha + " should be ~" + expected + ", was " + channel);
    }

    private static int apply(BlendComposite.Mode mode, int src, int dst, int srcAlpha) {
        BufferedImage srcImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        BufferedImage dstImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        srcImage.setRGB(0, 0, (srcAlpha << 24) | (src << 16) | (src << 8) | src);
        dstImage.setRGB(0, 0, 0xFF000000 | (dst << 16) | (dst << 8) | dst);
        CompositeContext context = new BlendComposite(mode, 1f)
                .createContext(srcImage.getColorModel(), dstImage.getColorModel(), null);
        context.compose(srcImage.getRaster(), dstImage.getRaster(), dstImage.getRaster());
        context.dispose();
        int pixel = dstImage.getRGB(0, 0);
        assertEquals(255, (pixel >>> 24) & 0xFF, "opaque over opaque stays opaque");
        return pixel & 0xFF;
    }
}
