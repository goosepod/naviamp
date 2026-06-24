#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>

#import <AppKit/AppKit.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>

#include <array>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <string>
#include <vector>

namespace {

constexpr int kBandCount = 32;

struct Uniforms {
    float time;
    float resolution[2];
    float energyLevel;
    float bassLevel;
    float midLevel;
    float trebleLevel;
    float spectralCentroid;
    float tempoBpm;
    float beatDetected;
    float active;
    float renderScale;
    int maxRaymarchSteps;
    float accent[4];
    float readable[4];
    float colorA[4];
    float colorB[4];
    float colorC[4];
    float albumArtSize[2];
};

struct Host {
    CAMetalLayer* layer = nil;
    id<MTLDevice> device = nil;
    id<MTLCommandQueue> commandQueue = nil;
    id<MTLRenderPipelineState> pipeline = nil;
    id<MTLSamplerState> samplerState = nil;
    id<MTLTexture> frequencyTexture = nil;
    id<MTLTexture> albumArtTexture = nil;
    id<MTLTexture> renderTexture = nil;
    id<JAWT_SurfaceLayers> surfaceLayers = nil;
    int width = 1;
    int height = 1;
    int renderWidth = 0;
    int renderHeight = 0;
    int albumArtWidth = 1;
    int albumArtHeight = 1;
};

std::string jstringToString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string metalLibrarySource(const std::string& fragmentSource) {
    return fragmentSource + R"metal(

vertex NaviampRasterizerData visualizerVertex(uint vertexId [[vertex_id]]) {
    float2 positions[3] = {
        float2(-1.0, -1.0),
        float2( 3.0, -1.0),
        float2(-1.0,  3.0)
    };
    NaviampRasterizerData out;
    out.position = float4(positions[vertexId], 0.0, 1.0);
    out.uv = positions[vertexId] * 0.5 + 0.5;
    return out;
}

)metal";
}

void throwIllegalState(JNIEnv* env, const std::string& message) {
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
    }
}

void configureLayerOnMain(Host* host) {
    dispatch_sync(dispatch_get_main_queue(), ^{
        host->layer = [CAMetalLayer layer];
        host->layer.device = host->device;
        host->layer.pixelFormat = MTLPixelFormatBGRA8Unorm;
        host->layer.framebufferOnly = YES;
        host->layer.opaque = NO;
        host->layer.contentsScale = NSScreen.mainScreen.backingScaleFactor ?: 1.0;
        host->layer.drawableSize = CGSizeMake(host->width, host->height);
        host->surfaceLayers.layer = host->layer;
    });
}

bool attachLayerToComponent(JNIEnv* env, jobject component, Host* host) {
    JAWT awt{};
    awt.version = JAWT_VERSION_9;
    if (JAWT_GetAWT(env, &awt) == JNI_FALSE) {
        return false;
    }

    JAWT_DrawingSurface* surface = awt.GetDrawingSurface(env, component);
    if (surface == nullptr) {
        return false;
    }

    bool attached = false;
    const jint lock = surface->Lock(surface);
    if ((lock & JAWT_LOCK_ERROR) == 0) {
        JAWT_DrawingSurfaceInfo* info = surface->GetDrawingSurfaceInfo(surface);
        if (info != nullptr && info->platformInfo != nullptr) {
            id<JAWT_SurfaceLayers> surfaceLayers = (__bridge id<JAWT_SurfaceLayers>)info->platformInfo;
            if (surfaceLayers != nil) {
                host->surfaceLayers = surfaceLayers;
                configureLayerOnMain(host);
                attached = true;
            }
            surface->FreeDrawingSurfaceInfo(info);
        }
        surface->Unlock(surface);
    }

    awt.FreeDrawingSurface(surface);
    return attached;
}

id<MTLSamplerState> createSamplerState(id<MTLDevice> device) {
    MTLSamplerDescriptor* descriptor = [[MTLSamplerDescriptor alloc] init];
    descriptor.minFilter = MTLSamplerMinMagFilterLinear;
    descriptor.magFilter = MTLSamplerMinMagFilterLinear;
    descriptor.mipFilter = MTLSamplerMipFilterNotMipmapped;
    descriptor.sAddressMode = MTLSamplerAddressModeClampToEdge;
    descriptor.tAddressMode = MTLSamplerAddressModeClampToEdge;
    return [device newSamplerStateWithDescriptor:descriptor];
}

id<MTLRenderPipelineState> createPipeline(id<MTLDevice> device, const std::string& fragmentSource, std::string& errorMessage) {
    NSString* source = [NSString stringWithUTF8String:metalLibrarySource(fragmentSource).c_str()];
    NSError* error = nil;
    id<MTLLibrary> library = [device newLibraryWithSource:source options:nil error:&error];
    if (library == nil) {
        errorMessage = error.localizedDescription.UTF8String ?: "Metal library compilation failed";
        return nil;
    }

    MTLRenderPipelineDescriptor* descriptor = [[MTLRenderPipelineDescriptor alloc] init];
    descriptor.vertexFunction = [library newFunctionWithName:@"visualizerVertex"];
    descriptor.fragmentFunction = [library newFunctionWithName:@"visualizerFragment"];
    descriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
    descriptor.colorAttachments[0].blendingEnabled = YES;
    descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorSourceAlpha;
    descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
    descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
    descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;

    id<MTLRenderPipelineState> pipeline = [device newRenderPipelineStateWithDescriptor:descriptor error:&error];
    if (pipeline == nil) {
        errorMessage = error.localizedDescription.UTF8String ?: "Metal pipeline creation failed";
    }
    return pipeline;
}

id<MTLTexture> makeTexture(id<MTLDevice> device, int width, int height, MTLPixelFormat pixelFormat) {
    MTLTextureDescriptor* descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:pixelFormat
                                                                                          width:width
                                                                                         height:height
                                                                                      mipmapped:NO];
    descriptor.usage = MTLTextureUsageShaderRead;
    return [device newTextureWithDescriptor:descriptor];
}

id<MTLTexture> makeRenderTexture(id<MTLDevice> device, int width, int height) {
    MTLTextureDescriptor* descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
                                                                                          width:width
                                                                                         height:height
                                                                                      mipmapped:NO];
    descriptor.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead;
    descriptor.storageMode = MTLStorageModeShared;
    return [device newTextureWithDescriptor:descriptor];
}

void ensureTextures(Host* host) {
    if (host->frequencyTexture == nil) {
        host->frequencyTexture = makeTexture(host->device, kBandCount, 1, MTLPixelFormatR32Float);
    }
    if (host->albumArtTexture == nil) {
        host->albumArtTexture = makeTexture(host->device, 1, 1, MTLPixelFormatRGBA8Unorm);
        const std::array<uint8_t, 4> white = {255, 255, 255, 255};
        [host->albumArtTexture replaceRegion:MTLRegionMake2D(0, 0, 1, 1)
                                 mipmapLevel:0
                                   withBytes:white.data()
                                 bytesPerRow:4];
        host->albumArtWidth = 1;
        host->albumArtHeight = 1;
    }
}

void ensureRenderTexture(Host* host, int width, int height) {
    width = std::max(width, 1);
    height = std::max(height, 1);
    if (host->renderTexture == nil || host->renderWidth != width || host->renderHeight != height) {
        host->renderTexture = makeRenderTexture(host->device, width, height);
        host->renderWidth = width;
        host->renderHeight = height;
    }
}

void updateLayerSize(Host* host, int width, int height) {
    host->width = std::max(width, 1);
    host->height = std::max(height, 1);
    dispatch_sync(dispatch_get_main_queue(), ^{
        host->layer.contentsScale = NSScreen.mainScreen.backingScaleFactor ?: 1.0;
        host->layer.drawableSize = CGSizeMake(host->width, host->height);
    });
}

void copyColor(JNIEnv* env, jfloatArray source, float destination[4], const float fallback[4]) {
    if (source == nullptr || env->GetArrayLength(source) < 4) {
        std::memcpy(destination, fallback, sizeof(float) * 4);
        return;
    }
    env->GetFloatArrayRegion(source, 0, 4, destination);
}

float average(const std::vector<float>& values, int start, int end) {
    start = std::max(start, 0);
    end = std::min(end, static_cast<int>(values.size()));
    if (start >= end) return 0.0f;
    float total = 0.0f;
    for (int index = start; index < end; ++index) total += values[index];
    return std::clamp(total / static_cast<float>(end - start), 0.0f, 1.0f);
}

Uniforms buildUniforms(
    JNIEnv* env,
    jint width,
    jint height,
    jfloat timeSeconds,
    jboolean active,
    jfloat tempoBpm,
    jfloat renderScale,
    jint maxRaymarchSteps,
    const std::vector<float>& bands,
    jfloatArray accent,
    jfloatArray readable,
    jfloatArray colorA,
    jfloatArray colorB,
    jfloatArray colorC
) {
    Uniforms uniforms{};
    uniforms.time = timeSeconds;
    uniforms.resolution[0] = static_cast<float>(std::max(width, 1));
    uniforms.resolution[1] = static_cast<float>(std::max(height, 1));
    uniforms.bassLevel = average(bands, 0, 8);
    uniforms.midLevel = average(bands, 8, 20);
    uniforms.trebleLevel = average(bands, 20, kBandCount);
    uniforms.energyLevel = average(bands, 0, kBandCount);

    float weightedTotal = 0.0f;
    float total = 0.0001f;
    for (int index = 0; index < static_cast<int>(bands.size()); ++index) {
        weightedTotal += (static_cast<float>(index) / static_cast<float>(std::max(kBandCount - 1, 1))) * bands[index];
        total += std::max(bands[index], 0.0f);
    }
    uniforms.spectralCentroid = std::clamp(weightedTotal / total, 0.0f, 1.0f);
    uniforms.tempoBpm = std::clamp(tempoBpm, 60.0f, 220.0f);
    uniforms.beatDetected = (uniforms.bassLevel > 0.68f && uniforms.bassLevel > uniforms.midLevel * 1.18f && uniforms.energyLevel > 0.34f) ? 1.0f : 0.0f;
    uniforms.active = active == JNI_TRUE ? 1.0f : 0.0f;
    uniforms.renderScale = renderScale;
    uniforms.maxRaymarchSteps = maxRaymarchSteps;
    uniforms.albumArtSize[0] = 1.0f;
    uniforms.albumArtSize[1] = 1.0f;

    const float defaultWhite[4] = {1.0f, 1.0f, 1.0f, 1.0f};
    const float defaultBlack[4] = {0.0f, 0.0f, 0.0f, 1.0f};
    copyColor(env, accent, uniforms.accent, defaultWhite);
    copyColor(env, readable, uniforms.readable, defaultWhite);
    copyColor(env, colorA, uniforms.colorA, defaultBlack);
    copyColor(env, colorB, uniforms.colorB, defaultBlack);
    copyColor(env, colorC, uniforms.colorC, defaultBlack);
    return uniforms;
}

void updateAlbumArtTexture(Host* host, int width, int height, const uint8_t* rgbaPixels, int byteCount) {
    if (host == nullptr || host->device == nil || rgbaPixels == nullptr) return;
    width = std::max(width, 1);
    height = std::max(height, 1);
    const int expectedBytes = width * height * 4;
    if (byteCount < expectedBytes) return;

    if (host->albumArtTexture == nil || host->albumArtWidth != width || host->albumArtHeight != height) {
        host->albumArtTexture = makeTexture(host->device, width, height, MTLPixelFormatRGBA8Unorm);
        host->albumArtWidth = width;
        host->albumArtHeight = height;
    }
    [host->albumArtTexture replaceRegion:MTLRegionMake2D(0, 0, width, height)
                             mipmapLevel:0
                               withBytes:rgbaPixels
                             bytesPerRow:width * 4];
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_naviamp_ui_NativeMetalVisualizerHost_nativeCreate(JNIEnv* env, jobject thiz, jstring fragmentSource) {
    @autoreleasepool {
        auto* host = new Host();
        host->device = MTLCreateSystemDefaultDevice();
        if (host->device == nil) {
            delete host;
            throwIllegalState(env, "Metal is not available on this system.");
            return 0;
        }
        host->commandQueue = [host->device newCommandQueue];
        host->samplerState = createSamplerState(host->device);
        std::string errorMessage;
        host->pipeline = createPipeline(host->device, jstringToString(env, fragmentSource), errorMessage);
        if (host->pipeline == nil) {
            delete host;
            throwIllegalState(env, errorMessage);
            return 0;
        }
        ensureTextures(host);
        return reinterpret_cast<jlong>(host);
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_naviamp_ui_NativeMetalVisualizerHost_nativeRenderImage(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jint width,
    jint height,
    jfloat timeSeconds,
    jboolean active,
    jfloat tempoBpm,
    jfloat renderScale,
    jint maxRaymarchSteps,
    jfloatArray bandsArray,
    jfloatArray accent,
    jfloatArray readable,
    jfloatArray colorA,
    jfloatArray colorB,
    jfloatArray colorC
) {
    @autoreleasepool {
        auto* host = reinterpret_cast<Host*>(handle);
        if (host == nullptr || host->pipeline == nil) return nullptr;

        width = std::max(width, 1);
        height = std::max(height, 1);
        ensureRenderTexture(host, width, height);
        ensureTextures(host);

        std::vector<float> bands(kBandCount, 0.0f);
        if (bandsArray != nullptr) {
            const jsize length = std::min<jsize>(env->GetArrayLength(bandsArray), kBandCount);
            env->GetFloatArrayRegion(bandsArray, 0, length, bands.data());
        }
        [host->frequencyTexture replaceRegion:MTLRegionMake2D(0, 0, kBandCount, 1)
                                  mipmapLevel:0
                                    withBytes:bands.data()
                                  bytesPerRow:sizeof(float) * kBandCount];

        Uniforms uniforms = buildUniforms(
            env,
            width,
            height,
            timeSeconds,
            active,
            tempoBpm,
            renderScale,
            maxRaymarchSteps,
            bands,
            accent,
            readable,
            colorA,
            colorB,
            colorC
        );
        uniforms.albumArtSize[0] = static_cast<float>(host->albumArtWidth);
        uniforms.albumArtSize[1] = static_cast<float>(host->albumArtHeight);

        MTLRenderPassDescriptor* passDescriptor = [MTLRenderPassDescriptor renderPassDescriptor];
        passDescriptor.colorAttachments[0].texture = host->renderTexture;
        passDescriptor.colorAttachments[0].loadAction = MTLLoadActionClear;
        passDescriptor.colorAttachments[0].storeAction = MTLStoreActionStore;
        passDescriptor.colorAttachments[0].clearColor = MTLClearColorMake(0.0, 0.0, 0.0, 0.0);

        id<MTLCommandBuffer> commandBuffer = [host->commandQueue commandBuffer];
        id<MTLRenderCommandEncoder> encoder = [commandBuffer renderCommandEncoderWithDescriptor:passDescriptor];
        [encoder setRenderPipelineState:host->pipeline];
        [encoder setFragmentBytes:&uniforms length:sizeof(Uniforms) atIndex:0];
        [encoder setFragmentTexture:host->frequencyTexture atIndex:0];
        [encoder setFragmentTexture:host->albumArtTexture atIndex:1];
        [encoder setFragmentSamplerState:host->samplerState atIndex:0];
        [encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
        [encoder endEncoding];
        [commandBuffer commit];
        [commandBuffer waitUntilCompleted];

        const int byteCount = width * height * 4;
        std::vector<uint8_t> pixels(byteCount);
        [host->renderTexture getBytes:pixels.data()
                           bytesPerRow:width * 4
                            fromRegion:MTLRegionMake2D(0, 0, width, height)
                           mipmapLevel:0];

        jbyteArray result = env->NewByteArray(byteCount);
        if (result == nullptr) return nullptr;
        env->SetByteArrayRegion(result, 0, byteCount, reinterpret_cast<const jbyte*>(pixels.data()));
        return result;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_app_naviamp_ui_NativeMetalVisualizerHost_nativeUpdateAlbumArt(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jint width,
    jint height,
    jbyteArray rgbaPixels
) {
    @autoreleasepool {
        auto* host = reinterpret_cast<Host*>(handle);
        if (host == nullptr || rgbaPixels == nullptr) return;
        const jsize byteCount = env->GetArrayLength(rgbaPixels);
        jbyte* pixels = env->GetByteArrayElements(rgbaPixels, nullptr);
        if (pixels == nullptr) return;
        updateAlbumArtTexture(
            host,
            width,
            height,
            reinterpret_cast<const uint8_t*>(pixels),
            static_cast<int>(byteCount)
        );
        env->ReleaseByteArrayElements(rgbaPixels, pixels, JNI_ABORT);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_app_naviamp_ui_NativeMetalVisualizerHost_nativeDispose(JNIEnv* env, jobject thiz, jlong handle) {
    @autoreleasepool {
        auto* host = reinterpret_cast<Host*>(handle);
        if (host == nullptr) return;
        dispatch_sync(dispatch_get_main_queue(), ^{
            if (host->surfaceLayers != nil && host->surfaceLayers.layer == host->layer) {
                host->surfaceLayers.layer = nil;
            }
        });
        delete host;
    }
}
