package app.naviamp.tvos.playback

import app.naviamp.apple.playback.AppleBassAudioBackend
import app.naviamp.apple.playback.AppleBassPlaybackEngineRuntime
import app.naviamp.domain.bass.BassCodecPluginInventory
import app.naviamp.domain.playback.CoreBassPlaybackEngine

/** Public tvOS distribution has base BASS, FLAC and Opus; no BASSmix ABI is linked. */
class TvosBassAudioBackend : AppleBassAudioBackend(
    bundledFrameworks = TvosBassFrameworks,
    codecPlugins = TvosBassFrameworks.filter(BassCodecPluginInventory.stems::contains).toSet(),
)

fun createTvosBassPlaybackEngine(): CoreBassPlaybackEngine = CoreBassPlaybackEngine(
    backendResult = Result.success(TvosBassAudioBackend()),
    runtime = AppleBassPlaybackEngineRuntime(),
)
