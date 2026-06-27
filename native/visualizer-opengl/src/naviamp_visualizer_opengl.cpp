#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>

#ifndef NOMINMAX
#define NOMINMAX
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <gl/GL.h>

#ifdef min
#undef min
#endif
#ifdef max
#undef max
#endif

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <sstream>
#include <string>
#include <vector>

namespace {

constexpr int kBandCount = 32;
constexpr const char* kWindowClassName = "NaviampVisualizerOpenGlHiddenWindow";

#ifndef GL_VERTEX_SHADER
#define GL_VERTEX_SHADER 0x8B31
#define GL_FRAGMENT_SHADER 0x8B30
#define GL_COMPILE_STATUS 0x8B81
#define GL_LINK_STATUS 0x8B82
#define GL_INFO_LOG_LENGTH 0x8B84
#define GL_TEXTURE0 0x84C0
#define GL_TEXTURE1 0x84C1
#define GL_CLAMP_TO_EDGE 0x812F
#define GL_ARRAY_BUFFER 0x8892
#define GL_STATIC_DRAW 0x88E4
#define GL_BGRA 0x80E1
#endif

using GlCreateShader = GLuint(APIENTRY*)(GLenum);
using GlShaderSource = void(APIENTRY*)(GLuint, GLsizei, const char* const*, const GLint*);
using GlCompileShader = void(APIENTRY*)(GLuint);
using GlGetShaderiv = void(APIENTRY*)(GLuint, GLenum, GLint*);
using GlGetShaderInfoLog = void(APIENTRY*)(GLuint, GLsizei, GLsizei*, char*);
using GlCreateProgram = GLuint(APIENTRY*)();
using GlAttachShader = void(APIENTRY*)(GLuint, GLuint);
using GlLinkProgram = void(APIENTRY*)(GLuint);
using GlGetProgramiv = void(APIENTRY*)(GLuint, GLenum, GLint*);
using GlGetProgramInfoLog = void(APIENTRY*)(GLuint, GLsizei, GLsizei*, char*);
using GlUseProgram = void(APIENTRY*)(GLuint);
using GlDeleteShader = void(APIENTRY*)(GLuint);
using GlDeleteProgram = void(APIENTRY*)(GLuint);
using GlGetUniformLocation = GLint(APIENTRY*)(GLuint, const char*);
using GlUniform1f = void(APIENTRY*)(GLint, GLfloat);
using GlUniform1i = void(APIENTRY*)(GLint, GLint);
using GlUniform2f = void(APIENTRY*)(GLint, GLfloat, GLfloat);
using GlUniform4fv = void(APIENTRY*)(GLint, GLsizei, const GLfloat*);
using GlActiveTexture = void(APIENTRY*)(GLenum);
using GlGenVertexArrays = void(APIENTRY*)(GLsizei, GLuint*);
using GlBindVertexArray = void(APIENTRY*)(GLuint);
using GlDeleteVertexArrays = void(APIENTRY*)(GLsizei, const GLuint*);

struct GlApi {
    GlCreateShader createShader = nullptr;
    GlShaderSource shaderSource = nullptr;
    GlCompileShader compileShader = nullptr;
    GlGetShaderiv getShaderiv = nullptr;
    GlGetShaderInfoLog getShaderInfoLog = nullptr;
    GlCreateProgram createProgram = nullptr;
    GlAttachShader attachShader = nullptr;
    GlLinkProgram linkProgram = nullptr;
    GlGetProgramiv getProgramiv = nullptr;
    GlGetProgramInfoLog getProgramInfoLog = nullptr;
    GlUseProgram useProgram = nullptr;
    GlDeleteShader deleteShader = nullptr;
    GlDeleteProgram deleteProgram = nullptr;
    GlGetUniformLocation getUniformLocation = nullptr;
    GlUniform1f uniform1f = nullptr;
    GlUniform1i uniform1i = nullptr;
    GlUniform2f uniform2f = nullptr;
    GlUniform4fv uniform4fv = nullptr;
    GlActiveTexture activeTexture = nullptr;
    GlGenVertexArrays genVertexArrays = nullptr;
    GlBindVertexArray bindVertexArray = nullptr;
    GlDeleteVertexArrays deleteVertexArrays = nullptr;
};

struct Host {
    HWND window = nullptr;
    HDC deviceContext = nullptr;
    HGLRC glContext = nullptr;
    bool ownsWindow = true;
    bool surfaceMode = false;
    std::string fragmentSource;
    GlApi gl;
    GLuint program = 0;
    GLuint vertexArray = 0;
    GLuint frequencyTexture = 0;
    GLuint albumArtTexture = 0;
    int albumArtWidth = 1;
    int albumArtHeight = 1;
    int width = 1;
    int height = 1;
};

std::string jstringToString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void throwIllegalState(JNIEnv* env, const std::string& message) {
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
    }
}

void* loadGlFunction(const char* name) {
    void* proc = reinterpret_cast<void*>(wglGetProcAddress(name));
    if (proc == nullptr || proc == reinterpret_cast<void*>(0x1) || proc == reinterpret_cast<void*>(0x2) ||
        proc == reinterpret_cast<void*>(0x3) || proc == reinterpret_cast<void*>(-1)) {
        HMODULE module = LoadLibraryA("opengl32.dll");
        proc = module != nullptr ? reinterpret_cast<void*>(GetProcAddress(module, name)) : nullptr;
    }
    return proc;
}

template <typename T>
bool loadGlFunction(T& target, const char* name) {
    target = reinterpret_cast<T>(loadGlFunction(name));
    return target != nullptr;
}

bool loadGlApi(GlApi* gl, std::string& errorMessage) {
    if (!loadGlFunction(gl->createShader, "glCreateShader") ||
        !loadGlFunction(gl->shaderSource, "glShaderSource") ||
        !loadGlFunction(gl->compileShader, "glCompileShader") ||
        !loadGlFunction(gl->getShaderiv, "glGetShaderiv") ||
        !loadGlFunction(gl->getShaderInfoLog, "glGetShaderInfoLog") ||
        !loadGlFunction(gl->createProgram, "glCreateProgram") ||
        !loadGlFunction(gl->attachShader, "glAttachShader") ||
        !loadGlFunction(gl->linkProgram, "glLinkProgram") ||
        !loadGlFunction(gl->getProgramiv, "glGetProgramiv") ||
        !loadGlFunction(gl->getProgramInfoLog, "glGetProgramInfoLog") ||
        !loadGlFunction(gl->useProgram, "glUseProgram") ||
        !loadGlFunction(gl->deleteShader, "glDeleteShader") ||
        !loadGlFunction(gl->deleteProgram, "glDeleteProgram") ||
        !loadGlFunction(gl->getUniformLocation, "glGetUniformLocation") ||
        !loadGlFunction(gl->uniform1f, "glUniform1f") ||
        !loadGlFunction(gl->uniform1i, "glUniform1i") ||
        !loadGlFunction(gl->uniform2f, "glUniform2f") ||
        !loadGlFunction(gl->uniform4fv, "glUniform4fv") ||
        !loadGlFunction(gl->activeTexture, "glActiveTexture") ||
        !loadGlFunction(gl->genVertexArrays, "glGenVertexArrays") ||
        !loadGlFunction(gl->bindVertexArray, "glBindVertexArray") ||
        !loadGlFunction(gl->deleteVertexArrays, "glDeleteVertexArrays")) {
        errorMessage = "Required OpenGL shader functions are unavailable.";
        return false;
    }
    return true;
}

LRESULT CALLBACK windowProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    return DefWindowProc(hwnd, message, wParam, lParam);
}

bool registerWindowClass() {
    static bool registered = false;
    if (registered) return true;
    WNDCLASSA windowClass{};
    windowClass.style = CS_OWNDC;
    windowClass.lpfnWndProc = windowProc;
    windowClass.hInstance = GetModuleHandle(nullptr);
    windowClass.lpszClassName = kWindowClassName;
    if (RegisterClassA(&windowClass) == 0 && GetLastError() != ERROR_CLASS_ALREADY_EXISTS) {
        return false;
    }
    registered = true;
    return true;
}

bool createContext(Host* host, std::string& errorMessage) {
    if (!registerWindowClass()) {
        errorMessage = "Failed to register OpenGL window class.";
        return false;
    }
    host->window = CreateWindowExA(
        0,
        kWindowClassName,
        "Naviamp Visualizer OpenGL",
        WS_POPUP,
        0,
        0,
        1,
        1,
        nullptr,
        nullptr,
        GetModuleHandle(nullptr),
        nullptr
    );
    if (host->window == nullptr) {
        errorMessage = "Failed to create hidden OpenGL window.";
        return false;
    }
    host->ownsWindow = true;
    host->surfaceMode = false;
    host->deviceContext = GetDC(host->window);
    if (host->deviceContext == nullptr) {
        errorMessage = "Failed to get hidden OpenGL window device context.";
        return false;
    }

    PIXELFORMATDESCRIPTOR pixelFormat{};
    pixelFormat.nSize = sizeof(pixelFormat);
    pixelFormat.nVersion = 1;
    pixelFormat.dwFlags = PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER;
    pixelFormat.iPixelType = PFD_TYPE_RGBA;
    pixelFormat.cColorBits = 32;
    pixelFormat.cAlphaBits = 8;
    pixelFormat.cDepthBits = 0;
    pixelFormat.iLayerType = PFD_MAIN_PLANE;
    const int format = ChoosePixelFormat(host->deviceContext, &pixelFormat);
    if (format == 0 || !SetPixelFormat(host->deviceContext, format, &pixelFormat)) {
        errorMessage = "Failed to set OpenGL pixel format.";
        return false;
    }
    host->glContext = wglCreateContext(host->deviceContext);
    if (host->glContext == nullptr || !wglMakeCurrent(host->deviceContext, host->glContext)) {
        errorMessage = "Failed to create OpenGL context.";
        return false;
    }
    return loadGlApi(&host->gl, errorMessage);
}

void releaseGlResources(Host* host) {
    if (host == nullptr || host->glContext == nullptr) return;
    wglMakeCurrent(host->deviceContext, host->glContext);
    if (host->program != 0 && host->gl.deleteProgram != nullptr) {
        host->gl.deleteProgram(host->program);
        host->program = 0;
    }
    if (host->vertexArray != 0 && host->gl.deleteVertexArrays != nullptr) {
        host->gl.deleteVertexArrays(1, &host->vertexArray);
        host->vertexArray = 0;
    }
    if (host->frequencyTexture != 0) {
        glDeleteTextures(1, &host->frequencyTexture);
        host->frequencyTexture = 0;
    }
    if (host->albumArtTexture != 0) {
        glDeleteTextures(1, &host->albumArtTexture);
        host->albumArtTexture = 0;
    }
}

void releaseContextAndWindow(Host* host) {
    if (host == nullptr) return;
    if (host->glContext != nullptr) {
        releaseGlResources(host);
        wglMakeCurrent(nullptr, nullptr);
        wglDeleteContext(host->glContext);
        host->glContext = nullptr;
    }
    if (host->window != nullptr && host->deviceContext != nullptr) {
        ReleaseDC(host->window, host->deviceContext);
        host->deviceContext = nullptr;
    }
    if (host->window != nullptr && host->ownsWindow) {
        DestroyWindow(host->window);
    }
    host->window = nullptr;
}

std::string shaderLog(const GlApi& gl, GLuint shader) {
    GLint length = 0;
    gl.getShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
    if (length <= 1) return {};
    std::string log(static_cast<size_t>(length), '\0');
    gl.getShaderInfoLog(shader, length, nullptr, log.data());
    return log;
}

std::string programLog(const GlApi& gl, GLuint program) {
    GLint length = 0;
    gl.getProgramiv(program, GL_INFO_LOG_LENGTH, &length);
    if (length <= 1) return {};
    std::string log(static_cast<size_t>(length), '\0');
    gl.getProgramInfoLog(program, length, nullptr, log.data());
    return log;
}

GLuint compileShader(const GlApi& gl, GLenum type, const std::string& source, std::string& errorMessage) {
    GLuint shader = gl.createShader(type);
    const char* sourceData = source.c_str();
    gl.shaderSource(shader, 1, &sourceData, nullptr);
    gl.compileShader(shader);
    GLint compiled = 0;
    gl.getShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled == 0) {
        errorMessage = shaderLog(gl, shader);
        gl.deleteShader(shader);
        return 0;
    }
    return shader;
}

std::string vertexShaderSource() {
    return R"glsl(#version 330 core
out vec2 v_uv;

void main() {
    vec2 positions[3] = vec2[](
        vec2(-1.0, -1.0),
        vec2( 3.0, -1.0),
        vec2(-1.0,  3.0)
    );
    gl_Position = vec4(positions[gl_VertexID], 0.0, 1.0);
    v_uv = positions[gl_VertexID] * 0.5 + 0.5;
}
)glsl";
}

GLuint buildProgram(const GlApi& gl, const std::string& fragmentSource, std::string& errorMessage) {
    GLuint vertexShader = compileShader(gl, GL_VERTEX_SHADER, vertexShaderSource(), errorMessage);
    if (vertexShader == 0) return 0;
    GLuint fragmentShader = compileShader(gl, GL_FRAGMENT_SHADER, fragmentSource, errorMessage);
    if (fragmentShader == 0) {
        gl.deleteShader(vertexShader);
        return 0;
    }
    GLuint program = gl.createProgram();
    gl.attachShader(program, vertexShader);
    gl.attachShader(program, fragmentShader);
    gl.linkProgram(program);
    GLint linked = 0;
    gl.getProgramiv(program, GL_LINK_STATUS, &linked);
    gl.deleteShader(vertexShader);
    gl.deleteShader(fragmentShader);
    if (linked == 0) {
        errorMessage = programLog(gl, program);
        gl.deleteProgram(program);
        return 0;
    }
    return program;
}

void ensureTextures(Host* host);

bool initializeProgramAndTextures(Host* host, std::string& errorMessage) {
    if (!loadGlApi(&host->gl, errorMessage)) return false;
    host->program = buildProgram(host->gl, host->fragmentSource, errorMessage);
    if (host->program == 0) return false;
    host->gl.genVertexArrays(1, &host->vertexArray);
    host->gl.bindVertexArray(host->vertexArray);
    ensureTextures(host);
    return true;
}

void createTexture(GLuint* texture, int width, int height, const void* pixels) {
    if (*texture == 0) {
        glGenTextures(1, texture);
    }
    glBindTexture(GL_TEXTURE_2D, *texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
}

void ensureTextures(Host* host) {
    if (host->frequencyTexture == 0) {
        std::array<uint8_t, kBandCount * 4> empty{};
        for (int index = 0; index < kBandCount; ++index) {
            empty[index * 4 + 3] = 255;
        }
        createTexture(&host->frequencyTexture, kBandCount, 1, empty.data());
    }
    if (host->albumArtTexture == 0) {
        const std::array<uint8_t, 4> white = {255, 255, 255, 255};
        createTexture(&host->albumArtTexture, 1, 1, white.data());
        host->albumArtWidth = 1;
        host->albumArtHeight = 1;
    }
}

float average(const std::vector<float>& values, int start, int end) {
    start = std::max(start, 0);
    end = std::min(end, static_cast<int>(values.size()));
    if (start >= end) return 0.0f;
    float total = 0.0f;
    for (int index = start; index < end; ++index) total += values[index];
    return std::clamp(total / static_cast<float>(end - start), 0.0f, 1.0f);
}

void copyColor(JNIEnv* env, jfloatArray source, float destination[4], const float fallback[4]) {
    if (source == nullptr || env->GetArrayLength(source) < 4) {
        std::memcpy(destination, fallback, sizeof(float) * 4);
        return;
    }
    env->GetFloatArrayRegion(source, 0, 4, destination);
}

GLint uniform(const GlApi& gl, GLuint program, const char* name) {
    return gl.getUniformLocation(program, name);
}

void destroyHost(Host* host) {
    if (host == nullptr) return;
    releaseContextAndWindow(host);
    delete host;
}

HWND componentHwnd(JNIEnv* env, jobject component) {
    JAWT awt{};
    awt.version = JAWT_VERSION_1_4;
    if (JAWT_GetAWT(env, &awt) == JNI_FALSE) return nullptr;

    JAWT_DrawingSurface* surface = awt.GetDrawingSurface(env, component);
    if (surface == nullptr) return nullptr;

    HWND hwnd = nullptr;
    const jint lock = surface->Lock(surface);
    if ((lock & JAWT_LOCK_ERROR) == 0) {
        JAWT_DrawingSurfaceInfo* info = surface->GetDrawingSurfaceInfo(surface);
        if (info != nullptr && info->platformInfo != nullptr) {
            auto* winInfo = static_cast<JAWT_Win32DrawingSurfaceInfo*>(info->platformInfo);
            hwnd = winInfo->hwnd;
            surface->FreeDrawingSurfaceInfo(info);
        }
        surface->Unlock(surface);
    }
    awt.FreeDrawingSurface(surface);
    return hwnd;
}

bool attachToComponent(JNIEnv* env, Host* host, jobject component, std::string& errorMessage) {
    HWND hwnd = componentHwnd(env, component);
    if (hwnd == nullptr) {
        errorMessage = "Could not resolve AWT Canvas HWND for OpenGL visualizer.";
        return false;
    }
    if (host->surfaceMode && host->window == hwnd && host->glContext != nullptr) {
        return wglMakeCurrent(host->deviceContext, host->glContext) == TRUE;
    }

    releaseContextAndWindow(host);
    host->window = hwnd;
    host->ownsWindow = false;
    host->surfaceMode = true;
    host->deviceContext = GetDC(hwnd);
    if (host->deviceContext == nullptr) {
        errorMessage = "Failed to get AWT Canvas device context for OpenGL visualizer.";
        return false;
    }

    PIXELFORMATDESCRIPTOR pixelFormat{};
    pixelFormat.nSize = sizeof(pixelFormat);
    pixelFormat.nVersion = 1;
    pixelFormat.dwFlags = PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER;
    pixelFormat.iPixelType = PFD_TYPE_RGBA;
    pixelFormat.cColorBits = 32;
    pixelFormat.cAlphaBits = 8;
    pixelFormat.cDepthBits = 0;
    pixelFormat.iLayerType = PFD_MAIN_PLANE;
    if (GetPixelFormat(host->deviceContext) == 0) {
        const int format = ChoosePixelFormat(host->deviceContext, &pixelFormat);
        if (format == 0 || !SetPixelFormat(host->deviceContext, format, &pixelFormat)) {
            errorMessage = "Failed to set AWT Canvas OpenGL pixel format.";
            return false;
        }
    }
    host->glContext = wglCreateContext(host->deviceContext);
    if (host->glContext == nullptr || !wglMakeCurrent(host->deviceContext, host->glContext)) {
        errorMessage = "Failed to create AWT Canvas OpenGL context.";
        return false;
    }
    return initializeProgramAndTextures(host, errorMessage);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_naviamp_ui_NativeOpenGlVisualizerHost_nativeCreate(JNIEnv* env, jobject thiz, jstring fragmentSource) {
    auto* host = new Host();
    host->fragmentSource = jstringToString(env, fragmentSource);
    std::string errorMessage;
    if (!createContext(host, errorMessage)) {
        destroyHost(host);
        throwIllegalState(env, errorMessage);
        return 0;
    }
    if (!initializeProgramAndTextures(host, errorMessage)) {
        destroyHost(host);
        throwIllegalState(env, "OpenGL visualizer shader failed: " + errorMessage);
        return 0;
    }
    return reinterpret_cast<jlong>(host);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_naviamp_ui_NativeOpenGlVisualizerHost_nativeRenderImage(
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
    auto* host = reinterpret_cast<Host*>(handle);
    if (host == nullptr || host->program == 0) return nullptr;
    if (!wglMakeCurrent(host->deviceContext, host->glContext)) return nullptr;

    width = std::max(width, 1);
    height = std::max(height, 1);
    const int renderWidth = std::max(static_cast<int>(width * std::clamp(renderScale, 0.25f, 1.0f)), 64);
    const int renderHeight = std::max(static_cast<int>(height * std::clamp(renderScale, 0.25f, 1.0f)), 64);
    if (host->width != renderWidth || host->height != renderHeight) {
        host->width = renderWidth;
        host->height = renderHeight;
        SetWindowPos(host->window, nullptr, 0, 0, renderWidth, renderHeight, SWP_NOACTIVATE | SWP_NOMOVE | SWP_NOZORDER);
    }

    std::vector<float> bands(kBandCount, 0.0f);
    if (bandsArray != nullptr) {
        const jsize length = std::min<jsize>(env->GetArrayLength(bandsArray), kBandCount);
        env->GetFloatArrayRegion(bandsArray, 0, length, bands.data());
    }
    std::array<uint8_t, kBandCount * 4> frequencyPixels{};
    for (int index = 0; index < kBandCount; ++index) {
        const uint8_t value = static_cast<uint8_t>(std::clamp(bands[index], 0.0f, 1.0f) * 255.0f);
        frequencyPixels[index * 4] = value;
        frequencyPixels[index * 4 + 1] = value;
        frequencyPixels[index * 4 + 2] = value;
        frequencyPixels[index * 4 + 3] = 255;
    }
    ensureTextures(host);
    host->gl.activeTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, host->frequencyTexture);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, kBandCount, 1, GL_RGBA, GL_UNSIGNED_BYTE, frequencyPixels.data());

    float accentValue[4];
    float readableValue[4];
    float colorAValue[4];
    float colorBValue[4];
    float colorCValue[4];
    const float white[4] = {1.0f, 1.0f, 1.0f, 1.0f};
    const float black[4] = {0.0f, 0.0f, 0.0f, 1.0f};
    copyColor(env, accent, accentValue, white);
    copyColor(env, readable, readableValue, white);
    copyColor(env, colorA, colorAValue, black);
    copyColor(env, colorB, colorBValue, black);
    copyColor(env, colorC, colorCValue, black);

    const float bass = average(bands, 0, 8);
    const float mids = average(bands, 8, 20);
    const float highs = average(bands, 20, kBandCount);
    const float energy = average(bands, 0, kBandCount);
    float weightedTotal = 0.0f;
    float total = 0.0001f;
    for (int index = 0; index < kBandCount; ++index) {
        weightedTotal += (static_cast<float>(index) / static_cast<float>(std::max(kBandCount - 1, 1))) * bands[index];
        total += std::max(bands[index], 0.0f);
    }
    const float spectralCentroid = std::clamp(weightedTotal / total, 0.0f, 1.0f);
    const float beatDetected = (bass > 0.68f && bass > mids * 1.18f && energy > 0.34f) ? 1.0f : 0.0f;

    glViewport(0, 0, renderWidth, renderHeight);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    host->gl.useProgram(host->program);
    host->gl.bindVertexArray(host->vertexArray);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_time"), timeSeconds);
    host->gl.uniform2f(uniform(host->gl, host->program, "u_resolution"), static_cast<float>(renderWidth), static_cast<float>(renderHeight));
    host->gl.uniform1f(uniform(host->gl, host->program, "u_energyLevel"), energy);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_bassLevel"), bass);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_midLevel"), mids);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_trebleLevel"), highs);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_spectralCentroid"), spectralCentroid);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_tempoBpm"), std::clamp(tempoBpm, 60.0f, 220.0f));
    host->gl.uniform1f(uniform(host->gl, host->program, "u_beatDetected"), beatDetected);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_active"), active == JNI_TRUE ? 1.0f : 0.0f);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_renderScale"), renderScale);
    host->gl.uniform1i(uniform(host->gl, host->program, "u_maxRaymarchSteps"), maxRaymarchSteps);
    host->gl.uniform2f(uniform(host->gl, host->program, "u_albumArtSize"), static_cast<float>(host->albumArtWidth), static_cast<float>(host->albumArtHeight));
    host->gl.uniform4fv(uniform(host->gl, host->program, "u_accent"), 1, accentValue);
    host->gl.uniform4fv(uniform(host->gl, host->program, "u_readable"), 1, readableValue);
    host->gl.uniform4fv(uniform(host->gl, host->program, "u_colorA"), 1, colorAValue);
    host->gl.uniform4fv(uniform(host->gl, host->program, "u_colorB"), 1, colorBValue);
    host->gl.uniform4fv(uniform(host->gl, host->program, "u_colorC"), 1, colorCValue);
    host->gl.activeTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, host->frequencyTexture);
    host->gl.uniform1i(uniform(host->gl, host->program, "u_frequencyTexture"), 0);
    host->gl.activeTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, host->albumArtTexture);
    host->gl.uniform1i(uniform(host->gl, host->program, "u_albumArtTexture"), 1);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glFinish();

    const int byteCount = renderWidth * renderHeight * 4;
    std::vector<uint8_t> bottomUpPixels(byteCount);
    std::vector<uint8_t> topDownPixels(byteCount);
    glReadPixels(0, 0, renderWidth, renderHeight, GL_BGRA, GL_UNSIGNED_BYTE, bottomUpPixels.data());
    const int rowBytes = renderWidth * 4;
    for (int row = 0; row < renderHeight; ++row) {
        std::memcpy(
            topDownPixels.data() + row * rowBytes,
            bottomUpPixels.data() + (renderHeight - 1 - row) * rowBytes,
            rowBytes
        );
    }

    jbyteArray result = env->NewByteArray(byteCount);
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(result, 0, byteCount, reinterpret_cast<const jbyte*>(topDownPixels.data()));
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_ui_NativeOpenGlVisualizerHost_nativeRenderSurface(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jobject component,
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
    auto* host = reinterpret_cast<Host*>(handle);
    if (host == nullptr || component == nullptr) return JNI_FALSE;

    std::string errorMessage;
    if (!attachToComponent(env, host, component, errorMessage)) {
        throwIllegalState(env, errorMessage);
        return JNI_FALSE;
    }
    if (host->program == 0) return JNI_FALSE;

    width = std::max(width, 1);
    height = std::max(height, 1);
    host->width = width;
    host->height = height;

    std::vector<float> bands(kBandCount, 0.0f);
    if (bandsArray != nullptr) {
        const jsize length = std::min<jsize>(env->GetArrayLength(bandsArray), kBandCount);
        env->GetFloatArrayRegion(bandsArray, 0, length, bands.data());
    }
    std::array<uint8_t, kBandCount * 4> frequencyPixels{};
    for (int index = 0; index < kBandCount; ++index) {
        const uint8_t value = static_cast<uint8_t>(std::clamp(bands[index], 0.0f, 1.0f) * 255.0f);
        frequencyPixels[index * 4] = value;
        frequencyPixels[index * 4 + 1] = value;
        frequencyPixels[index * 4 + 2] = value;
        frequencyPixels[index * 4 + 3] = 255;
    }
    ensureTextures(host);
    host->gl.activeTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, host->frequencyTexture);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, kBandCount, 1, GL_RGBA, GL_UNSIGNED_BYTE, frequencyPixels.data());

    float accentValue[4];
    float readableValue[4];
    float colorAValue[4];
    float colorBValue[4];
    float colorCValue[4];
    const float white[4] = {1.0f, 1.0f, 1.0f, 1.0f};
    const float black[4] = {0.0f, 0.0f, 0.0f, 1.0f};
    copyColor(env, accent, accentValue, white);
    copyColor(env, readable, readableValue, white);
    copyColor(env, colorA, colorAValue, black);
    copyColor(env, colorB, colorBValue, black);
    copyColor(env, colorC, colorCValue, black);

    const float bass = average(bands, 0, 8);
    const float mids = average(bands, 8, 20);
    const float highs = average(bands, 20, kBandCount);
    const float energy = average(bands, 0, kBandCount);
    float weightedTotal = 0.0f;
    float total = 0.0001f;
    for (int index = 0; index < kBandCount; ++index) {
        weightedTotal += (static_cast<float>(index) / static_cast<float>(std::max(kBandCount - 1, 1))) * bands[index];
        total += std::max(bands[index], 0.0f);
    }
    const float spectralCentroid = std::clamp(weightedTotal / total, 0.0f, 1.0f);
    const float beatDetected = (bass > 0.68f && bass > mids * 1.18f && energy > 0.34f) ? 1.0f : 0.0f;

    glViewport(0, 0, width, height);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    host->gl.useProgram(host->program);
    host->gl.bindVertexArray(host->vertexArray);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_time"), timeSeconds);
    host->gl.uniform2f(uniform(host->gl, host->program, "u_resolution"), static_cast<float>(width), static_cast<float>(height));
    host->gl.uniform1f(uniform(host->gl, host->program, "u_energyLevel"), energy);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_bassLevel"), bass);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_midLevel"), mids);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_trebleLevel"), highs);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_spectralCentroid"), spectralCentroid);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_tempoBpm"), std::clamp(tempoBpm, 60.0f, 220.0f));
    host->gl.uniform1f(uniform(host->gl, host->program, "u_beatDetected"), beatDetected);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_active"), active == JNI_TRUE ? 1.0f : 0.0f);
    host->gl.uniform1f(uniform(host->gl, host->program, "u_renderScale"), renderScale);
    host->gl.uniform1i(uniform(host->gl, host->program, "u_maxRaymarchSteps"), maxRaymarchSteps);
    host->gl.uniform2f(uniform(host->gl, host->program, "u_albumArtSize"), static_cast<float>(host->albumArtWidth), static_cast<float>(host->albumArtHeight));
    host->gl.uniform4fv(uniform(host->gl, host->program, "u_accent"), 1, accentValue);
    host->gl.uniform4fv(uniform(host->gl, host->program, "u_readable"), 1, readableValue);
    host->gl.uniform4fv(uniform(host->gl, host->program, "u_colorA"), 1, colorAValue);
    host->gl.uniform4fv(uniform(host->gl, host->program, "u_colorB"), 1, colorBValue);
    host->gl.uniform4fv(uniform(host->gl, host->program, "u_colorC"), 1, colorCValue);
    host->gl.activeTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, host->frequencyTexture);
    host->gl.uniform1i(uniform(host->gl, host->program, "u_frequencyTexture"), 0);
    host->gl.activeTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, host->albumArtTexture);
    host->gl.uniform1i(uniform(host->gl, host->program, "u_albumArtTexture"), 1);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    SwapBuffers(host->deviceContext);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_app_naviamp_ui_NativeOpenGlVisualizerHost_nativeUpdateAlbumArt(
    JNIEnv* env,
    jobject thiz,
    jlong handle,
    jint width,
    jint height,
    jbyteArray rgbaPixels
) {
    auto* host = reinterpret_cast<Host*>(handle);
    if (host == nullptr || rgbaPixels == nullptr) return;
    if (!wglMakeCurrent(host->deviceContext, host->glContext)) return;
    width = std::max(width, 1);
    height = std::max(height, 1);
    const int expectedBytes = width * height * 4;
    const jsize byteCount = env->GetArrayLength(rgbaPixels);
    if (byteCount < expectedBytes) return;
    jbyte* pixels = env->GetByteArrayElements(rgbaPixels, nullptr);
    if (pixels == nullptr) return;
    createTexture(&host->albumArtTexture, width, height, pixels);
    host->albumArtWidth = width;
    host->albumArtHeight = height;
    env->ReleaseByteArrayElements(rgbaPixels, pixels, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_app_naviamp_ui_NativeOpenGlVisualizerHost_nativeDispose(JNIEnv* env, jobject thiz, jlong handle) {
    destroyHost(reinterpret_cast<Host*>(handle));
}
