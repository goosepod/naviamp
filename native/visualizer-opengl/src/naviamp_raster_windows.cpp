#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <d3d11.h>
#include <d2d1_1.h>
#include <dcomp.h>
#include <wincodec.h>
#include <shlwapi.h>
#include <wrl/client.h>
#include <memory>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

// JNI presentation only: Core supplies all pixels, absolute scalar timelines and clipping.
// DirectComposition owns frame scheduling. No application timer or per-frame rendering is used.
namespace {
using Microsoft::WRL::ComPtr;
void checked(HRESULT hr) {
    if (FAILED(hr)) throw std::runtime_error("DirectComposition HRESULT " + std::to_string(static_cast<unsigned long>(hr)));
}
struct Host {
    bool uninitialize = false;
    ComPtr<ID3D11Device> d3d;
    ComPtr<ID2D1Device> d2d;
    ComPtr<IDCompositionDevice> device;
    ComPtr<IDCompositionTarget> target;
    ComPtr<IDCompositionVisual> root;
    ComPtr<IWICImagingFactory> wic;
    explicit Host(HWND window) {
        const auto hr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
        if (hr != RPC_E_CHANGED_MODE) checked(hr);
        uninitialize = SUCCEEDED(hr);
        try {
            checked(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr,
                D3D11_CREATE_DEVICE_BGRA_SUPPORT, nullptr, 0, D3D11_SDK_VERSION, &d3d, nullptr, nullptr));
            ComPtr<IDXGIDevice> dxgi; checked(d3d.As(&dxgi));
            checked(D2D1CreateDevice(dxgi.Get(), nullptr, &d2d));
            checked(DCompositionCreateDevice2(d2d.Get(), IID_PPV_ARGS(&device)));
            checked(device->CreateTargetForHwnd(window, TRUE, &target));
            checked(device->CreateVisual(&root));
            checked(target->SetRoot(root.Get()));
            checked(CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&wic)));
        } catch (...) {
            // Members release before apartment teardown in the normal destructor below.
            wic.Reset(); root.Reset(); target.Reset(); device.Reset(); d2d.Reset(); d3d.Reset();
            if (uninitialize) CoUninitialize();
            throw;
        }
    }
    ~Host() {
        if (target) target->SetRoot(nullptr);
        if (device) device->Commit();
        wic.Reset(); root.Reset(); target.Reset(); device.Reset(); d2d.Reset(); d3d.Reset();
        if (uninitialize) CoUninitialize();
    }
};
struct Layer {
    ComPtr<IDCompositionVisual> mask, pixels;
    ComPtr<IDCompositionRectangleClip> clip;
    ComPtr<IDCompositionSurface> surface;
};
struct Region {
    std::shared_ptr<Host> host;
    ComPtr<IDCompositionVisual> outer, viewport;
    ComPtr<IDCompositionRectangleClip> clip, rounded;
    std::vector<Layer> layers;
    explicit Region(std::shared_ptr<Host> parent) : host(std::move(parent)) {
        auto device = host->device.Get();
        checked(device->CreateVisual(&outer)); checked(device->CreateVisual(&viewport));
        checked(device->CreateRectangleClip(&clip)); checked(device->CreateRectangleClip(&rounded));
        checked(outer->SetClip(clip.Get())); checked(viewport->SetClip(rounded.Get()));
        checked(outer->AddVisual(viewport.Get(), TRUE, nullptr));
        checked(host->root->AddVisual(outer.Get(), TRUE, nullptr));
    }
    ~Region() {
        host->root->RemoveVisual(outer.Get());
        host->device->Commit();
    }
};
// All calls are confined to the AWT event dispatch thread, including resource disposal.
std::unordered_map<HWND, std::weak_ptr<Host>> hosts;
std::vector<double> doubles(JNIEnv* env, jdoubleArray array) {
    std::vector<double> result(env->GetArrayLength(array));
    env->GetDoubleArrayRegion(array, 0, static_cast<jsize>(result.size()), result.data());
    return result;
}
void report(JNIEnv* env, const std::exception& error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}
HWND windowFor(JNIEnv* env, jobject component) {
    JAWT awt{}; awt.version = JAWT_VERSION_9;
    if (!JAWT_GetAWT(env, &awt)) return nullptr;
    auto surface = awt.GetDrawingSurface(env, component);
    if (!surface) return nullptr;
    HWND window = nullptr;
    if (!(surface->Lock(surface) & JAWT_LOCK_ERROR)) {
        auto info = surface->GetDrawingSurfaceInfo(surface);
        if (info && info->platformInfo) window = static_cast<JAWT_Win32DrawingSurfaceInfo*>(info->platformInfo)->hwnd;
        if (info) surface->FreeDrawingSurfaceInfo(info);
        surface->Unlock(surface);
    }
    awt.FreeDrawingSurface(surface);
    return window;
}
void rectangle(IDCompositionRectangleClip* clip, float left, float top, float right, float bottom) {
    checked(clip->SetLeft(left)); checked(clip->SetTop(top));
    checked(clip->SetRight(right)); checked(clip->SetBottom(bottom));
}
ComPtr<IDCompositionAnimation> animation(IDCompositionDevice* device,
    const std::vector<double>& values, const std::vector<double>& times, bool repeat, LARGE_INTEGER begin) {
    ComPtr<IDCompositionAnimation> result;
    if (values.empty()) return result;
    if (values.size() < 2 || times.size() != values.size()) throw std::runtime_error("Invalid scalar timeline");
    checked(device->CreateAnimation(&result));
    checked(result->SetAbsoluteBeginTime(begin));
    for (size_t i = 0; i + 1 < values.size(); ++i) {
        checked(result->AddCubic(times[i], static_cast<float>(values[i]),
            static_cast<float>((values[i + 1] - values[i]) / (times[i + 1] - times[i])), 0, 0));
    }
    if (repeat) checked(result->AddRepeat(times.back(), times.back()));
    else checked(result->End(times.back(), static_cast<float>(values.back())));
    return result;
}
void upload(JNIEnv* env, Host& host, Layer& layer, jbyteArray png) {
    if (!png) return; // Existing cached surface remains attached for geometry/motion-only updates.
    std::vector<BYTE> bytes(env->GetArrayLength(png));
    env->GetByteArrayRegion(png, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<jbyte*>(bytes.data()));
    ComPtr<IStream> stream; stream.Attach(SHCreateMemStream(bytes.data(), static_cast<UINT>(bytes.size())));
    if (!stream) throw std::runtime_error("Could not create bitmap stream");
    ComPtr<IWICBitmapDecoder> decoder;
    checked(host.wic->CreateDecoderFromStream(stream.Get(), nullptr, WICDecodeMetadataCacheOnLoad, &decoder));
    ComPtr<IWICBitmapFrameDecode> frame; checked(decoder->GetFrame(0, &frame));
    ComPtr<IWICFormatConverter> converter; checked(host.wic->CreateFormatConverter(&converter));
    checked(converter->Initialize(frame.Get(), GUID_WICPixelFormat32bppPBGRA, WICBitmapDitherTypeNone, nullptr, 0, WICBitmapPaletteTypeCustom));
    UINT width, height; checked(converter->GetSize(&width, &height));
    ComPtr<IDCompositionSurface> surface;
    checked(host.device->CreateSurface(width, height, DXGI_FORMAT_B8G8R8A8_UNORM, DXGI_ALPHA_MODE_PREMULTIPLIED, &surface));
    ComPtr<ID2D1DeviceContext> context; POINT offset{};
    checked(surface->BeginDraw(nullptr, IID_PPV_ARGS(&context), &offset));
    try {
        ComPtr<ID2D1Bitmap1> bitmap;
        checked(context->CreateBitmapFromWicBitmap(converter.Get(), nullptr, &bitmap));
        context->SetDpi(96, 96);
        context->SetTransform(D2D1::Matrix3x2F::Translation(static_cast<float>(offset.x), static_cast<float>(offset.y)));
        context->Clear(D2D1::ColorF(0, 0));
        context->DrawBitmap(bitmap.Get(), D2D1::RectF(0, 0, static_cast<float>(width), static_cast<float>(height)));
    } catch (...) { surface->EndDraw(); throw; }
    checked(surface->EndDraw());
    checked(layer.pixels->SetContent(surface.Get()));
    layer.surface = std::move(surface);
}
}

extern "C" JNIEXPORT jlong JNICALL Java_app_naviamp_ui_WindowsRasterNative_create(JNIEnv* env, jobject, jobject component) {
    try {
        auto window = windowFor(env, component);
        if (!window) return 0;
        for (auto it = hosts.begin(); it != hosts.end();) {
            if (it->second.expired()) it = hosts.erase(it); else ++it;
        }
        auto host = hosts[window].lock();
        if (!host) { host = std::make_shared<Host>(window); hosts[window] = host; }
        return reinterpret_cast<jlong>(new Region(std::move(host)));
    } catch (const std::exception& e) { report(env, e); return 0; }
}

extern "C" JNIEXPORT void JNICALL Java_app_naviamp_ui_WindowsRasterNative_present(
    JNIEnv* env, jobject, jlong handle, jdoubleArray regionArray, jobjectArray pngs,
    jobjectArray geometryArray, jobjectArray valuesArray, jobjectArray timesArray,
    jbooleanArray repeatArray, jboolean restart) {
    try {
        auto region = reinterpret_cast<Region*>(handle);
        if (!region) throw std::runtime_error("Missing raster region");
        auto& host = *region->host;
        auto g = doubles(env, regionArray);
        if (g.size() != 9) throw std::runtime_error("Invalid region geometry");
        checked(region->outer->SetOffsetX(static_cast<float>(g[0])));
        checked(region->outer->SetOffsetY(static_cast<float>(g[1])));
        rectangle(region->clip.Get(), 0, 0, static_cast<float>(g[2]), static_cast<float>(g[3]));
        checked(region->viewport->SetOffsetX(static_cast<float>(g[4])));
        checked(region->viewport->SetOffsetY(static_cast<float>(g[5])));
        rectangle(region->rounded.Get(), 0, 0, static_cast<float>(g[6]), static_cast<float>(g[7]));
        const float radius = static_cast<float>(g[8]);
        checked(region->rounded->SetTopLeftRadiusX(radius)); checked(region->rounded->SetTopLeftRadiusY(radius));
        checked(region->rounded->SetTopRightRadiusX(radius)); checked(region->rounded->SetTopRightRadiusY(radius));
        checked(region->rounded->SetBottomLeftRadiusX(radius)); checked(region->rounded->SetBottomLeftRadiusY(radius));
        checked(region->rounded->SetBottomRightRadiusX(radius)); checked(region->rounded->SetBottomRightRadiusY(radius));
        const auto count = env->GetArrayLength(pngs);
        if (region->layers.size() != static_cast<size_t>(count)) {
            checked(region->viewport->RemoveAllVisuals()); region->layers.clear();
            for (int i = 0; i < count; ++i) {
                Layer layer;
                checked(host.device->CreateVisual(&layer.mask)); checked(host.device->CreateVisual(&layer.pixels));
                checked(host.device->CreateRectangleClip(&layer.clip)); checked(layer.mask->SetClip(layer.clip.Get()));
                checked(layer.mask->AddVisual(layer.pixels.Get(), TRUE, nullptr));
                checked(region->viewport->AddVisual(layer.mask.Get(), TRUE, nullptr));
                region->layers.push_back(std::move(layer));
            }
        }
        LARGE_INTEGER begin; QueryPerformanceCounter(&begin);
        std::vector<jboolean> repeats(count * 3);
        env->GetBooleanArrayRegion(repeatArray, 0, count * 3, repeats.data());
        for (int i = 0; i < count; ++i) {
            auto& layer = region->layers[i];
            auto png = static_cast<jbyteArray>(env->GetObjectArrayElement(pngs, i));
            upload(env, host, layer, png); if (png) env->DeleteLocalRef(png);
            if (!restart) continue;
            auto raw = static_cast<jdoubleArray>(env->GetObjectArrayElement(geometryArray, i));
            auto geometry = doubles(env, raw); env->DeleteLocalRef(raw);
            if (geometry.size() != 5) throw std::runtime_error("Invalid layer geometry");
            checked(layer.pixels->SetOffsetY(static_cast<float>(geometry[1])));
            checked(layer.clip->SetTop(0.0f)); checked(layer.clip->SetBottom(static_cast<float>(geometry[4])));
            for (int channel = 0; channel < 3; ++channel) {
                const int index = i * 3 + channel;
                auto v = static_cast<jdoubleArray>(env->GetObjectArrayElement(valuesArray, index));
                auto t = static_cast<jdoubleArray>(env->GetObjectArrayElement(timesArray, index));
                auto motion = animation(host.device.Get(), doubles(env, v), doubles(env, t), repeats[index], begin);
                env->DeleteLocalRef(v); env->DeleteLocalRef(t);
                if (channel == 0) checked(motion ? layer.pixels->SetOffsetX(motion.Get()) : layer.pixels->SetOffsetX(static_cast<float>(geometry[0])));
                if (channel == 1) checked(motion ? layer.clip->SetLeft(motion.Get()) : layer.clip->SetLeft(static_cast<float>(geometry[2])));
                if (channel == 2) checked(motion ? layer.clip->SetRight(motion.Get()) : layer.clip->SetRight(static_cast<float>(geometry[3])));
            }
        }
        checked(host.device->Commit());
    } catch (const std::exception& e) { report(env, e); }
}

extern "C" JNIEXPORT void JNICALL Java_app_naviamp_ui_WindowsRasterNative_close(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<Region*>(handle);
}
