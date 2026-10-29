package com.sighs.apricityui.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class RenderCompatibilityTest {
    @Test
    void narrowAtlasRegionUsesIndependentRowsAndPreservesSourceState() {
        ByteBuffer atlas = ByteBuffer.allocate(8 * 5 * 4);
        for (int i = 0; i < atlas.capacity(); i++) atlas.put(i, (byte) i);
        atlas.position(7);
        int limit = atlas.limit();
        ByteBuffer region = RenderCompatibility.packRegion(atlas, 8, 5, 6, 1, 2, 4,
                ByteBuffer.allocate(2 * 4 * 4));
        assertEquals(32, region.remaining());
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 8; column++) {
                assertEquals(atlas.get(((row + 1) * 8 + 6) * 4 + column), region.get(row * 8 + column));
            }
        }
        assertEquals(7, atlas.position());
        assertEquals(limit, atlas.limit());
    }

    @Test
    void pixelOffsetIsAbsoluteAndUploadRangeIsExact() {
        ByteBuffer pixels = ByteBuffer.allocate(80);
        pixels.position(5).limit(72);
        ByteBuffer upload = RenderCompatibility.pixels(pixels, 8, 2, 2);
        assertEquals(0, upload.position());
        assertEquals(16, upload.remaining());
        upload.put(0, (byte) 12);
        assertEquals(12, pixels.get(8));
        assertEquals(5, pixels.position());
        assertEquals(72, pixels.limit());
        assertEquals(16, RenderCompatibility.pixels(pixels, 56, 2, 2).remaining());
        assertThrows(IllegalArgumentException.class, () -> RenderCompatibility.pixels(pixels, 57, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> RenderCompatibility.pixels(pixels, -1, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> RenderCompatibility.pixels(pixels, 0, Integer.MAX_VALUE, 2));
        assertFalse(RenderCompatibility.regionFits(10, 10, Integer.MAX_VALUE, 0, 2, 2));
        assertFalse(RenderCompatibility.regionFits(10, 10, 0, 0, 0, 2));
        assertTrue(RenderCompatibility.regionFits(10, 10, 8, 8, 2, 2));
    }

    @Test
    void disabledGlobalScissorCannotMutateSavedSnapshot() throws Exception {
        // MC is runtime-only in this target's test source set.
        Class<?> type = Class.forName("com.mojang.blaze3d.systems.ScissorState");
        Object live = type.getConstructor().newInstance();
        type.getMethod("enable", int.class, int.class, int.class, int.class).invoke(live, 4, 5, 16, 17);
        Object saved = RenderCompatibility.class.getMethod("snapshot", type).invoke(null, live);
        type.getMethod("disable").invoke(live);
        assertEquals(true, type.getMethod("enabled").invoke(saved));
        type.getMethod("setFrom", type).invoke(live, saved);
        assertEquals(true, type.getMethod("enabled").invoke(live));
        assertEquals(4, type.getMethod("x").invoke(live));
        assertEquals(5, type.getMethod("y").invoke(live));
        assertEquals(16, type.getMethod("width").invoke(live));
        assertEquals(17, type.getMethod("height").invoke(live));
    }

    @Test
    void classicPerspectiveAndOrthoBecomeReverseZeroToOneWithoutTouchingCpuMatrix() {
        for (Matrix4f cpu : new Matrix4f[] {
                new Matrix4f().setPerspective(1.1f, 1.6f, 0.1f, 100),
                new Matrix4f().setOrtho(-4, 4, -3, 3, 0.1f, 100)}) {
            Matrix4f original = new Matrix4f(cpu);
            Matrix4f gpu = RenderCompatibility.projection(cpu, true, true);
            for (float z : new float[] {-0.1f, -1, -100}) {
                Vector4f classic = cpu.transform(new Vector4f(0, 0, z, 1));
                Vector4f converted = gpu.transform(new Vector4f(0, 0, z, 1));
                assertEquals(0.5f - 0.5f * classic.z / classic.w, converted.z / converted.w, 1e-5f);
            }
            assertEquals(original, cpu);
        }
        Matrix4f gui = new Matrix4f().setOrtho(0, 800, 600, 0, -1000, 1000);
        Matrix4f gpu = RenderCompatibility.projection(gui, true, false);
        assertEquals(0.5f * (gui.m22() + gui.m23()), gpu.m22());
        assertEquals(0.5f * (gui.m32() + gui.m33()), gpu.m32());
    }
}
