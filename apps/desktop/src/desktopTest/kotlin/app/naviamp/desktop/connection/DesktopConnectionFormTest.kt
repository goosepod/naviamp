package app.naviamp.desktop

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopConnectionFormTest {
    @Test
    fun displayNameFallsBackToNormalizedServerUrl() {
        assertEquals(
            "My Server",
            desktopConnectionDisplayName(" My Server ", " https://music.example.test/ "),
        )
        assertEquals(
            "https://music.example.test",
            desktopConnectionDisplayName(" ", " https://music.example.test/ "),
        )
    }

    @Test
    fun newFormStateClearsConnectionFields() {
        assertEquals(DesktopConnectionFormState(), newDesktopConnectionFormState())
    }

    @Test
    fun savedFormStateLoadsConnectionFieldsAndHidesDefaultDisplayName() {
        val formState = savedDesktopConnectionFormState(
            savedSource(
                displayName = "https://music.example.test",
                tlsSettings = ConnectionTlsSettings(
                    insecureSkipTlsVerification = true,
                    customCertificatePath = "/cert.pem",
                    clientCertificateKeyStorePath = "/client.p12",
                    clientCertificateKeyStorePassword = "secret",
                ),
            ),
        )

        assertEquals("https://music.example.test", formState.serverUrl)
        assertEquals("", formState.connectionName)
        assertEquals("demo", formState.username)
        assertEquals("", formState.password)
        assertEquals(true, formState.insecureSkipTlsVerification)
        assertEquals("/cert.pem", formState.customCertificatePath)
        assertEquals("/client.p12", formState.clientCertificateKeyStorePath)
        assertEquals("secret", formState.clientCertificateKeyStorePassword)
        assertEquals("https://music.example.test", formState.savedConnectionForLogin?.baseUrl)
    }

    @Test
    fun savedFormStateKeepsCustomDisplayName() {
        val formState = savedDesktopConnectionFormState(
            savedSource(displayName = "Home Music"),
        )

        assertEquals("Home Music", formState.connectionName)
    }

    @Test
    fun restoredPlaybackSessionPlansTrackQueueWithCoverArtUrl() {
        val track = track(id = "track-1", coverArtId = "cover-1")
        val plan = restoredDesktopPlaybackSession(
            savedPlaybackSession = PlaybackSessionSettings.fromTracks(
                tracks = listOf(track),
                currentIndex = 0,
                positionSeconds = 42.0,
            ),
            provider = NavidromeProvider(navidromeConnection()),
        )

        val queuePlan = assertIs<DesktopRestoredPlaybackSession.TrackQueue>(plan)
        assertEquals(track.id, queuePlan.session.currentTrack.id)
        assertEquals(42.0, queuePlan.session.playbackProgress.positionSeconds)
        assertTrue(queuePlan.coverArtUrl.orEmpty().startsWith("https://music.example.test/rest/getCoverArt.view?"))
        assertTrue(queuePlan.coverArtUrl.orEmpty().contains("id=cover-1"))
    }

    @Test
    fun restoredPlaybackSessionPrefersInternetRadioWhenPresent() {
        val plan = restoredDesktopPlaybackSession(
            savedPlaybackSession = PlaybackSessionSettings.fromInternetRadioStation(
                InternetRadioStation(
                    id = "station-1",
                    name = "Deep Space",
                    streamUrl = "https://radio.example.test/stream.mp3",
                    homePageUrl = "https://radio.example.test",
                ),
            ),
            provider = NavidromeProvider(navidromeConnection()),
        )

        val radioPlan = assertIs<DesktopRestoredPlaybackSession.InternetRadio>(plan)
        assertEquals("station-1", radioPlan.station.id)
        assertEquals("Deep Space", radioPlan.track.title)
    }

    private fun savedSource(
        displayName: String,
        tlsSettings: ConnectionTlsSettings = ConnectionTlsSettings(),
    ): SavedMediaSource =
        SavedMediaSource(
            id = "source",
            providerId = "navidrome",
            cacheNamespace = "navidrome:https://music.example.test:demo",
            displayName = displayName,
            baseUrl = "https://music.example.test",
            username = "demo",
            token = "token",
            salt = "salt",
            nativeToken = "native",
            tlsSettings = tlsSettings,
            createdAtEpochMillis = 1L,
            lastConnectedAtEpochMillis = 2L,
            lastSyncStartedAtEpochMillis = 3L,
            lastSyncCompletedAtEpochMillis = 4L,
        )

    private fun navidromeConnection(
        token: String = "token",
        nativeToken: String? = null,
    ): NavidromeConnection =
        NavidromeConnection(
            baseUrl = "https://music.example.test",
            username = "demo",
            token = token,
            salt = "salt",
            nativeToken = nativeToken,
            displayName = "Home Music",
        )

    private fun track(
        id: String,
        coverArtId: String? = null,
    ): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = coverArtId,
            audioInfo = null,
            replayGain = null,
        )
}
