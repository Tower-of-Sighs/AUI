package com.sighs.apricityui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionDevicePixelSnapTest {
    @Test
    void crispPaintOriginAndScrollSnapToTheDocumentDeviceGrid() {
        assertEquals(10.5d, Position.snapForPaint(10.328d, 2.0d));
        assertEquals(11.0d, Position.snapForPaint(10.91d, 1.0d));
        assertEquals(-0.5d, Position.snapForPaint(-0.49d, 2.0d));

        Position snapped = Position.snapPositionForPaint(new Position(10.328d, 20.74d), 2.0d);
        assertEquals(10.5d, snapped.x);
        assertEquals(20.5d, snapped.y);
    }
}
