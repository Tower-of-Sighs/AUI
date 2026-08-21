package com.sighs.apricityui.element;

import com.sighs.apricityui.canvas.CanvasImageData;
import com.sighs.apricityui.canvas.CanvasRenderingContext2D;
import com.sighs.apricityui.canvas.OffscreenCanvas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasDirtyRegionTest {
    private static OffscreenCanvas newCanvas() {
        return new OffscreenCanvas(60, 40);
    }

    private static int[] region(Canvas canvas) {
        return canvas.dirtyRegion();
    }

    private static boolean isEmpty(int[] region) {
        return region[2] <= region[0] || region[3] <= region[1];
    }

    @Test
    void fillRectMarksTightRegionPlusAaFringe() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.fillRect(10, 10, 20, 10);
        // 1px antialiasing fringe around (10,10)-(30,20)
        assertArrayEquals(new int[]{9, 9, 31, 21}, region(canvas));
    }

    @Test
    void drawFullyOutsideMarksNothing() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.fillRect(-100, -100, 10, 10);
        assertTrue(isEmpty(region(canvas)), "off-canvas draws must not dirty the surface");
    }

    @Test
    void regionsUnionAcrossOperations() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.fillRect(5, 5, 10, 10);
        ctx.fillRect(40, 20, 10, 10);
        assertArrayEquals(new int[]{4, 4, 51, 31}, region(canvas));
    }

    @Test
    void strokeExpandsByLineWidthAndMiterLimit() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.setLineWidth(2);
        ctx.strokeRect(20, 10, 20, 10);
        // strokePad = lineWidth/2 * miterLimit = 1 * 10 = 10, plus 1px AA fringe
        assertArrayEquals(new int[]{9, 0, 51, 31}, region(canvas));
    }

    @Test
    void clearRectFastPathMarksExactRect() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.clearRect(10, 10, 20, 10);
        assertArrayEquals(new int[]{10, 10, 30, 20}, region(canvas));
    }

    @Test
    void transformMovesDirtyRegion() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.translate(5, 5);
        ctx.fillRect(10, 10, 20, 10);
        assertArrayEquals(new int[]{14, 14, 36, 26}, region(canvas));
    }

    @Test
    void shadowExtendsDirtyRegionByOffsetAndBlur() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.setShadowColor("#ff0000");
        ctx.setShadowOffsetX(5);
        ctx.setShadowOffsetY(5);
        ctx.fillRect(10, 10, 20, 10);
        // main: (9,9)-(31,21); shadow: shifted by (5,5), padded by blur*2+3 = 3 -> (11,11)-(39,29)
        assertArrayEquals(new int[]{9, 9, 39, 29}, region(canvas));
    }

    @Test
    void putImageDataMarksExactRegion() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        CanvasImageData data = ctx.createImageData(4, 4);
        ctx.putImageData(data, 7, 8);
        assertArrayEquals(new int[]{7, 8, 11, 12}, region(canvas));
    }

    @Test
    void activeFilterFallsBackToFullSurface() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.setFilter("blur(4px)");
        ctx.fillRect(10, 10, 20, 10);
        assertArrayEquals(new int[]{0, 0, 60, 40}, region(canvas));
    }
}
