package com.sighs.apricityui.render;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

public final class MatrixBridge {
    private static final ThreadLocal<FloatBuffer> SCRATCH = ThreadLocal.withInitial(() -> BufferUtils.createFloatBuffer(16));
    private MatrixBridge() { }

    public static net.minecraft.util.math.vector.Matrix4f toMojang(Matrix4f source) {
        float[] values = new float[16];
        for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++)
            values[row * 4 + column] = source.get(column, row);
        return new net.minecraft.util.math.vector.Matrix4f(values);
    }

    public static net.minecraft.util.math.vector.Matrix4f toMojang(Matrix4f source, net.minecraft.util.math.vector.Matrix4f target) {
        target.set(toMojang(source));
        return target;
    }

    public static Matrix4f fromMojang(net.minecraft.util.math.vector.Matrix4f source) {
        FloatBuffer buffer = SCRATCH.get();
        buffer.clear();
        source.store(buffer);
        return new Matrix4f(buffer);
    }

    public static net.minecraft.util.math.vector.Matrix3f toMojang(Matrix3f source, net.minecraft.util.math.vector.Matrix3f target) {
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++)
            target.set(row, column, source.get(column, row));
        return target;
    }

    public static Matrix3f fromMojang(net.minecraft.util.math.vector.Matrix3f source) {
        Matrix3f result = new Matrix3f();
        for (int column = 0; column < 3; column++) {
            var vector = new net.minecraft.util.math.vector.Vector3f(column == 0 ? 1 : 0, column == 1 ? 1 : 0, column == 2 ? 1 : 0);
            vector.transform(source);
            result.setColumn(column, vector.x(), vector.y(), vector.z());
        }
        return result;
    }
}
