// Raw frame streaming out of a window's DWM composition.
//
// This is the alternative to WebView2's CapturePreview, which can only hand back an
// encoded image (PNG/JPEG) that then has to be decoded again — an encode/decode round trip
// of tens of milliseconds per frame. Windows.Graphics.Capture hands us the window's
// composited output directly as a GPU texture and tells us when a new frame exists, so the
// only cost left is a staging copy plus a byte swap.
#pragma once

#include <windows.h>

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

/**
 * Pushes window frames as raw RGBA.
 *
 * Frame delivery is callback-driven: Windows calls back whenever the window's composition
 * changes, so a static window costs nothing at all. The callback runs on a Windows thread
 * pool thread, never on the caller's thread.
 */
class FrameStream {
public:
    /** Receives one frame: RGBA byte order, row major, exactly width * height * 4 bytes. */
    using Sink = std::function<void(int width, int height, const uint8_t* rgba, size_t bytes)>;

    FrameStream();
    ~FrameStream();

    FrameStream(const FrameStream&) = delete;
    FrameStream& operator=(const FrameStream&) = delete;

    /** True when this OS build offers window capture at all. */
    static bool isSupported();

    /** Starts streaming the given window. Returns false (and sets lastError) on failure. */
    bool start(HWND window, int width, int height, Sink sink);

    /** Changes the captured size; the frame pool is recreated. */
    bool resize(int width, int height);

    void stop();
    bool isRunning() const { return running_; }

    /** Frames handed to the sink so far. */
    long long frameCount() const { return frames_; }

    /** Times the capture callback fired. */
    long long callbackCount() const { return callbacks_; }

    /** Callbacks that produced no frame at all. */
    long long emptyCallbackCount() const { return emptyCallbacks_; }

    /** Size of the most recent frame the pool reported. */
    int lastFrameWidth() const { return lastWidth_; }

    int lastFrameHeight() const { return lastHeight_; }

    /** Frames whose pixels were entirely one colour — the signature of a capture that can
     *  see nothing, which is what an off-screen window can produce. */
    long long blankFrameCount() const { return blankFrames_; }

    std::string lastError();

private:
    struct Impl;
    Impl* impl_ = nullptr;
    volatile bool running_ = false;
    volatile long long frames_ = 0;
    volatile long long blankFrames_ = 0;
    volatile long long callbacks_ = 0;
    volatile long long emptyCallbacks_ = 0;
    volatile int lastWidth_ = 0;
    volatile int lastHeight_ = 0;
};
