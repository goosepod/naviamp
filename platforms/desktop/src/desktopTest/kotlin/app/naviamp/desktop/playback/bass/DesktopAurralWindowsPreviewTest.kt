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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import kotlin.math.abs
import kotlin.test.assertTrue
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
        val binding = DesktopBassJniBinding.load().getOrThrow()
        val native = DesktopBassAudioBackend(binding)
        val previewChannel = MutableStateFlow<BassStreamHandle?>(null)
        val observed = object : BassAudioBackend by native {
            override fun createRandomAccessStream(source: PlaybackRandomAccessSource, decode: Boolean) =
                native.createRandomAccessStream(source, decode).onSuccess { previewChannel.value = it }
        }
        val engine = CoreBassPlaybackEngine(Result.success(observed), DesktopBassPlaybackEngineRuntime())
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
            val channel = assertNotNull(previewChannel.value)
            suspend fun awaitPositionAfter(position: Double) = withTimeout(5_000) {
                while ((native.positionSeconds(channel) ?: 0.0) <= position) {
                    check(state.value !is PlaybackState.Error) { "Preview failed: ${state.value}" }
                    delay(25)
                }
            }
            awaitPositionAfter(0.5)
            engine.pausePreview()
            assertEquals(PlaybackState.Paused, state.value)
            val paused = assertNotNull(native.positionSeconds(channel))
            delay(350)
            assertTrue(abs(assertNotNull(native.positionSeconds(channel)) - paused) < 0.1,
                "Native preview clock must remain paused")
            engine.resumePreview()
            assertEquals(PlaybackState.Playing, state.value)
            awaitPositionAfter(paused + 0.5)
            engine.stopPreview()
            assertEquals(PlaybackState.Stopped, state.value)
            assertEquals(0, binding.endSyncRegistrationCount, "Preview callback must be released")
            println("Live chart preview clock advanced, paused and resumed with verified TLS and muted output")
        } finally { engine.release(); relay.close() }
    }
}
