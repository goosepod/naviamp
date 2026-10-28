# The Android BASS bridge uses name-based JNI exports from naviamp_bass_jni.cpp.
# Preserve this one native boundary while allowing R8 to optimize the rest of the app.
-keep class app.naviamp.android.playback.AndroidBassJni {
    native <methods>;
}

# BASS FILEPROCS invokes these shared cursor methods by JNI name/signature.
-keep class app.naviamp.domain.playback.BassFileCallbacks {
    public *;
}

# Keep source and line information so the archived mapping file can retrace release crashes.
-keepattributes SourceFile,LineNumberTable

# The shared JNI end-sync bridge invokes this common callback by name/signature.
-keep class app.naviamp.domain.playback.BassEndSyncCallback {
    public void onEndSync(int);
}
