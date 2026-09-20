package com.sighs.apricityui.webview;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reader against a synthetic channel built the way the native host builds it.
 *
 * <p>{@link FakeHost} mirrors {@code native/webview/src/frame_channel.h}; if the two ever
 * drift apart these tests are what fails, which is the point of pinning the wire format down
 * here rather than only in the field.</p>
 */
class FrameUpdateChannelTest {

    /** Wire constants, repeated so a reader change cannot quietly follow a host change. */
    private static final int MAGIC = 0x43495541;
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 256;
    private static final int PACKET_MAGIC = 0x4B504155;
    private static final int PAD_MAGIC = 0x21444150;
    private static final int PACKET_HEADER_BYTES = 40;
    private static final int OFF_WRITE_OFFSET = 32;
    private static final int OFF_WRITE_SEQ = 40;
    private static final int OFF_READ_OFFSET = 88;
    private static final int OFF_READ_SEQ = 96;
    private static final int OFF_FLAGS = 28;

    /** Minimal producer: writes packets into a heap buffer with the host's layout. */
    private static final class FakeHost {
        final ByteBuffer buffer;
        final int arena;
        long writeOffset;
        long sequence;
        int canvasWidth;
        int canvasHeight;

        FakeHost(int arena, int canvasWidth, int canvasHeight) {
            this.arena = arena;
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
            this.buffer = ByteBuffer.allocate(HEADER_BYTES + arena).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(0, MAGIC);
            buffer.putInt(4, VERSION);
            buffer.putInt(8, HEADER_BYTES);
            buffer.putInt(12, arena);
            buffer.putInt(16, 32);
            buffer.putInt(20, canvasWidth);
            buffer.putInt(24, canvasHeight);
        }

        /** One packet of {@code rectangles}, each {@code {x, y, width, height, pixels...}}. */
        void packet(boolean fullRefresh, int[]... rectangles) {
            int rectCount = rectangles.length;
            int payloadBytes = 0;
            for (int[] rectangle : rectangles) {
                payloadBytes += rectangle[2] * rectangle[3] * 4;
            }
            int position = HEADER_BYTES + (int) (writeOffset % arena);
            int packetBytes = align8(PACKET_HEADER_BYTES + rectCount * 16 + payloadBytes);
            buffer.putInt(position, PACKET_MAGIC);
            buffer.putInt(position + 4, PACKET_HEADER_BYTES);
            buffer.putLong(position + 8, ++sequence);
            buffer.putInt(position + 16, canvasWidth);
            buffer.putInt(position + 20, canvasHeight);
            buffer.putInt(position + 24, rectCount);
            buffer.putInt(position + 28, payloadBytes);
            buffer.putInt(position + 32, fullRefresh ? 1 : 0);
            buffer.putInt(position + 36, 0);
            int table = position + PACKET_HEADER_BYTES;
            int payload = table + rectCount * 16;
            for (int index = 0; index < rectCount; index++) {
                int[] rectangle = rectangles[index];
                buffer.putInt(table + index * 16, rectangle[0]);
                buffer.putInt(table + index * 16 + 4, rectangle[1]);
                buffer.putInt(table + index * 16 + 8, rectangle[2]);
                buffer.putInt(table + index * 16 + 12, rectangle[3]);
                for (int pixel = 0; pixel < rectangle[2] * rectangle[3]; pixel++) {
                    buffer.putInt(payload, rectangle[4 + pixel]);
                    payload += 4;
                }
            }
            writeOffset += packetBytes;
            publish();
        }

        /** A wrap filler, exactly as {@code FrameChannel::reserve} writes one. */
        void padding(int bytes) {
            int position = HEADER_BYTES + (int) (writeOffset % arena);
            buffer.putInt(position, PAD_MAGIC);
            buffer.putInt(position + 4, bytes);
            writeOffset += bytes;
            publish();
        }

        /** Publishes the bytes; without this the reader must treat the tail as in flight. */
        void publish() {
            buffer.putLong(OFF_WRITE_OFFSET, writeOffset);
            buffer.putLong(OFF_WRITE_SEQ, sequence);
        }

        long readOffset() {
            return buffer.getLong(OFF_READ_OFFSET);
        }

        long readSeq() {
            return buffer.getLong(OFF_READ_SEQ);
        }

        boolean fullRefreshRequested() {
            return (buffer.getInt(OFF_FLAGS) & 1) != 0;
        }

        static int align8(int value) {
            return (value + 7) & ~7;
        }
    }

    /** Collects what the reader hands over. */
    private static final class Recorder implements FrameUpdateChannel.Target {
        final List<String> calls = new ArrayList<>();
        int width;
        int height;

        @Override
        public void resize(int newWidth, int newHeight) {
            width = newWidth;
            height = newHeight;
            calls.add("resize " + newWidth + "x" + newHeight);
        }

        @Override
        public void rect(int x, int y, int rectWidth, int rectHeight, int[] pixels) {
            StringBuilder pixelsText = new StringBuilder();
            for (int index = 0; index < rectWidth * rectHeight; index++) {
                pixelsText.append(index == 0 ? "" : ",").append(pixels[index]);
            }
            calls.add("rect " + x + "," + y + " " + rectWidth + "x" + rectHeight + " [" + pixelsText + "]");
        }
    }

    @Test
    void appliesRectanglesAndReportsGeometryOnce() {
        FakeHost host = new FakeHost(1 << 16, 4, 4);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        assertTrue(channel.isValid());
        Recorder recorder = new Recorder();

        host.packet(false, new int[]{1, 1, 2, 2, 10, 11, 12, 13});
        assertEquals(1, channel.drain(recorder));
        assertEquals(List.of("resize 4x4", "rect 1,1 2x2 [10,11,12,13]"), recorder.calls);
        assertEquals(4, channel.canvasWidth());
        assertEquals(4, channel.canvasHeight());
        assertEquals(1, channel.packets());
        assertEquals(1, channel.rectangles());
        assertEquals(16, channel.payloadBytes());

        // A second drain has nothing to do, and the reader has told the host so.
        assertEquals(0, channel.drain(recorder));
        assertEquals(host.writeOffset, host.readOffset());
        assertEquals(host.sequence, host.readSeq());
    }

    @Test
    void canvasResizeIsReportedBeforeItsRectangles() {
        FakeHost host = new FakeHost(1 << 16, 4, 4);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        Recorder recorder = new Recorder();

        host.packet(false, new int[]{0, 0, 2, 2, 1, 2, 3, 4});
        channel.drain(recorder);
        recorder.calls.clear();

        host.canvasWidth = 8;
        host.canvasHeight = 6;
        host.packet(true, new int[]{7, 5, 1, 1, 99});
        assertEquals(1, channel.drain(recorder));
        assertEquals(List.of("resize 8x6", "rect 7,5 1x1 [99]"), recorder.calls);
        assertEquals(8, channel.canvasWidth());
        assertEquals(6, channel.canvasHeight());
    }

    @Test
    void wrapPaddingIsSkippedWithoutDisturbingRectangles() {
        FakeHost host = new FakeHost(4096, 4, 4);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        Recorder recorder = new Recorder();

        host.packet(false, new int[]{0, 0, 2, 2, 1, 1, 1, 1});
        channel.drain(recorder);
        recorder.calls.clear();

        int used = (int) (host.writeOffset % host.arena);
        host.padding(host.arena - used);
        host.packet(false, new int[]{2, 2, 1, 1, 7});
        assertEquals(1, channel.drain(recorder));
        assertEquals(List.of("rect 2,2 1x1 [7]"), recorder.calls);
        assertEquals(host.writeOffset, host.readOffset());
    }

    @Test
    void unreadableRecordResyncsAndAsksForAFullRefresh() {
        FakeHost host = new FakeHost(1 << 16, 4, 4);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        Recorder recorder = new Recorder();

        host.packet(false, new int[]{0, 0, 1, 1, 5});
        // Corrupt the record's magic the way a bug or a stale reader would see it.
        host.buffer.putInt(HEADER_BYTES, 0x12345678);
        assertEquals(0, channel.drain(recorder));
        assertTrue(recorder.calls.isEmpty());
        assertEquals(1, channel.resyncs());
        assertEquals(host.writeOffset, host.readOffset());
        assertTrue(host.fullRefreshRequested());
    }

    @Test
    void packetStillInFlightIsLeftAlone() {
        FakeHost host = new FakeHost(1 << 16, 4, 4);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        Recorder recorder = new Recorder();

        host.packet(false, new int[]{0, 0, 1, 1, 42});
        // The host writes the bytes before it publishes the offset: hide the publish.
        host.buffer.putLong(OFF_WRITE_OFFSET, 0);
        assertEquals(0, channel.drain(recorder));
        assertTrue(recorder.calls.isEmpty());
        assertEquals(0, channel.resyncs());
    }

    @Test
    void geometryOutsideTheCanvasIsRefused() {
        FakeHost host = new FakeHost(1 << 16, 4, 4);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        Recorder recorder = new Recorder();

        host.packet(false, new int[]{3, 3, 4, 4, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        assertEquals(0, channel.drain(recorder));
        // Geometry is refused before any pixel reaches the target; the canvas is announced
        // first because that is what tells the target what it is about to be handed.
        assertTrue(recorder.calls.stream().noneMatch(call -> call.startsWith("rect ")),
                "no rectangle may be applied: " + recorder.calls);
        assertEquals(1, channel.resyncs());
    }

    @Test
    void layoutMismatchIsRefusedRatherThanMisread() {
        FakeHost host = new FakeHost(1 << 16, 4, 4);
        host.buffer.putInt(4, VERSION + 1);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        assertFalse(channel.isValid());
        Recorder recorder = new Recorder();
        host.packet(false, new int[]{0, 0, 1, 1, 1});
        assertEquals(0, channel.drain(recorder));
        assertTrue(recorder.calls.isEmpty());

        assertFalse(new FrameUpdateChannel(null).isValid());
        assertFalse(new FrameUpdateChannel(ByteBuffer.allocate(16)).isValid());
    }

    @Test
    void pixelsLandRowMajor() {
        FakeHost host = new FakeHost(1 << 16, 4, 4);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        Recorder recorder = new Recorder();

        host.packet(false, new int[]{0, 0, 3, 2, 1, 2, 3, 4, 5, 6});
        channel.drain(recorder);
        assertEquals(List.of("resize 4x4", "rect 0,0 3x2 [1,2,3,4,5,6]"), recorder.calls);
    }

    @Test
    void requestFullRefreshSetsTheHostFlag() {
        FakeHost host = new FakeHost(1 << 16, 4, 4);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        channel.requestFullRefresh();
        assertTrue(host.fullRefreshRequested());
    }

    @Test
    void paddedPacketSizeIsWhatAdvancesTheCursor() {
        // A 1x1 rectangle is a 4-byte payload: the host pads that packet to 8 bytes, and a
        // reader that advanced by the unpadded size would lose the next packet.
        FakeHost host = new FakeHost(1 << 16, 4, 4);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        Recorder recorder = new Recorder();

        host.packet(false, new int[]{0, 0, 1, 1, 7});
        host.packet(false, new int[]{1, 1, 1, 1, 8});
        assertEquals(2, channel.drain(recorder));
        assertEquals(List.of("resize 4x4", "rect 0,0 1x1 [7]", "rect 1,1 1x1 [8]"), recorder.calls);
        assertEquals(0, channel.resyncs());
        assertEquals(host.writeOffset, host.readOffset());
    }

    @Test
    void countersSurviveAnEmptyDrain() {
        FakeHost host = new FakeHost(1 << 16, 4, 4);
        FrameUpdateChannel channel = new FrameUpdateChannel(host.buffer);
        Recorder recorder = new Recorder();
        assertEquals(0, channel.drain(recorder));
        assertEquals(0, channel.drain(recorder));
        assertEquals(0, channel.packets());
        assertEquals(0, channel.resyncs());
        assertArrayEquals(new int[0], new int[0]);
    }
}
