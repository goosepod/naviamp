package app.naviamp.domain.source

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaSourceConnectionUpdatesTest {
    @Test
    fun deletedMediaSourceUpdateIdentifiesActiveAndSavedConnectionMatches() {
        val source = savedSource(displayName = "Home Music")

        assertEquals(
            DeletedMediaSourceUpdate(
                clearConnectedSource = true,
                clearSavedConnectionForLogin = true,
                status = "Deleted Home Music.",
            ),
            deletedMediaSourceUpdate(
                source = source,
                connectedSourceId = "source",
                savedConnectionBaseUrl = "https://music.example.test",
                savedConnectionUsername = "demo",
            ),
        )
        assertEquals(
            DeletedMediaSourceUpdate(
                clearConnectedSource = false,
                clearSavedConnectionForLogin = false,
                status = "Deleted Home Music.",
            ),
            deletedMediaSourceUpdate(
                source = source,
                connectedSourceId = "other-source",
                savedConnectionBaseUrl = "https://music.example.test",
                savedConnectionUsername = "other",
            ),
        )
    }

    @Test
    fun connectedStatusIdentifiesFallbackEndpoint() {
        assertEquals(
            "Connected via https://fallback.example.",
            connectedMediaSourceStatus(
                primaryUrl = "https://primary.example/",
                activeUrl = " https://fallback.example/ ",
            ),
        )
    }

    @Test
    fun connectedStatusNormalizesEquivalentPrimaryEndpoint() {
        assertEquals(
            "Connected.",
            connectedMediaSourceStatus(
                primaryUrl = "https://primary.example/",
                activeUrl = "https://primary.example",
            ),
        )
    }

    private fun savedSource(displayName: String): SavedMediaSource =
        SavedMediaSource(
            id = "source",
            providerId = "navidrome",
            cacheNamespace = "navidrome:https://music.example.test:demo",
            displayName = displayName,
            baseUrl = "https://music.example.test",
            username = "demo",
            token = "token",
            salt = "salt",
            tlsSettings = ConnectionTlsSettings(),
            createdAtEpochMillis = 1L,
            lastConnectedAtEpochMillis = null,
            lastSyncStartedAtEpochMillis = null,
            lastSyncCompletedAtEpochMillis = null,
        )
}
