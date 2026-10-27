#include "frame_channel.h"

#include <atomic>
#include <cstring>

namespace {

/** Rectangles one packet may carry; bounds the packing buffer. */
constexpr int kMaxRectsPerPacket = 1024;

constexpr size_t align8(size_t value) {
    return (value + 7u) & ~static_cast<size_t>(7u);
}

constexpr int alignTo(int value, int block) {
    return (value + block - 1) / block * block;
}

}  // namespace

FrameChannel::FrameChannel() = default;

FrameChannel::~FrameChannel() {
    close();
}

bool FrameChannel::open(int canvasWidth, int canvasHeight) {
    close();
    const int width = canvasWidth > 0 ? canvasWidth : 1;
    const int height = canvasHeight > 0 ? canvasHeight : 1;

    // The arena has to hold a full refresh of the canvas plus the packets the reader has not
    // picked up yet; when that is too small for a later layout the producer simply spreads
    // the refresh over several publishes instead of growing the section.
    const int64_t canvasBytes = static_cast<int64_t>(width) * height * 4;
    int64_t wanted = canvasBytes + canvasBytes / 4 + (1 << 20);
    if (wanted < kMinArenaBytes) {
        wanted = kMinArenaBytes;
    }
    if (wanted > kMaxArenaBytes) {
        wanted = kMaxArenaBytes;
    }
    arenaBytes_ = alignTo(static_cast<int>(wanted), 64 * 1024);
    sectionBytes_ = kHeaderBytes + static_cast<size_t>(arenaBytes_);

    section_ = CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE,
                                  static_cast<DWORD>(sectionBytes_ >> 32),
                                  static_cast<DWORD>(sectionBytes_ & 0xFFFFFFFFu), nullptr);
    if (section_ == nullptr) {
        sectionBytes_ = 0;
        return false;
    }
    mapping_ = static_cast<uint8_t*>(MapViewOfFile(section_, FILE_MAP_ALL_ACCESS, 0, 0, 0));
    if (mapping_ == nullptr) {
        CloseHandle(section_);
        section_ = nullptr;
        sectionBytes_ = 0;
        return false;
    }

    std::memset(mapping_, 0, sectionBytes_);
    storeI32(kOffMagic, kMagic);
    storeI32(kOffVersion, kVersion);
    storeI32(kOffHeaderBytes, kHeaderBytes);
    storeI32(kOffArenaBytes, arenaBytes_);
    storeI32(kOffTileSize, kTileSize);
    storeI32(kOffCanvasWidth, width);
    storeI32(kOffCanvasHeight, height);
    storeI32(kOffFlags, 0);

    sequence_ = 0;
    writeOffset_ = 0;
    referenceIsBgra_ = false;
    resetReference(width, height);
    return true;
}

void FrameChannel::close() {
    if (mapping_ != nullptr) {
        UnmapViewOfFile(mapping_);
        mapping_ = nullptr;
    }
    if (section_ != nullptr) {
        CloseHandle(section_);
        section_ = nullptr;
    }
    sectionBytes_ = 0;
    arenaBytes_ = 0;
    writeOffset_ = 0;
    sequence_ = 0;
    deferred_ = false;
    reference_.clear();
    reference_.shrink_to_fit();
    referenceWidth_ = 0;
    referenceHeight_ = 0;
    referenceValid_ = false;
    referenceIsBgra_ = false;
    tiles_.clear();
    rects_.clear();
    packed_.clear();
}

void FrameChannel::resetReference(int width, int height) {
    referenceWidth_ = width;
    referenceHeight_ = height;
    reference_.assign(static_cast<size_t>(width) * height * 4, 0);
    referenceValid_ = true;
}

bool FrameChannel::publish(const uint8_t* pixels, int width, int height, bool swapRedBlue) {
    if (!isOpen() || pixels == nullptr || width <= 0 || height <= 0) {
        return false;
    }
    const bool resized = !referenceValid_ || width != referenceWidth_ || height != referenceHeight_;
    if (resized) {
        resetReference(width, height);
    }
    canvasWidth_ = width;
    canvasHeight_ = height;
    storeI32(kOffCanvasWidth, width);
    storeI32(kOffCanvasHeight, height);

    // The reference holds the source's byte order, so a source that switches formats (the
    // composition stream versus the codec path) invalidates it even at the same size.
    bool fullRefresh = resized || swapRedBlue != referenceIsBgra_;
    referenceIsBgra_ = swapRedBlue;
    if (!fullRefresh && (loadI32(kOffFlags) & kFlagFullRefresh) != 0) {
        fullRefresh = true;
    }
    if (fullRefresh) {
        storeI32(kOffFlags, 0);
    }

    rects_.clear();
    if (fullRefresh) {
        rects_.push_back(Rect{0, 0, width, height});
    } else {
        collectDirtyRects(pixels, width * 4);
    }
    if (rects_.empty()) {
        return false;
    }

    // One run: as many packets as the free arena space allows, in rect order. Whatever does
    // not fit is left behind; the next publish re-diffs against the reference, which only
    // advanced for the packets actually written, so the leftovers come back on their own.
    const int stride = width * 4;
    if (packed_.size() != static_cast<size_t>(kMaxRectsPerPacket)) {
        packed_.assign(static_cast<size_t>(kMaxRectsPerPacket), Rect{0, 0, 0, 0});
    }
    int cursor = 0;
    bool wroteAnything = false;
    while (cursor < static_cast<int>(rects_.size())) {
        int payloadBytes = 0;
        int next = cursor;
        const int count = packRects(cursor, &payloadBytes, packed_.data(), kMaxRectsPerPacket, &next);
        if (count <= 0) {
            break;
        }
        if (!writePacket(pixels, stride, packed_.data(), count, payloadBytes, fullRefresh, swapRedBlue)) {
            break;
        }
        cursor = next;
        wroteAnything = true;
    }
    const bool complete = cursor >= static_cast<int>(rects_.size());
    // Anything left over counts as deferred, including a run that could not write a single
    // packet: the reader is owed those rectangles, and a caller that skips work while "the
    // capture did not change" has to know that.
    deferred_ = !complete;
    if (complete) {
        if (fullRefresh) {
            storeI64(kOffFullRefreshes, loadI64(kOffFullRefreshes) + 1);
        }
    } else {
        storeI64(kOffDeferredRuns, loadI64(kOffDeferredRuns) + 1);
    }
    return wroteAnything;
}

void FrameChannel::collectDirtyRects(const uint8_t* pixels, int stride) {
    const int width = referenceWidth_;
    const int height = referenceHeight_;
    const int tile = kTileSize;
    const int tilesX = (width + tile - 1) / tile;
    const int tilesY = (height + tile - 1) / tile;
    tiles_.assign(static_cast<size_t>(tilesX) * tilesY, 0);

    const uint8_t* reference = reference_.data();
    for (int tileY = 0; tileY < tilesY; tileY++) {
        const int y0 = tileY * tile;
        const int bandRows = (y0 + tile <= height) ? tile : height - y0;
        for (int tileX = 0; tileX < tilesX; tileX++) {
            const int x0 = tileX * tile;
            const int cols = (x0 + tile <= width) ? tile : width - x0;
            bool dirty = false;
            for (int row = 0; row < bandRows && !dirty; row++) {
                const size_t offset = static_cast<size_t>(y0 + row) * stride + static_cast<size_t>(x0) * 4;
                dirty = std::memcmp(pixels + offset, reference + offset, static_cast<size_t>(cols) * 4) != 0;
            }
            tiles_[static_cast<size_t>(tileY) * tilesX + tileX] = dirty ? 1 : 0;
        }
    }

    // Merge dirty tiles: horizontal runs first, then grow each run downwards while the whole
    // run stays dirty, which turns a scrolled page into a handful of tall rectangles.
    for (int tileY = 0; tileY < tilesY; tileY++) {
        int tileX = 0;
        while (tileX < tilesX) {
            if (tiles_[static_cast<size_t>(tileY) * tilesX + tileX] == 0) {
                tileX++;
                continue;
            }
            const int start = tileX;
            while (tileX < tilesX && tiles_[static_cast<size_t>(tileY) * tilesX + tileX] != 0) {
                tileX++;
            }
            int rows = 1;
            while (tileY + rows < tilesY) {
                bool all = true;
                for (int column = start; column < tileX && all; column++) {
                    all = tiles_[static_cast<size_t>(tileY + rows) * tilesX + column] != 0;
                }
                if (!all) {
                    break;
                }
                rows++;
            }
            const int x0 = start * tile;
            const int x1 = tileX * tile <= width ? tileX * tile : width;
            const int y0 = tileY * tile;
            const int y1 = (tileY + rows) * tile <= height ? (tileY + rows) * tile : height;
            for (int row = 0; row < rows; row++) {
                for (int column = start; column < tileX; column++) {
                    tiles_[static_cast<size_t>(tileY + row) * tilesX + column] = 0;
                }
            }
            const Rect tight = refineRect(Rect{x0, y0, x1 - x0, y1 - y0}, pixels, stride);
            if (tight.width > 0) {
                rects_.push_back(tight);
            }
            if (static_cast<int>(rects_.size()) >= kMaxRects) {
                // Pathological content (noise, a video, a canvas animation): one bounding box
                // is cheaper than a thousand rectangles and the payload is the same order.
                int minX = rects_[0].x;
                int minY = rects_[0].y;
                int maxX = rects_[0].x + rects_[0].width;
                int maxY = rects_[0].y + rects_[0].height;
                for (const Rect& rect : rects_) {
                    if (rect.x < minX) {
                        minX = rect.x;
                    }
                    if (rect.y < minY) {
                        minY = rect.y;
                    }
                    if (rect.x + rect.width > maxX) {
                        maxX = rect.x + rect.width;
                    }
                    if (rect.y + rect.height > maxY) {
                        maxY = rect.y + rect.height;
                    }
                }
                rects_.clear();
                rects_.push_back(Rect{minX, minY, maxX - minX, maxY - minY});
                return;
            }
        }
    }
}

FrameChannel::Rect FrameChannel::refineRect(Rect rect, const uint8_t* pixels, int stride) const {
    const uint8_t* reference = reference_.data();
    const size_t rowBytes = static_cast<size_t>(rect.width) * 4;
    const int yEnd = rect.y + rect.height;

    // Row range first: whole-row compares, so a band that only changed in its middle drops
    // the unchanged rows above and below it. Tile granularity bounds that slack to 31 pixels
    // per side, but the merge can also grow a run over rows that are only partly dirty.
    int top = rect.y;
    while (top < yEnd) {
        const size_t offset = static_cast<size_t>(top) * stride + static_cast<size_t>(rect.x) * 4;
        if (std::memcmp(pixels + offset, reference + offset, rowBytes) != 0) {
            break;
        }
        top++;
    }
    if (top == yEnd) {
        return Rect{rect.x, rect.y, 0, 0};
    }
    int bottom = yEnd - 1;
    while (bottom > top) {
        const size_t offset = static_cast<size_t>(bottom) * stride + static_cast<size_t>(rect.x) * 4;
        if (std::memcmp(pixels + offset, reference + offset, rowBytes) != 0) {
            break;
        }
        bottom--;
    }

    // Column range next, as the union over the changed rows. Equal runs at both ends are
    // skipped four pixels at a time, so a row whose change starts at its first pixel costs
    // one compare and a fully repainted band costs nothing extra.
    int left = rect.width;
    int right = -1;
    for (int row = top; row <= bottom; row++) {
        const size_t rowOffset = static_cast<size_t>(row) * stride + static_cast<size_t>(rect.x) * 4;
        const uint8_t* current = pixels + rowOffset;
        const uint8_t* previous = reference + rowOffset;
        if (std::memcmp(current, previous, rowBytes) == 0) {
            continue;
        }
        int first = 0;
        while (first + 4 <= rect.width
               && std::memcmp(current + static_cast<size_t>(first) * 4,
                              previous + static_cast<size_t>(first) * 4, 16) == 0) {
            first += 4;
        }
        while (first < rect.width
               && std::memcmp(current + static_cast<size_t>(first) * 4,
                              previous + static_cast<size_t>(first) * 4, 4) == 0) {
            first++;
        }
        int last = rect.width - 1;
        while (last - 3 >= first
               && std::memcmp(current + static_cast<size_t>(last - 3) * 4,
                              previous + static_cast<size_t>(last - 3) * 4, 16) == 0) {
            last -= 4;
        }
        while (last > first
               && std::memcmp(current + static_cast<size_t>(last) * 4,
                              previous + static_cast<size_t>(last) * 4, 4) == 0) {
            last--;
        }
        if (first < left) {
            left = first;
        }
        if (last > right) {
            right = last;
        }
    }
    if (right < left) {
        return Rect{rect.x, rect.y, 0, 0};
    }
    return Rect{rect.x + left, top, right - left + 1, bottom - top + 1};
}

/**
 * Fills {@code packed} with rectangles from {@code cursor} on, up to the payload budget.
 *
 * @param nextCursor receives the index to continue from: past every rectangle that was
 *                   packed whole, and at the one that had to be split so its remaining rows
 *                   come back on the next packet
 * @return the number of rectangles written to {@code packed}
 */
int FrameChannel::packRects(int cursor, int* payloadBytes, Rect* packed, int capacity, int* nextCursor) {
    int budget = kMaxPayloadBytes;
    int count = 0;
    *nextCursor = cursor;
    while (cursor < static_cast<int>(rects_.size()) && count < capacity) {
        const Rect rect = rects_[cursor];
        const size_t rowBytes = static_cast<size_t>(rect.width) * 4;
        const int rowsFit = static_cast<int>(static_cast<size_t>(budget) / rowBytes);
        if (rowsFit >= rect.height) {
            packed[count++] = rect;
            budget -= static_cast<int>(rowBytes * rect.height);
            cursor++;
            *nextCursor = cursor;
            continue;
        }
        // One rectangle is taller than a whole packet: take the rows that fit and leave the
        // rest at this index for the next one. The budget makes rowsFit >= 1 unless a single
        // row is wider than the packet, which the canvas cap rules out.
        const int rows = rowsFit < 1 ? 1 : rowsFit;
        packed[count++] = Rect{rect.x, rect.y, rect.width, rows};
        budget -= static_cast<int>(rowBytes * rows);
        rects_[cursor].y += rows;
        rects_[cursor].height -= rows;
        break;
    }
    *payloadBytes = kMaxPayloadBytes - budget;
    return count;
}

bool FrameChannel::writePacket(const uint8_t* pixels, int stride, const Rect* rects, int count,
                               int payloadBytes, bool fullRefresh, bool swapRedBlue) {
    const size_t packetBytes = align8(kPacketHeaderBytes + static_cast<size_t>(count) * 16
                                      + static_cast<size_t>(payloadBytes));
    const size_t at = reserve(packetBytes);
    if (at == 0) {
        return false;
    }
    uint8_t* packet = mapping_ + at;
    std::memset(packet, 0, kPacketHeaderBytes);
    int32_t* fields = reinterpret_cast<int32_t*>(packet);
    fields[0] = kPacketMagic;
    fields[1] = kPacketHeaderBytes;
    *reinterpret_cast<int64_t*>(packet + 8) = sequence_ + 1;
    fields[4] = canvasWidth_;
    fields[5] = canvasHeight_;
    fields[6] = count;
    fields[7] = payloadBytes;
    fields[8] = fullRefresh ? kFlagFullRefresh : 0;
    fields[9] = 0;

    int32_t* table = reinterpret_cast<int32_t*>(packet + kPacketHeaderBytes);
    uint8_t* payload = packet + kPacketHeaderBytes + static_cast<size_t>(count) * 16;
    uint8_t* reference = reference_.data();
    size_t payloadOffset = 0;
    for (int index = 0; index < count; index++) {
        const Rect& rect = rects[index];
        table[index * 4 + 0] = rect.x;
        table[index * 4 + 1] = rect.y;
        table[index * 4 + 2] = rect.width;
        table[index * 4 + 3] = rect.height;
        const size_t bytes = static_cast<size_t>(rect.width) * 4;
        for (int row = 0; row < rect.height; row++) {
            const size_t offset = static_cast<size_t>(rect.y + row) * stride
                                  + static_cast<size_t>(rect.x) * 4;
            uint8_t* target = payload + payloadOffset;
            const uint8_t* source = pixels + offset;
            if (swapRedBlue) {
                // BGRA source: the arena always carries R,G,B,A, and only the pixels that are
                // actually being sent are flipped — the whole-canvas swap this replaces ran
                // once per capture whether the page had moved or not.
                for (size_t byte = 0; byte + 3 < bytes; byte += 4) {
                    target[byte + 0] = source[byte + 2];
                    target[byte + 1] = source[byte + 1];
                    target[byte + 2] = source[byte + 0];
                    target[byte + 3] = source[byte + 3];
                }
            } else {
                std::memcpy(target, source, bytes);
            }
            // The reference advances only for rectangles that made it into a packet, which is
            // exactly what makes a deferred run resume correctly on the next capture. It keeps
            // the source's byte order, so the diff never has to care which one it is.
            std::memcpy(reference + offset, source, bytes);
            payloadOffset += bytes;
        }
    }

    sequence_++;
    writeOffset_ += packetBytes;
    publishI64(kOffWriteOffset, static_cast<int64_t>(writeOffset_));
    publishI64(kOffWriteSeq, sequence_);
    storeI64(kOffPublishedPackets, loadI64(kOffPublishedPackets) + 1);
    storeI64(kOffPublishedBytes, loadI64(kOffPublishedBytes) + payloadBytes);
    storeI64(kOffPublishedRects, loadI64(kOffPublishedRects) + count);
    return true;
}

size_t FrameChannel::reserve(size_t packetBytes) {
    if (!isOpen() || packetBytes > static_cast<size_t>(arenaBytes_)) {
        return 0;
    }
    const int64_t readOffset = loadI64(kOffReadOffset);
    uint64_t used = writeOffset_ - static_cast<uint64_t>(readOffset);
    if (used > static_cast<uint64_t>(arenaBytes_)) {
        used = static_cast<uint64_t>(arenaBytes_);  // reader fell behind a resize; be conservative
    }
    const size_t free = static_cast<size_t>(arenaBytes_) - static_cast<size_t>(used);
    size_t position = static_cast<size_t>(writeOffset_ % static_cast<uint64_t>(arenaBytes_));
    size_t filler = 0;
    if (position + packetBytes > static_cast<size_t>(arenaBytes_)) {
        filler = static_cast<size_t>(arenaBytes_) - position;
    }
    if (free < filler + packetBytes) {
        return 0;
    }
    if (filler > 0) {
        // Keep every packet contiguous: fill the tail with a filler record and wrap.
        uint8_t* pad = mapping_ + kHeaderBytes + position;
        *reinterpret_cast<int32_t*>(pad) = kPadMagic;
        *reinterpret_cast<int32_t*>(pad + 4) = static_cast<int32_t>(filler);
        writeOffset_ += filler;
        publishI64(kOffWriteOffset, static_cast<int64_t>(writeOffset_));
        position = 0;
    }
    return kHeaderBytes + position;
}

int32_t FrameChannel::loadI32(size_t offset) const {
    const int32_t value = *reinterpret_cast<volatile int32_t*>(mapping_ + offset);
    std::atomic_thread_fence(std::memory_order_acquire);
    return value;
}

void FrameChannel::storeI32(size_t offset, int32_t value) const {
    std::atomic_thread_fence(std::memory_order_release);
    *reinterpret_cast<volatile int32_t*>(mapping_ + offset) = value;
}

int64_t FrameChannel::loadI64(size_t offset) const {
    const int64_t value = *reinterpret_cast<volatile int64_t*>(mapping_ + offset);
    std::atomic_thread_fence(std::memory_order_acquire);
    return value;
}

void FrameChannel::storeI64(size_t offset, int64_t value) const {
    std::atomic_thread_fence(std::memory_order_release);
    *reinterpret_cast<volatile int64_t*>(mapping_ + offset) = value;
}

/** Publishes a value the consumer must see only after the bytes it describes. */
void FrameChannel::publishI64(size_t offset, int64_t value) const {
    std::atomic_thread_fence(std::memory_order_release);
    *reinterpret_cast<volatile int64_t*>(mapping_ + offset) = value;
}

long long FrameChannel::publishedPackets() const {
    return isOpen() ? static_cast<long long>(loadI64(kOffPublishedPackets)) : 0;
}

long long FrameChannel::publishedBytes() const {
    return isOpen() ? static_cast<long long>(loadI64(kOffPublishedBytes)) : 0;
}

long long FrameChannel::publishedRects() const {
    return isOpen() ? static_cast<long long>(loadI64(kOffPublishedRects)) : 0;
}

long long FrameChannel::fullRefreshes() const {
    return isOpen() ? static_cast<long long>(loadI64(kOffFullRefreshes)) : 0;
}

long long FrameChannel::deferredRuns() const {
    return isOpen() ? static_cast<long long>(loadI64(kOffDeferredRuns)) : 0;
}

long long FrameChannel::consumerPackets() const {
    return isOpen() ? static_cast<long long>(loadI64(kOffConsumerPackets)) : 0;
}

long long FrameChannel::consumerBytes() const {
    return isOpen() ? static_cast<long long>(loadI64(kOffConsumerBytes)) : 0;
}

long long FrameChannel::consumerRects() const {
    return isOpen() ? static_cast<long long>(loadI64(kOffConsumerRects)) : 0;
}

long long FrameChannel::consumerResyncs() const {
    return isOpen() ? static_cast<long long>(loadI64(kOffConsumerResyncs)) : 0;
}

size_t FrameChannel::freeBytes() const {
    if (!isOpen()) {
        return 0;
    }
    const int64_t readOffset = loadI64(kOffReadOffset);
    const uint64_t used = writeOffset_ - static_cast<uint64_t>(readOffset);
    if (used >= static_cast<uint64_t>(arenaBytes_)) {
        return 0;
    }
    return static_cast<size_t>(arenaBytes_) - static_cast<size_t>(used);
}

std::wstring FrameChannel::statusText() const {
    if (!isOpen()) {
        return L"channel=closed";
    }
    wchar_t buffer[512];
    swprintf_s(buffer,
               L"channel=%dx%d arena=%dKB pub=%lld/%lldKB/%lldr read=%lld/%lldKB/%lldr "
               L"full=%lld deferred=%lld resync=%lld free=%zuKB",
               static_cast<int>(loadI32(kOffCanvasWidth)), static_cast<int>(loadI32(kOffCanvasHeight)),
               arenaBytes_ / 1024,
               publishedPackets(), publishedBytes() / 1024, publishedRects(),
               consumerPackets(), consumerBytes() / 1024, consumerRects(),
               fullRefreshes(), deferredRuns(), consumerResyncs(), freeBytes() / 1024);
    return std::wstring(buffer);
}
