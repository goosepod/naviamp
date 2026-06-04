#pragma once

#include <jni.h>

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeBassVersion(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeLastErrorCode(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeBassVersion(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeLastErrorCode(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeMixerVersion(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeInit(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT void JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeFree(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeConfigureInternetStreams(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeCreateUrlStream(JNIEnv* env, jobject thiz, jstring url);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeCreateFileStream(JNIEnv* env, jobject thiz, jstring path);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeCreateUrlDecodeStream(JNIEnv* env, jobject thiz, jstring url);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeCreateFileDecodeStream(JNIEnv* env, jobject thiz, jstring path);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeCreateMixer(JNIEnv* env, jobject thiz, jint frequency, jint channels, jboolean queueSources);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeChannelInfoFrequency(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeChannelInfoChannels(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeAddMixerChannel(JNIEnv* env, jobject thiz, jint mixer, jint stream);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeRemoveMixerChannel(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeSetEndSync(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativePlay(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativePause(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeStop(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeFreeStream(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeActiveState(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeSetVolume(JNIEnv* env, jobject thiz, jint stream, jfloat volume);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeSlideVolume(JNIEnv* env, jobject thiz, jint stream, jfloat volume, jint millis);

extern "C" JNIEXPORT jboolean JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeSeek(JNIEnv* env, jobject thiz, jint stream, jdouble seconds);

extern "C" JNIEXPORT jdouble JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativePositionSeconds(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jdouble JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeDurationSeconds(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jlong JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeLengthBytes(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeStreamTags(JNIEnv* env, jobject thiz, jint stream);

extern "C" JNIEXPORT jfloatArray JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeFft(JNIEnv* env, jobject thiz, jint stream, jint bins);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeReadFloatData(JNIEnv* env, jobject thiz, jint stream, jfloatArray output);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_DesktopBassJniBinding_nativeLoadPlugin(JNIEnv* env, jobject thiz, jstring path);
