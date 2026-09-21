#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <dlfcn.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <stdexcept>
#include <thread>
#include <vector>

// X11 presentation only: Core supplies pixels, scalar timelines, clipping, and visibility.
// Cached child-window pixmaps move or reveal without repainting the Compose parent surface.
namespace {
using Clock = std::chrono::steady_clock;

// X Shape has a stable C ABI but its development header is not present on every packaging host.
// Resolve the two presentation calls at runtime; rectangular clipping remains available without it.
constexpr int ShapeBounding = 0;
constexpr int ShapeInput = 2;
constexpr int ShapeSet = 0;
using ShapeQueryExtension = Bool (*)(Display*, int*, int*);
using ShapeCombineMask = void (*)(Display*, Window, int, int, int, Pixmap, int);

struct ShapeApi {
    void* library = nullptr;
    ShapeQueryExtension query = nullptr;
    ShapeCombineMask combineMask = nullptr;

    ShapeApi() {
        library = dlopen("libXext.so.6", RTLD_LAZY | RTLD_LOCAL);
        if (!library) return;
        query = reinterpret_cast<ShapeQueryExtension>(dlsym(library, "XShapeQueryExtension"));
        combineMask = reinterpret_cast<ShapeCombineMask>(dlsym(library, "XShapeCombineMask"));
    }

    ~ShapeApi() { if (library) dlclose(library); }
};

struct Motion {
    double value = 0.0;
    std::vector<double> values;
    std::vector<double> times;
    bool repeat = false;

    double at(double elapsedMillis) const {
        if (values.empty()) return value;
        const double duration = times.back();
        double time = std::max(0.0, elapsedMillis);
        if (repeat && duration > 0.0) time = std::fmod(time, duration);
        else time = std::min(time, duration);
        const auto end = std::lower_bound(times.begin(), times.end(), time);
        if (end == times.begin()) return values.front();
        if (end == times.end()) return values.back();
        const size_t index = static_cast<size_t>(end - times.begin());
        const double span = times[index] - times[index - 1];
        const double fraction = span > 0.0 ? (time - times[index - 1]) / span : 0.0;
        return values[index - 1] + (values[index] - values[index - 1]) * fraction;
    }

    bool active(double elapsedMillis) const {
        return !values.empty() && (repeat || elapsedMillis < times.back());
    }
};

struct Layer {
    Window clip = 0;
    Window pixels = 0;
    Pixmap pixmap = 0;
    int imageWidth = 1;
    int imageHeight = 1;
    double y = 0.0;
    double height = 1.0;
    Motion x;
    Motion left;
    Motion right;
};

struct X11RasterRegion {
    Display* display = nullptr;
    Window parent = 0;
    Visual* visual = nullptr;
    int depth = 0;
    Colormap colormap = 0;
    Window outer = 0;
    Window viewport = 0;
    std::vector<Layer> layers;
    double clipX = 0.0;
    double clipY = 0.0;
    double clipWidth = 1.0;
    double clipHeight = 1.0;
    double viewportX = 0.0;
    double viewportY = 0.0;
    double viewportWidth = 1.0;
    double viewportHeight = 1.0;
    double cornerRadius = 0.0;
    int shapedWidth = 0;
    int shapedHeight = 0;
    int shapedRadius = -1;
    ShapeApi shape;
    bool shapeAvailable = false;
    Clock::time_point started = Clock::now();
    std::mutex mutex;
    std::condition_variable changed;
    bool stopping = false;
    unsigned long long revision = 0;
    bool diagnostics = [] {
        const char* value = std::getenv("NAVIAMP_RASTER_DIAGNOSTICS");
        return value && std::strcmp(value, "true") == 0;
    }();
    std::thread animator;

    X11RasterRegion(Display* nextDisplay, Window nextParent, Visual* nextVisual, int nextDepth)
        : display(nextDisplay), parent(nextParent), visual(nextVisual), depth(nextDepth) {
        int shapeEvent = 0;
        int shapeError = 0;
        shapeAvailable = shape.query && shape.combineMask && shape.query(display, &shapeEvent, &shapeError);
        if (!shapeAvailable) {
            throw std::runtime_error("The X Shape extension is required for raster clipping and input passthrough");
        }
        XSetWindowAttributes attributes{};
        colormap = XCreateColormap(display, parent, visual, AllocNone);
        attributes.colormap = colormap;
        attributes.border_pixel = 0;
        attributes.background_pixel = 0;
        attributes.event_mask = 0;
        outer = XCreateWindow(display, parent, 0, 0, 1, 1, 0, depth, InputOutput, visual,
            CWColormap | CWBorderPixel | CWBackPixel | CWEventMask, &attributes);
        if (!outer) throw std::runtime_error("Could not create X11 raster region");
        clearInput(outer);
        viewport = child(outer);
        if (!viewport) throw std::runtime_error("Could not create X11 raster viewport");
        XMapWindow(display, viewport);
        XMapWindow(display, outer);
        XFlush(display);
        animator = std::thread([this] { animate(); });
    }

    ~X11RasterRegion() {
        {
            std::lock_guard lock(mutex);
            stopping = true;
        }
        changed.notify_one();
        if (animator.joinable()) animator.join();
        XLockDisplay(display);
        clearLayers();
        if (viewport) XDestroyWindow(display, viewport);
        if (outer) XDestroyWindow(display, outer);
        if (colormap) XFreeColormap(display, colormap);
        XFlush(display);
        XUnlockDisplay(display);
    }

    void clearLayers() {
        for (auto& layer : layers) {
            if (layer.pixmap) XFreePixmap(display, layer.pixmap);
            if (layer.clip) XDestroyWindow(display, layer.clip);
        }
        layers.clear();
    }

    Window child(Window parentWindow) {
        XSetWindowAttributes attributes{};
        attributes.colormap = colormap;
        attributes.border_pixel = 0;
        attributes.background_pixel = 0;
        attributes.event_mask = 0;
        const Window result = XCreateWindow(display, parentWindow, 0, 0, 1, 1, 0, depth, InputOutput, visual,
            CWColormap | CWBorderPixel | CWBackPixel | CWEventMask, &attributes);
        clearInput(result);
        return result;
    }

    void clearInput(Window window) {
        if (!shapeAvailable || !window) return;
        Pixmap empty = XCreatePixmap(display, window, 1, 1, 1);
        GC gc = XCreateGC(display, empty, 0, nullptr);
        XSetForeground(display, gc, 0);
        XFillRectangle(display, empty, gc, 0, 0, 1, 1);
        shape.combineMask(display, window, ShapeInput, 0, 0, empty, ShapeSet);
        XFreeGC(display, gc);
        XFreePixmap(display, empty);
    }

    void ensureLayers(size_t count) {
        if (layers.size() == count) return;
        clearLayers();
        layers.resize(count);
        for (auto& layer : layers) {
            layer.clip = child(viewport);
            layer.pixels = child(layer.clip);
            if (!layer.clip || !layer.pixels) throw std::runtime_error("Could not create X11 raster layer");
            XMapWindow(display, layer.pixels);
            XMapWindow(display, layer.clip);
        }
    }

    void upload(Layer& layer, int width, int height, const std::vector<unsigned char>& bytes) {
        if (bytes.empty()) return;
        if (bytes.size() != static_cast<size_t>(width * height * 4)) {
            throw std::runtime_error("Invalid X11 raster pixel buffer");
        }
        if (layer.pixmap) XFreePixmap(display, layer.pixmap);
        layer.imageWidth = std::max(1, width);
        layer.imageHeight = std::max(1, height);
        layer.pixmap = XCreatePixmap(display, layer.pixels, layer.imageWidth, layer.imageHeight, depth);
        if (!layer.pixmap) throw std::runtime_error("Could not allocate X11 raster pixmap");
        auto* data = static_cast<char*>(std::malloc(bytes.size()));
        if (!data) throw std::bad_alloc();
        std::memcpy(data, bytes.data(), bytes.size());
        XImage* image = XCreateImage(display, visual, depth, ZPixmap, 0, data,
            layer.imageWidth, layer.imageHeight, 32, layer.imageWidth * 4);
        if (!image) { std::free(data); throw std::runtime_error("Could not create X11 raster image"); }
        GC gc = XCreateGC(display, layer.pixmap, 0, nullptr);
        XPutImage(display, layer.pixmap, gc, image, 0, 0, 0, 0, layer.imageWidth, layer.imageHeight);
        XFreeGC(display, gc);
        XDestroyImage(image);
        XSetWindowBackgroundPixmap(display, layer.pixels, layer.pixmap);
        XMoveResizeWindow(display, layer.pixels, 0, 0, layer.imageWidth, layer.imageHeight);
        XClearWindow(display, layer.pixels);
    }

    bool configure(double elapsedMillis) {
        const int outerWidth = std::max(1, static_cast<int>(std::lround(clipWidth)));
        const int outerHeight = std::max(1, static_cast<int>(std::lround(clipHeight)));
        const int contentWidth = std::max(1, static_cast<int>(std::lround(viewportWidth)));
        const int contentHeight = std::max(1, static_cast<int>(std::lround(viewportHeight)));
        XMoveResizeWindow(display, outer,
            static_cast<int>(std::lround(clipX)), static_cast<int>(std::lround(clipY)),
            outerWidth, outerHeight);
        XMoveResizeWindow(display, viewport,
            static_cast<int>(std::lround(viewportX)), static_cast<int>(std::lround(viewportY)),
            contentWidth, contentHeight);
        applyShapes(contentWidth, contentHeight);
        bool active = false;
        for (auto& layer : layers) {
            const double x = layer.x.at(elapsedMillis);
            const double left = layer.left.at(elapsedMillis);
            const double right = layer.right.at(elapsedMillis);
            const int clipLeft = static_cast<int>(std::lround(left));
            const int clipTop = 0;
            const int clipWidthValue = std::max(1, static_cast<int>(std::lround(right - left)));
            const int clipHeightValue = std::max(1, static_cast<int>(std::lround(layer.height)));
            XMoveResizeWindow(display, layer.clip, clipLeft, clipTop, clipWidthValue, clipHeightValue);
            XMoveWindow(display, layer.pixels,
                static_cast<int>(std::lround(x - left)), static_cast<int>(std::lround(layer.y)));
            active = active || layer.x.active(elapsedMillis) || layer.left.active(elapsedMillis) ||
                layer.right.active(elapsedMillis);
        }
        XFlush(display);
        return active;
    }

    void applyShapes(int contentWidth, int contentHeight) {
        if (!shapeAvailable) return;
        if (contentWidth == shapedWidth && contentHeight == shapedHeight &&
            static_cast<int>(std::lround(cornerRadius)) == shapedRadius) return;
        shapedWidth = contentWidth;
        shapedHeight = contentHeight;
        shapedRadius = std::clamp(static_cast<int>(std::lround(cornerRadius)), 0,
            std::min(contentWidth, contentHeight) / 2);

        Pixmap bounds = XCreatePixmap(display, viewport, contentWidth, contentHeight, 1);
        GC gc = XCreateGC(display, bounds, 0, nullptr);
        XSetForeground(display, gc, 0);
        XFillRectangle(display, bounds, gc, 0, 0, contentWidth, contentHeight);
        XSetForeground(display, gc, 1);
        if (shapedRadius == 0) {
            XFillRectangle(display, bounds, gc, 0, 0, contentWidth, contentHeight);
        } else {
            const int diameter = shapedRadius * 2;
            XFillRectangle(display, bounds, gc, shapedRadius, 0,
                std::max(1, contentWidth - diameter), contentHeight);
            XFillRectangle(display, bounds, gc, 0, shapedRadius,
                contentWidth, std::max(1, contentHeight - diameter));
            XFillArc(display, bounds, gc, 0, 0, diameter, diameter, 0, 360 * 64);
            XFillArc(display, bounds, gc, contentWidth - diameter, 0, diameter, diameter, 0, 360 * 64);
            XFillArc(display, bounds, gc, 0, contentHeight - diameter, diameter, diameter, 0, 360 * 64);
            XFillArc(display, bounds, gc, contentWidth - diameter, contentHeight - diameter,
                diameter, diameter, 0, 360 * 64);
        }
        shape.combineMask(display, viewport, ShapeBounding, 0, 0, bounds, ShapeSet);
        XFreeGC(display, gc);
        XFreePixmap(display, bounds);

    }

    void animate() {
        std::unique_lock lock(mutex);
        unsigned long long handledRevision = 0;
        while (!stopping) {
            changed.wait(lock, [this, &handledRevision] { return stopping || revision != handledRevision; });
            if (stopping) break;
            int frames = 0;
            while (!stopping) {
                handledRevision = revision;
                const double elapsed = std::chrono::duration<double, std::milli>(Clock::now() - started).count();
                XLockDisplay(display);
                const bool active = configure(elapsed);
                XUnlockDisplay(display);
                ++frames;
                if (!active) break;
                changed.wait_for(lock, std::chrono::milliseconds(16),
                    [this, &handledRevision] { return stopping || revision != handledRevision; });
            }
            if (diagnostics) {
                std::fprintf(stderr, "NaviampRaster X11 region=%p frames=%d revision=%llu\n",
                    static_cast<void*>(this), frames, handledRevision);
            }
        }
    }
};

std::vector<double> doubles(JNIEnv* env, jdoubleArray array) {
    std::vector<double> result(env->GetArrayLength(array));
    env->GetDoubleArrayRegion(array, 0, static_cast<jsize>(result.size()), result.data());
    return result;
}

std::vector<unsigned char> bytes(JNIEnv* env, jbyteArray array) {
    if (!array) return {};
    std::vector<unsigned char> result(env->GetArrayLength(array));
    env->GetByteArrayRegion(array, 0, static_cast<jsize>(result.size()), reinterpret_cast<jbyte*>(result.data()));
    return result;
}

void report(JNIEnv* env, const std::exception& error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}

struct ParentInfo { Display* display = nullptr; Window drawable = 0; VisualID visualId = 0; int depth = 0; };

ParentInfo parentFor(JNIEnv* env, jobject component) {
    JAWT awt{};
    awt.version = JAWT_VERSION_9;
    if (!JAWT_GetAWT(env, &awt)) return {};
    auto surface = awt.GetDrawingSurface(env, component);
    if (!surface) return {};
    ParentInfo result;
    if (!(surface->Lock(surface) & JAWT_LOCK_ERROR)) {
        auto info = surface->GetDrawingSurfaceInfo(surface);
        if (info && info->platformInfo) {
            auto* x11 = static_cast<JAWT_X11DrawingSurfaceInfo*>(info->platformInfo);
            result = {x11->display, static_cast<Window>(x11->drawable), x11->visualID, x11->depth};
        }
        if (info) surface->FreeDrawingSurfaceInfo(info);
        surface->Unlock(surface);
    }
    awt.FreeDrawingSurface(surface);
    return result;
}

Motion motion(JNIEnv* env, double value, jobjectArray valuesArray, jobjectArray timesArray,
    const std::vector<jboolean>& repeats, int index) {
    Motion result;
    result.value = value;
    auto values = static_cast<jdoubleArray>(env->GetObjectArrayElement(valuesArray, index));
    auto times = static_cast<jdoubleArray>(env->GetObjectArrayElement(timesArray, index));
    result.values = doubles(env, values);
    result.times = doubles(env, times);
    result.repeat = repeats[index];
    env->DeleteLocalRef(values);
    env->DeleteLocalRef(times);
    if (result.values.size() != result.times.size() || (!result.values.empty() && result.values.size() < 2)) {
        throw std::runtime_error("Invalid X11 raster timeline");
    }
    return result;
}
}

extern "C" JNIEXPORT jlong JNICALL Java_app_naviamp_ui_LinuxRasterNative_create(
    JNIEnv* env, jobject, jobject component) {
    try {
        const auto parent = parentFor(env, component);
        if (!parent.display || !parent.drawable) return 0;
        XVisualInfo match{};
        XLockDisplay(parent.display);
        X11RasterRegion* region = nullptr;
        try {
            if (!XMatchVisualInfo(parent.display, DefaultScreen(parent.display), 32, TrueColor, &match)) {
                throw std::runtime_error("A 32-bit ARGB X11 visual is unavailable");
            }
            region = new X11RasterRegion(parent.display, parent.drawable, match.visual, match.depth);
        }
        catch (...) { XUnlockDisplay(parent.display); throw; }
        XUnlockDisplay(parent.display);
        return reinterpret_cast<jlong>(region);
    } catch (const std::exception& error) { report(env, error); return 0; }
}

extern "C" JNIEXPORT void JNICALL Java_app_naviamp_ui_LinuxRasterNative_present(
    JNIEnv* env, jobject, jlong handle, jdoubleArray regionArray, jobjectArray pixelArrays,
    jintArray widthsArray, jintArray heightsArray, jobjectArray geometryArray,
    jobjectArray valuesArray, jobjectArray timesArray, jbooleanArray repeatArray, jboolean restart) {
    try {
        auto* region = reinterpret_cast<X11RasterRegion*>(handle);
        if (!region) throw std::runtime_error("Missing X11 raster region");
        const auto geometry = doubles(env, regionArray);
        if (geometry.size() != 9) throw std::runtime_error("Invalid X11 region geometry");
        const int count = env->GetArrayLength(pixelArrays);
        std::vector<jint> widths(count), heights(count);
        std::vector<jboolean> repeats(count * 3);
        env->GetIntArrayRegion(widthsArray, 0, count, widths.data());
        env->GetIntArrayRegion(heightsArray, 0, count, heights.data());
        env->GetBooleanArrayRegion(repeatArray, 0, count * 3, repeats.data());
        std::lock_guard lock(region->mutex);
        XLockDisplay(region->display);
        try {
            region->clipX = geometry[0]; region->clipY = geometry[1];
            region->clipWidth = geometry[2]; region->clipHeight = geometry[3];
            region->viewportX = geometry[4]; region->viewportY = geometry[5];
            region->viewportWidth = geometry[6]; region->viewportHeight = geometry[7];
            region->cornerRadius = geometry[8];
            region->ensureLayers(static_cast<size_t>(count));
            for (int index = 0; index < count; ++index) {
                auto pixels = static_cast<jbyteArray>(env->GetObjectArrayElement(pixelArrays, index));
                if (pixels) {
                    region->upload(region->layers[index], widths[index], heights[index], bytes(env, pixels));
                    env->DeleteLocalRef(pixels);
                }
                if (restart) {
                    auto raw = static_cast<jdoubleArray>(env->GetObjectArrayElement(geometryArray, index));
                    const auto layerGeometry = doubles(env, raw);
                    env->DeleteLocalRef(raw);
                    if (layerGeometry.size() != 5) throw std::runtime_error("Invalid X11 layer geometry");
                    auto& layer = region->layers[index];
                    layer.y = layerGeometry[1]; layer.height = layerGeometry[4];
                    layer.x = motion(env, layerGeometry[0], valuesArray, timesArray, repeats, index * 3);
                    layer.left = motion(env, layerGeometry[2], valuesArray, timesArray, repeats, index * 3 + 1);
                    layer.right = motion(env, layerGeometry[3], valuesArray, timesArray, repeats, index * 3 + 2);
                }
            }
            if (restart) region->started = Clock::now();
            ++region->revision;
            if (region->diagnostics) {
                int motions = 0;
                for (const auto& layer : region->layers) {
                    motions += !layer.x.values.empty();
                    motions += !layer.left.values.empty();
                    motions += !layer.right.values.empty();
                }
                std::fprintf(stderr, "NaviampRaster X11 present region=%p layers=%d motions=%d restart=%d\n",
                    static_cast<void*>(region), count, motions, restart ? 1 : 0);
            }
            XMapWindow(region->display, region->outer);
            XFlush(region->display);
        } catch (...) { XUnlockDisplay(region->display); throw; }
        XUnlockDisplay(region->display);
        region->changed.notify_one();
    } catch (const std::exception& error) { report(env, error); }
}

extern "C" JNIEXPORT void JNICALL Java_app_naviamp_ui_LinuxRasterNative_close(
    JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<X11RasterRegion*>(handle);
}
