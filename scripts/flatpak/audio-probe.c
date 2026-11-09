/* Test-only native ABI probe, run inside the installed Flatpak with real clocked output. */
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

static void pause_ms(int ms) { struct timespec t = {ms / 1000, (ms % 1000) * 1000000L}; nanosleep(&t, NULL); }
#define LOAD(name, type) type name = (type)dlsym(library, #name); if (!name) { fprintf(stderr, "Missing ABI %s\n", #name); return 1; }
typedef int (*Init)(int, uint32_t, uint32_t, void *, void *);
typedef uint32_t (*Create)(int, const void *, uint64_t, uint64_t, uint32_t);
typedef int (*Control)(uint32_t, int);
typedef int (*Pause)(uint32_t);
typedef int (*Attribute)(uint32_t, uint32_t, float);
typedef uint64_t (*Position)(uint32_t, uint32_t);
typedef double (*Seconds)(uint32_t, uint64_t);
typedef int (*Seek)(uint32_t, uint64_t, uint32_t);
typedef uint64_t (*Bytes)(uint32_t, double);
typedef int (*Free)(void);
typedef uint32_t (*Plugin)(const char *, uint32_t);
typedef int (*Error)(void);

int main(int argc, char **argv) {
    if (argc != 2) return 2;
    const char *base = "/app/lib/naviamp/lib/app/resources/playback/bass/linux-x64/";
    char path[512]; snprintf(path, sizeof(path), "%slibbass.so", base);
    void *library = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    if (!library) { fprintf(stderr, "%s\n", dlerror()); return 1; }
    LOAD(BASS_Init, Init); LOAD(BASS_StreamCreateFile, Create); LOAD(BASS_ChannelPlay, Control);
    LOAD(BASS_ChannelPause, Pause); LOAD(BASS_ChannelSetAttribute, Attribute);
    LOAD(BASS_ChannelGetPosition, Position); LOAD(BASS_ChannelBytes2Seconds, Seconds);
    LOAD(BASS_ChannelSetPosition, Seek); LOAD(BASS_ChannelSeconds2Bytes, Bytes);
    LOAD(BASS_PluginLoad, Plugin); LOAD(BASS_Free, Free);
    LOAD(BASS_ErrorGetCode, Error);
    LOAD(BASS_ChannelIsActive, Pause);
    if (!BASS_Init(-1, 44100, 0, NULL, NULL)) { fprintf(stderr, "Real default audio device failed\n"); return 1; }
    snprintf(path, sizeof(path), "%slibbassflac.so", base);
    if (!BASS_PluginLoad(path, 0)) { fprintf(stderr, "FLAC decoder failed\n"); return 1; }
    uint32_t stream = BASS_StreamCreateFile(0, argv[1], 0, 0, 0);
    if (!stream || !BASS_ChannelSetAttribute(stream, 2, 0) || !BASS_ChannelPlay(stream, 0)) {
        fprintf(stderr, "Stream/play failed: stream=%u error=%d file=%s\n", stream, BASS_ErrorGetCode(), argv[1]); return 1;
    }
    pause_ms(700);
    double first = BASS_ChannelBytes2Seconds(stream, BASS_ChannelGetPosition(stream, 0));
    if (first < .1) { fprintf(stderr, "Native output clock did not advance: %.3f\n", first); return 1; }
    if (!BASS_ChannelPause(stream)) { fprintf(stderr, "Pause failed: %d\n", BASS_ErrorGetCode()); return 1; }
    if (BASS_ChannelIsActive(stream) != 3) { fprintf(stderr, "Channel did not enter paused state\n"); return 1; }
    /* Already queued device audio can drain after Pause. Its audible clock must then stop. */
    pause_ms(300);
    double paused = BASS_ChannelBytes2Seconds(stream, BASS_ChannelGetPosition(stream, 0));
    pause_ms(300);
    double still = BASS_ChannelBytes2Seconds(stream, BASS_ChannelGetPosition(stream, 0));
    if (still - paused > .03) { fprintf(stderr, "Paused clock moved: %.3f -> %.3f\n", paused, still); return 1; }
    if (!BASS_ChannelSetPosition(stream, BASS_ChannelSeconds2Bytes(stream, 1.0), 0) || !BASS_ChannelPlay(stream, 0)) {
        fprintf(stderr, "Seek/resume failed: %d\n", BASS_ErrorGetCode()); return 1;
    }
    pause_ms(400);
    double resumed = BASS_ChannelBytes2Seconds(stream, BASS_ChannelGetPosition(stream, 0));
    if (resumed < 1.1) { fprintf(stderr, "Resumed clock stalled: %.3f\n", resumed); return 1; }
    printf("NATIVE_AUDIO_OK first=%.3f paused=%.3f resumed=%.3f (muted real output, FLAC decoder)\n", first, paused, resumed);
    BASS_Free(); dlclose(library); return 0;
}
