#include "naviamp_bass_jni.h"

#include "bass.h"

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
