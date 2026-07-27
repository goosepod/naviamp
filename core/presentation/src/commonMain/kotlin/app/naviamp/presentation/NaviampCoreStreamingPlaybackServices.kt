package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.ui.NaviampVisualizer
import kotlinx.coroutines.CoroutineScope

/**
 * Core composition for hosts that can stream through their native engine before file-backed
 * downloads, cache, lyrics, and waveform effects are mounted.
 *
 * The reduced native capability set is explicit, while queue resolution, engine settings,
 * provider URLs, callbacks, radio playback, and presentation updates stay in common code.
 */
fun naviampCoreStreamingPlaybackServices(
    scope: CoroutineScope,
    engine: PlaybackEngine,
    providerSource: NaviampCoreMediaProviderSource,
    initialPlaybackSettings: PlaybackSettings,
    persistPlaybackSettings: (PlaybackSettings) -> Unit,
    playbackSessionRepository: PlaybackSessionRepository,
    saveVisualizerSettings: (VisualizerSettings) -> Unit,
    verifyProviderNetworkCertificates: () -> Boolean = { true },
): NaviampCorePlaybackServices {
    val settings = NaviampCorePlaybackEngineSettings(
        engine = engine,
        initial = initialPlaybackSettings,
        persist = persistPlaybackSettings,
    )
    return NaviampCorePlaybackServices(
        effects = NaviampCorePlaybackEngineAdapter(
            scope = scope,
            engine = engine,
            providerSource = providerSource,
            settings = settings::current,
            verifyProviderNetworkCertificates = verifyProviderNetworkCertificates,
        ),
        settings = settings,
        sidecars = NaviampCoreMutableNowPlayingSidecars(),
        visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
            override fun save(visualizer: NaviampVisualizer) {
                saveVisualizerSettings(VisualizerSettings(visualizer.name))
            }
        },
        sessions = NaviampPlaybackSessionController(playbackSessionRepository),
    )
}
