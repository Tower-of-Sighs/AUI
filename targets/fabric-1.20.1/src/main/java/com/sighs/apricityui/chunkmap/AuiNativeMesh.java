package com.sighs.apricityui.chunkmap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.system.MemoryUtil;

/** Versioned AUI terrain vertices with opaque and translucent draw ranges. */
public final class AuiNativeMesh implements AutoCloseable {
    public static final int STRIDE = 32;
    private static final int MAGIC = 0x4D495541;
    private static final int HEADER = 32;
    private ByteBuffer data;
    private final int count;
    private final Range opaque, translucent;
    private final float minY, maxY;

    public record Range(int start, int count) { }

    private AuiNativeMesh(ByteBuffer data, int count, int opaqueCount, float minY, float maxY) {
        this.data = data; this.count = count; this.minY = minY; this.maxY = maxY;
        opaque = new Range(0, opaqueCount); translucent = new Range(opaqueCount, count - opaqueCount);
    }

    public static byte[] encode(AuiChunkMeshBaker.Mesh mesh) {
        ByteBuffer vertices = mesh.vertices();
        ByteBuffer output = ByteBuffer.allocate(Math.addExact(HEADER, vertices.remaining())).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(MAGIC).putInt(1).putInt(mesh.vertexCount()).putInt(mesh.opaque().count());
        output.putFloat(mesh.minY()).putFloat(mesh.maxY()).putInt(STRIDE).putInt(0);
        output.put(vertices);
        return output.array();
    }

    public static AuiNativeMesh decode(byte[] encoded) throws IOException {
        if (encoded.length < HEADER) throw new IOException("Truncated AUI mesh");
        ByteBuffer input = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        if (input.getInt() != MAGIC || input.getInt() != 1) throw new IOException("Unsupported AUI mesh format");
        int count = input.getInt(), opaque = input.getInt();
        float min = input.getFloat(), max = input.getFloat();
        if (input.getInt() != STRIDE || input.getInt() != 0 || count < 0 || count % 3 != 0
                || opaque < 0 || opaque > count || opaque % 3 != 0 || (long) count * STRIDE != input.remaining()
                || !Float.isFinite(min) || !Float.isFinite(max) || min > max) throw new IOException("Invalid AUI mesh bounds");
        ByteBuffer data = MemoryUtil.memAlloc(Math.max(1, input.remaining())).order(ByteOrder.nativeOrder());
        data.put(input).flip();
        return new AuiNativeMesh(data, count, opaque, min, max);
    }

    public ByteBuffer vertices() { return data.asReadOnlyBuffer().order(ByteOrder.nativeOrder()); }
    public int vertexCount() { return count; }
    public Range opaque() { return opaque; }
    public Range translucent() { return translucent; }
    public float minY() { return minY; }
    public float maxY() { return maxY; }
    @Override public void close() { if (data != null) { MemoryUtil.memFree(data); data = null; } }
}

