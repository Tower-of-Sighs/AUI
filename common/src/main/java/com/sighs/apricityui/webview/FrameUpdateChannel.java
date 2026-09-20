package com.sighs.apricityui.webview;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Reader for the offscreen web view's image update stream.
 *
 * <p>The host publishes into a block of shared memory mapped into this process
 * ({@link com.sighs.apricityui.spi.AuiWebViewService.View#channel()}): a small header, then a
 * packet arena holding rectangles of the picture that changed. {@link #drain} applies every
 * packet that is fully written and hands each rectangle to the caller, so the renderer
 * neither copies a whole frame nor re-uploads a whole texture for a page that moved one
 * element.</p>
 *
 * <p>Packets are never dropped and never reordered: the producer publishes a packet's bytes,
 * then its end offset, then its sequence number, and this reader republishes its own read
 * offset only after the rectangles have been applied. Nothing beyond a single packet's
 * payload is ever held, and a reader that stops reading simply stops the producer from
 * starting a new run — it never loses one.</p>
 *
 * <p>The layout is a wire contract with the native host
 * ({@code native/webview/src/frame_channel.h}); {@link #VERSION} is the pair's agreement, and
 * a buffer that does not carry it is refused rather than misread.</p>
 */
public final class FrameUpdateChannel {

    /** {@code "AUIC"} as little-endian bytes; the header's magic. */
    private static final int MAGIC = 0x43495541;
    /** Bumped whenever any offset below changes on either side. */
    private static final int VERSION = 1;
    /** {@code "UAPK"}: a packet of rectangles. */
    private static final int PACKET_MAGIC = 0x4B504155;
    /** {@code "PAD!"}: filler written when a packet would straddle the arena end. */
    private static final int PAD_MAGIC = 0x21444150;

    private static final int PACKET_HEADER_BYTES = 40;
    /** Header block every packet offset is relative to; see the class comment. */
    private static final int HEADER_BYTES = 256;
    /** Largest payload the host packs into one packet; a rectangle is split to fit. */
    private static final int MAX_PACKET_PAYLOAD = 1 << 18;
    /** Guard against reading geometry out of a corrupt packet. */
    private static final int MAX_CANVAS_EDGE = 1 << 14;

    // Header offsets, mirroring frame_channel.h.
    private static final int OFF_MAGIC = 0;
    private static final int OFF_VERSION = 4;
    private static final int OFF_HEADER_BYTES = 8;
    private static final int OFF_ARENA_BYTES = 12;
    private static final int OFF_CANVAS_WIDTH = 20;
    private static final int OFF_CANVAS_HEIGHT = 24;
    private static final int OFF_FLAGS = 28;
    private static final int OFF_WRITE_OFFSET = 32;
    private static final int OFF_WRITE_SEQ = 40;
    private static final int OFF_PUBLISHED_PACKETS = 48;
    private static final int OFF_PUBLISHED_BYTES = 56;
    private static final int OFF_READ_OFFSET = 88;
    private static final int OFF_READ_SEQ = 96;
    private static final int OFF_CONSUMER_PACKETS = 104;
    private static final int OFF_CONSUMER_BYTES = 112;
    private static final int OFF_CONSUMER_RECTS = 120;
    private static final int OFF_CONSUMER_RESYNCS = 128;

    /** Asks the producer for a full canvas refresh on its next publish. */
    private static final int FLAG_NEEDS_FULL_REFRESH = 1;

    /** Receives the stream: geometry first, then one call per dirty rectangle. */
    public interface Target {

        /**
         * The canvas the following rectangles belong to; it changes when the element is
         * resized or the page reflows into a new raster size. Anything the reader had cached
         * for the previous canvas is stale at this point.
         */
        void resize(int width, int height);

        /**
         * Applies one dirty rectangle.
         *
         * @param pixels ABGR-packed pixels, row major, tightly packed, at least
         *               {@code width * height} entries; owned by the reader and reused.
         */
        void rect(int x, int y, int width, int height, int[] pixels);
    }

    private final ByteBuffer buffer;
    private final boolean valid;
    private int[] rectanglePixels = new int[0];
    private int canvasWidth;
    private int canvasHeight;
    private int arenaBytes;

    private long packets;
    private long rectangles;
    private long payloadBytes;
    private long resyncs;

    public FrameUpdateChannel(ByteBuffer buffer) {
        // A ByteBuffer defaults to big-endian; the wire format is little-endian on every
        // platform the host runs on. The duplicate shares the memory and carries its own
        // order, so the caller's buffer is left alone.
        this.buffer = buffer == null ? null : buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        boolean ok = false;
        int arena = 0;
        if (this.buffer != null) {
            ok = this.buffer.capacity() > HEADER_BYTES
                    && this.buffer.getInt(OFF_MAGIC) == MAGIC
                    && this.buffer.getInt(OFF_VERSION) == VERSION
                    && this.buffer.getInt(OFF_HEADER_BYTES) == HEADER_BYTES;
            if (ok) {
                arena = this.buffer.getInt(OFF_ARENA_BYTES);
                ok = arena > 0 && (long) HEADER_BYTES + arena <= this.buffer.capacity();
            }
        }
        this.arenaBytes = ok ? arena : 0;
        this.valid = ok;
    }

    /** Asks the host for a full canvas refresh on its next publish. */
    public void requestFullRefresh() {
        if (valid) {
            buffer.putInt(OFF_FLAGS, buffer.getInt(OFF_FLAGS) | FLAG_NEEDS_FULL_REFRESH);
        }
    }

    /** False when the buffer is missing or carries a layout this reader does not know. */
    public boolean isValid() {
        return valid;
    }

    public int canvasWidth() {
        return canvasWidth;
    }

    public int canvasHeight() {
        return canvasHeight;
    }

    /** Packets applied so far. */
    public long packets() {
        return packets;
    }

    /** Dirty rectangles applied so far. */
    public long rectangles() {
        return rectangles;
    }

    /** Pixel bytes applied so far. */
    public long payloadBytes() {
        return payloadBytes;
    }

    /** Times the reader met a record it could not parse; each one costs a full refresh. */
    public long resyncs() {
        return resyncs;
    }

    /** Numbers the producer published, for comparing against {@link #packets()}. */
    public long publishedPackets() {
        return valid ? buffer.getLong(OFF_PUBLISHED_PACKETS) : 0L;
    }

    /** Pixel bytes the producer published. */
    public long publishedBytes() {
        return valid ? buffer.getLong(OFF_PUBLISHED_BYTES) : 0L;
    }

    /** One-line diagnostic for {@code Iframe.status()}. */
    public String stats() {
        if (!valid) {
            return "stream=unavailable";
        }
        return "stream=" + canvasWidth + "x" + canvasHeight
                + " packets=" + packets + "/" + publishedPackets()
                + " rects=" + rectangles
                + " payload=" + (payloadBytes / 1024) + "/" + (publishedBytes() / 1024) + "KB"
                + (resyncs > 0 ? " resync=" + resyncs : "");
    }

    /**
     * Applies every complete packet and returns how many were applied.
     *
     * <p>Must be called from one thread (the render thread) — the stream is
     * single-consumer, and the read cursor this advances is what tells the producer how much
     * room it has.</p>
     */
    public int drain(Target target) {
        if (!valid || target == null || buffer == null) {
            return 0;
        }
        int applied = 0;
        while (true) {
            final long readOffset = buffer.getLong(OFF_READ_OFFSET);
            final long writeOffset = buffer.getLong(OFF_WRITE_OFFSET);
            if (readOffset == writeOffset) {
                break;
            }
            final int position = HEADER_BYTES + (int) (readOffset % arenaBytes);
            final int magic = buffer.getInt(position);
            if (magic == PAD_MAGIC) {
                final int padBytes = buffer.getInt(position + 4);
                if (padBytes < 8 || readOffset + padBytes > writeOffset) {
                    resync();
                    break;
                }
                buffer.putLong(OFF_READ_OFFSET, readOffset + padBytes);
                continue;
            }
            if (magic != PACKET_MAGIC) {
                resync();
                break;
            }
            final long sequence = buffer.getLong(position + 8);
            final int packetWidth = buffer.getInt(position + 16);
            final int packetHeight = buffer.getInt(position + 20);
            final int rectCount = buffer.getInt(position + 24);
            final int payloadBytes = buffer.getInt(position + 28);
            final int tableOffset = position + PACKET_HEADER_BYTES;
            final int payloadOffset = tableOffset + rectCount * 16;
            // The host pads every packet to 8 bytes, so the cursor has to advance by the
            // padded size: a 1x1 rectangle is a 4-byte payload and would otherwise leave the
            // reader four bytes short of the next packet.
            final int packetBytes = align8(PACKET_HEADER_BYTES + rectCount * 16 + payloadBytes);
            final long packetEnd = readOffset + packetBytes;
            if (!plausible(packetWidth, packetHeight, rectCount, payloadBytes, payloadOffset)
                    || packetEnd > writeOffset
                    || position + packetBytes > HEADER_BYTES + arenaBytes) {
                // The tail of a packet can still be in flight; anything else is unreadable.
                if (packetEnd > writeOffset) {
                    break;
                }
                resync();
                break;
            }
            if (packetWidth != canvasWidth || packetHeight != canvasHeight) {
                canvasWidth = packetWidth;
                canvasHeight = packetHeight;
                target.resize(canvasWidth, canvasHeight);
            }
            int appliedRects = 0;
            int offset = payloadOffset;
            boolean intact = true;
            for (int index = 0; index < rectCount && intact; index++) {
                final int table = tableOffset + index * 16;
                final int x = buffer.getInt(table);
                final int y = buffer.getInt(table + 4);
                final int width = buffer.getInt(table + 8);
                final int height = buffer.getInt(table + 12);
                final int count = width * height;
                if (x < 0 || y < 0 || width <= 0 || height <= 0
                        || x + width > canvasWidth || y + height > canvasHeight
                        || offset + (long) count * 4 > payloadOffset + payloadBytes) {
                    intact = false;
                    break;
                }
                if (rectanglePixels.length < count) {
                    rectanglePixels = new int[count];
                }
                readPixels(offset, rectanglePixels, count);
                offset += count * 4;
                target.rect(x, y, width, height, rectanglePixels);
                appliedRects++;
            }
            if (!intact) {
                resync();
                break;
            }
            buffer.putLong(OFF_READ_OFFSET, packetEnd);
            buffer.putLong(OFF_READ_SEQ, sequence);
            this.packets++;
            this.rectangles += appliedRects;
            this.payloadBytes += payloadBytes;
            applied++;
        }
        if (applied > 0) {
            buffer.putLong(OFF_CONSUMER_PACKETS, packets);
            buffer.putLong(OFF_CONSUMER_BYTES, payloadBytes);
            buffer.putLong(OFF_CONSUMER_RECTS, rectangles);
        }
        return applied;
    }

    private boolean plausible(int width, int height, int rectCount, int payloadBytes, int payloadOffset) {
        return width > 0 && height > 0
                && width <= MAX_CANVAS_EDGE && height <= MAX_CANVAS_EDGE
                && rectCount >= 0 && rectCount <= MAX_PACKET_PAYLOAD / 4
                && payloadBytes >= 0 && payloadBytes <= MAX_PACKET_PAYLOAD + 4
                && payloadOffset >= 0
                && payloadOffset + (long) payloadBytes <= HEADER_BYTES + arenaBytes;
    }

    /**
     * Skips to the end of the stream and asks the producer for a full refresh, so a reader
     * that met something it could not parse ends up consistent instead of stuck.
     */
    private void resync() {
        resyncs++;
        buffer.putLong(OFF_READ_OFFSET, buffer.getLong(OFF_WRITE_OFFSET));
        buffer.putLong(OFF_READ_SEQ, buffer.getLong(OFF_WRITE_SEQ));
        buffer.putLong(OFF_CONSUMER_RESYNCS, resyncs);
        requestFullRefresh();
    }

    private static int align8(int value) {
        return (value + 7) & ~7;
    }

    /**
     * Bulk-copies {@code count} packed pixels into {@code destination}.
     *
     * <p>An {@link IntBuffer} view is the fast path; a JDK that hands back a big-endian view
     * (the order is inherited from the parent, but nothing promises it) falls back to
     * per-pixel reads through the buffer's own order.</p>
     */
    private void readPixels(int byteOffset, int[] destination, int count) {
        final ByteBuffer slice = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        slice.position(byteOffset).limit(byteOffset + count * 4);
        final IntBuffer ints = slice.slice().asIntBuffer();
        if (ints.order() == ByteOrder.LITTLE_ENDIAN) {
            ints.get(destination, 0, count);
            return;
        }
        for (int index = 0; index < count; index++) {
            destination[index] = buffer.getInt(byteOffset + index * 4);
        }
    }
}
