#pragma once

#include <jni.h>

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeBassVersion(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_android_playback_AndroidBassJni_nativeLastErrorCode(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_BassJniBinding_nativeBassVersion(JNIEnv* env, jobject thiz);

extern "C" JNIEXPORT jint JNICALL
Java_app_naviamp_desktop_playback_bass_BassJniBinding_nativeLastErrorCode(JNIEnv* env, jobject thiz);
