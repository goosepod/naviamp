package app.naviamp.desktop.playback.bass

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.playback.CoreBassPlaybackEngine

/** Desktop construction adapter for the shared Core BASS playback engine. */
class DesktopBassPlaybackEngine(
    backendResult: Result<BassAudioBackend> = loadDesktopBassAudioBackend(),
) : CoreBassPlaybackEngine(
    backendResult = backendResult,
    runtime = DesktopBassPlaybackEngineRuntime(),
)
