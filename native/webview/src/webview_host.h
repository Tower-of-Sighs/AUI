// ApricityUI offscreen WebView host.
//
// Owns one WebView2 instance rendered into a hidden (off-desktop) top-level window
// and exposes its pixels as a raw RGBA byte buffer, so the AUI renderer can treat
// it like any other texture-backed element.
#pragma once

#include <windows.h>
#include <dcomp.h>
#include <wrl.h>
#include <WebView2.h>
#include <WebView2EnvironmentOptions.h>

#include "frame_channel.h"
#include "frame_stream.h"

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

// Win32 message values, matching COREWEBVIEW2_MOUSE_EVENT_KIND one-to-one so the
// Java side can pass them straight through.
enum {
    AUI_WEBVIEW_MOUSE_MOVE = 512,
    AUI_WEBVIEW_MOUSE_LEFT_DOWN = 513,
    AUI_WEBVIEW_MOUSE_LEFT_UP = 514,
    AUI_WEBVIEW_MOUSE_RIGHT_DOWN = 516,
    AUI_WEBVIEW_MOUSE_RIGHT_UP = 517,
    AUI_WEBVIEW_MOUSE_MIDDLE_DOWN = 519,
    AUI_WEBVIEW_MOUSE_MIDDLE_UP = 520,
    AUI_WEBVIEW_MOUSE_WHEEL = 522,
    AUI_WEBVIEW_MOUSE_HWHEEL = 526,
    AUI_WEBVIEW_MOUSE_LEAVE = 675,

    AUI_WEBVIEW_VK_LEFT = 0x1,
    AUI_WEBVIEW_VK_RIGHT = 0x2,
    AUI_WEBVIEW_VK_SHIFT = 0x4,
    AUI_WEBVIEW_VK_CONTROL = 0x8,
    AUI_WEBVIEW_VK_MIDDLE = 0x10
};

class WebViewHost {
public:
    WebViewHost(int width,
                int height,
                bool transparent,
                bool autoCapture,
                int frameIntervalMs,
                int frameFormat,
                const std::wstring& userDataDir);
    ~WebViewHost();

    WebViewHost(const WebViewHost&) = delete;
    WebViewHost& operator=(const WebViewHost&) = delete;

    /** Spins up the host thread and blocks until the WebView2 controller is ready. */
    bool start(unsigned long timeoutMs);

    /** Tears the host down; safe to call more than once. */
    void stop();

    bool isReady() const { return ready_.load(); }

    // --- commands, safe from any thread -----------------------------------
    void navigate(const std::wstring& url);
    /** Raster size and page zoom applied together, mirroring SetBoundsAndZoomFactor. */
    void setBoundsAndZoom(int width, int height, double zoom);
    void setFrameInterval(int ms);
    void setAutoCapture(bool enabled);
    /**
     * Capture path: 0 = PNG, 1 = JPEG, 2 = auto, 3 = composition stream only.
     *
     * <p>{@code 3} streams the window's composited output as raw pixels: no encode/decode
     * round trip, and Windows calls back only when the content actually changes. {@code 2}
     * prefers that stream and falls back to the codec pair when it cannot start (unsupported
     * OS, or a window the compositor refuses to hand over). The fallback keeps the lossless
     * codec while the page is quiet — a static panel is encoded once and then de-duplicated,
     * so quality is free — and switches to the fast one once frames keep changing.</p>
     */
    void setFrameFormat(int format);
    void focus(bool focused);
    void mouse(int kind, int virtualKeys, int mouseData, int x, int y);
    void eval(const std::wstring& script);
    /** Force one capture even when auto capture is off. */
    void requestCapture();

    /**
     * Section backing the image update stream.
     *
     * <p>The reader maps this section itself and follows the packets; the host never pushes
     * pixels through JNI. Null when the section could not be created, in which case the view
     * simply never paints.</p>
     */
    HANDLE channelSection() const { return channel_.section(); }

    /** Size of the update section in bytes; the reader maps exactly this much. */
    size_t channelBytes() const { return channel_.sectionBytes(); }

    /** Registers a reader mapping so teardown can unmap it after the host thread is down. */
    void addChannelView(void* view);

    /** Last failure reported by the host thread; empty when healthy. */
    std::wstring lastError();

    /** Diagnostic snapshot: capture/navigation counters and the last HRESULT seen. */
    std::wstring statusText();

private:
    void noteStatus(const std::wstring& message);
    void setError(const std::wstring& message);
    void threadMain();
    void post(std::function<void()> fn);
    void drainCommands();

    // --- decode worker ------------------------------------------------------
    // WIC decoding, the channel byte order swap, the tile diff and the publish all happen off
    // the host thread: that thread is WebView2's UI thread, and every millisecond it spends
    // decoding is a millisecond the message pump is not delivering pointer input.
    void startDecodeThread();
    void stopDecodeThread();
    void decodeThreadMain();
    void enqueueDecode(Microsoft::WRL::ComPtr<IStream> stream, uint64_t captureId);
    /** Sends the newest coalesced pointer position, if one is pending. */
    void flushPendingMouseMove();
    bool createWindow(HINSTANCE instance);
    bool createEnvironment();
    HRESULT attachController(ICoreWebView2CompositionController* controller);
    void tickCapture();
    int resolveFrameFormat();
    /** Starts the raw composition stream if it is not running yet. */
    bool ensureStream();
    /** Hands one decoded canvas to the update channel. */
    bool publishCanvas(const uint8_t* pixels, int width, int height);
    /** Publishes a frame handed over by the stream callback; safe from any thread. */
    void publishRaw(int width, int height, const uint8_t* rgba, size_t bytes);
    void decodeAndPublish(IStream* stream);
    void releaseAll();

    static LRESULT CALLBACK windowProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam);

    // configuration
    int width_;
    int height_;
    bool transparent_;
    std::atomic<bool> autoCapture_;
    std::atomic<int> frameIntervalMs_;
    std::wstring userDataDir_;

    // thread plumbing
    std::thread thread_;
    DWORD threadId_ = 0;
    std::atomic<bool> running_{false};
    std::atomic<bool> ready_{false};
    HANDLE readyEvent_ = nullptr;
    std::atomic<bool> initFailed_{false};

    /** One queued command with the moment it was queued, for the latency diagnostic. */
    struct Command {
        std::function<void()> fn;
        uint64_t queuedAt;
    };

    std::mutex commandMutex_;
    std::deque<Command> commands_;

    // decode worker
    std::thread decodeThread_;
    std::mutex decodeMutex_;
    std::condition_variable decodeSignal_;
    std::deque<std::pair<Microsoft::WRL::ComPtr<IStream>, uint64_t>> decodeQueue_;
    bool decodeRunning_ = false;
    /** Decodes queued but not finished; also bounds how far capture may run ahead. */
    std::atomic<int> pendingDecodes_{0};

    /**
     * Pointer moves are coalesced to the newest position: Chromium only needs where the
     * cursor is now, and forwarding every intermediate sample is what made dragging feel
     * laggy. Buttons, wheel and leave keep their order and are never coalesced.
     */
    std::atomic<bool> pendingMouseMove_{false};
    std::atomic<int> pendingMouseX_{0};
    std::atomic<int> pendingMouseY_{0};
    std::atomic<int> pendingMouseKeys_{0};
    std::atomic<int> coalescedMouseMoves_{0};

    // window / COM, touched only from the host thread
    HWND hwnd_ = nullptr;
    std::atomic<int> originX_{-32000};
    std::atomic<int> originY_{-32000};
    std::atomic<int> windowWidth_{0};
    std::atomic<int> windowHeight_{0};
    Microsoft::WRL::ComPtr<ICoreWebView2Environment> environment_;
    Microsoft::WRL::ComPtr<ICoreWebView2Controller> controller_;
    Microsoft::WRL::ComPtr<ICoreWebView2CompositionController> compositionController_;
    Microsoft::WRL::ComPtr<ICoreWebView2> webview_;
    Microsoft::WRL::ComPtr<IDCompositionDevice> dcompDevice_;
    Microsoft::WRL::ComPtr<IDCompositionTarget> dcompTarget_;
    Microsoft::WRL::ComPtr<IDCompositionVisual> dcompVisual_;
    std::wstring pendingUrl_;

    // capture state, host thread only
    /**
     * Captures allowed in flight.
     *
     * <p>{@code CapturePreview} costs a fixed ~20-30 ms per call no matter how small the
     * raster is, most of it waiting on the browser process, so a second request is issued
     * while the first is still out. More than two only queues work the browser cannot reach
     * any sooner.</p>
     */
    static constexpr int kMaxCapturesInFlight = 4;
    /** Decodes allowed to queue behind the worker; past this a capture is not worth taking. */
    static constexpr int kMaxPendingDecodes = 4;
    int capturesInFlight_ = 0;
    /** Monotonic capture id; the completion handler publishes only the newest one. */
    uint64_t captureSequence_ = 0;
    uint64_t publishedCaptureSequence_ = 0;
    ULONGLONG lastCaptureTick_ = 0;
    // 0 = PNG, 1 = JPEG, 2 = auto, 3 = composition stream only.
    int frameFormat_ = 2;
    FrameStream stream_;
    bool streamActive_ = false;
    bool streamUnavailable_ = false;
    std::atomic<bool> streamAbandon_{false};
    bool autoUsesFast_ = false;
    std::atomic<long long> lastPublishTick_{0};
    ULONGLONG decisionWindowStart_ = 0;
    std::atomic<int> decisionWindowPublishes_{0};
    uint64_t lastCaptureStartTick_ = 0;
    uint64_t loopIterations_ = 0;
    uint64_t loopIterationsAtWindowStart_ = 0;
    ULONGLONG rateWindowStart_ = 0;
    int rateWindowFrames_ = 0;

    // frame state, shared with the reader through the section
    FrameChannel channel_;
    /** Serialises publishes: the capture callback and the host thread can both produce one. */
    std::mutex publishMutex_;
    std::vector<uint8_t> payload_;      // scratch, host thread only
    std::vector<uint8_t> lastPayload_;  // encoded payload of the last published frame
    std::vector<void*> channelViews_;   // reader mappings, unmapped at teardown
    std::mutex channelViewMutex_;

    std::mutex errorMutex_;
    std::wstring lastError_;
    std::wstring status_;

    // diagnostics
    std::atomic<int> captureAttempts_{0};
    std::atomic<int> captureCompleted_{0};
    std::atomic<int> captureRejected_{0};
    /** Captures that completed after a newer one had already published, and were dropped. */
    std::atomic<int> staleCaptures_{0};
    /** Queue wait of the last command, and the worst one in the current second. */
    std::atomic<long long> lastCommandLatencyMs_{0};
    std::atomic<long long> maxCommandLatencyMs_{0};
    /** Decodes dropped because the worker was still behind. */
    std::atomic<int> droppedDecodes_{0};
    std::atomic<int> decodeFailures_{0};
    std::atomic<long long> lastPngBytes_{0};
    std::atomic<long long> lastCaptureHr_{0};
    std::atomic<int> navigationCompleted_{0};
    std::atomic<bool> navigationSucceeded_{false};
    std::atomic<long long> lastRoundTripMs_{0};
    /** Time between the last two capture starts: the real capture cadence. */
    std::atomic<long long> lastCapturePeriodMs_{0};
    /** Host loop iterations per second, so a stalled loop is distinguishable from a slow codec. */
    std::atomic<int> loopHz_{0};
    std::atomic<long long> lastDecodeMs_{0};
    std::atomic<int> capturedPerSecond_{0};
    std::atomic<int> rasterBytes_{0};
};
