package com.sighs.apricityui.client;

import com.sighs.apricityui.layout.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MouseCoordinatesTest {
    @Test
    void convertsWindowCoordinatesUsingGuiViewportRatio() {
        Position position = MouseCoordinates.toGui(960.0d, 540.0d, 1920, 1080, 640, 360);

        assertEquals(320.0d, position.x);
        assertEquals(180.0d, position.y);
    }

    @Test
    void retinaFramebufferDensityDoesNotScaleTheCursorTwice() {
        // GLFW reports points in the 1440x900 window while Minecraft renders to a
        // 2880x1800 framebuffer. Only the window-to-GUI ratio belongs here.
        Position position = MouseCoordinates.toGui(1000.0d, 600.0d, 1440, 900, 720, 450);

        assertEquals(500.0d, position.x);
        assertEquals(300.0d, position.y);
    }

    @Test
    void keepsLiveCoordinatesWhenWindowDimensionsAreTemporarilyUnavailable() {
        Position position = MouseCoordinates.toGui(37.5d, 24.25d, 0, 0, 640, 360);

        assertEquals(37.5d, position.x);
        assertEquals(24.25d, position.y);
    }
}
