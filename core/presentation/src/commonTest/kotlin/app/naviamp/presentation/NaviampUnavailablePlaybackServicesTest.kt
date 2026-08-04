package app.naviamp.presentation

import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NaviampUnavailablePlaybackServicesTest {
    @Test
    fun advertisesNoNativeAudioCapabilitiesAndRetainsSharedSettingsPolicy() {
        var persisted: PlaybackSettings? = null
        val services = unavailableNaviampCorePlaybackServices(
            persistSettings = { persisted = it },
            sessions = EmptyPlaybackSessions,
        )

        assertFalse(services.effects.capabilities.supportsPause)
        assertFalse(services.effects.capabilities.supportsSeek)
        assertFalse(services.effects.capabilities.supportsSoftwareVolume)
        assertFalse(services.effects.startOrRestore())

        val effective = services.settings.apply(
            PlaybackSettings(gaplessEnabled = false, crossfadeDurationSeconds = 999),
            redownload = false,
        )
        assertEquals(effective, persisted)
        assertEquals(0, effective.crossfadeDurationSeconds)
    }
}

private object EmptyPlaybackSessions : PlaybackSessionRepository {
    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null
    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
}
