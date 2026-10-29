package com.sighs.apricityui.render;

import com.mojang.blaze3d.systems.ScissorState;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

/** Pure matrix/buffer/state operations shared by the 26.3 render bridge. */
public final class RenderCompatibility {
    private RenderCompatibility() {
    }

    public static Matrix4f projection(Matrix4f matrix, boolean zeroToOne, boolean reverse) {
        Matrix4f out = new Matrix4f(matrix);
        float scale = reverse ? -1 : 1;
        float offset = 0;
        if (zeroToOne) {
            scale *= 0.5f;
            offset = 0.5f;
        }
        out.m02(scale * matrix.m02() + offset * matrix.m03());
        out.m12(scale * matrix.m12() + offset * matrix.m13());
        out.m22(scale * matrix.m22() + offset * matrix.m23());
        out.m32(scale * matrix.m32() + offset * matrix.m33());
        return out;
    }

    /** Absolute offset relative to buffer index zero; position/limit remain untouched. */
    public static ByteBuffer pixels(ByteBuffer source, int offset, int width, int height) {
        long pixelCount = (long) width * height;
        if (width <= 0 || height <= 0 || offset < 0 || pixelCount > Integer.MAX_VALUE / 4)
            throw new IllegalArgumentException("Invalid pixel range");
        long bytes = pixelCount * 4;
        if (offset + bytes > source.limit()) throw new IllegalArgumentException("Invalid pixel range");
        return source.slice(offset, (int) bytes);
    }

    /** Independent row slices avoid carrying a previous row's limit to the next row. */
    public static ByteBuffer packRegion(ByteBuffer source, int imageWidth, int imageHeight,
                                        int x, int y, int width, int height, ByteBuffer destination) {
        if (!regionFits(imageWidth, imageHeight, x, y, width, height))
            throw new IllegalArgumentException("Invalid image region");
        int rowBytes = Math.multiplyExact(width, 4);
        for (int row = 0; row < height; row++) {
            int start = Math.toIntExact(((long) (y + row) * imageWidth + x) * 4);
            destination.put(source.slice(start, rowBytes));
        }
        return destination.flip();
    }

    public static boolean regionFits(int imageWidth, int imageHeight, int x, int y, int width, int height) {
        return x >= 0 && y >= 0 && width > 0 && height > 0
                && (long) x + width <= imageWidth && (long) y + height <= imageHeight;
    }

    public static ScissorState snapshot(ScissorState source) {
        return new ScissorState(source);
    }
}
