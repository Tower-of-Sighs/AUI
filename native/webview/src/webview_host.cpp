#include "webview_host.h"

#include <objbase.h>
#include <wincodec.h>

#include <algorithm>
#include <cstring>

namespace {

const wchar_t* kWindowClass = L"ApricityUIWebViewOffscreen";
const UINT kHostWakeMessage = WM_APP + 1;

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
          userDataDir_(userDataDir) {}

WebViewHost::~WebViewHost() {
    stop();
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
        commands_.push_back(std::move(fn));
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

long WebViewHost::pollFrame(int* out, int* meta, int outCapacity) {
    std::lock_guard<std::mutex> lock(frameMutex_);
    if (frameSeq_ == 0 || frameSeq_ == consumedSeq_) {
        return 0;
    }
    if (meta != nullptr) {
        meta[0] = frameWidth_;
        meta[1] = frameHeight_;
    }
    const size_t needed = static_cast<size_t>(frameWidth_) * static_cast<size_t>(frameHeight_);
    if (out == nullptr || static_cast<size_t>(outCapacity) < needed) {
        // Report the geometry but keep the frame pending so the caller can grow its
        // buffer and ask again.
        return -1;
    }
    std::memcpy(out, frameBytes_.data(), needed * sizeof(uint32_t));
    consumedSeq_ = frameSeq_;
    return static_cast<long>(frameSeq_);
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
    wchar_t buffer[512];
    swprintf_s(buffer,
               L"ready=%d nav=%d capture=%d done=%d rejected=%d decodeFail=%d bytes=%lld "
               L"roundTrip=%lldms decode=%lldms fps=%d stream=%lld/%lld/%lld/%lld@%dx%d raster=%dx%d format=%s "
               L"frame=%dx%d seq=%lld window=%dx%d | %s",
               ready_.load() ? 1 : 0, navigationCompleted_.load(),
               captureAttempts_.load(), captureCompleted_.load(), captureRejected_.load(),
               decodeFailures_.load(), lastPngBytes_.load(),
               lastRoundTripMs_.load(), lastDecodeMs_.load(), capturedPerSecond_.load(),
               stream_.callbackCount(), stream_.frameCount(), stream_.emptyCallbackCount(),
               stream_.blankFrameCount(), stream_.lastFrameWidth(), stream_.lastFrameHeight(),
               windowWidth_.load(), windowHeight_.load(),
               frameFormat_ == 2 ? (streamActive_ ? L"auto-stream"
                                                  : (autoUsesFast_ ? L"auto-jpeg" : L"auto-png"))
                                 : (frameFormat_ == 3 ? L"stream"
                                                      : (frameFormat_ == 1 ? L"jpeg" : L"png")),

               frameWidth_, frameHeight_, frameSeq_,
               windowWidth_.load(), windowHeight_.load(), status_.c_str());
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
    lastCaptureTick_ = GetTickCount64();
    return S_OK;
}

void WebViewHost::drainCommands() {
    std::deque<std::function<void()>> pending;
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        pending.swap(commands_);
    }
    for (auto& command : pending) {
        command();
    }
}

void WebViewHost::tickCapture() {
    if (!ready_ || webview_ == nullptr || capturing_) {
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
    capturing_ = true;
    ++captureAttempts_;
    captureStartTick_ = GetTickCount64();
    const COREWEBVIEW2_CAPTURE_PREVIEW_IMAGE_FORMAT format =
            resolveFrameFormat() == 1 ? COREWEBVIEW2_CAPTURE_PREVIEW_IMAGE_FORMAT_JPEG
                                      : COREWEBVIEW2_CAPTURE_PREVIEW_IMAGE_FORMAT_PNG;
    HRESULT hr = webview_->CapturePreview(
            format, stream.Get(),
            Microsoft::WRL::Callback<ICoreWebView2CapturePreviewCompletedHandler>(
                    [this, stream](HRESULT result) -> HRESULT {
                        lastCaptureHr_ = static_cast<long long>(result);
                        ++captureCompleted_;
                        lastRoundTripMs_ = static_cast<long long>(
                                GetTickCount64() - captureStartTick_);
                        ++rateWindowFrames_;
                        if (SUCCEEDED(result)) {
                            const ULONGLONG decodeStart = GetTickCount64();
                            decodeAndStore(stream.Get());
                            lastDecodeMs_ = static_cast<long long>(
                                    GetTickCount64() - decodeStart);
                        } else {
                            ++captureRejected_;
                        }
                        capturing_ = false;
                        return S_OK;
                    })
                    .Get());
    if (FAILED(hr)) {
        lastCaptureHr_ = static_cast<long long>(hr);
        ++captureRejected_;
        capturing_ = false;
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
 * callback thread, so it only touches state guarded by frameMutex_ and the rate counters.
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
    {
        std::lock_guard<std::mutex> lock(frameMutex_);
        if (frameWidth_ == width && frameHeight_ == height && frameBytes_.size() == bytes &&
            std::memcmp(frameBytes_.data(), rgba, bytes) == 0) {
            return;  // identical frame: keep the sequence stable
        }
        frameBytes_.assign(rgba, rgba + bytes);
        frameWidth_ = width;
        frameHeight_ = height;
        ++frameSeq_;
        lastPngBytes_ = static_cast<long long>(bytes);
        ++captureCompleted_;
    }
    ++rateWindowFrames_;
    lastPublishTick_ = static_cast<long long>(GetTickCount64());
    ++decisionWindowPublishes_;
    lastRoundTripMs_ = 0;
}

/**
 * Stores a decoded/grabbed frame and bumps the sequence when it differs from the last
 * published one.
 */
void WebViewHost::publishFrame(int width, int height, std::vector<uint8_t>& pixels) {
    std::lock_guard<std::mutex> lock(frameMutex_);
    const bool sameSize = frameWidth_ == width && frameHeight_ == height;
    if (sameSize && frameBytes_.size() == pixels.size() &&
        std::memcmp(frameBytes_.data(), pixels.data(), pixels.size()) == 0) {
        return;  // identical frame: keep the sequence stable
    }
    frameWidth_ = width;
    frameHeight_ = height;
    frameBytes_.swap(pixels);
    ++frameSeq_;
    lastPublishTick_ = static_cast<long long>(GetTickCount64());
    ++decisionWindowPublishes_;
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
    const ULONGLONG now = GetTickCount64();
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

void WebViewHost::decodeAndStore(IStream* stream) {
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
    {
        std::lock_guard<std::mutex> lock(frameMutex_);
        if (lastPayload_.size() == payloadBytes &&
            std::memcmp(lastPayload_.data(), payload_.data(), payloadBytes) == 0) {
            return;  // identical frame: keep the sequence stable
        }
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
    // same bytes, which is what lets the next capture skip all of this work.
    {
        std::lock_guard<std::mutex> lock(frameMutex_);
        const int previousWidth = frameWidth_;
        const int previousHeight = frameHeight_;
        lastPayload_.swap(payload_);
        if (frameWidth_ == static_cast<int>(width) && frameHeight_ == static_cast<int>(height)
                && frameBytes_.size() == pixels.size()
                && std::memcmp(frameBytes_.data(), pixels.data(), pixels.size()) == 0) {
            return;
        }
        frameWidth_ = previousWidth;
        frameHeight_ = previousHeight;
    }
    publishFrame(static_cast<int>(width), static_cast<int>(height), pixels);
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
    // Thread-scoped per-monitor DPI awareness: makes "N requested pixels" mean "N
    // captured pixels" while leaving the host process's own DPI awareness (set by
    // Minecraft) untouched.
    SetThreadDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);

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
        while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
        drainCommands();
        if (streamAbandon_.exchange(false)) {
            stream_.stop();
            streamActive_ = false;
            streamUnavailable_ = true;
            noteStatus(L"composition stream saw no content; fell back to the capture codecs");
        }
        {
            const ULONGLONG now = GetTickCount64();
            if (rateWindowStart_ == 0) {
                rateWindowStart_ = now;
            } else if (now - rateWindowStart_ >= 1000) {
                capturedPerSecond_ = rateWindowFrames_;
                rateWindowFrames_ = 0;
                rateWindowStart_ = now;
            }
        }
        if (ready_.load() && autoCapture_.load()) {
            ULONGLONG now = GetTickCount64();
            if (now - lastCaptureTick_ >= static_cast<ULONGLONG>(frameIntervalMs_.load())) {
                lastCaptureTick_ = now;
                tickCapture();
            }
        }
        // A short wait keeps the capture cadence tight; 8 ms of loop granularity was
        // capping the frame rate around 35 fps regardless of how fast the codec was.
        MsgWaitForMultipleObjectsEx(0, nullptr, (ready_.load() && autoCapture_.load()) ? 1 : 8,
                                    QS_ALLINPUT, 0);
    }

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
    threadId_ = 0;
}
