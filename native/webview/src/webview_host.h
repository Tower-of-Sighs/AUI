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

#include <atomic>
#include <cstdint>
#include <deque>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
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
    void resize(int width, int height);
    void setZoom(double zoom);
    void setFrameInterval(int ms);
    void setAutoCapture(bool enabled);
    void focus(bool focused);
    void mouse(int kind, int virtualKeys, int mouseData, int x, int y);
    void eval(const std::wstring& script);
    /** Force one capture even when auto capture is off. */
    void requestCapture();

    /**
     * Copies the newest frame into {@code out} when one is pending.
     *
     * @param out         destination, RGBA byte order, row major; may be null
     * @param meta        receives {width, height} on success
     * @param outCapacity number of ints available in {@code out}
     * @return the frame sequence number, or 0 when nothing new is pending. Returns
     *         -1 when a frame is pending but {@code out} is missing or too small, in
     *         which case {@code meta} still describes the frame.
     */
    long pollFrame(int* out, int* meta, int outCapacity);

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
    bool createWindow(HINSTANCE instance);
    bool createEnvironment();
    HRESULT attachController(ICoreWebView2CompositionController* controller);
    void tickCapture();
    void decodeAndStore(IStream* stream);
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

    std::mutex commandMutex_;
    std::deque<std::function<void()>> commands_;

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
    bool capturing_ = false;
    ULONGLONG lastCaptureTick_ = 0;

    // frame state, shared with pollFrame
    std::mutex frameMutex_;
    std::vector<uint8_t> frameBytes_;
    int frameWidth_ = 0;
    int frameHeight_ = 0;
    long long frameSeq_ = 0;
    long long consumedSeq_ = 0;

    std::mutex errorMutex_;
    std::wstring lastError_;
    std::wstring status_;

    // diagnostics
    std::atomic<int> captureAttempts_{0};
    std::atomic<int> captureCompleted_{0};
    std::atomic<int> captureRejected_{0};
    std::atomic<int> decodeFailures_{0};
    std::atomic<long long> lastPngBytes_{0};
    std::atomic<long long> lastCaptureHr_{0};
    std::atomic<int> navigationCompleted_{0};
    std::atomic<bool> navigationSucceeded_{false};
};
