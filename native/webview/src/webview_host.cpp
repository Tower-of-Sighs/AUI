#include "webview_host.h"

#include <objbase.h>
#include <wincodec.h>

#include <algorithm>
#include <cstring>

namespace {

const wchar_t* kWindowClass = L"ApricityUIWebViewOffscreen";
const UINT kHostWakeMessage = WM_APP + 1;

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
                         const std::wstring& userDataDir)
        : width_(std::max(1, width)),
          height_(std::max(1, height)),
          transparent_(transparent),
          autoCapture_(autoCapture),
          frameIntervalMs_(std::max(8, frameIntervalMs)),
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
               L"ready=%d nav=%d navOk=%d capture=%d done=%d rejected=%d decodeFail=%d pngBytes=%lld "
               L"lastHr=0x%08llX frame=%dx%d seq=%lld window=%dx%d@%d,%d | %s",
               ready_.load() ? 1 : 0, navigationCompleted_.load(),
               navigationSucceeded_.load() ? 1 : 0, captureAttempts_.load(),
               captureCompleted_.load(), captureRejected_.load(), decodeFailures_.load(),
               lastPngBytes_.load(), static_cast<unsigned long long>(lastCaptureHr_.load()),
               frameWidth_, frameHeight_, frameSeq_,
               windowWidth_.load(), windowHeight_.load(), originX_.load(), originY_.load(),
               status_.c_str());
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
    hwnd_ = CreateWindowExW(WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW, kWindowClass, L"",
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
    Microsoft::WRL::ComPtr<IStream> stream;
    if (FAILED(CreateStreamOnHGlobal(nullptr, TRUE, &stream))) {
        return;
    }
    capturing_ = true;
    ++captureAttempts_;
    HRESULT hr = webview_->CapturePreview(
            COREWEBVIEW2_CAPTURE_PREVIEW_IMAGE_FORMAT_PNG, stream.Get(),
            Microsoft::WRL::Callback<ICoreWebView2CapturePreviewCompletedHandler>(
                    [this, stream](HRESULT result) -> HRESULT {
                        lastCaptureHr_ = static_cast<long long>(result);
                        ++captureCompleted_;
                        if (SUCCEEDED(result)) {
                            decodeAndStore(stream.Get());
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

void WebViewHost::decodeAndStore(IStream* stream) {
    LARGE_INTEGER origin{};
    stream->Seek(origin, STREAM_SEEK_SET, nullptr);

    STATSTG stats{};
    if (SUCCEEDED(stream->Stat(&stats, STATFLAG_NONAME))) {
        lastPngBytes_ = static_cast<long long>(stats.cbSize.QuadPart);
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
        FAILED(wicStream->InitializeFromIStream(stream))) {
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
    const UINT stride = width * 4;
    if (FAILED(converter->CopyPixels(nullptr, stride, static_cast<UINT>(pixels.size()),
                                     pixels.data()))) {
        ++decodeFailures_;
        noteStatus(L"WIC CopyPixels failed");
        return;
    }
    swapRedBlue(pixels);

    std::lock_guard<std::mutex> lock(frameMutex_);
    const bool sameSize = frameWidth_ == static_cast<int>(width) &&
                          frameHeight_ == static_cast<int>(height);
    if (sameSize && frameBytes_.size() == pixels.size() &&
        std::memcmp(frameBytes_.data(), pixels.data(), pixels.size()) == 0) {
        // Identical frame: keep the sequence stable so the caller skips the
        // texture upload entirely.
        return;
    }
    frameWidth_ = static_cast<int>(width);
    frameHeight_ = static_cast<int>(height);
    frameBytes_.swap(pixels);
    ++frameSeq_;
}

void WebViewHost::releaseAll() {
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
        if (ready_.load() && autoCapture_.load()) {
            ULONGLONG now = GetTickCount64();
            if (now - lastCaptureTick_ >= static_cast<ULONGLONG>(frameIntervalMs_.load())) {
                lastCaptureTick_ = now;
                tickCapture();
            }
        }
        MsgWaitForMultipleObjectsEx(0, nullptr, 8, QS_ALLINPUT, 0);
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
