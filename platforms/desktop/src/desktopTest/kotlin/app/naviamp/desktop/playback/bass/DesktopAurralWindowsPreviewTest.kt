package app.naviamp.desktop.playback.bass

import app.naviamp.app.NaviampPlaybackStreamRelay
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.*
import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Opt-in real JNI/output and provider transport probe. Never acquires media or submits listens. */
class DesktopAurralWindowsPreviewTest {
    @Test fun liveChartPreviewReachesNativePlayingThroughAuthenticatedByteTransport() = runBlocking {
        val address = System.getenv("NAVIAMP_AURRAL_TEST_URL")
        assumeTrue("Requires an explicitly configured read-only test connection", address != null)
        val connection = NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
            baseUrl = address!!, username = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_USER")),
            password = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_PASSWORD")),
            tlsSettings = NavidromeTlsSettings(customCertificatePath = System.getenv("NAVIAMP_AURRAL_TEST_CA")))
        val provider = NavidromeProvider(connection)
        val charts = assertNotNull(provider.discovery).charts()
        assertEquals(ProviderDiscoveryStatus.Ready, charts.status)
        val preview = assertNotNull(charts.songs?.firstOrNull { it.previewId != null }?.previewId)
        val engine = CoreBassPlaybackEngine(Result.success(DesktopBassAudioBackend(
            DesktopBassJniBinding.load().getOrThrow())), DesktopBassPlaybackEngineRuntime())
        val relay = NaviampPlaybackStreamRelay(this) { it === provider }
        try {
            println("Native preview outputs: ${engine.outputDevices()}")
            // The application's shared playback-settings adapter selects its output at startup.
            engine.setAudioOutputDevice(null).getOrThrow()
            val url = relay.url(NaviampPlaybackStreamRelay.Slot.Current, provider,
                StreamRequest(TrackId(DiscoveryPreviewTrackPrefix + preview.value), StreamQuality.Original))
            val source = relay.randomAccessSource(url)
            val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
            engine.playPreview(this, PlaybackRequest(url, randomAccess = source), true, 0) { state.value = it }
            val result = withTimeout(15_000) {
                state.first { it == PlaybackState.Playing || it is PlaybackState.Error }
            }
            assertEquals(PlaybackState.Playing, result,
                "Live native preview failed; transport failure=${source?.failure}; outputs=${engine.outputDevices()}")
            println("Live chart preview reached native Playing with TLS verification and muted output")
        } finally { engine.release(); relay.close() }
    }
}
