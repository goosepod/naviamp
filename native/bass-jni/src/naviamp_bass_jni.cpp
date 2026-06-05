#if defined(_WIN32) && !defined(NOMINMAX)
#define NOMINMAX
#endif

#include "naviamp_bass_jni.h"

#include "bass.h"
#include "bassmix.h"

#if defined(_WIN32) && !defined(__ANDROID__)
#include <windows.h>
#endif

#include <algorithm>
#include <cmath>
#include <string>
#include <vector>

namespace {
constexpr DWORD PLAYBACK_BUFFER_MILLIS = 1500;
constexpr DWORD UPDATE_PERIOD_MILLIS = 10;
constexpr DWORD DEVICE_BUFFER_MILLIS = 60;
constexpr DWORD NETWORK_BUFFER_MILLIS = 5000;
constexpr DWORD NETWORK_TIMEOUT_MILLIS = 15000;
constexpr DWORD NETWORK_PREBUFFER_PERCENT = 75;
constexpr DWORD NETWORK_PLAYLIST_DEPTH = 5;

#if defined(_WIN32) && !defined(__ANDROID__)
struct BassApi {
    using StreamCreateUrlProc = HSTREAM(WINAPI*)(const char*, DWORD, DWORD, DOWNLOADPROC*, void*);
    using StreamCreateFileProc = HSTREAM(WINAPI*)(BOOL, const void*, QWORD, QWORD, DWORD);
    using PluginLoadProc = HPLUGIN(WINAPI*)(const char*, DWORD);

    HMODULE bass = nullptr;
    HMODULE bassmix = nullptr;
    decltype(&::BASS_SetConfig) BASS_SetConfig = nullptr;
    StreamCreateUrlProc BASS_StreamCreateURL = nullptr;
    StreamCreateFileProc BASS_StreamCreateFile = nullptr;
    decltype(&::BASS_ChannelGetInfo) BASS_ChannelGetInfo = nullptr;
    decltype(&::BASS_Mixer_StreamAddChannel) BASS_Mixer_StreamAddChannel = nullptr;
    decltype(&::BASS_Mixer_StreamCreate) BASS_Mixer_StreamCreate = nullptr;
    decltype(&::BASS_ChannelSlideAttribute) BASS_ChannelSlideAttribute = nullptr;
    decltype(&::BASS_ChannelSeconds2Bytes) BASS_ChannelSeconds2Bytes = nullptr;
    decltype(&::BASS_Mixer_ChannelSetPosition) BASS_Mixer_ChannelSetPosition = nullptr;
    decltype(&::BASS_ChannelSetPosition) BASS_ChannelSetPosition = nullptr;
    decltype(&::BASS_ChannelGetPosition) BASS_ChannelGetPosition = nullptr;
    decltype(&::BASS_ChannelBytes2Seconds) BASS_ChannelBytes2Seconds = nullptr;
    decltype(&::BASS_ChannelGetLength) BASS_ChannelGetLength = nullptr;
    decltype(&::BASS_ChannelGetData) BASS_ChannelGetData = nullptr;
    decltype(&::BASS_GetVersion) BASS_GetVersion = nullptr;
    decltype(&::BASS_ErrorGetCode) BASS_ErrorGetCode = nullptr;
    decltype(&::BASS_Mixer_GetVersion) BASS_Mixer_GetVersion = nullptr;
    decltype(&::BASS_Init) BASS_Init = nullptr;
    decltype(&::BASS_Free) BASS_Free = nullptr;
    decltype(&::BASS_Mixer_ChannelRemove) BASS_Mixer_ChannelRemove = nullptr;
    decltype(&::BASS_ChannelSetSync) BASS_ChannelSetSync = nullptr;
    decltype(&::BASS_ChannelPlay) BASS_ChannelPlay = nullptr;
    decltype(&::BASS_ChannelPause) BASS_ChannelPause = nullptr;
    decltype(&::BASS_ChannelStop) BASS_ChannelStop = nullptr;
    decltype(&::BASS_StreamFree) BASS_StreamFree = nullptr;
    decltype(&::BASS_ChannelIsActive) BASS_ChannelIsActive = nullptr;
    decltype(&::BASS_ChannelSetAttribute) BASS_ChannelSetAttribute = nullptr;
    decltype(&::BASS_ChannelGetTags) BASS_ChannelGetTags = nullptr;
    PluginLoadProc BASS_PluginLoad = nullptr;
};

BassApi bassApi;

template <typename T>
bool load_symbol(HMODULE module, const char* name, T& target) {
    target = reinterpret_cast<T>(GetProcAddress(module, name));
    return target != nullptr;
}

bool load_bass_symbols() {
    if (bassApi.bass != nullptr && bassApi.bassmix != nullptr) return true;

    bassApi.bass = GetModuleHandleW(L"bass.dll");
    if (bassApi.bass == nullptr) bassApi.bass = LoadLibraryW(L"bass.dll");
    bassApi.bassmix = GetModuleHandleW(L"bassmix.dll");
    if (bassApi.bassmix == nullptr) bassApi.bassmix = LoadLibraryW(L"bassmix.dll");
    if (bassApi.bass == nullptr || bassApi.bassmix == nullptr) return false;

    bool ok = true;
    ok = load_symbol(bassApi.bass, "BASS_SetConfig", bassApi.BASS_SetConfig) && ok;
    ok = load_symbol(bassApi.bass, "BASS_StreamCreateURL", bassApi.BASS_StreamCreateURL) && ok;
    ok = load_symbol(bassApi.bass, "BASS_StreamCreateFile", bassApi.BASS_StreamCreateFile) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelGetInfo", bassApi.BASS_ChannelGetInfo) && ok;
    ok = load_symbol(bassApi.bassmix, "BASS_Mixer_StreamAddChannel", bassApi.BASS_Mixer_StreamAddChannel) && ok;
    ok = load_symbol(bassApi.bassmix, "BASS_Mixer_StreamCreate", bassApi.BASS_Mixer_StreamCreate) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelSlideAttribute", bassApi.BASS_ChannelSlideAttribute) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelSeconds2Bytes", bassApi.BASS_ChannelSeconds2Bytes) && ok;
    ok = load_symbol(bassApi.bassmix, "BASS_Mixer_ChannelSetPosition", bassApi.BASS_Mixer_ChannelSetPosition) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelSetPosition", bassApi.BASS_ChannelSetPosition) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelGetPosition", bassApi.BASS_ChannelGetPosition) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelBytes2Seconds", bassApi.BASS_ChannelBytes2Seconds) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelGetLength", bassApi.BASS_ChannelGetLength) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelGetData", bassApi.BASS_ChannelGetData) && ok;
    ok = load_symbol(bassApi.bass, "BASS_GetVersion", bassApi.BASS_GetVersion) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ErrorGetCode", bassApi.BASS_ErrorGetCode) && ok;
    ok = load_symbol(bassApi.bassmix, "BASS_Mixer_GetVersion", bassApi.BASS_Mixer_GetVersion) && ok;
    ok = load_symbol(bassApi.bass, "BASS_Init", bassApi.BASS_Init) && ok;
    ok = load_symbol(bassApi.bass, "BASS_Free", bassApi.BASS_Free) && ok;
    ok = load_symbol(bassApi.bassmix, "BASS_Mixer_ChannelRemove", bassApi.BASS_Mixer_ChannelRemove) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelSetSync", bassApi.BASS_ChannelSetSync) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelPlay", bassApi.BASS_ChannelPlay) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelPause", bassApi.BASS_ChannelPause) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelStop", bassApi.BASS_ChannelStop) && ok;
    ok = load_symbol(bassApi.bass, "BASS_StreamFree", bassApi.BASS_StreamFree) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelIsActive", bassApi.BASS_ChannelIsActive) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelSetAttribute", bassApi.BASS_ChannelSetAttribute) && ok;
    ok = load_symbol(bassApi.bass, "BASS_ChannelGetTags", bassApi.BASS_ChannelGetTags) && ok;
    ok = load_symbol(bassApi.bass, "BASS_PluginLoad", bassApi.BASS_PluginLoad) && ok;
    return ok;
}

#define BASS_SetConfig bassApi.BASS_SetConfig
#define BASS_StreamCreateURL bassApi.BASS_StreamCreateURL
#define BASS_StreamCreateFile bassApi.BASS_StreamCreateFile
#define BASS_ChannelGetInfo bassApi.BASS_ChannelGetInfo
#define BASS_Mixer_StreamAddChannel bassApi.BASS_Mixer_StreamAddChannel
#define BASS_Mixer_StreamCreate bassApi.BASS_Mixer_StreamCreate
#define BASS_ChannelSlideAttribute bassApi.BASS_ChannelSlideAttribute
#define BASS_ChannelSeconds2Bytes bassApi.BASS_ChannelSeconds2Bytes
#define BASS_Mixer_ChannelSetPosition bassApi.BASS_Mixer_ChannelSetPosition
#define BASS_ChannelSetPosition bassApi.BASS_ChannelSetPosition
#define BASS_ChannelGetPosition bassApi.BASS_ChannelGetPosition
#define BASS_ChannelBytes2Seconds bassApi.BASS_ChannelBytes2Seconds
#define BASS_ChannelGetLength bassApi.BASS_ChannelGetLength
#define BASS_ChannelGetData bassApi.BASS_ChannelGetData
#define BASS_GetVersion bassApi.BASS_GetVersion
#define BASS_ErrorGetCode bassApi.BASS_ErrorGetCode
#define BASS_Mixer_GetVersion bassApi.BASS_Mixer_GetVersion
#define BASS_Init bassApi.BASS_Init
#define BASS_Free bassApi.BASS_Free
#define BASS_Mixer_ChannelRemove bassApi.BASS_Mixer_ChannelRemove
#define BASS_ChannelSetSync bassApi.BASS_ChannelSetSync
#define BASS_ChannelPlay bassApi.BASS_ChannelPlay
#define BASS_ChannelPause bassApi.BASS_ChannelPause
#define BASS_ChannelStop bassApi.BASS_ChannelStop
#define BASS_StreamFree bassApi.BASS_StreamFree
#define BASS_ChannelIsActive bassApi.BASS_ChannelIsActive
#define BASS_ChannelSetAttribute bassApi.BASS_ChannelSetAttribute
#define BASS_ChannelGetTags bassApi.BASS_ChannelGetTags
#define BASS_PluginLoad bassApi.BASS_PluginLoad
#endif

struct DesktopEndSyncRegistration {
    JavaVM* javaVm;
    jobject binding;
    jmethodID callbackMethod;
};

void configure_playback_buffers() {
    BASS_SetConfig(BASS_CONFIG_UPDATEPERIOD, UPDATE_PERIOD_MILLIS);
    BASS_SetConfig(BASS_CONFIG_BUFFER, PLAYBACK_BUFFER_MILLIS);
    BASS_SetConfig(BASS_CONFIG_DEV_BUFFER, DEVICE_BUFFER_MILLIS);
    BASS_SetConfig(BASS_CONFIG_NET_BUFFER, NETWORK_BUFFER_MILLIS);
    BASS_SetConfig(BASS_CONFIG_NET_PREBUF, NETWORK_PREBUFFER_PERCENT);
    BASS_SetConfig(BASS_CONFIG_NET_PREBUF_WAIT, 1);
    BASS_SetConfig(BASS_CONFIG_NET_TIMEOUT, NETWORK_TIMEOUT_MILLIS);
    BASS_SetConfig(BASS_CONFIG_NET_READTIMEOUT, NETWORK_TIMEOUT_MILLIS);
}

BOOL configure_internet_streams() {
    BOOL ok = TRUE;
    ok = BASS_SetConfig(BASS_CONFIG_NET_PLAYLIST, 1) && ok;
    ok = BASS_SetConfig(BASS_CONFIG_NET_META, 1) && ok;
    ok = BASS_SetConfig(BASS_CONFIG_NET_PLAYLIST_DEPTH, NETWORK_PLAYLIST_DEPTH) && ok;
    ok = BASS_SetConfig(BASS_CONFIG_NET_BUFFER, NETWORK_BUFFER_MILLIS) && ok;
    ok = BASS_SetConfig(BASS_CONFIG_NET_PREBUF, NETWORK_PREBUFFER_PERCENT) && ok;
    ok = BASS_SetConfig(BASS_CONFIG_NET_TIMEOUT, NETWORK_TIMEOUT_MILLIS) && ok;
    ok = BASS_SetConfig(BASS_CONFIG_NET_READTIMEOUT, NETWORK_TIMEOUT_MILLIS) && ok;
    return ok;
}

HSTREAM create_url_stream(const char* url, DWORD flags) {
    return BASS_StreamCreateURL(
        url,
        0,
        flags,
        nullptr,
        nullptr
    );
}

HSTREAM create_file_stream(const char* path, DWORD flags) {
    return BASS_StreamCreateFile(
        FALSE,
        path,
        0,
        0,
        flags
    );
}

DWORD stream_flags() {
    return BASS_STREAM_STATUS | BASS_SAMPLE_FLOAT;
}

DWORD file_flags() {
    return BASS_STREAM_PRESCAN | BASS_SAMPLE_FLOAT;
}

jint channel_info_frequency(jint stream) {
    BASS_CHANNELINFO info{};
    if (!BASS_ChannelGetInfo(static_cast<DWORD>(stream), &info)) return 0;
    return static_cast<jint>(info.freq);
}

jint channel_info_channels(jint stream) {
    BASS_CHANNELINFO info{};
    if (!BASS_ChannelGetInfo(static_cast<DWORD>(stream), &info)) return 0;
    return static_cast<jint>(info.chans);
}

jboolean add_mixer_channel(jint mixer, jint stream) {
    return BASS_Mixer_StreamAddChannel(
        static_cast<DWORD>(mixer),
        static_cast<DWORD>(stream),
        BASS_MIXER_CHAN_NORAMPIN
    ) ? JNI_TRUE : JNI_FALSE;
}

jint create_mixer(jint frequency, jint channels, jboolean queueSources) {
    DWORD flags = BASS_SAMPLE_FLOAT;
    if (queueSources == JNI_TRUE) flags |= BASS_MIXER_QUEUE;
    HSTREAM mixer = BASS_Mixer_StreamCreate(
        static_cast<DWORD>(std::max(1, static_cast<int>(frequency))),
        static_cast<DWORD>(std::max(1, static_cast<int>(channels))),
        flags
    );
    return static_cast<jint>(mixer);
}

jboolean slide_volume(jint stream, jfloat volume, jint millis) {
    float safeVolume = std::max(0.0f, std::min(volume, 4.0f));
    return BASS_ChannelSlideAttribute(
        static_cast<DWORD>(stream),
        BASS_ATTRIB_VOL,
        safeVolume,
        std::max(0, static_cast<int>(millis))
    ) ? JNI_TRUE : JNI_FALSE;
}

jboolean seek_seconds(jint stream, jdouble seconds) {
    QWORD bytes = BASS_ChannelSeconds2Bytes(static_cast<DWORD>(stream), seconds);
    if (bytes == static_cast<QWORD>(-1)) return JNI_FALSE;
    if (BASS_Mixer_ChannelSetPosition(static_cast<DWORD>(stream), bytes, BASS_POS_BYTE | BASS_POS_MIXER_RESET)) {
        return JNI_TRUE;
    }
    return BASS_ChannelSetPosition(static_cast<DWORD>(stream), bytes, BASS_POS_BYTE) ? JNI_TRUE : JNI_FALSE;
}

jdouble position_seconds(jint stream) {
    QWORD bytes = BASS_ChannelGetPosition(static_cast<DWORD>(stream), BASS_POS_BYTE);
    if (bytes == static_cast<QWORD>(-1)) return -1.0;
    return BASS_ChannelBytes2Seconds(static_cast<DWORD>(stream), bytes);
}

jdouble duration_seconds(jint stream) {
    QWORD bytes = BASS_ChannelGetLength(static_cast<DWORD>(stream), BASS_POS_BYTE);
    if (bytes == static_cast<QWORD>(-1)) return -1.0;
    return BASS_ChannelBytes2Seconds(static_cast<DWORD>(stream), bytes);
}

jlong length_bytes(jint stream) {
    QWORD bytes = BASS_ChannelGetLength(static_cast<DWORD>(stream), BASS_POS_BYTE);
    if (bytes == static_cast<QWORD>(-1)) return -1;
    return static_cast<jlong>(bytes);
}

void CALLBACK desktop_end_sync_proc(HSYNC handle, DWORD channel, DWORD data, void* user) {
    (void)handle;
    (void)data;
    auto* registration = static_cast<DesktopEndSyncRegistration*>(user);
    if (registration == nullptr || registration->javaVm == nullptr) return;

    JNIEnv* env = nullptr;
    bool didAttach = false;
    jint envResult = registration->javaVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (envResult == JNI_EDETACHED) {
#if defined(__ANDROID__)
        if (registration->javaVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
#else
        if (registration->javaVm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) return;
#endif
        didAttach = true;
    } else if (envResult != JNI_OK) {
        return;
    }

    env->CallVoidMethod(registration->binding, registration->callbackMethod, static_cast<jint>(channel));
    env->DeleteGlobalRef(registration->binding);
    if (didAttach) {
        registration->javaVm->DetachCurrentThread();
    }
    delete registration;
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
#if defined(_WIN32) && !defined(__ANDROID__)
    if (!load_bass_symbols()) return JNI_ERR;
#endif
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeBassVersion(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_GetVersion());
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeMixerVersion(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_Mixer_GetVersion());
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeLastErrorCode(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_ErrorGetCode());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeInit(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    configure_playback_buffers();
    if (BASS_Init(-1, 44100, 0, nullptr, nullptr)) {
        return JNI_TRUE;
    }
    if (BASS_ErrorGetCode() == BASS_ERROR_ALREADY) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeFree(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    BASS_Free();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeSetVerifyNet(JNIEnv* env, jobject thiz, jboolean verify) {
    (void)env;
    (void)thiz;
    return BASS_SetConfig(BASS_CONFIG_VERIFY_NET, verify == JNI_TRUE ? 1 : 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeConfigureInternetStreams(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return configure_internet_streams() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeCreateUrlStream(JNIEnv* env, jobject thiz, jstring url) {
    (void)thiz;
    const char* chars = env->GetStringUTFChars(url, nullptr);
    if (chars == nullptr) return 0;
    HSTREAM stream = BASS_StreamCreateURL(
        chars,
        0,
        BASS_STREAM_STATUS | BASS_SAMPLE_FLOAT,
        nullptr,
        nullptr
    );
    env->ReleaseStringUTFChars(url, chars);
    return static_cast<jint>(stream);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeCreateFileStream(JNIEnv* env, jobject thiz, jstring path) {
    (void)thiz;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return 0;
    HSTREAM stream = BASS_StreamCreateFile(
        FALSE,
        chars,
        0,
        0,
        BASS_STREAM_PRESCAN | BASS_SAMPLE_FLOAT
    );
    env->ReleaseStringUTFChars(path, chars);
    return static_cast<jint>(stream);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeCreateUrlDecodeStream(JNIEnv* env, jobject thiz, jstring url) {
    (void)thiz;
    const char* chars = env->GetStringUTFChars(url, nullptr);
    if (chars == nullptr) return 0;
    HSTREAM stream = BASS_StreamCreateURL(
        chars,
        0,
        BASS_STREAM_STATUS | BASS_SAMPLE_FLOAT | BASS_STREAM_DECODE,
        nullptr,
        nullptr
    );
    env->ReleaseStringUTFChars(url, chars);
    return static_cast<jint>(stream);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeCreateFileDecodeStream(JNIEnv* env, jobject thiz, jstring path) {
    (void)thiz;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return 0;
    HSTREAM stream = BASS_StreamCreateFile(
        FALSE,
        chars,
        0,
        0,
        BASS_STREAM_PRESCAN | BASS_SAMPLE_FLOAT | BASS_STREAM_DECODE
    );
    env->ReleaseStringUTFChars(path, chars);
    return static_cast<jint>(stream);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeCreateMixer(JNIEnv* env, jobject thiz, jint frequency, jint channels, jboolean queueSources) {
    (void)env;
    (void)thiz;
    DWORD flags = BASS_SAMPLE_FLOAT;
    if (queueSources == JNI_TRUE) flags |= BASS_MIXER_QUEUE;
    HSTREAM mixer = BASS_Mixer_StreamCreate(
        static_cast<DWORD>(std::max(1, static_cast<int>(frequency))),
        static_cast<DWORD>(std::max(1, static_cast<int>(channels))),
        flags
    );
    return static_cast<jint>(mixer);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeChannelInfoFrequency(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    BASS_CHANNELINFO info{};
    if (!BASS_ChannelGetInfo(static_cast<DWORD>(stream), &info)) return 0;
    return static_cast<jint>(info.freq);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeChannelInfoChannels(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    BASS_CHANNELINFO info{};
    if (!BASS_ChannelGetInfo(static_cast<DWORD>(stream), &info)) return 0;
    return static_cast<jint>(info.chans);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeAddMixerChannel(JNIEnv* env, jobject thiz, jint mixer, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_Mixer_StreamAddChannel(
        static_cast<DWORD>(mixer),
        static_cast<DWORD>(stream),
        BASS_MIXER_CHAN_NORAMPIN
    ) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeRemoveMixerChannel(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_Mixer_ChannelRemove(static_cast<DWORD>(stream)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeSlideVolume(JNIEnv* env, jobject thiz, jint stream, jfloat volume, jint millis) {
    (void)env;
    (void)thiz;
    float safeVolume = std::max(0.0f, std::min(volume, 4.0f));
    return BASS_ChannelSlideAttribute(
        static_cast<DWORD>(stream),
        BASS_ATTRIB_VOL,
        safeVolume,
        std::max(0, static_cast<int>(millis))
    ) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativePlay(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_ChannelPlay(static_cast<DWORD>(stream), FALSE) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativePause(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_ChannelPause(static_cast<DWORD>(stream)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeStop(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_ChannelStop(static_cast<DWORD>(stream)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeFreeStream(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_StreamFree(static_cast<HSTREAM>(stream)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeActiveState(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_ChannelIsActive(static_cast<DWORD>(stream)));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeSetVolume(JNIEnv* env, jobject thiz, jint stream, jfloat volume) {
    (void)env;
    (void)thiz;
    float safeVolume = std::max(0.0f, std::min(volume, 4.0f));
    return BASS_ChannelSetAttribute(static_cast<DWORD>(stream), BASS_ATTRIB_VOL, safeVolume) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeSeek(JNIEnv* env, jobject thiz, jint stream, jdouble seconds) {
    (void)env;
    (void)thiz;
    QWORD bytes = BASS_ChannelSeconds2Bytes(static_cast<DWORD>(stream), seconds);
    if (bytes == static_cast<QWORD>(-1)) return JNI_FALSE;
    if (BASS_Mixer_ChannelSetPosition(static_cast<DWORD>(stream), bytes, BASS_POS_BYTE | BASS_POS_MIXER_RESET)) {
        return JNI_TRUE;
    }
    return BASS_ChannelSetPosition(static_cast<DWORD>(stream), bytes, BASS_POS_BYTE) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativePositionSeconds(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    QWORD bytes = BASS_ChannelGetPosition(static_cast<DWORD>(stream), BASS_POS_BYTE);
    if (bytes == static_cast<QWORD>(-1)) return -1.0;
    return BASS_ChannelBytes2Seconds(static_cast<DWORD>(stream), bytes);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeDurationSeconds(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    QWORD bytes = BASS_ChannelGetLength(static_cast<DWORD>(stream), BASS_POS_BYTE);
    if (bytes == static_cast<QWORD>(-1)) return -1.0;
    return BASS_ChannelBytes2Seconds(static_cast<DWORD>(stream), bytes);
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeLengthBytes(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    QWORD bytes = BASS_ChannelGetLength(static_cast<DWORD>(stream), BASS_POS_BYTE);
    if (bytes == static_cast<QWORD>(-1)) return -1;
    return static_cast<jlong>(bytes);
}

static void append_string_tags(std::vector<std::string>& tags, const char* raw) {
    if (raw == nullptr) return;
    const char* cursor = raw;
    while (*cursor != '\0') {
        std::string entry(cursor);
        if (!entry.empty()) tags.push_back(entry);
        cursor += entry.size() + 1;
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeStreamTags(JNIEnv* env, jobject thiz, jint stream) {
    (void)thiz;
    std::vector<std::string> tags;
    append_string_tags(tags, BASS_ChannelGetTags(static_cast<DWORD>(stream), BASS_TAG_ICY));
    append_string_tags(tags, BASS_ChannelGetTags(static_cast<DWORD>(stream), BASS_TAG_HTTP));
    const char* meta = BASS_ChannelGetTags(static_cast<DWORD>(stream), BASS_TAG_META);
    if (meta != nullptr && meta[0] != '\0') {
        tags.emplace_back(std::string("StreamTitle=") + meta);
    }
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(tags.size()), stringClass, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(tags.size()); ++i) {
        jstring value = env->NewStringUTF(tags[static_cast<size_t>(i)].c_str());
        env->SetObjectArrayElement(result, i, value);
        env->DeleteLocalRef(value);
    }
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeFft(JNIEnv* env, jobject thiz, jint stream, jint bins) {
    (void)thiz;
    int safeBins = std::max(1, std::min(static_cast<int>(bins), 256));
    std::vector<float> buffer(512, 0.0f);
    DWORD request = BASS_DATA_FFT1024 | BASS_DATA_FFT_REMOVEDC;
    int read = BASS_ChannelGetData(static_cast<DWORD>(stream), buffer.data(), request);
    if (read < 0) {
        return env->NewFloatArray(0);
    }
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(safeBins));
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(safeBins), buffer.data());
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeReadFloatData(JNIEnv* env, jobject thiz, jint stream, jfloatArray output) {
    (void)thiz;
    jsize sampleCapacity = env->GetArrayLength(output);
    if (sampleCapacity <= 0) return 0;
    std::vector<float> buffer(static_cast<size_t>(sampleCapacity), 0.0f);
    int read = BASS_ChannelGetData(
        static_cast<DWORD>(stream),
        buffer.data(),
        static_cast<DWORD>(buffer.size() * sizeof(float))
    );
    if (read < 0) return -1;
    int sampleCount = read / static_cast<int>(sizeof(float));
    if (sampleCount <= 0) return 0;
    env->SetFloatArrayRegion(output, 0, static_cast<jsize>(sampleCount), buffer.data());
    return static_cast<jint>(sampleCount);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeBassVersion(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_GetVersion());
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeLastErrorCode(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_ErrorGetCode());
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeMixerVersion(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_Mixer_GetVersion());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeInit(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    configure_playback_buffers();
    if (BASS_Init(-1, 44100, 0, nullptr, nullptr)) {
        return JNI_TRUE;
    }
    if (BASS_ErrorGetCode() == BASS_ERROR_ALREADY) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeFree(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    BASS_Free();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeConfigureInternetStreams(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return configure_internet_streams() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeCreateUrlStream(JNIEnv* env, jobject thiz, jstring url) {
    (void)thiz;
    const char* chars = env->GetStringUTFChars(url, nullptr);
    if (chars == nullptr) return 0;
    HSTREAM stream = create_url_stream(chars, stream_flags());
    env->ReleaseStringUTFChars(url, chars);
    return static_cast<jint>(stream);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeCreateFileStream(JNIEnv* env, jobject thiz, jstring path) {
    (void)thiz;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return 0;
    HSTREAM stream = create_file_stream(chars, file_flags());
    env->ReleaseStringUTFChars(path, chars);
    return static_cast<jint>(stream);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeCreateUrlDecodeStream(JNIEnv* env, jobject thiz, jstring url) {
    (void)thiz;
    const char* chars = env->GetStringUTFChars(url, nullptr);
    if (chars == nullptr) return 0;
    HSTREAM stream = create_url_stream(chars, stream_flags() | BASS_STREAM_DECODE);
    env->ReleaseStringUTFChars(url, chars);
    return static_cast<jint>(stream);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeCreateFileDecodeStream(JNIEnv* env, jobject thiz, jstring path) {
    (void)thiz;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return 0;
    HSTREAM stream = create_file_stream(chars, file_flags() | BASS_STREAM_DECODE);
    env->ReleaseStringUTFChars(path, chars);
    return static_cast<jint>(stream);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeCreateMixer(JNIEnv* env, jobject thiz, jint frequency, jint channels, jboolean queueSources) {
    (void)env;
    (void)thiz;
    return create_mixer(frequency, channels, queueSources);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeChannelInfoFrequency(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return channel_info_frequency(stream);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeChannelInfoChannels(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return channel_info_channels(stream);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeAddMixerChannel(JNIEnv* env, jobject thiz, jint mixer, jint stream) {
    (void)env;
    (void)thiz;
    return add_mixer_channel(mixer, stream);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeRemoveMixerChannel(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_Mixer_ChannelRemove(static_cast<DWORD>(stream)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeSetEndSync(JNIEnv* env, jobject thiz, jint stream) {
    JavaVM* javaVm = nullptr;
    if (env->GetJavaVM(&javaVm) != JNI_OK || javaVm == nullptr) return 0;
    jclass bindingClass = env->GetObjectClass(thiz);
    if (bindingClass == nullptr) return 0;
    jmethodID callbackMethod = env->GetMethodID(bindingClass, "onEndSync", "(I)V");
    env->DeleteLocalRef(bindingClass);
    if (callbackMethod == nullptr) return 0;

    auto* registration = new DesktopEndSyncRegistration{
        javaVm,
        env->NewGlobalRef(thiz),
        callbackMethod,
    };
    HSYNC sync = BASS_ChannelSetSync(
        static_cast<DWORD>(stream),
        BASS_SYNC_END | BASS_SYNC_ONETIME,
        0,
        desktop_end_sync_proc,
        registration
    );
    if (sync == 0) {
        env->DeleteGlobalRef(registration->binding);
        delete registration;
    }
    return static_cast<jint>(sync);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativePlay(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_ChannelPlay(static_cast<DWORD>(stream), FALSE) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativePause(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_ChannelPause(static_cast<DWORD>(stream)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeStop(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_ChannelStop(static_cast<DWORD>(stream)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeFreeStream(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return BASS_StreamFree(static_cast<HSTREAM>(stream)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeActiveState(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_ChannelIsActive(static_cast<DWORD>(stream)));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeSetVolume(JNIEnv* env, jobject thiz, jint stream, jfloat volume) {
    (void)env;
    (void)thiz;
    float safeVolume = std::max(0.0f, std::min(volume, 4.0f));
    return BASS_ChannelSetAttribute(static_cast<DWORD>(stream), BASS_ATTRIB_VOL, safeVolume) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeSlideVolume(JNIEnv* env, jobject thiz, jint stream, jfloat volume, jint millis) {
    (void)env;
    (void)thiz;
    return slide_volume(stream, volume, millis);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeSeek(JNIEnv* env, jobject thiz, jint stream, jdouble seconds) {
    (void)env;
    (void)thiz;
    return seek_seconds(stream, seconds);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativePositionSeconds(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return position_seconds(stream);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeDurationSeconds(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return duration_seconds(stream);
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeLengthBytes(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    return length_bytes(stream);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeStreamTags(JNIEnv* env, jobject thiz, jint stream) {
    (void)thiz;
    std::vector<std::string> tags;
    append_string_tags(tags, BASS_ChannelGetTags(static_cast<DWORD>(stream), BASS_TAG_ICY));
    append_string_tags(tags, BASS_ChannelGetTags(static_cast<DWORD>(stream), BASS_TAG_HTTP));
    const char* meta = BASS_ChannelGetTags(static_cast<DWORD>(stream), BASS_TAG_META);
    if (meta != nullptr && meta[0] != '\0') {
        tags.emplace_back(std::string("StreamTitle=") + meta);
    }
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(tags.size()), stringClass, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(tags.size()); ++i) {
        jstring value = env->NewStringUTF(tags[static_cast<size_t>(i)].c_str());
        env->SetObjectArrayElement(result, i, value);
        env->DeleteLocalRef(value);
    }
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeFft(JNIEnv* env, jobject thiz, jint stream, jint bins) {
    (void)thiz;
    int safeBins = std::max(1, std::min(static_cast<int>(bins), 256));
    std::vector<float> buffer(512, 0.0f);
    DWORD request = BASS_DATA_FFT1024 | BASS_DATA_FFT_REMOVEDC;
    int read = BASS_ChannelGetData(static_cast<DWORD>(stream), buffer.data(), request);
    if (read < 0) {
        return env->NewFloatArray(0);
    }
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(safeBins));
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(safeBins), buffer.data());
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeReadFloatData(JNIEnv* env, jobject thiz, jint stream, jfloatArray output) {
    (void)thiz;
    jsize sampleCapacity = env->GetArrayLength(output);
    if (sampleCapacity <= 0) return 0;
    std::vector<float> buffer(static_cast<size_t>(sampleCapacity), 0.0f);
    int read = BASS_ChannelGetData(
        static_cast<DWORD>(stream),
        buffer.data(),
        static_cast<DWORD>(buffer.size() * sizeof(float))
    );
    if (read < 0) return -1;
    int sampleCount = read / static_cast<int>(sizeof(float));
    if (sampleCount <= 0) return 0;
    env->SetFloatArrayRegion(output, 0, static_cast<jsize>(sampleCount), buffer.data());
    return static_cast<jint>(sampleCount);
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeLoadPlugin(JNIEnv* env, jobject thiz, jstring path) {
    (void)thiz;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return 0;
    HPLUGIN plugin = BASS_PluginLoad(chars, 0);
    env->ReleaseStringUTFChars(path, chars);
    return static_cast<jint>(plugin);
}
