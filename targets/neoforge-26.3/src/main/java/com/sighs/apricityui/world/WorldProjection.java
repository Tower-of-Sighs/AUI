package com.sighs.apricityui.world;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Frame-local copy of GameRenderer's final reverse-Z, zero-to-one world projection. */
public final class WorldProjection {
    public static final WorldProjection FRAME = new WorldProjection();

    private Matrix4f projection;

    public void clear() {
        projection = null;
    }

    public void capture(Matrix4fc gpuProjection) {
        projection = toClassic(gpuProjection);
    }

    /** Consume once: missing captures must never fall back to a previous frame or base camera. */
    public Matrix4f take() {
        Matrix4f result = projection;
        projection = null;
        return result;
    }

    public static Matrix4f toClassic(Matrix4fc reverseZeroToOne) {
        // Left multiply the clip-space remap: x/y/w stay unchanged, z' = w - 2z.
        return new Matrix4f(reverseZeroToOne)
                .m02(reverseZeroToOne.m03() - 2.0f * reverseZeroToOne.m02())
                .m12(reverseZeroToOne.m13() - 2.0f * reverseZeroToOne.m12())
                .m22(reverseZeroToOne.m23() - 2.0f * reverseZeroToOne.m22())
                .m32(reverseZeroToOne.m33() - 2.0f * reverseZeroToOne.m32());
    }
}
