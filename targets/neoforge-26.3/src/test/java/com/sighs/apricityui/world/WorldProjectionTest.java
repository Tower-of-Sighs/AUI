package com.sighs.apricityui.world;

import com.sighs.apricityui.render.RenderCompatibility;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldProjectionTest {
    @Test
    void finalBobHurtAndNauseaProjectionRoundTripsWithoutChangingInput() {
        Matrix4f gpu = new Matrix4f().perspective(1.2f, 1.7f, 300.0f, 0.05f, true)
                .translate(0.02f, -0.04f, 0.0f)
                .rotateZ(0.03f).rotateX(-0.02f)
                .rotate(0.4f, new Vector3f(0, 1, 1).normalize())
                .scale(1.1f, 1, 1)
                .rotate(-0.4f, new Vector3f(0, 1, 1).normalize());
        Matrix4f original = new Matrix4f(gpu);
        Matrix4f classic = WorldProjection.toClassic(gpu);
        assertTrue(gpu.equals(original));
        assertTrue(gpu.equals(RenderCompatibility.projection(classic, true, true), 0.000001f));

        Vector4f point = new Vector4f(0.7f, -0.2f, -4, 1);
        Vector4f nativeClip = gpu.transform(new Vector4f(point));
        Vector4f cpuClip = classic.transform(new Vector4f(point));
        assertEquals(nativeClip.x, cpuClip.x, 0.000001f);
        assertEquals(nativeClip.y, cpuClip.y, 0.000001f);
        assertEquals(nativeClip.w, cpuClip.w, 0.000001f);
        assertEquals(nativeClip.w - 2 * nativeClip.z, cpuClip.z, 0.000001f);
    }

    @Test
    void classicNearFarUnprojectionPicksTheSameDocumentPoint() {
        Matrix4f gpu = new Matrix4f().perspective(1.1f, 1.6f, 200.0f, 0.05f, true)
                .translate(0.03f, -0.02f, 0).rotateZ(0.04f).rotateX(-0.02f);
        Matrix4f classic = WorldProjection.toClassic(gpu);
        Matrix4f modelView = new Matrix4f().translate(-0.4f, 0.2f, -4).rotateY(0.2f);
        Matrix4f clip = new Matrix4f(classic).mul(modelView);
        Vector4f expected = new Vector4f(0.3f, -0.15f, 0, 1);
        Vector4f projected = clip.transform(new Vector4f(expected));
        float x = projected.x / projected.w;
        float y = projected.y / projected.w;
        Matrix4f inverse = new Matrix4f(clip).invert();
        // These are the exact depth endpoints common WorldWindow uses for picking.
        Vector4f near = divide(inverse.transform(new Vector4f(x, y, -1, 1)));
        Vector4f far = divide(inverse.transform(new Vector4f(x, y, 1, 1)));
        float amount = -near.z / (far.z - near.z);
        assertTrue(amount > 0 && amount < 1);
        assertEquals(expected.x, near.x + (far.x - near.x) * amount, 0.0001f);
        assertEquals(expected.y, near.y + (far.y - near.y) * amount, 0.0001f);
        assertTrue(WorldWindow.isQuadVisible(modelView, classic, 1, 1));
        assertFalse(WorldWindow.isQuadVisible(new Matrix4f().translate(0, 0, -0.01f),
                WorldProjection.toClassic(new Matrix4f().perspective(1.1f, 1.6f, 200, 0.05f, true)), 0.001f, 0.001f));
    }

    @Test
    void captureIsCopiedConsumedOnceAndClearedBetweenFramesAndDisconnects() {
        WorldProjection frame = new WorldProjection();
        assertNull(frame.take());
        Matrix4f gpu = new Matrix4f().perspective(1.2f, 1.7f, 300, 0.05f, true);
        Matrix4f expected = WorldProjection.toClassic(gpu);
        frame.capture(gpu);
        gpu.identity();
        assertTrue(expected.equals(frame.take()));
        assertNull(frame.take());
        frame.capture(gpu);
        frame.clear();
        assertNull(frame.take());
        frame.capture(new Matrix4f().scale(2));
        frame.capture(new Matrix4f().scale(3));
        assertTrue(WorldProjection.toClassic(new Matrix4f().scale(3)).equals(frame.take()));
    }

    @Test
    void closedDisconnectedAndReplacedLevelsReleaseTheTrackedTestWindow() {
        assertFalse(WorldWindowTestSpawner.shouldReleaseWindow(true, true, true));
        assertTrue(WorldWindowTestSpawner.shouldReleaseWindow(true, true, false));
        assertTrue(WorldWindowTestSpawner.shouldReleaseWindow(false, true, true));
        assertTrue(WorldWindowTestSpawner.shouldReleaseWindow(false, false, false));
        assertTrue(WorldWindowTestSpawner.shouldReleaseWindow(true, false, true));
    }

    private static Vector4f divide(Vector4f point) {
        return point.div(point.w);
    }
}
