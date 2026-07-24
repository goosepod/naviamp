#import "IosBassProbe.h"
#import "bass.h"

uint32_t NaviampBassProbeVersion(void) {
    return BASS_GetVersion();
}

BOOL NaviampBassProbeInitialize(void) {
    return BASS_Init(-1, 44100, 0, NULL, NULL) == TRUE;
}

int32_t NaviampBassProbeLastError(void) {
    return BASS_ErrorGetCode();
}

void NaviampBassProbeFree(void) {
    BASS_Free();
}
