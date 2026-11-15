package app.naviamp.tvos.playback

import app.naviamp.apple.playback.AppleBassAudioBackend
import app.naviamp.apple.playback.AppleBassPlaybackEngineRuntime
import app.naviamp.domain.playback.CoreBassPlaybackEngine

/** Public tvOS distribution has base BASS, FLAC and Opus; no BASSmix ABI is linked. */
class TvosBassAudioBackend : AppleBassAudioBackend(
    bundledFrameworks = listOf("bass", "bassflac", "bassopus"),
    codecPlugins = setOf("bassflac", "bassopus"),
)

fun createTvosBassPlaybackEngine(): CoreBassPlaybackEngine = CoreBassPlaybackEngine(
    backendResult = Result.success(TvosBassAudioBackend()),
    runtime = AppleBassPlaybackEngineRuntime(),
)
