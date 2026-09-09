package app.naviamp.provider.navidrome

import app.naviamp.domain.*
import app.naviamp.domain.provider.ProviderIdSubsonic
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class OpenSubsonicAuditTest {
    @Test fun genrePagesUseTheExactLibraryTagAndFolderContinuation() = runTest {
        val http = Http().apply { payload = "\"songsByGenre\":{\"song\":[${song("a")},${song("b") }]}" }
        val provider = provider(http, listOf("one", "two"))
        val page = assertNotNull(provider.genreTracksPage("R&B / Soul", app.naviamp.domain.provider.MediaPageRequest(limit = 2)))
        assertEquals(listOf("a", "b"), page.items.map { it.id.value })
        assertEquals("getSongsByGenre.view", http.calls.single().first)
        assertEquals("R&B / Soul", io.ktor.http.parseQueryString(http.calls.single().second)["genre"])
        assertTrue(http.calls.single().second.contains("musicFolderId=one"))
        http.payload = "\"songsByGenre\":{\"song\":[]}"
        provider.genreTracksPage("R&B / Soul", assertNotNull(page.nextRequest))
        assertTrue(http.calls[1].second.contains("offset=2"))
        assertTrue(http.calls.last().second.contains("musicFolderId=two"))
    }

    private class Http(val extensions: List<String> = emptyList()) : NavidromeHttpClient {
        val calls = mutableListOf<Pair<String, String>>()
        var payload: String = ""
        val responses = mutableMapOf<String, String>()
        override suspend fun get(url: String): String {
            calls += url.substringBefore('?').substringAfterLast('/') to url.substringAfter('?', "")
            return response(url.substringBefore('?').substringAfterLast('/'))
        }
        override suspend fun postForm(url: String, body: String, headers: Map<String, String>): String {
            calls += "POST:${url.substringAfterLast('/')}" to body
            return response(url.substringAfterLast('/'))
        }
        private fun response(endpoint: String): String = when (endpoint) {
            "ping.view" -> envelope("")
            "getOpenSubsonicExtensions.view" -> envelope("\"openSubsonicExtensions\":[${extensions.joinToString { "{\"name\":\"$it\",\"versions\":[1,2]}" }}]")
            else -> envelope(responses[endpoint] ?: payload)
        }
    }
    private fun provider(http: Http, folders: List<String> = emptyList()) = NavidromeProvider(
        NavidromeConnection(providerId = ProviderIdSubsonic, baseUrl = "https://test.invalid",
            username = "me", token = "token", salt = "salt", selectedMusicFolderIds = folders), http,
    )

    @Test fun playlistOccurrencesAreIndependentOfFolderSelection() = runTest {
        val http = Http().apply { payload = "\"playlist\":{\"entry\":[${song("a")},${song("b")},${song("a") }]}" }
        val tracks = provider(http, listOf("one", "two")).playlistTracks("p")
        assertEquals(listOf("a", "b", "a"), tracks.map { it.id.value })
        assertEquals(1, http.calls.size)
        assertFalse(http.calls.single().second.contains("musicFolderId"))
    }

    @Test fun fullReplacementUsesOnlyDesiredOccurrencesInFormBody() = runTest {
        val http = Http(listOf("formPost"))
        val provider = provider(http)
        provider.validateConnection()
        http.calls.clear()
        val ids = List(2000) { TrackId("track-${it % 3}") }
        provider.replacePlaylistTracks("p", listOf(TrackId("stale")), ids)
        val call = http.calls.single()
        assertEquals("POST:createPlaylist.view", call.first)
        assertEquals(2000, Regex("songId=").findAll(call.second).count())
        assertFalse(call.second.contains("songIndexToRemove"))
        assertFalse(call.second.contains("stale"))
        provider.replacePlaylistTracks("p", ids, emptyList())
        assertFalse(http.calls.last().second.contains("songId="))
        assertEquals("POST:updatePlaylist.view", http.calls.last().first)
        assertEquals(2000, Regex("songIndexToRemove=").findAll(http.calls.last().second).count())
        assertTrue(http.calls.last().second.contains("songIndexToRemove=1999"))
    }

    @Test fun originalPlaybackAndDownloadHaveExplicitSemantics() = runTest {
        val p = provider(Http())
        val request = StreamRequest(TrackId("s"), StreamQuality.Original)
        assertTrue(p.streamUrl(request).contains("format=raw"))
        assertTrue(p.downloadUrl(request).contains("/download.view?"))
        assertTrue(p.downloadUrl(request.copy(quality = StreamQuality.Transcoded(AudioCodec.Opus, 128))).contains("/stream.view?"))
    }

    @Test fun audioOffsetsRequireAdvertisedCapability() = runTest {
        for (supported in listOf(false, true)) {
            val p = provider(Http(if (supported) listOf("transcodeOffset") else emptyList()))
            p.validateConnection()
            val url = p.streamUrl(StreamRequest(TrackId("s"), StreamQuality.Transcoded(AudioCodec.Opus, 128), 95.0))
            assertEquals(supported, url.contains("timeOffset=95"))
        }
    }

    @Test fun genericServerUsesAdvertisedArtistIdentity() = runTest {
        val http = Http(listOf("topSongsByArtistId"))
        val p = provider(http)
        p.validateConnection()
        p.popularTracks(Artist(ArtistId("identity"), "Same name"), 10)
        assertTrue(http.calls.last().second.contains("id=identity"))
    }

    @Test fun favoritesShareSnapshotAndInvalidateOnMutation() = runTest {
        val http = Http().apply { payload = "\"starred2\":{\"song\":[${song("s")}],\"artist\":[{\"id\":\"a\",\"name\":\"Artist\"}]}" }
        val p = provider(http)
        assertEquals(1, p.favoriteTracks(10).size)
        assertEquals(1, p.favoriteArtists(10).size)
        assertEquals(1, http.calls.size)
        p.setTrackFavorite(TrackId("s"), false)
        http.payload = "\"starred2\":{}"
        assertTrue(p.favoriteTracks(10).isEmpty())
        assertEquals(2, http.calls.count { it.first == "getStarred2.view" })
    }

    @Test fun albumInformationUsesOnlyId3Identity() = runTest {
        val http = Http().apply { payload = "\"albumInfo\":{\"notes\":\"notes\"}" }
        assertEquals("notes", provider(http).albumInfo(AlbumId("a"))?.notes)
        assertEquals("getAlbumInfo2.view", http.calls.single().first)
    }

    @Test fun enhancedTranslationCannotReplaceMainLyrics() = runTest {
        val http = Http(listOf("songLyrics"))
        val p = provider(http)
        p.validateConnection()
        http.payload = """"lyricsList":{"structuredLyrics":[
            {"kind":"translation","synced":true,"line":[{"start":0,"value":"translation"},{"start":1,"value":"more"}]},
            {"kind":"main","synced":true,"line":[{"start":0,"value":"main"}]}]}"""
        assertEquals("main", p.lyrics(TrackId("s"))?.lines?.single()?.text)
    }

    @Test fun playlistOwnershipIsPreserved() = runTest {
        val http = Http().apply { payload = "\"playlists\":{\"playlist\":[{\"id\":\"p\",\"name\":\"Shared\",\"owner\":\"someone\",\"public\":true}]}" }
        val playlist = provider(http).playlists(20).single()
        assertEquals("someone", playlist.owner)
        assertEquals(true, playlist.public)
        assertFalse(playlist.canEdit)
    }

    @Test fun legacyCreationRoleDoesNotHideOwnedPlaylists() = runTest {
        val http = Http().apply {
            responses["getUser.view"] = """"user":{"username":"me","playlistRole":false}"""
            payload = """"playlists":{"playlist":[{"id":"p","name":"Mine","owner":"me"}]}"""
        }
        val p = provider(http)
        p.validateConnection()
        assertTrue(p.playlists(20).single().canEdit)
    }

    @Test fun explicitPlaylistPermissionOverridesOwnershipAndLegacyRole() = runTest {
        val http = Http().apply {
            responses["getUser.view"] = """"user":{"username":"me","playlistRole":false}"""
            payload = """"playlists":{"playlist":[
                {"id":"shared","name":"Shared editable","owner":"someone","readonly":false},
                {"id":"locked","name":"Mine locked","owner":"me","readonly":true}
            ]}"""
        }
        val p = provider(http)
        p.validateConnection()
        val playlists = p.playlists(20)
        assertTrue(playlists[0].canEdit)
        assertFalse(playlists[1].canEdit)
    }

    @Test fun ownershipFallbackUsesServerAuthenticatedUsername() = runTest {
        val http = Http().apply {
            responses["getUser.view"] = """"user":{"username":"Me"}"""
            payload = """"playlists":{"playlist":[{"id":"p","name":"Mine","owner":"Me"}]}"""
        }
        val p = provider(http)
        p.validateConnection()
        assertTrue(p.playlists(20).single().canEdit)
    }

    @Test fun errorDocumentsCannotBecomeAudio() {
        for (type in listOf("text/xml", "application/octet-stream", null)) {
            val error = assertFailsWith<NavidromeException> {
                validateSubsonicMediaResponse(type, "<subsonic-response status=\"failed\"><error code=\"40\" message=\"Denied\"/></subsonic-response>".encodeToByteArray())
            }
            assertEquals(40, error.subsonicErrorCode)
        }
        assertFailsWith<NavidromeException> {
            validateSubsonicMediaResponse("application/json", "{\"subsonic-response\":{\"status\":\"failed\"}}".encodeToByteArray())
        }
        validateSubsonicMediaResponse("application/octet-stream", "fLaCbinary".encodeToByteArray())
        validateSubsonicMediaResponse(null, byteArrayOf(0, -1, 10))
    }

    @Test fun libraryFoldersAreMergedByRankingInsteadOfSelectionOrder() {
        fun album(id: String, created: String, plays: Int) = Album(AlbumId(id), id, "Artist", null, created,
            playCount = plays, lastPlayedAtIso8601 = created)
        val old = album("old", "2020-01-01T00:00:00Z", 1)
        val newer = album("new", "2025-01-01T00:00:00Z", 20)
        val newest = album("newest", "2026-01-01T00:00:00Z", 3)
        val folders = listOf(listOf(old), listOf(newest, newer))
        assertEquals(listOf(newest, newer), mergeSubsonicAlbumLists(folders, "newest", 2))
        assertEquals(listOf(newer, newest), mergeSubsonicAlbumLists(folders, "frequent", 2))
        assertEquals(listOf(newest, newer), mergeSubsonicAlbumLists(folders, "recent", 2))
    }

    @Test fun legacyLyricsUseKnownTrackMetadataWithoutAnotherSongRead() = runTest {
        val http = Http().apply {
            responses["getPlaylist.view"] = "\"playlist\":{\"entry\":[${song("s")}]}"
            responses["getLyrics.view"] = "\"lyrics\":{\"value\":\"First line\\nSecond line\"}"
        }
        val p = provider(http)
        p.validateConnection()
        p.playlistTracks("p")
        assertEquals(listOf("First line", "Second line"), p.lyrics(TrackId("s"))?.lines?.map { it.text })
        assertEquals(0, http.calls.count { it.first == "getSong.view" || it.first == "getLyricsBySongId.view" })
    }

    @Test fun accountDownloadRoleIsRespected() = runTest {
        val http = Http().apply { responses["getUser.view"] = "\"user\":{\"downloadRole\":false}" }
        val p = provider(http)
        p.validateConnection()
        assertFalse(p.capabilities.supportsDownloads)
        assertFailsWith<NavidromeException> { p.downloadUrl(StreamRequest(TrackId("s"), StreamQuality.Original)) }
        assertTrue(p.streamUrl(StreamRequest(TrackId("s"), StreamQuality.Original)).contains("format=raw"))
    }

    companion object {
        private fun envelope(body: String) = "{\"subsonic-response\":{\"status\":\"ok\"${if (body.isEmpty()) "" else ",$body"}}}"
        private fun song(id: String) = "{\"id\":\"$id\",\"title\":\"Song\",\"artist\":\"Artist\"}"
    }
}
