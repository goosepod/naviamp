#include "naviamp_bass_jni.h"

#include "bass.h"
#include "bassmix.h"

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
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeBassVersion(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_GetVersion());
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
Java_app_naviamp_android_playback_AndroidBassJni_nativeSetMixerVolumeEnvelope(JNIEnv* env, jobject thiz, jint stream, jlongArray positions, jfloatArray volumes) {
    (void)thiz;
    if (positions == nullptr || volumes == nullptr) return JNI_FALSE;
    jsize positionCount = env->GetArrayLength(positions);
    jsize volumeCount = env->GetArrayLength(volumes);
    jsize count = std::min(positionCount, volumeCount);
    if (count <= 0) return JNI_TRUE;

    jlong* positionValues = env->GetLongArrayElements(positions, nullptr);
    jfloat* volumeValues = env->GetFloatArrayElements(volumes, nullptr);
    if (positionValues == nullptr || volumeValues == nullptr) {
        if (positionValues != nullptr) env->ReleaseLongArrayElements(positions, positionValues, JNI_ABORT);
        if (volumeValues != nullptr) env->ReleaseFloatArrayElements(volumes, volumeValues, JNI_ABORT);
        return JNI_FALSE;
    }

    std::vector<BASS_MIXER_NODE> nodes(static_cast<size_t>(count));
    for (jsize index = 0; index < count; ++index) {
        nodes[static_cast<size_t>(index)].pos = static_cast<QWORD>(std::max<jlong>(0, positionValues[index]));
        nodes[static_cast<size_t>(index)].value = std::max(0.0f, std::min(1.0f, volumeValues[index]));
    }
    env->ReleaseLongArrayElements(positions, positionValues, JNI_ABORT);
    env->ReleaseFloatArrayElements(volumes, volumeValues, JNI_ABORT);

    return BASS_Mixer_ChannelSetEnvelope(
        static_cast<DWORD>(stream),
        BASS_MIXER_ENV_VOL,
        nodes.data(),
        static_cast<DWORD>(nodes.size())
    ) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativePositionBytes(JNIEnv* env, jobject thiz, jint stream) {
    (void)env;
    (void)thiz;
    QWORD bytes = BASS_ChannelGetPosition(static_cast<DWORD>(stream), BASS_POS_BYTE);
    if (bytes == static_cast<QWORD>(-1)) return -1;
    return static_cast<jlong>(bytes);
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeSecondsToBytes(JNIEnv* env, jobject thiz, jint stream, jdouble seconds) {
    (void)env;
    (void)thiz;
    QWORD bytes = BASS_ChannelSeconds2Bytes(static_cast<DWORD>(stream), seconds);
    if (bytes == static_cast<QWORD>(-1)) return -1;
    return static_cast<jlong>(bytes);
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
Java_app_naviamp_desktop_playback_bass_BassJniBinding_nativeBassVersion(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_GetVersion());
}

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_BassJniBinding_nativeLastErrorCode(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return static_cast<jint>(BASS_ErrorGetCode());
}
