// Streaming image-update channel between the offscreen web view host and the renderer.
//
// This replaces the old "host publishes one whole frame, Java polls it over JNI" transport.
// The host owns a page-file-backed section: a small shared header followed by a packet
// arena. Every published packet carries the absolute pixels of the rectangles that changed
// since the canvas the consumer is known to hold, so the renderer transfers and uploads
// only the part of the picture that actually moved.
#pragma once

#include <windows.h>

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

/**
 * Single-producer / single-consumer update stream.
 *
 * <p>Packets are never dropped and never reordered: the producer writes a packet, then
 * publishes {@code writeOffset} and finally {@code writeSeq}; the consumer copies
 * {@code writeOffset} into {@code readOffset} once it has applied everything below it. Free
 * space is {@code arenaBytes - (writeOffset - readOffset)}, and a packet that does not fit
 * is left for the next publish, which re-diffs the new capture against the canvas the
 * consumer is known to have. Backpressure therefore costs fresh frames, never correctness.</p>
 *
 * <p>The pixel buffers compare byte-wise, so the caller's format (R,G,B,A bytes, which is
 * the packed int layout {@code NativeImage} expects on little-endian) is what lands in the
 * arena.</p>
 *
 * <p>Wire layout, little endian, offsets absolute inside the section. Kept in step with the
 * Java reader ({@code common/.../webview/FrameUpdateChannel.java}); {@link #kVersion} is the
 * contract for that, so a mismatch is a version bump, not a silent misread.</p>
 *
 * <pre>
 *   header (kHeaderBytes = 256)
 *      0  int32  magic                "AUIC"
 *      4  int32  version
 *      8  int32  headerBytes
 *     12  int32  arenaBytes
 *     16  int32  tileSize
 *     20  int32  canvasWidth
 *     24  int32  canvasHeight
 *     28  int32  flags                bit0: consumer asked for a full refresh
 *     32  int64  writeOffset          producer, published before writeSeq
 *     40  int64  writeSeq             producer
 *     48  int64  publishedPackets
 *     56  int64  publishedBytes
 *     64  int64  publishedRects
 *     72  int64  fullRefreshes
 *     80  int64  deferredRuns         publishes that had to leave packets for later
 *     88  int64  readOffset           consumer, published before readSeq
 *     96  int64  readSeq              consumer
 *    104  int64  consumerPackets
 *    112  int64  consumerBytes
 *    120  int64  consumerRects
 *    128  int64  consumerResyncs      consumer saw something it could not parse
 *    136  ...    reserved to 256
 *
 *   packet, at 256 + writeOffset % arenaBytes
 *      0  int32  magic                "UAPK", or "PAD!" for a wrap filler
 *      4  int32  headerBytes          kPacketHeaderBytes = 40
 *      8  int64  seq                  1, 2, 3, ... strictly increasing
 *     16  int32  canvasWidth
 *     20  int32  canvasHeight
 *     24  int32  rectCount
 *     28  int32  payloadBytes         pixel bytes after the rect table
 *     32  int32  flags                bit0: part of a full refresh
 *     36  int32  reserved
 *     40  ...    rectCount * (int32 x, int32 y, int32 width, int32 height)
 *     ..  ...    payloadBytes of pixels, ABGR packed, rows tightly packed, rect order
 *
 *   padding packet (only when a packet would straddle the arena end)
 *      0  int32  magic                "PAD!"
 *      4  int32  bytes                total filler, including this header
 * </pre>
 */
class FrameChannel {
public:
    enum : int32_t {
        /** {@code "AUIC"} as little-endian bytes, i.e. 'A','U','I','C'. */
        kMagic = 0x43495541,
        kVersion = 1,
        kHeaderBytes = 256,
        /** {@code "UAPK"}. */
        kPacketMagic = 0x4B504155,
        kPacketHeaderBytes = 40,
        /** {@code "PAD!"}. */
        kPadMagic = 0x21444150,
        kFlagFullRefresh = 1,
    };

    /** Upper bound on one packet's pixel payload; bounds the arena and the reader's buffer. */
    static constexpr int kMaxPayloadBytes = 1 << 18;

    /** Square edge of the dirty-detection tile: 4 KB of pixels, L1 friendly. */
    static constexpr int kTileSize = 32;

    /** Beyond this many dirty rectangles in one pass the producer sends the bounding box. */
    static constexpr int kMaxRects = 1024;

    static constexpr int kMinArenaBytes = 2 << 20;
    static constexpr int kMaxArenaBytes = 24 << 20;

    FrameChannel();
    ~FrameChannel();

    FrameChannel(const FrameChannel&) = delete;
    FrameChannel& operator=(const FrameChannel&) = delete;

    /**
     * Creates the section for a canvas of the given raster size. The arena is sized so a full
     * refresh of that canvas fits in one publish; past the arena cap a refresh simply spreads
     * over several publishes.
     */
    bool open(int canvasWidth, int canvasHeight);

    /** Releases the section. The producer must not be inside {@link #publish} here. */
    void close();

    bool isOpen() const { return mapping_ != nullptr; }

    /** Section handle for the reader's own mapping; null when the channel is closed. */
    HANDLE section() const { return section_; }

    /** Total section bytes, header included; the reader maps exactly this much. */
    size_t sectionBytes() const { return sectionBytes_; }

    /**
     * Publishes the parts of {@code pixels} (width * height, tightly packed, 4 bytes per
     * pixel) that differ from the canvas the consumer holds, as one run of packets.
     *
     * @return true when at least one packet was written; whatever did not fit comes back on
     *         the next publish and is reported by {@link #hasDeferredRects()}
     */
    bool publish(const uint8_t* pixels, int width, int height);

    /**
     * Whether a full refresh is pending, from either side.
     *
     * <p>The reader asks for one by setting the flag in the header itself; this is how the
     * producer notices, and why a request is honoured even when the page has not changed.</p>
     */
    bool fullRefreshRequested() const {
        return isOpen() && (loadI32(kOffFlags) & kFlagFullRefresh) != 0;
    }

    /**
     * Whether the last publish ran out of arena space and left rectangles for later.
     *
     * <p>The producer re-derives them from the next capture on its own, so this only tells a
     * caller that skips work based on "the capture did not change" that the reader is still
     * owed pixels.</p>
     */
    bool hasDeferredRects() const { return deferred_; }

    // --- diagnostics, safe from any thread ---------------------------------
    long long publishedPackets() const;
    long long publishedBytes() const;
    long long publishedRects() const;
    long long fullRefreshes() const;
    long long deferredRuns() const;
    long long consumerPackets() const;
    long long consumerBytes() const;
    long long consumerRects() const;
    long long consumerResyncs() const;

    /** Free arena bytes, measured against the consumer's published read offset. */
    size_t freeBytes() const;

    std::wstring statusText() const;

private:
    // Header field offsets. These are the wire contract: the Java reader mirrors them one
    // for one, and kVersion is what any change here has to bump.
    static constexpr size_t kOffMagic = 0;
    static constexpr size_t kOffVersion = 4;
    static constexpr size_t kOffHeaderBytes = 8;
    static constexpr size_t kOffArenaBytes = 12;
    static constexpr size_t kOffTileSize = 16;
    static constexpr size_t kOffCanvasWidth = 20;
    static constexpr size_t kOffCanvasHeight = 24;
    static constexpr size_t kOffFlags = 28;
    static constexpr size_t kOffWriteOffset = 32;
    static constexpr size_t kOffWriteSeq = 40;
    static constexpr size_t kOffPublishedPackets = 48;
    static constexpr size_t kOffPublishedBytes = 56;
    static constexpr size_t kOffPublishedRects = 64;
    static constexpr size_t kOffFullRefreshes = 72;
    static constexpr size_t kOffDeferredRuns = 80;
    static constexpr size_t kOffReadOffset = 88;
    static constexpr size_t kOffReadSeq = 96;
    static constexpr size_t kOffConsumerPackets = 104;
    static constexpr size_t kOffConsumerBytes = 112;
    static constexpr size_t kOffConsumerRects = 120;
    static constexpr size_t kOffConsumerResyncs = 128;

    struct Rect {
        int x;
        int y;
        int width;
        int height;
    };

    /** Dirty rectangles of the last pass, as (x, y, width, height) pixel boxes. */
    void collectDirtyRects(const uint8_t* pixels, int stride);

    /** Packs rectangles from {@code cursor} into one packet; see the definition. */
    int packRects(int cursor, int* payloadBytes, Rect* packed, int capacity, int* nextCursor);

    /** Writes one packet (header, rect table, pixels) and advances the stream. */
    bool writePacket(const uint8_t* pixels, int stride, const Rect* rects, int count,
                     int payloadBytes, bool fullRefresh);

    /** Space the packet at the current position needs, or 0 when it cannot fit right now. */
    size_t reserve(size_t packetBytes);

    /** Header accessors; the producer publishes with a release fence, the reader acquires. */
    int32_t loadI32(size_t offset) const;
    void storeI32(size_t offset, int32_t value) const;
    int64_t loadI64(size_t offset) const;
    void storeI64(size_t offset, int64_t value) const;
    void publishI64(size_t offset, int64_t value) const;

    void resetReference(int width, int height);

    HANDLE section_ = nullptr;
    /** The producer's own mapping; the reader maps the section separately. */
    uint8_t* mapping_ = nullptr;
    size_t sectionBytes_ = 0;
    int arenaBytes_ = 0;

    int canvasWidth_ = 0;
    int canvasHeight_ = 0;
    int64_t sequence_ = 0;
    uint64_t writeOffset_ = 0;

    /** The canvas the consumer is known to hold: updated only for packets actually written. */
    std::vector<uint8_t> reference_;
    int referenceWidth_ = 0;
    int referenceHeight_ = 0;
    bool referenceValid_ = false;
    /** Set by the last publish when it could not write every dirty rectangle. */
    bool deferred_ = false;

    // scratch buffers, reused across publishes
    std::vector<uint8_t> tiles_;
    std::vector<Rect> rects_;
};
