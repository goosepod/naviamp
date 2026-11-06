package app.naviamp.provider.aurral

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ProviderIdAurral
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AurralLibraryRadioTest {
    @Test fun genreMatchesProduceVariedTrackAlbumAndArtistRadioWithoutUnfilteredRequests() = runTest {
        val catalog = variedSongs()
        val http = Catalog { _, _ -> catalog }
        val provider = NavidromeProvider(connection(), http)
        provider.track(TrackId("seed"))
        for (mix in listOf(provider.instantMixTracks(TrackId("seed"), 12),
            provider.albumRadio(AlbumId("owned-album"), 12), provider.artistRadio(ArtistId("seed-artist"), 12))) {
            assertEquals(12, mix.size)
            assertTrue(mix.map { it.artistName }.distinct().size >= 4)
            assertTrue(mix.groupingBy { it.artistName }.eachCount().values.all { it <= 3 })
            assertTrue(mix.none { it.id.value == "seed" })
        }
        assertEquals(3, http.randomRequests.size)
        assertTrue(http.randomRequests.all { "genre=Rock" in it })
    }

    @Test fun sameArtistServerRecommendationsAreBroadenedInsteadOfAcceptedAsAWholeStation() = runTest {
        val http = Catalog(recommendations = (1..20).map { song("same-$it", "Seed artist") }) { _, _ -> variedSongs() }
        val mix = NavidromeProvider(connection(), http).instantMixTracks(TrackId("seed"), 12)
        assertTrue(mix.map { it.artistName }.distinct().size >= 4)
        assertTrue(mix.count { it.artistName == "Seed artist" } <= 3)
        assertEquals(1, http.randomRequests.size)
    }

    @Test fun missingTagsUseScopedLibraryCandidatesWithoutScanningArtistAlbums() = runTest {
        val http = Catalog(seedGenres = false) { _, _ -> variedSongs() }
        val provider = NavidromeProvider(connection().copy(selectedMusicFolderIds = listOf("1", "2")), http)
        val mix = provider.instantMixTracks(TrackId("seed"), 12)
        assertEquals(12, mix.size)
        assertTrue(mix.map { it.artistName }.distinct().size >= 4)
        assertEquals(2, http.randomRequests.size)
        assertTrue(http.randomRequests.all { "genre=" !in it && "musicFolderId=" in it })
        assertTrue(http.urls.none { "getAlbum.view" in it || "getArtist.view" in it })
    }

    @Test fun sparseGenreMatchesLeadTheMixAndExplicitlyWrongTagsAreRejected() = runTest {
        val http = Catalog { filtered, _ -> if (filtered) listOf(
            song("related", "Related artist"), song("wrong-tag", "Wrong artist", "Classical")) else variedSongs() }
        val mix = NavidromeProvider(connection(), http).instantMixTracks(TrackId("seed"), 12)
        assertEquals("related", mix.first().id.value)
        assertTrue(mix.none { it.id.value == "wrong-tag" })
        assertEquals(2, http.randomRequests.size)
        assertTrue("genre=Rock" in http.randomRequests.first())
        assertTrue("genre=" !in http.randomRequests.last())
    }

    @Test fun unsupportedGenreFilterCanUseGeneralLibraryButPermissionsAndCancellationPropagate() = runTest {
        val unsupported = Catalog { filtered, _ ->
            if (filtered) throw NavidromeException("Unsupported", 70) else variedSongs()
        }
        assertEquals(12, NavidromeProvider(connection(), unsupported).instantMixTracks(TrackId("seed"), 12).size)
        assertEquals(2, unsupported.randomRequests.size)
        for (failure in listOf(NavidromeHttpException(403), CancellationException("Cancelled"))) {
            val denied = Catalog { _, _ -> throw failure }
            val provider = NavidromeProvider(connection(), denied)
            val actual = assertFailsWith<Exception> { provider.instantMixTracks(TrackId("seed"), 12) }
            assertSame(failure, actual)
            assertEquals(1, denied.randomRequests.size)
        }
    }

    private class Catalog(
        val seedGenres: Boolean = true,
        val recommendations: List<JsonObject> = emptyList(),
        val random: (Boolean, String) -> List<JsonObject>,
    ) : NavidromeHttpClient {
        val urls = mutableListOf<String>()
        val randomRequests = mutableListOf<String>()
        override suspend fun get(url: String): String {
            urls += url
            return when (url.substringBefore('?').substringAfterLast('/')) {
                "getSong.view" -> envelope("song", buildJsonObject {
                    put("id", "seed"); put("title", "Seed"); put("artist", "Seed artist")
                    put("artistId", "seed-artist"); put("albumId", "owned-album")
                    if (seedGenres) put("genre", "Rock")
                })
                "getSimilarSongs.view" -> songs("similarSongs", recommendations)
                "getSimilarSongs2.view" -> songs("similarSongs2", recommendations)
                "getRandomSongs.view" -> {
                    randomRequests += url
                    songs("randomSongs", random("genre=" in url, url))
                }
                else -> error("Unexpected catalog request: ${url.substringBefore('?')}")
            }
        }
    }

    companion object {
        private fun connection() = NavidromeConnection(providerId = ProviderIdAurral,
            baseUrl = "https://fixture.invalid", username = "fixture", token = "token", salt = "salt")
        private fun song(id: String, artist: String, genre: String = "Rock") = buildJsonObject {
            put("id", id); put("title", id); put("artist", artist); put("artistId", artist)
            put("albumId", "owned-album"); put("genre", genre)
        }
        private fun variedSongs() = (1..6).flatMap { artist ->
            (1..8).map { song("$artist-$it", "Artist $artist") }
        }
        private fun songs(key: String, songs: List<JsonObject>) = envelope(key,
            buildJsonObject { put("song", JsonArray(songs)) })
        private fun envelope(key: String, value: JsonObject) = buildJsonObject {
            put("subsonic-response", buildJsonObject { put("status", "ok"); put(key, value) })
        }.toString()
    }
}
