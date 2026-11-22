package com.sighs.apricityui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BorderRadiusTest {

    private static float[] radii(String css, float w, float h) {
        String[] hTokens = new String[4];
        String[] vTokens = new String[4];
        Box.parseBorderRadius(css, hTokens, vTokens);
        return Box.calculateRadii(hTokens, vTokens, w, h);
    }

    @Test
    void singlePxRadiusAppliesToAllCorners() {
        // [tlH, tlV, trH, trV, brH, brV, blH, blV]
        assertArrayEquals(
                new float[]{10, 10, 10, 10, 10, 10, 10, 10},
                radii("10px", 200, 100));
    }

    @Test
    void shorthandExpandsDiagonally() {
        // 2 值：TL/BR 用第一个，TR/BL 用第二个
        assertArrayEquals(
                new float[]{10, 10, 20, 20, 10, 10, 20, 20},
                radii("10px 20px", 200, 100));
        // 3 值：BL 复用 TR
        assertArrayEquals(
                new float[]{10, 10, 20, 20, 30, 30, 20, 20},
                radii("10px 20px 30px", 200, 100));
    }

    @Test
    void percentageResolvesHorizontalAgainstWidthAndVerticalAgainstHeight() {
        // 200x100 盒上的 50%：水平半径 100、垂直半径 50（椭圆角）
        assertArrayEquals(
                new float[]{100, 50, 100, 50, 100, 50, 100, 50},
                radii("50%", 200, 100));
    }

    @Test
    void slashSyntaxUsesSeparateVerticalRadii() {
        assertArrayEquals(
                new float[]{10, 30, 20, 40, 10, 30, 20, 40},
                radii("10px 20px / 30px 40px", 200, 100));
    }

    @Test
    void calcWithPercentageResolvesAgainstBoxSize() {
        // calc(10px + 5%)：水平 10 + 200*5% = 20，垂直 10 + 100*5% = 15
        assertArrayEquals(
                new float[]{20, 15, 20, 15, 20, 15, 20, 15},
                radii("calc(10px + 5%)", 200, 100));
    }

    @Test
    void oversizedRadiiScaleDownProportionally() {
        // 100 宽的盒上 60px 圆角：60+60=120 > 100，按 100/120 缩放
        float[] r = radii("60px", 100, 100);
        assertEquals(50f, r[0], 0.001);
        assertEquals(50f, r[1], 0.001);
    }

    @Test
    void percentageRadiiScaleDownOnSmallBox() {
        // 100x100 上的 75%：75+75=150 > 100，缩放为 50
        float[] r = radii("75%", 100, 100);
        assertEquals(50f, r[0], 0.001);
        assertEquals(50f, r[1], 0.001);
    }

    @Test
    void innerRadiiSubtractEachAdjacentSideAfterOuterNormalization() {
        assertArrayEquals(
                new float[]{36, 48, 44, 48, 44, 40, 36, 40},
                Box.insetRadii(radii("75%", 100, 100), 2, 6, 10, 14));
        // 每个轴分别钳位；不按内盒尺寸再次缩放。
        assertArrayEquals(
                new float[]{0, 50, 50, 50, 50, 50, 0, 50},
                Box.insetRadii(radii("50%", 100, 100), 0, 0, 0, 90));
    }

    @Test
    void oversizedAsymmetricRadiiNormalizeBeforeInset() {
        assertArrayEquals(new float[]{64,64,24,24,64,64,24,24},
                Box.insetRadii(radii("120px 60px", 120, 120), 16, 16, 16, 16), .001f);
    }

    @Test
    void invalidTokensFallBackToZero() {
        assertArrayEquals(
                new float[]{0, 0, 0, 0, 0, 0, 0, 0},
                radii("banana", 200, 100));
        // 非法数量（5 值）整组回退
        assertArrayEquals(
                new float[]{0, 0, 0, 0, 0, 0, 0, 0},
                radii("1px 2px 3px 4px 5px", 200, 100));
    }

    @Test
    void zeroSizedBoxDoesNotProduceNaN() {
        float[] r = radii("10px", 0, 0);
        for (float v : r) {
            assertEquals(0f, v, 0.001);
        }
    }
}
