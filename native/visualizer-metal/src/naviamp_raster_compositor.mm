#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>
#include <atomic>
#include <memory>
#include <vector>
#import <AppKit/AppKit.h>
#import <CoreFoundation/CoreFoundation.h>
#import <QuartzCore/QuartzCore.h>

// Presentation-only JNI bridge. All pixels, timing, clipping, and repeat values originate in Core.
struct RasterRegion {
    __strong CALayer* root = nil;
    __strong CALayer* parent = nil;
    std::atomic_bool closed{false};
};
using RasterHandle = std::shared_ptr<RasterRegion>;
struct RasterImage {
    __strong NSData* png;
    std::vector<double> geometry, values, times;
    double duration;
    bool repeat;
    int kind;
};

// AWT and AppKit synchronously call into one another for accessibility and input. Waiting in either
// direction can therefore deadlock. Copy presentation data before this boundary, enqueue only the
// native layer mutation, and let the AppKit run loop perform it without blocking AWT.
static void runOnAppKitThread(dispatch_block_t work) {
    if ([NSThread isMainThread]) {
        work();
        return;
    }
    CFRunLoopPerformBlock(CFRunLoopGetMain(), kCFRunLoopCommonModes, work);
    CFRunLoopWakeUp(CFRunLoopGetMain());
}

static RasterHandle regionFor(jlong handle) {
    auto holder = reinterpret_cast<RasterHandle*>(handle);
    return holder ? *holder : nullptr;
}

static std::vector<double> doubles(JNIEnv* env, jdoubleArray array) {
    std::vector<double> result(env->GetArrayLength(array));
    env->GetDoubleArrayRegion(array, 0, result.size(), result.data());
    return result;
}
extern "C" JNIEXPORT jlong JNICALL Java_app_naviamp_ui_DesktopRasterNative_create(JNIEnv* env, jobject, jobject component) {
    JAWT awt{}; awt.version = JAWT_VERSION_9;
    if (!JAWT_GetAWT(env, &awt)) return 0;
    auto surface = awt.GetDrawingSurface(env, component);
    if (!surface) return 0;
    RasterHandle region;
    if (!(surface->Lock(surface) & JAWT_LOCK_ERROR)) {
        auto info = surface->GetDrawingSurfaceInfo(surface);
        if (info && info->platformInfo) {
            id<JAWT_SurfaceLayers> layers = (__bridge id<JAWT_SurfaceLayers>) info->platformInfo;
            CALayer* parent = layers.windowLayer ?: layers.layer;
            if (parent) {
                region = std::make_shared<RasterRegion>();
                region->parent = parent;
            }
        }
        if (info) surface->FreeDrawingSurfaceInfo(info);
        surface->Unlock(surface);
    }
    awt.FreeDrawingSurface(surface);
    return region ? reinterpret_cast<jlong>(new RasterHandle(std::move(region))) : 0;
}
static void animate(CALayer* layer, NSString* key, const RasterImage& image, double multiplier, double add, double begin) {
    if (image.values.size() < 2) return;
    NSMutableArray* values = [NSMutableArray array];
    NSMutableArray* times = [NSMutableArray array];
    for (size_t i = 0; i < image.values.size(); ++i) {
        [values addObject:@(image.values[i] * multiplier + add)];
        [times addObject:@(image.times[i])];
    }
    CAKeyframeAnimation* motion = [CAKeyframeAnimation animationWithKeyPath:key];
    motion.values = values; motion.keyTimes = times; motion.duration = image.duration;
    motion.beginTime = begin; motion.calculationMode = kCAAnimationLinear;
    motion.repeatCount = image.repeat ? HUGE_VALF : 0;
    motion.removedOnCompletion = NO; motion.fillMode = kCAFillModeForwards;
    [layer addAnimation:motion forKey:key];
}
extern "C" JNIEXPORT void JNICALL Java_app_naviamp_ui_DesktopRasterNative_present(
    JNIEnv* env, jobject, jlong handle, jdoubleArray regionGeometry, jobjectArray pngs,
    jobjectArray geometries, jobjectArray values, jobjectArray times, jdoubleArray durations,
    jintArray kinds, jbooleanArray repeats) {
    auto region = regionFor(handle);
    if (!region) return;
    auto geometry = doubles(env, regionGeometry);
    if (geometry.size() != 10) return;
    const auto count = env->GetArrayLength(pngs);
    std::vector<RasterImage> images;
    auto durationValues = doubles(env, durations);
    std::vector<jint> kindValues(count); env->GetIntArrayRegion(kinds, 0, count, kindValues.data());
    std::vector<jboolean> repeatValues(count); env->GetBooleanArrayRegion(repeats, 0, count, repeatValues.data());
    for (int i = 0; i < count; ++i) {
        auto png = (jbyteArray) env->GetObjectArrayElement(pngs, i);
        auto bytes = env->GetByteArrayElements(png, nullptr);
        NSData* data = [NSData dataWithBytes:bytes length:env->GetArrayLength(png)];
        env->ReleaseByteArrayElements(png, bytes, JNI_ABORT); env->DeleteLocalRef(png);
        auto g = (jdoubleArray) env->GetObjectArrayElement(geometries, i);
        auto v = (jdoubleArray) env->GetObjectArrayElement(values, i);
        auto t = (jdoubleArray) env->GetObjectArrayElement(times, i);
        images.push_back({data, doubles(env,g), doubles(env,v), doubles(env,t), durationValues[i], repeatValues[i] != 0, kindValues[i]});
        env->DeleteLocalRef(g); env->DeleteLocalRef(v); env->DeleteLocalRef(t);
    }
    runOnAppKitThread(^{
        if (region->closed.load()) return;
        if (!region->root) {
            region->root = [CALayer layer];
            region->root.anchorPoint = CGPointZero;
            region->root.geometryFlipped = YES;
            region->root.masksToBounds = YES;
            [region->parent addSublayer:region->root];
        }
        [CATransaction begin]; [CATransaction setDisableActions:YES];
        const double scale = geometry[7];
        const double x = geometry[2]/scale, y = geometry[3]/scale;
        const double width = geometry[4]/scale, height = geometry[5]/scale;
        region->root.position = CGPointMake(x, region->parent.geometryFlipped ? y : region->parent.bounds.size.height-y-height);
        region->root.bounds = CGRectMake(0,0,width,height);
        region->root.cornerRadius = geometry[6]/scale;
        region->root.contentsScale = scale;
        region->root.sublayers = nil;
        const double begin = CACurrentMediaTime();
        for (const auto& image : images) {
            auto g = image.geometry;
            const double viewportWidth = geometry[8]/scale;
            CALayer* clip = [CALayer layer]; clip.anchorPoint = CGPointZero; clip.masksToBounds = YES;
            clip.position = CGPointMake((geometry[0]-geometry[2])/scale, (geometry[1]-geometry[3])/scale);
            clip.bounds = CGRectMake(0,0,viewportWidth,geometry[9]/scale);
            CALayer* pixels = [CALayer layer]; pixels.anchorPoint = CGPointZero;
            pixels.position = CGPointMake(g[0]/scale,g[1]/scale);
            pixels.bounds = CGRectMake(0,0,g[2]/scale,g[3]/scale);
            pixels.contentsScale = scale;
            NSBitmapImageRep* rep = [NSBitmapImageRep imageRepWithData:image.png];
            pixels.contents = (__bridge id)rep.CGImage;
            [clip addSublayer:pixels]; [region->root addSublayer:clip];
            if (image.kind == 0) animate(pixels,@"transform.translation.x",image,1.0/scale,0,begin);
            else {
                const bool inverse = g[5] != 0;
                const double initial = g[4] * viewportWidth;
                clip.bounds = CGRectMake(inverse ? initial : 0,0,inverse ? viewportWidth-initial : initial,geometry[9]/scale);
                if (inverse) {
                    clip.position = CGPointMake(clip.position.x+initial,clip.position.y);
                    animate(clip,@"bounds.origin.x",image,viewportWidth,0,begin);
                    animate(clip,@"position.x",image,viewportWidth,(geometry[0]-geometry[2])/scale,begin);
                }
                animate(clip,@"bounds.size.width",image,inverse ? -viewportWidth : viewportWidth,inverse ? viewportWidth : 0,begin);
            }
        }
        [CATransaction commit];
    });
}
extern "C" JNIEXPORT void JNICALL Java_app_naviamp_ui_DesktopRasterNative_close(JNIEnv*, jobject, jlong handle) {
    auto holder = reinterpret_cast<RasterHandle*>(handle);
    if (!holder) return;
    auto region = *holder;
    delete holder;
    if (!region) return;
    region->closed.store(true);
    runOnAppKitThread(^{ [region->root removeFromSuperlayer]; region->root = nil; });
}

extern "C" JNIEXPORT jdouble JNICALL Java_app_naviamp_ui_DesktopRasterNative_translationX(JNIEnv*, jobject, jlong handle, jint index) {
    return 0;
}
