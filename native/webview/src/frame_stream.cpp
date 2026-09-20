#include "frame_stream.h"

#include <d3d11.h>
#include <dxgi.h>
#include <windows.graphics.capture.interop.h>
#include <windows.graphics.directx.direct3d11.interop.h>

#include <winrt/base.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Graphics.Capture.h>
#include <winrt/Windows.Graphics.DirectX.h>
#include <winrt/Windows.Graphics.DirectX.Direct3D11.h>

#include <mutex>

namespace {

using namespace winrt::Windows::Graphics;
using namespace winrt::Windows::Graphics::Capture;
using namespace winrt::Windows::Graphics::DirectX;
using namespace winrt::Windows::Graphics::DirectX::Direct3D11;

constexpr int kPoolBuffers = 2;

std::string hrToText(HRESULT hr) {
    char buffer[32];
    sprintf_s(buffer, "0x%08lX", static_cast<unsigned long>(hr));
    return buffer;
}

}  // namespace

struct FrameStream::Impl {
    FrameStream* owner = nullptr;
    HWND window = nullptr;
    int width = 0;
    int height = 0;
    Sink sink;

    winrt::com_ptr<ID3D11Device> device;
    winrt::com_ptr<ID3D11DeviceContext> context;
    IDirect3DDevice direct3DDevice{nullptr};
    GraphicsCaptureItem item{nullptr};
    Direct3D11CaptureFramePool pool{nullptr};
    GraphicsCaptureSession session{nullptr};
    winrt::com_ptr<ID3D11Texture2D> staging;
    int stagingWidth = 0;
    int stagingHeight = 0;

    std::vector<uint8_t> buffer;
    std::mutex errorMutex;
    std::string error;
    winrt::event_token token{};

    void setError(const std::string& text) {
        std::lock_guard<std::mutex> lock(errorMutex);
        error = text;
    }

    /** Confirms the staging texture matches the frame size and is CPU readable. */
    bool ensureStaging(int width, int height) {
        if (staging && stagingWidth == width && stagingHeight == height) {
            return true;
        }
        D3D11_TEXTURE2D_DESC desc{};
        desc.Width = static_cast<UINT>(width);
        desc.Height = static_cast<UINT>(height);
        desc.MipLevels = 1;
        desc.ArraySize = 1;
        desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
        desc.SampleDesc.Count = 1;
        desc.Usage = D3D11_USAGE_STAGING;
        desc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;
        staging = nullptr;
        if (FAILED(device->CreateTexture2D(&desc, nullptr, staging.put()))) {
            return false;
        }
        stagingWidth = width;
        stagingHeight = height;
        return true;
    }

    void onFrame(const Direct3D11CaptureFramePool& sender, const winrt::Windows::Foundation::IInspectable&) {
        if (owner == nullptr || !owner->running_) {
            return;
        }
        ++owner->callbacks_;
        Direct3D11CaptureFrame frame{nullptr};
        try {
            frame = sender.TryGetNextFrame();
        } catch (const winrt::hresult_error& exception) {
            setError("TryGetNextFrame: " + hrToText(exception.code().value));
            return;
        }
        if (frame == nullptr) {
            ++owner->emptyCallbacks_;
            return;
        }

        const auto contentSize = frame.ContentSize();
        owner->lastWidth_ = contentSize.Width;
        owner->lastHeight_ = contentSize.Height;
        const int width = contentSize.Width;
        const int height = contentSize.Height;
        if (width <= 0 || height <= 0) {
            return;
        }

        winrt::com_ptr<ID3D11Texture2D> source;
        try {
            auto access = frame.Surface().as<
                    ::Windows::Graphics::DirectX::Direct3D11::IDirect3DDxgiInterfaceAccess>();
            if (FAILED(access->GetInterface(__uuidof(ID3D11Texture2D), source.put_void()))) {
                setError("frame surface has no D3D11 texture");
                return;
            }
        } catch (const winrt::hresult_error& exception) {
            setError("frame surface: " + hrToText(exception.code().value));
            return;
        }

        if (!ensureStaging(width, height)) {
            setError("staging texture creation failed");
            return;
        }

        // Copy the GPU texture into a CPU-readable one and map it. This is the only
        // per-frame GPU sync in the whole path.
        context->CopyResource(staging.get(), source.get());
        D3D11_MAPPED_SUBRESOURCE mapped{};
        if (FAILED(context->Map(staging.get(), 0, D3D11_MAP_READ, 0, &mapped))) {
            setError("staging map failed");
            return;
        }

        const size_t count = static_cast<size_t>(width) * static_cast<size_t>(height);
        buffer.resize(count * 4);
        const uint8_t* rows = static_cast<const uint8_t*>(mapped.pData);
        bool blank = true;
        uint8_t first[4] = {0, 0, 0, 0};
        for (int y = 0; y < height; ++y) {
            const uint8_t* src = rows + static_cast<size_t>(y) * mapped.RowPitch;
            uint8_t* dst = buffer.data() + static_cast<size_t>(y) * width * 4;
            for (int x = 0; x < width; ++x) {
                // B,G,R,A -> R,G,B,A, which is what NativeImage expects on little-endian.
                const uint8_t b = src[x * 4 + 0];
                const uint8_t g = src[x * 4 + 1];
                const uint8_t r = src[x * 4 + 2];
                dst[x * 4 + 0] = r;
                dst[x * 4 + 1] = g;
                dst[x * 4 + 2] = b;
                dst[x * 4 + 3] = 0xFF;
                if (blank) {
                    if (y == 0 && x == 0) {
                        first[0] = r;
                        first[1] = g;
                        first[2] = b;
                    } else if (r != first[0] || g != first[1] || b != first[2]) {
                        blank = false;
                    }
                }
            }
        }
        context->Unmap(staging.get(), 0);

        if (blank) {
            ++owner->blankFrames_;
            setError("frame is a single flat colour (capture cannot see the window content)");
        }
        ++owner->frames_;
        sink(width, height, buffer.data(), buffer.size());
    }
};

FrameStream::FrameStream() : impl_(new Impl()) {
    impl_->owner = this;
}

FrameStream::~FrameStream() {
    stop();
    delete impl_;
}

bool FrameStream::isSupported() {
    try {
        return GraphicsCaptureSession::IsSupported();
    } catch (...) {
        return false;
    }
}

bool FrameStream::start(HWND window, int width, int height, Sink sink) {
    if (running_) {
        return true;
    }
    impl_->window = window;
    impl_->sink = std::move(sink);
    impl_->setError("");

    const D3D_FEATURE_LEVEL levels[] = {D3D_FEATURE_LEVEL_11_1, D3D_FEATURE_LEVEL_11_0};
    HRESULT hr = D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr,
                                   D3D11_CREATE_DEVICE_BGRA_SUPPORT, levels,
                                   static_cast<UINT>(std::size(levels)), D3D11_SDK_VERSION,
                                   impl_->device.put(), nullptr, impl_->context.put());
    if (FAILED(hr)) {
        impl_->setError("D3D11CreateDevice: " + hrToText(hr));
        return false;
    }

    auto dxgiDevice = impl_->device.as<IDXGIDevice>();
    hr = CreateDirect3D11DeviceFromDXGIDevice(
            dxgiDevice.get(),
            reinterpret_cast<IInspectable**>(winrt::put_abi(impl_->direct3DDevice)));
    if (FAILED(hr) || impl_->direct3DDevice == nullptr) {
        impl_->setError("CreateDirect3D11DeviceFromDXGIDevice: " + hrToText(hr));
        return false;
    }

    try {
        auto factory = winrt::get_activation_factory<GraphicsCaptureItem, IGraphicsCaptureItemInterop>();
        hr = factory->CreateForWindow(
                window, winrt::guid_of<GraphicsCaptureItem>(), winrt::put_abi(impl_->item));
        if (FAILED(hr) || impl_->item == nullptr) {
            impl_->setError("IGraphicsCaptureItemInterop::CreateForWindow: " + hrToText(hr));
            return false;
        }
    } catch (const winrt::hresult_error& exception) {
        impl_->setError("capture item: " + hrToText(exception.code().value));
        return false;
    }

    if (!resize(width, height)) {
        return false;
    }
    running_ = true;
    return true;
}

bool FrameStream::resize(int width, int height) {
    if (impl_ == nullptr || impl_->item == nullptr) {
        return false;
    }
    const int safeWidth = width < 1 ? 1 : width;
    const int safeHeight = height < 1 ? 1 : height;
    if (impl_->pool != nullptr && impl_->width == safeWidth && impl_->height == safeHeight) {
        return true;
    }
    try {
        if (impl_->pool == nullptr) {
            impl_->pool = Direct3D11CaptureFramePool::CreateFreeThreaded(
                    impl_->direct3DDevice, DirectXPixelFormat::B8G8R8A8UIntNormalized,
                    kPoolBuffers, {safeWidth, safeHeight});
            impl_->token = impl_->pool.FrameArrived({impl_, &Impl::onFrame});
            impl_->session = impl_->pool.CreateCaptureSession(impl_->item);
            impl_->session.IsCursorCaptureEnabled(false);
            try {
                impl_->session.IsBorderRequired(false);
            } catch (const winrt::hresult_error&) {
                // Needs Windows 11 / the borderless capability; the border is invisible for
                // a window nobody can see anyway.
            }
            impl_->session.StartCapture();
        } else {
            impl_->pool.Recreate(impl_->direct3DDevice, DirectXPixelFormat::B8G8R8A8UIntNormalized,
                                 kPoolBuffers, {safeWidth, safeHeight});
            impl_->staging = nullptr;
            impl_->stagingWidth = 0;
            impl_->stagingHeight = 0;
        }
    } catch (const winrt::hresult_error& exception) {
        impl_->setError("frame pool: " + hrToText(exception.code().value));
        return false;
    }
    impl_->width = safeWidth;
    impl_->height = safeHeight;
    return true;
}

void FrameStream::stop() {
    running_ = false;
    if (impl_ == nullptr) {
        return;
    }
    try {
        if (impl_->session != nullptr) {
            impl_->session.Close();
            impl_->session = nullptr;
        }
        if (impl_->pool != nullptr) {
            impl_->pool.FrameArrived(impl_->token);
            impl_->pool.Close();
            impl_->pool = nullptr;
        }
    } catch (const winrt::hresult_error&) {
        // Closing a session that is already gone is not interesting.
    }
    impl_->item = nullptr;
    impl_->staging = nullptr;
    impl_->stagingWidth = 0;
    impl_->stagingHeight = 0;
    impl_->direct3DDevice = nullptr;
    impl_->context = nullptr;
    impl_->device = nullptr;
}

std::string FrameStream::lastError() {
    std::lock_guard<std::mutex> lock(impl_->errorMutex);
    return impl_->error;
}
