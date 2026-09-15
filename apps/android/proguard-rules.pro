# The Android BASS bridge uses name-based JNI exports from naviamp_bass_jni.cpp.
# Preserve this one native boundary while allowing R8 to optimize the rest of the app.
-keep class app.naviamp.android.playback.AndroidBassJni {
    native <methods>;
}

# Keep source and line information so the archived mapping file can retrace release crashes.
-keepattributes SourceFile,LineNumberTable
