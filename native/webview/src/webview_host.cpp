#include "webview_host.h"

#include <objbase.h>
#include <timeapi.h>
#include <wincodec.h>

#include <algorithm>
#include <chrono>
#include <cstring>

namespace {

const wchar_t* kWindowClass = L"ApricityUIWebViewOffscreen";
const UINT kHostWakeMessage = WM_APP + 1;

/**
 * Millisecond clock with sub-tick resolution.
 *
 * GetTickCount64 is quantised to the system timer tick (15.6 ms by default), which caps the
 * capture cadence at roughly two ticks per frame no matter how fast the codec is: a 16 ms
 * interval was really a ~31 ms one. steady_clock reads the performance counter instead.
 */
uint64_t nowMs() {
    return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count());
}

// Auto codec tuning: look at published frames over a short window; go fast once the page
// keeps changing, fall back to lossless after it has been quiet for a while.
const ULONGLONG AUTO_WINDOW_MS = 250;
const int AUTO_PUBLISHES_TO_GO_FAST = 2;
const ULONGLONG AUTO_QUIET_MS = 1500;

// Pixels come back from WIC as BGRA; NativeImage on little-endian wants the
// bytes in R,G,B,A order, which is exactly a C-style ABGR packed int.
void swapRedBlue(std::vector<uint8_t>& pixels) {
    for (size_t i = 0; i + 3 < pixels.size(); i += 4) {
        std::swap(pixels[i], pixels[i + 2]);
    }
}

}  // namespace

WebViewHost::WebViewHost(int width,
                         int height,
                         bool transparent,
                         bool autoCapture,
                         int frameIntervalMs,
                         int frameFormat,
                         const std::wstring& userDataDir)
        : width_(std::max(1, width)),
          height_(std::max(1, height)),
          transparent_(transparent),
          autoCapture_(autoCapture),
          frameIntervalMs_(std::max(8, frameIntervalMs)),
          frameFormat_(frameFormat < 0 || frameFormat > 3 ? 3 : frameFormat),
          userDataDir_(userDataDir) {
    // The update section is created up front so the reader can map it as soon as the view
    // exists; a view that never paints then simply leaves it empty.
    if (!channel_.open(width_, height_)) {
        setError(L"update channel could not be created");
    }
}

WebViewHost::~WebViewHost() {
    stop();
    // Reader mappings outlive the host thread by design; drop them only once nothing can be
    // writing any more.
    {
        std::lock_guard<std::mutex> lock(channelViewMutex_);
        for (void* view : channelViews_) {
            UnmapViewOfFile(view);
        }
        channelViews_.clear();
    }
    channel_.close();
}

void WebViewHost::addChannelView(void* view) {
    if (view == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(channelViewMutex_);
    channelViews_.push_back(view);
}

bool WebViewHost::start(unsigned long timeoutMs) {
    if (running_.exchange(true)) {
        return isReady();
    }
    readyEvent_ = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (readyEvent_ == nullptr) {
        running_ = false;
        return false;
    }
    thread_ = std::thread(&WebViewHost::threadMain, this);
    DWORD wait = WaitForSingleObject(readyEvent_, timeoutMs == 0 ? INFINITE : timeoutMs);
    if (wait != WAIT_OBJECT_0) {
        setError(L"timed out waiting for the WebView2 controller");
        stop();
        return false;
    }
    return isReady();
}

void WebViewHost::stop() {
    if (!running_.exchange(false)) {
        return;
    }
    if (threadId_ != 0) {
        PostThreadMessageW(threadId_, kHostWakeMessage, 0, 0);
    }
    if (thread_.joinable()) {
        thread_.join();
    }
    if (readyEvent_ != nullptr) {
        CloseHandle(readyEvent_);
        readyEvent_ = nullptr;
    }
}

void WebViewHost::post(std::function<void()> fn) {
    if (!running_.load()) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        commands_.push_back(Command{std::move(fn), nowMs()});
    }
    if (threadId_ != 0) {
        PostThreadMessageW(threadId_, kHostWakeMessage, 0, 0);
    }
}

void WebViewHost::navigate(const std::wstring& url) {
    post([this, url] {
        pendingUrl_ = url;
        if (webview_) {
            webview_->Navigate(url.c_str());
        }
    });
}

void WebViewHost::setBoundsAndZoom(int width, int height, double zoom) {
    post([this, width, height, zoom] {
        width_ = std::max(1, width);
        height_ = std::max(1, height);
        windowWidth_ = width_;
        windowHeight_ = height_;
        if (hwnd_ != nullptr) {
            SetWindowPos(hwnd_, nullptr, originX_.load(), originY_.load(), width_, height_,
                         SWP_NOZORDER | SWP_NOACTIVATE);
        }
        if (streamActive_) {
            stream_.resize(width_, height_);
        }
        if (controller_ == nullptr) {
            return;
        }
        RECT bounds{0, 0, width_, height_};
        // Bounds and zoom in one call: setting them separately would let the page lay out
        // once against a mismatched viewport.
        if (FAILED(controller_->SetBoundsAndZoomFactor(bounds, zoom))) {
            controller_->put_Bounds(bounds);
            controller_->put_ZoomFactor(zoom);
        }
    });
}

void WebViewHost::setFrameInterval(int ms) {
    frameIntervalMs_ = std::max(8, ms);
}

void WebViewHost::setAutoCapture(bool enabled) {
    autoCapture_ = enabled;
}

void WebViewHost::setFrameFormat(int format) {
    post([this, format] { frameFormat_ = format < 0 || format > 3 ? 3 : format; });
}

void WebViewHost::focus(bool focused) {
    post([this, focused] {
        if (!controller_) {
            return;
        }
        if (focused) {
            controller_->MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC);
        }
    });
}

void WebViewHost::mouse(int kind, int virtualKeys, int mouseData, int x, int y) {
    if (kind == AUI_WEBVIEW_MOUSE_MOVE) {
        // Keep only the newest position and let the host thread forward it once: a pointer
        // sample that is already stale by the time the browser reads it is pure latency, and
        // a drag produces far more of them than the browser can use.
        pendingMouseX_ = x;
        pendingMouseY_ = y;
        pendingMouseKeys_ = virtualKeys;
        if (pendingMouseMove_.exchange(true)) {
            ++coalescedMouseMoves_;
            return;
        }
        if (threadId_ != 0) {
            PostThreadMessageW(threadId_, kHostWakeMessage, 0, 0);
        }
        return;
    }
    if (kind == AUI_WEBVIEW_MOUSE_LEAVE) {
        // A queued move must not be forwarded after the pointer has left, or the page keeps
        // its hover state; leave wins.
        pendingMouseMove_ = false;
    }
    post([this, kind, virtualKeys, mouseData, x, y] {
        if (!compositionController_) {
            return;
        }
        POINT point{x, y};
        compositionController_->SendMouseInput(
                static_cast<COREWEBVIEW2_MOUSE_EVENT_KIND>(kind),
                static_cast<COREWEBVIEW2_MOUSE_EVENT_VIRTUAL_KEYS>(virtualKeys),
                static_cast<UINT32>(mouseData),
                point);
    });
}

void WebViewHost::flushPendingMouseMove() {
    if (!pendingMouseMove_.exchange(false)) {
        return;
    }
    if (!compositionController_) {
        return;
    }
    POINT point{pendingMouseX_.load(), pendingMouseY_.load()};
    compositionController_->SendMouseInput(
            static_cast<COREWEBVIEW2_MOUSE_EVENT_KIND>(AUI_WEBVIEW_MOUSE_MOVE),
            static_cast<COREWEBVIEW2_MOUSE_EVENT_VIRTUAL_KEYS>(pendingMouseKeys_.load()),
            0, point);
}

void WebViewHost::eval(const std::wstring& script) {
    post([this, script] {
        if (webview_) {
            webview_->ExecuteScript(script.c_str(), nullptr);
        }
    });
}

void WebViewHost::requestCapture() {
    post([this] { tickCapture(); });
}

bool WebViewHost::publishCanvas(const uint8_t* pixels, int width, int height) {
    std::lock_guard<std::mutex> lock(publishMutex_);
    if (pixels == nullptr || width <= 0 || height <= 0 || !channel_.isOpen()) {
        return false;
    }
    const bool published = channel_.publish(pixels, width, height);
    if (published) {
        lastPublishTick_ = static_cast<long long>(nowMs());
        ++decisionWindowPublishes_;
    }
    return published;
}

std::wstring WebViewHost::lastError() {
    std::lock_guard<std::mutex> lock(errorMutex_);
    return lastError_;
}

void WebViewHost::setError(const std::wstring& message) {
    std::lock_guard<std::mutex> lock(errorMutex_);
    lastError_ = message;
}

void WebViewHost::noteStatus(const std::wstring& message) {
    std::lock_guard<std::mutex> lock(errorMutex_);
    status_ = message;
}

std::wstring WebViewHost::statusText() {
    std::lock_guard<std::mutex> lock(errorMutex_);
    wchar_t buffer[1024];
    swprintf_s(buffer,
               L"ready=%d nav=%d capture=%d done=%d rejected=%d stale=%d decodeFail=%d bytes=%lld "
               L"roundTrip=%lldms decode=%lldms period=%lldms loop=%dHz fps=%d "
               L"cmd=%lld/%lldms pending=%d dropped=%d coalesced=%d "
               L"stream=%lld/%lld/%lld/%lld@%dx%d raster=%dx%d format=%s "
               L"window=%dx%d | %s | %s",
               ready_.load() ? 1 : 0, navigationCompleted_.load(),
               captureAttempts_.load(), captureCompleted_.load(), captureRejected_.load(),
               staleCaptures_.load(), decodeFailures_.load(), lastPngBytes_.load(),
               lastRoundTripMs_.load(), lastDecodeMs_.load(), lastCapturePeriodMs_.load(),
               loopHz_.load(), capturedPerSecond_.load(),
               lastCommandLatencyMs_.load(), maxCommandLatencyMs_.load(),
               pendingDecodes_.load(), droppedDecodes_.load(), coalescedMouseMoves_.load(),
               stream_.callbackCount(), stream_.frameCount(), stream_.emptyCallbackCount(),
               stream_.blankFrameCount(), stream_.lastFrameWidth(), stream_.lastFrameHeight(),
               windowWidth_.load(), windowHeight_.load(),
               frameFormat_ == 2 ? (streamActive_ ? L"auto-stream"
                                                  : (autoUsesFast_ ? L"auto-jpeg" : L"auto-png"))
                                 : (frameFormat_ == 3 ? L"stream"
                                                      : (frameFormat_ == 1 ? L"jpeg" : L"png")),
               windowWidth_.load(), windowHeight_.load(),
               channel_.statusText().c_str(), status_.c_str());
    return std::wstring(buffer);
}

LRESULT CALLBACK WebViewHost::windowProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    return DefWindowProcW(hwnd, message, wParam, lParam);
}

bool WebViewHost::createWindow(HINSTANCE instance) {
    static std::atomic<bool> registered{false};
    if (!registered.exchange(true)) {
        WNDCLASSEXW wc{};
        wc.cbSize = sizeof(wc);
        wc.lpfnWndProc = &WebViewHost::windowProc;
        wc.hInstance = instance;
        wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
        wc.lpszClassName = kWindowClass;
        if (RegisterClassExW(&wc) == 0 && GetLastError() != ERROR_CLASS_ALREADY_EXISTS) {
            setError(L"RegisterClassExW failed");
            return false;
        }
    }
    // Parked off the desktop: the window stays "visible" to the compositor (so the
    // renderer keeps painting and CapturePreview returns real pixels) while the user
    // never sees it. AUI_WEBVIEW_X/AUI_WEBVIEW_Y override the parking spot for
    // diagnosing compositor throttling.
    int originX = -32000;
    int originY = -32000;
    wchar_t envBuffer[32];
    if (GetEnvironmentVariableW(L"AUI_WEBVIEW_X", envBuffer, 32) > 0) {
        originX = _wtoi(envBuffer);
    }
    if (GetEnvironmentVariableW(L"AUI_WEBVIEW_Y", envBuffer, 32) > 0) {
        originY = _wtoi(envBuffer);
    }
    originX_ = originX;
    originY_ = originY;
    windowWidth_ = width_;
    windowHeight_ = height_;
    // AUI_WEBVIEW_TOPMOST is a diagnostic knob: the composition stream cannot see a window
    // that something else covers, so this is how to tell "occluded" apart from "capturable
    // content is not exposed at all".
    DWORD exStyle = WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW;
    if (GetEnvironmentVariableW(L"AUI_WEBVIEW_TOPMOST", envBuffer, 32) > 0 && _wtoi(envBuffer) != 0) {
        exStyle |= WS_EX_TOPMOST;
    }
    hwnd_ = CreateWindowExW(exStyle, kWindowClass, L"",
                            WS_POPUP, originX_, originY_, width_, height_,
                            nullptr, nullptr, instance, nullptr);
    if (hwnd_ == nullptr) {
        setError(L"CreateWindowExW failed");
        return false;
    }
    ShowWindow(hwnd_, SW_SHOWNOACTIVATE);
    UpdateWindow(hwnd_);
    return true;
}

bool WebViewHost::createEnvironment() {
    auto options = Microsoft::WRL::Make<CoreWebView2EnvironmentOptions>();
    // The host window is parked off the desktop, which Windows reports as occluded;
    // Chromium then stops compositing and CapturePreview keeps returning the last
    // presented frame. These are the standard switches for offscreen capture.
    //
    // The device scale factor is pinned to 1 so the raster size is exactly the bounds in
    // pixels; the host Java side then drives ZoomFactor itself, which is what makes the
    // page's CSS viewport equal the element's content box (bounds / zoom) with a matching
    // devicePixelRatio. Leaving the scale factor automatic would make both depend on the
    // monitor and break that.
    options->put_AdditionalBrowserArguments(
            L"--disable-features=CalculateNativeWinOcclusion,msEdgeAutofill"
            L" --disable-background-timer-throttling"
            L" --disable-backgrounding-occluded-windows"
            L" --disable-renderer-backgrounding"
            L" --force-device-scale-factor=1");
    const wchar_t* userData = userDataDir_.empty() ? nullptr : userDataDir_.c_str();

    HRESULT hr = CreateCoreWebView2EnvironmentWithOptions(
            nullptr, userData, options.Get(),
            Microsoft::WRL::Callback<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>(
                    [this](HRESULT result, ICoreWebView2Environment* environment) -> HRESULT {
                        if (FAILED(result) || environment == nullptr) {
                            setError(L"CreateCoreWebView2EnvironmentWithOptions failed");
                            initFailed_ = true;
                            SetEvent(readyEvent_);
                            return S_OK;
                        }
                        environment_ = environment;
                        Microsoft::WRL::ComPtr<ICoreWebView2Environment3> environment3;
                        if (FAILED(environment_->QueryInterface(IID_PPV_ARGS(&environment3)))) {
                            setError(L"WebView2 runtime is too old for offscreen hosting");
                            initFailed_ = true;
                            SetEvent(readyEvent_);
                            return S_OK;
                        }
                        HRESULT attach = environment3->CreateCoreWebView2CompositionController(
                                hwnd_,
                                Microsoft::WRL::Callback<ICoreWebView2CreateCoreWebView2CompositionControllerCompletedHandler>(
                                        [this](HRESULT controllerResult,
                                               ICoreWebView2CompositionController* controller) -> HRESULT {
                                            if (FAILED(controllerResult) || controller == nullptr) {
                                                setError(L"CreateCoreWebView2CompositionController failed");
                                                initFailed_ = true;
                                            } else {
                                                HRESULT attached = attachController(controller);
                                                if (FAILED(attached)) {
                                                    initFailed_ = true;
                                                }
                                            }
                                            SetEvent(readyEvent_);
                                            return S_OK;
                                        })
                                        .Get());
                        if (FAILED(attach)) {
                            setError(L"CreateCoreWebView2CompositionController call failed");
                            initFailed_ = true;
                            SetEvent(readyEvent_);
                        }
                        return S_OK;
                    })
                    .Get());

    if (FAILED(hr)) {
        setError(L"WebView2 runtime is not installed");
        return false;
    }
    return true;
}

HRESULT WebViewHost::attachController(ICoreWebView2CompositionController* compositionController) {
    compositionController_ = compositionController;
    // The composition controller implements ICoreWebView2Controller as well; the
    // controller half owns bounds/visibility/focus while the composition half adds
    // the visual target and mouse injection.
    HRESULT hr = compositionController_.As(&controller_);
    if (FAILED(hr) || controller_ == nullptr) {
        setError(L"composition controller has no ICoreWebView2Controller");
        return FAILED(hr) ? hr : E_NOINTERFACE;
    }

    // DirectComposition gives the composition controller a visual tree to draw
    // into; without a root visual it renders nothing.
    hr = DCompositionCreateDevice(nullptr, IID_PPV_ARGS(&dcompDevice_));
    if (FAILED(hr)) {
        setError(L"DCompositionCreateDevice failed");
        return hr;
    }
    hr = dcompDevice_->CreateTargetForHwnd(hwnd_, TRUE, &dcompTarget_);
    if (FAILED(hr)) {
        setError(L"CreateTargetForHwnd failed");
        return hr;
    }
    hr = dcompDevice_->CreateVisual(&dcompVisual_);
    if (FAILED(hr)) {
        setError(L"CreateVisual failed");
        return hr;
    }
    dcompTarget_->SetRoot(dcompVisual_.Get());
    dcompDevice_->Commit();
    hr = compositionController_->put_RootVisualTarget(dcompVisual_.Get());
    if (FAILED(hr)) {
        setError(L"put_RootVisualTarget failed");
        return hr;
    }

    RECT bounds{0, 0, width_, height_};
    controller_->put_Bounds(bounds);
    controller_->put_IsVisible(TRUE);

    if (transparent_) {
        Microsoft::WRL::ComPtr<ICoreWebView2Controller2> controller2;
        if (SUCCEEDED(controller_.As(&controller2))) {
            COREWEBVIEW2_COLOR transparentColor{0, 0, 0, 0};
            controller2->put_DefaultBackgroundColor(transparentColor);
        }
    }

    hr = controller_->get_CoreWebView2(&webview_);
    if (FAILED(hr)) {
        setError(L"get_CoreWebView2 failed");
        return hr;
    }

    Microsoft::WRL::ComPtr<ICoreWebView2Settings> settings;
    if (SUCCEEDED(webview_->get_Settings(&settings))) {
        settings->put_AreDefaultContextMenusEnabled(FALSE);
        settings->put_IsStatusBarEnabled(FALSE);
        settings->put_AreDevToolsEnabled(FALSE);
        settings->put_IsZoomControlEnabled(TRUE);
    }

    EventRegistrationToken navigationToken{};
    webview_->add_NavigationCompleted(
            Microsoft::WRL::Callback<ICoreWebView2NavigationCompletedEventHandler>(
                    [this](ICoreWebView2*, ICoreWebView2NavigationCompletedEventArgs* args) -> HRESULT {
                        BOOL success = FALSE;
                        if (args != nullptr) {
                            args->get_IsSuccess(&success);
                        }
                        ++navigationCompleted_;
                        navigationSucceeded_ = success == TRUE;
                        return S_OK;
                    })
                    .Get(),
            &navigationToken);

    if (!pendingUrl_.empty()) {
        webview_->Navigate(pendingUrl_.c_str());
    }

    controller_->MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC);
    ready_ = true;
    lastCaptureTick_ = nowMs();
    return S_OK;
}

void WebViewHost::drainCommands() {
    std::deque<Command> pending;
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        pending.swap(commands_);
    }
    for (auto& command : pending) {
        // This is the number that matters for feel: how long pointer input sat in the queue
        // before the host thread got to it. It is large exactly while the thread is busy
        // decoding, which is why decoding no longer happens here.
        const long long latency = static_cast<long long>(nowMs() - command.queuedAt);
        lastCommandLatencyMs_ = latency;
        if (latency > maxCommandLatencyMs_.load()) {
            maxCommandLatencyMs_ = latency;
        }
        command.fn();
    }
}

void WebViewHost::tickCapture() {
    if (!ready_ || webview_ == nullptr || capturesInFlight_ >= kMaxCapturesInFlight) {
        return;
    }
    if (resolveFrameFormat() == 3) {
        // Frames arrive by callback; there is nothing to request.
        if (!ensureStream()) {
            if (frameFormat_ == 3) {
                ++captureRejected_;
            } else {
                frameFormat_ = 2;  // auto: fall back to the codec path on the next tick
            }
        }
        return;
    }
    Microsoft::WRL::ComPtr<IStream> stream;
    if (FAILED(CreateStreamOnHGlobal(nullptr, TRUE, &stream))) {
        return;
    }
    ++capturesInFlight_;
    const uint64_t captureId = ++captureSequence_;
    uint64_t startTick = 0;
    ++captureAttempts_;
    {
        const uint64_t now = nowMs();
        if (lastCaptureStartTick_ != 0) {
            lastCapturePeriodMs_ = static_cast<long long>(now - lastCaptureStartTick_);
        }
        lastCaptureStartTick_ = now;
        startTick = now;
    }
    const COREWEBVIEW2_CAPTURE_PREVIEW_IMAGE_FORMAT format =
            resolveFrameFormat() == 1 ? COREWEBVIEW2_CAPTURE_PREVIEW_IMAGE_FORMAT_JPEG
                                      : COREWEBVIEW2_CAPTURE_PREVIEW_IMAGE_FORMAT_PNG;
    HRESULT hr = webview_->CapturePreview(
            format, stream.Get(),
            Microsoft::WRL::Callback<ICoreWebView2CapturePreviewCompletedHandler>(
                    [this, stream, captureId, startTick](HRESULT result) -> HRESULT {
                        lastCaptureHr_ = static_cast<long long>(result);
                        ++captureCompleted_;
                        lastRoundTripMs_ = static_cast<long long>(nowMs() - startTick);
                        ++rateWindowFrames_;
                        if (SUCCEEDED(result)) {
                            if (captureId > publishedCaptureSequence_) {
                                // Overlapping captures can complete out of order; an older
                                // frame published after a newer one would leave stale
                                // rectangles on the canvas, so only the newest one lands. The
                                // decision is made here, on the host thread, and the decoder
                                // then applies the survivors in that order.
                                publishedCaptureSequence_ = captureId;
                                enqueueDecode(stream, captureId);
                            } else {
                                // A newer capture already published; this one is stale, not
                                // failed, and dropping it is what keeps the canvas monotonic.
                                ++staleCaptures_;
                            }
                        } else {
                            ++captureRejected_;
                        }
                        --capturesInFlight_;
                        return S_OK;
                    })
                    .Get());
    if (FAILED(hr)) {
        lastCaptureHr_ = static_cast<long long>(hr);
        ++captureRejected_;
        --capturesInFlight_;
    }
}

/**
 * Grabs the window's composited content as raw BGRA.
 *
 * The WebView2 content is attached to this HWND through DirectComposition
 * (CreateTargetForHwnd), so Windows' own compositor has the finished picture for that
 * window. PrintWindow with PW_RENDERFULLCONTENT asks for exactly that, which gives us
 * pixels without any encode/decode round trip.
 */
bool WebViewHost::ensureStream() {
    if (streamActive_) {
        return true;
    }
    if (streamUnavailable_ || hwnd_ == nullptr) {
        return false;
    }
    if (!FrameStream::isSupported()) {
        streamUnavailable_ = true;
        noteStatus(L"composition stream not supported on this system");
        return false;
    }
    const bool started = stream_.start(
            hwnd_, width_, height_,
            [this](int frameWidth, int frameHeight, const uint8_t* rgba, size_t bytes) {
                publishRaw(frameWidth, frameHeight, rgba, bytes);
            });
    if (!started) {
        streamUnavailable_ = true;
        const std::string error = stream_.lastError();
        noteStatus(L"composition stream failed: " + std::wstring(error.begin(), error.end()));
        return false;
    }
    streamActive_ = true;
    noteStatus(L"composition stream running");
    return true;
}

/**
 * Publishes a frame produced by the composition stream. Called from the stream's own
 * callback thread, so it only touches the channel (guarded by the publish mutex) and the
 * rate counters.
 */
void WebViewHost::publishRaw(int width, int height, const uint8_t* rgba, size_t bytes) {
    if (width <= 0 || height <= 0 || rgba == nullptr || bytes == 0) {
        return;
    }
    if (stream_.blankFrameCount() > 0) {
        // Every frame the composition stream hands back is a single flat colour, which
        // means it cannot see this window's content at all: the WebView2 visual tree is
        // attached through DirectComposition and neither Windows.Graphics.Capture nor
        // PrintWindow reports it. Drop the frame (never publish it — it would show as a
        // blank rectangle) and ask the host thread to fall back to the capture codecs.
        streamAbandon_ = true;
        return;
    }
    if (publishCanvas(rgba, width, height)) {
        lastPngBytes_ = static_cast<long long>(bytes);
        ++captureCompleted_;
    }
    ++rateWindowFrames_;
}

/**
 * Picks the codec for the next capture.
 *
 * Auto stays lossless until frames keep changing, then uses the fast codec; the switching
 * has hysteresis so a page hovering around the threshold does not flip codecs (each switch
 * invalidates the payload baseline and costs one extra published frame).
 */
int WebViewHost::resolveFrameFormat() {
    if (frameFormat_ == 3 && streamUnavailable_) {
        return 0;  // nothing but the codecs left; the caller treats this as "no stream"
    }
    // Auto deliberately does not try mode 3. Measured on Windows 11 with this hosting mode,
    // a window capture of the WebView2 HWND returns a single flat-colour frame and then no
    // further callbacks — both while the window was parked off the desktop and while it was
    // on screen and topmost — so the DirectComposition visual is simply not exposed to
    // window capture. Mode 3 stays available as an explicit opt-in in case a future
    // Windows/WebView2 build changes that, but nothing selects it automatically.
    if (frameFormat_ != 2) {
        return frameFormat_;
    }
    const ULONGLONG now = nowMs();
    if (decisionWindowStart_ == 0) {
        decisionWindowStart_ = now;
    } else if (now - decisionWindowStart_ >= AUTO_WINDOW_MS) {
        if (decisionWindowPublishes_ >= AUTO_PUBLISHES_TO_GO_FAST) {
            autoUsesFast_ = true;
        } else if (autoUsesFast_ && now - lastPublishTick_ >= AUTO_QUIET_MS) {
            autoUsesFast_ = false;
        }
        decisionWindowStart_ = now;
        decisionWindowPublishes_ = 0;
    }
    return autoUsesFast_ ? 1 : 0;
}

void WebViewHost::decodeAndPublish(IStream* stream) {
    LARGE_INTEGER origin{};
    stream->Seek(origin, STREAM_SEEK_SET, nullptr);

    STATSTG stats{};
    size_t payloadBytes = 0;
    if (SUCCEEDED(stream->Stat(&stats, STATFLAG_NONAME))) {
        payloadBytes = static_cast<size_t>(stats.cbSize.QuadPart);
        lastPngBytes_ = static_cast<long long>(payloadBytes);
    }
    if (payloadBytes == 0) {
        ++decodeFailures_;
        noteStatus(L"capture produced no payload");
        return;
    }

    // Pull the encoded payload out of the stream once and compare it against the
    // previous one before doing any work. The encoder is deterministic for identical
    // content, so an unchanged page produces byte-identical payloads — which lets a
    // static page skip the decode, the per-pixel swap and the Java-side copy entirely.
    // This matters most with JPEG, where decoded pixels are never bit-identical.
    if (payload_.size() != payloadBytes) {
        payload_.resize(payloadBytes);
    }
    ULONG read = 0;
    if (FAILED(stream->Read(payload_.data(), static_cast<ULONG>(payloadBytes), &read)) ||
        read != payloadBytes) {
        ++decodeFailures_;
        noteStatus(L"capture stream read failed");
        return;
    }
    // An unchanged page re-encodes to identical bytes, which is where the whole per-frame
    // cost of a static view disappears. The shortcut is skipped while the reader is still
    // owed rectangles or has asked for a full refresh — a static page must still be able to
    // finish its own picture.
    if (lastPayload_.size() == payloadBytes &&
        std::memcmp(lastPayload_.data(), payload_.data(), payloadBytes) == 0 &&
        !channel_.hasDeferredRects() && !channel_.fullRefreshRequested()) {
        return;
    }

    Microsoft::WRL::ComPtr<IWICImagingFactory> factory;
    if (FAILED(CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
                                IID_PPV_ARGS(&factory)))) {
        ++decodeFailures_;
        noteStatus(L"WIC factory unavailable");
        return;
    }
    Microsoft::WRL::ComPtr<IWICStream> wicStream;
    if (FAILED(factory->CreateStream(&wicStream)) ||
        FAILED(wicStream->InitializeFromMemory(payload_.data(), static_cast<DWORD>(payloadBytes)))) {
        ++decodeFailures_;
        noteStatus(L"WIC stream init failed");
        return;
    }
    Microsoft::WRL::ComPtr<IWICBitmapDecoder> decoder;
    if (FAILED(factory->CreateDecoderFromStream(wicStream.Get(), nullptr,
                                                WICDecodeMetadataCacheOnDemand, &decoder))) {
        ++decodeFailures_;
        noteStatus(L"WIC decoder creation failed");
        return;
    }
    Microsoft::WRL::ComPtr<IWICBitmapFrameDecode> decoded;
    if (FAILED(decoder->GetFrame(0, &decoded))) {
        ++decodeFailures_;
        noteStatus(L"WIC GetFrame failed");
        return;
    }
    UINT width = 0;
    UINT height = 0;
    if (FAILED(decoded->GetSize(&width, &height)) || width == 0 || height == 0) {
        ++decodeFailures_;
        noteStatus(L"WIC frame has no size");
        return;
    }
    Microsoft::WRL::ComPtr<IWICFormatConverter> converter;
    if (FAILED(factory->CreateFormatConverter(&converter)) ||
        FAILED(converter->Initialize(decoded.Get(), GUID_WICPixelFormat32bppBGRA,
                                     WICBitmapDitherTypeNone, nullptr, 0.0,
                                     WICBitmapPaletteTypeCustom))) {
        ++decodeFailures_;
        noteStatus(L"WIC format conversion failed");
        return;
    }

    std::vector<uint8_t> pixels(static_cast<size_t>(width) * height * 4);
    rasterBytes_ = static_cast<int>(pixels.size());
    const UINT stride = width * 4;
    if (FAILED(converter->CopyPixels(nullptr, stride, static_cast<UINT>(pixels.size()),
                                     pixels.data()))) {
        ++decodeFailures_;
        noteStatus(L"WIC CopyPixels failed");
        return;
    }
    swapRedBlue(pixels);
    // Remember the encoded payload as the baseline: an unchanged page re-encodes to the
    // same bytes, which is what lets the next capture skip all of this work. The skip is
    // held back while the channel still owes the reader rectangles, otherwise a static page
    // would never get the rest of its update.
    lastPayload_.swap(payload_);
    publishCanvas(pixels.data(), static_cast<int>(width), static_cast<int>(height));
}

void WebViewHost::startDecodeThread() {
    decodeRunning_ = true;
    decodeThread_ = std::thread(&WebViewHost::decodeThreadMain, this);
}

void WebViewHost::stopDecodeThread() {
    {
        std::lock_guard<std::mutex> lock(decodeMutex_);
        decodeRunning_ = false;
    }
    decodeSignal_.notify_all();
    if (decodeThread_.joinable()) {
        decodeThread_.join();
    }
    {
        std::lock_guard<std::mutex> lock(decodeMutex_);
        decodeQueue_.clear();
    }
    pendingDecodes_ = 0;
}

void WebViewHost::enqueueDecode(Microsoft::WRL::ComPtr<IStream> stream, uint64_t captureId) {
    {
        std::lock_guard<std::mutex> lock(decodeMutex_);
        if (decodeQueue_.size() >= static_cast<size_t>(kMaxPendingDecodes)) {
            // The decoder is still behind: this frame would be applied far too late to be
            // worth the work, and the capture loop will be told to slow down anyway.
            ++droppedDecodes_;
            return;
        }
        decodeQueue_.emplace_back(std::move(stream), captureId);
        ++pendingDecodes_;
    }
    decodeSignal_.notify_one();
}

/**
 * Decodes and publishes off the UI thread.
 *
 * <p>WIC decoding, the channel byte-order swap, the tile diff and the shared-memory publish
 * all used to run inside the capture completion handler, which is the UI thread — the same
 * thread that pumps WebView2's messages and forwards pointer input. On a 1200x900 raster
 * that was 15-25 ms of message pump per frame, which is exactly what made dragging and
 * scrolling feel laggy. Nothing here touches COM objects owned by the UI thread.</p>
 */
void WebViewHost::decodeThreadMain() {
    // Below normal: the game's render thread must win any CPU contention with an iframe.
    SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_BELOW_NORMAL);
    // WIC needs an apartment on whichever thread creates its factory.
    const HRESULT com = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    while (true) {
        std::pair<Microsoft::WRL::ComPtr<IStream>, uint64_t> item;
        {
            std::unique_lock<std::mutex> lock(decodeMutex_);
            decodeSignal_.wait_for(lock, std::chrono::milliseconds(50),
                                   [this] { return !decodeQueue_.empty() || !decodeRunning_; });
            if (decodeQueue_.empty()) {
                if (!decodeRunning_) {
                    break;
                }
                continue;
            }
            item = std::move(decodeQueue_.front());
            decodeQueue_.pop_front();
        }
        const uint64_t start = nowMs();
        decodeAndPublish(item.first.Get());
        lastDecodeMs_ = static_cast<long long>(nowMs() - start);
        --pendingDecodes_;
    }
    if (SUCCEEDED(com)) {
        CoUninitialize();
    }
}

void WebViewHost::releaseAll() {
    if (streamActive_) {
        stream_.stop();
        streamActive_ = false;
    }
    webview_.Reset();
    if (controller_) {
        controller_->Close();
        controller_.Reset();
    }
    compositionController_.Reset();
    dcompVisual_.Reset();
    if (dcompTarget_) {
        dcompTarget_->SetRoot(nullptr);
        dcompTarget_.Reset();
    }
    if (dcompDevice_) {
        dcompDevice_->Commit();
        dcompDevice_.Reset();
    }
    environment_.Reset();
}

void WebViewHost::threadMain() {
    threadId_ = GetCurrentThreadId();
    // Raise the timer resolution for this thread only: every wait in the capture loop below
    // is rounded up to the system tick otherwise, which is the difference between a 32 fps
    // ceiling and whatever the codec can actually do. Paired with timeEndPeriod on the way
    // out so the process does not leave the machine with a raised timer.
    timeBeginPeriod(1);
    // Thread-scoped per-monitor DPI awareness: makes "N requested pixels" mean "N
    // captured pixels" while leaving the host process's own DPI awareness (set by
    // Minecraft) untouched.
    SetThreadDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    startDecodeThread();

    HINSTANCE instance = GetModuleHandleW(nullptr);
    if (!createWindow(instance)) {
        initFailed_ = true;
        SetEvent(readyEvent_);
    } else if (!createEnvironment()) {
        initFailed_ = true;
        SetEvent(readyEvent_);
    }

    MSG message{};
    while (running_.load()) {
        ++loopIterations_;
        while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
        drainCommands();
        flushPendingMouseMove();
        if (streamAbandon_.exchange(false)) {
            stream_.stop();
            streamActive_ = false;
            streamUnavailable_ = true;
            noteStatus(L"composition stream saw no content; fell back to the capture codecs");
        }
        {
            const ULONGLONG now = nowMs();
            if (rateWindowStart_ == 0) {
                rateWindowStart_ = now;
            } else if (now - rateWindowStart_ >= 1000) {
                capturedPerSecond_ = rateWindowFrames_;
                rateWindowFrames_ = 0;
                // Worst queue wait in the last second is the number that describes how the
                // view feels; a lifetime maximum would only ever grow.
                maxCommandLatencyMs_ = lastCommandLatencyMs_.load();
                loopHz_ = static_cast<int>(loopIterations_ - loopIterationsAtWindowStart_);
                loopIterationsAtWindowStart_ = loopIterations_;
                rateWindowStart_ = now;
            }
        }
        // The interval is a ceiling on the *request* rate, so it may only be consumed when a
        // capture can actually start: counting the ticks that land while one is in flight
        // added a whole interval to every frame (the callback already arrives tens of
        // milliseconds later, and waiting 8 ms past it was pure loss).
        // Only keep the pipeline as deep as the browser is serving cheaply. When a capture
        // already takes tens of milliseconds the extra requests just queue readbacks the GPU
        // has to work through — and in game that GPU is busy drawing the world.
        const long long latency = lastRoundTripMs_.load();
        const int depth = latency > 60 ? 2 : (latency > 45 ? 3 : kMaxCapturesInFlight);
        if (ready_.load() && autoCapture_.load()
                && capturesInFlight_ < depth
                && pendingDecodes_ < kMaxPendingDecodes) {
            const uint64_t now = nowMs();
            if (now - lastCaptureTick_ >= static_cast<uint64_t>(frameIntervalMs_.load())) {
                lastCaptureTick_ = now;
                tickCapture();
            }
        }
        // A short wait keeps the capture cadence tight. This only means ~1 ms because
        // timeBeginPeriod(1) is in effect for this thread: without it Windows rounds every
        // wait up to the 15.6 ms system tick, which is what pinned the capture loop to about
        // 32 fps however fast the codec was.
        MsgWaitForMultipleObjectsEx(0, nullptr, (ready_.load() && autoCapture_.load()) ? 1 : 8,
                                    QS_ALLINPUT, 0);
    }

    stopDecodeThread();
    releaseAll();
    if (hwnd_ != nullptr) {
        DestroyWindow(hwnd_);
        hwnd_ = nullptr;
    }
    // Give WebView2 a moment to finish tearing down before COM goes away.
    for (int i = 0; i < 10; ++i) {
        while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
        Sleep(5);
    }
    CoUninitialize();
    timeEndPeriod(1);
    threadId_ = 0;
}
