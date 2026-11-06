package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.*
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/** Opt-in read-only acceptance; credentials and private server addresses never enter the repository. */
class AurralLiveTest {
    @Test fun instantMixAndRadioUseOwnedLibraryTracksWithoutSonicSupportOrMutations() = runBlocking {
        val connection = liveConnection() ?: return@runBlocking
        val provider = NavidromeProvider(connection, readOnlyTransport(connection))
        provider.validateConnection()
        assertFalse(provider.capabilities.supportsSonicSimilarity)
        val seed = provider.album(provider.recentlyAddedAlbums(1).first().id).tracks.first()
        val direct = provider.instantMixTracks(seed.id, 20)
        assertTrue(direct.isNotEmpty(), "Aurral Instant Mix must produce playable Library tracks")
        assertTrue(direct.none { it.id == seed.id || it.id.isDiscoveryPreview() })
        var endpoint: app.naviamp.domain.radio.SimilarityEndpoint? = null
        val radio = app.naviamp.domain.radio.RadioService(provider, count = 20,
            onSimilarityResult = { endpoint = it.source }).trackRadio(seed, preferSonicSimilarity = true)
        assertEquals(app.naviamp.domain.radio.SimilarityEndpoint.Regular, endpoint)
        assertTrue(radio.isNotEmpty())
        // Resolve a returned identity through the real Library path, rather than accepting a chart/preview ID.
        assertEquals(direct.first().id, provider.track(direct.first().id)?.id)
        println("Aurral read-only radio: Sonic advertised=false, Instant Mix tracks=${direct.size}, capability fallback tracks=${radio.size}, returned Library identity validated")
    }

    @Test fun originalFileTagsAndPropertiesLoadWithAudioCachingDisabled() = runBlocking {
        val provider = NavidromeProvider(liveConnection() ?: return@runBlocking)
        provider.validateConnection()
        val track = provider.search("Rhythm Is a Dancer", 30).tracks.first {
            it.artistName == "Snap!" && it.title == "Rhythm Is a Dancer" && it.albumTitle == "The Madman’s Return"
        }
        val metadata = app.naviamp.domain.audio.AudioMetadataSidecarService(
            app.naviamp.domain.playback.emptyPlaybackAudioAssetRepository(),
        ) { error("No local file is required") }.metadataForTrack(
            "read-only-acceptance", provider, track, StreamQuality.Original, false,
        )
        assertEquals(44100, metadata.audioInfo?.samplingRateHz)
        assertEquals(16, metadata.audioInfo?.bitDepth)
        assertTrue(metadata.tags.any { it.key == "Title" && it.value == "Rhythm Is a Dancer" })
        assertTrue(metadata.tags.any { it.key == "Date" && it.value == "1992-03-24" })
        println("Aurral original metadata: cache disabled, embedded title/date present, FLAC 44100 Hz / 16 bit")
    }

    @Test fun trendingAndGenreBlockReadsUseTheCurrentUserWithoutMutations() = runBlocking {
        val connection = liveConnection() ?: return@runBlocking
        val transport = createDefaultNavidromeHttpClient(connection.tlsSettings)
        var logins = 0
        val readOnly = object : NavidromeHttpClient by transport {
            override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
                check(url == connection.normalizedBaseUrl + "/api/auth/login") { "Live acceptance is read-only" }
                logins++
                return transport.postJson(url, body, headers)
            }
            override suspend fun postJsonResponse(url: String, body: String, headers: Map<String, String>) =
                NavidromeHttpResponse(postJson(url, body, headers))
            override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String = error("Read-only acceptance")
            override suspend fun deleteJson(url: String, headers: Map<String, String>): String = error("Read-only acceptance")
        }
        val discovery = app.naviamp.provider.aurral.AurralDiscoveryClient(connection, readOnly)
        val charts = discovery.charts()
        assertEquals(ProviderDiscoveryStatus.Ready, charts.status)
        assertTrue(charts.songs != null || charts.albums != null || charts.artists != null)
        val blocked = discovery.blockedGenres()
        assertEquals(ProviderDiscoveryStatus.Ready, blocked.status)
        charts.genres.firstOrNull()?.let { genre ->
            assertEquals(ProviderDiscoveryStatus.Ready, discovery.genreChart(genre.id).status)
        }
        assertEquals(1, logins)
        println("Aurral chart reads: songs=${charts.songs?.size}, albums=${charts.albums?.size}, artists=${charts.artists?.size}, genres=${charts.genres.size}, previews=${charts.songs.orEmpty().count { it.previewId != null }}, blockedGenres=${blocked.tags.size}")
    }

    @Test fun releaseContextLoadsEditionAndPreviewMetadataWithoutAcquisition() = runBlocking {
        val connection = liveConnection() ?: return@runBlocking
        val transport = createDefaultNavidromeHttpClient(connection.tlsSettings)
        val readOnly = object : NavidromeHttpClient by transport {
            override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
                check(url == connection.normalizedBaseUrl + "/api/auth/login") { "Live acceptance cannot submit acquisition" }
                return transport.postJson(url, body, headers)
            }
            override suspend fun postJsonResponse(url: String, body: String, headers: Map<String, String>) =
                NavidromeHttpResponse(postJson(url, body, headers))
            override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String = error("Read-only acceptance")
            override suspend fun deleteJson(url: String, headers: Map<String, String>): String = error("Read-only acceptance")
        }
        val client = app.naviamp.provider.aurral.AurralDiscoveryClient(connection, readOnly)
        val artist = ProviderDiscoveryArtist("a74b1b7f-71a5-4011-9441-d0b5e4122711", "Radiohead", detailsAvailable = true)
        val releases = client.artistDetails(artist).details!!.releases
        val release = releases.first { it.releaseGroupMbid != null }
        val details = client.releaseDetails(release.releaseGroupMbid!!)
        val tracks = client.releaseTracks(release.releaseGroupMbid!!, ProviderDiscoveryReleaseContext(artist, release))
        assertEquals(ProviderDiscoveryStatus.Ready, details.status)
        assertEquals(ProviderDiscoveryStatus.Ready, tracks.status)
        assertTrue(tracks.tracks.isNotEmpty())
        assertTrue(tracks.tracks.any { it.recordingMbid != null })
        println("Aurral release context: editions=${details.editions.size}, tracks=${tracks.tracks.size}, previews=${tracks.tracks.count { it.previewId != null }}")
    }

    @Test fun upgradedBootstrapRetainsV1AndAdvertisesTheExpectedRelease() = runBlocking {
        val connection = liveConnection() ?: return@runBlocking
        val transport = createDefaultNavidromeHttpClient(connection.tlsSettings)
        val bootstrap = Json.parseToJsonElement(transport.get(
            connection.normalizedBaseUrl + "/api/health/bootstrap")).jsonObject
        val api = bootstrap.getValue("api").jsonObject
        assertEquals(1, api.getValue("version").jsonPrimitive.int)
        val version = bootstrap.getValue("appVersion").jsonPrimitive.content
        System.getenv("NAVIAMP_AURRAL_TEST_EXPECTED_VERSION")?.let { assertEquals(it, version) }
        val features = api.getValue("features").jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertTrue("discover" in features)
        if (version == "2.13.0") assertTrue(features.containsAll(setOf(
            "playlistCoverTemplates", "shareImports", "trending", "flowAutoDownload")))
        println("Aurral bootstrap live result: appVersion=$version, apiVersion=1, featureCount=${features.size}")
    }

    @Test fun libraryAudioServesInitialAndNonzeroByteRangesThroughTheProvider() = runBlocking {
        val provider = NavidromeProvider(liveConnection() ?: return@runBlocking)
        provider.validateConnection()
        val album = provider.album(provider.recentlyAddedAlbums(1).first().id)
        val track = album.tracks.first()
        for (start in listOf(0L, 1024L)) {
            var response: ProviderMediaByteResponse? = null
            var bytes = 0L
            assertTrue(provider.streamTrackBytes(StreamRequest(track.id, StreamQuality.Original),
                rangeHeader = "bytes=$start-${start + 1023}", headOnly = false,
                onResponse = { response = it }, writeChunk = { _, count -> bytes += count }))
            assertEquals(206, response?.statusCode)
            assertTrue(response?.contentRange?.startsWith("bytes $start-${start + 1023}/") == true)
            assertEquals(1024L, bytes)
        }
        println("Aurral range live result: initial=206/1024 bytes, nonzero=206/1024 bytes")
    }

    @Test fun expandedCatalogContractsUseReadOnlyRoutesAndKeepIdentityNamespaces() = runBlocking {
        val connection = liveConnection() ?: return@runBlocking
        val transport = createDefaultNavidromeHttpClient(connection.tlsSettings)
        var loginCount = 0
        val metadataFailures = mutableMapOf<String, Int>()
        val readOnly = object : NavidromeHttpClient by transport {
            override suspend fun get(url: String, headers: Map<String, String>): String = try {
                transport.get(url, headers)
            } catch (failure: NavidromeHttpException) {
                if ("/release-group/" in url) {
                    metadataFailures[if (url.endsWith("/tracks")) "tracks" else "details"] = failure.statusCode
                }
                if ("/similar?" in url) metadataFailures["similar"] = failure.statusCode
                throw failure
            }
            override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
                check(url == connection.normalizedBaseUrl + "/api/auth/login") { "Live research cannot submit mutations" }
                loginCount++
                return transport.postJson(url, body, headers)
            }
            override suspend fun postJsonResponse(url: String, body: String, headers: Map<String, String>) =
                NavidromeHttpResponse(postJson(url, body, headers))
            override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String = error("Live research is read-only")
            override suspend fun deleteJson(url: String, headers: Map<String, String>): String = error("Live research is read-only")
        }
        val discovery = app.naviamp.provider.aurral.AurralDiscoveryClient(connection, readOnly)
        val first = discovery.recommendations(0, 200)
        assertEquals(ProviderDiscoveryStatus.Ready, first.status)
        assertNotNull(first.totalCount)
        assertTrue(first.artists.isNotEmpty())
        val nextOffset = first.nextOffset
        if (nextOffset != null) {
            val next = discovery.recommendations(nextOffset, 200)
            assertEquals(ProviderDiscoveryStatus.Ready, next.status)
            assertTrue(next.artists.first().recommendation!!.rank!! > first.artists.last().recommendation!!.rank!!)
        }
        val artists = discovery.searchCatalog("Radiohead", ProviderCatalogScope.Artist, limit = 2)
        assertEquals(ProviderDiscoveryStatus.Ready, artists.status)
        // The configured catalog service can legitimately return no artist matches.
        assertNotNull(artists.totalCount)
        val albums = discovery.searchCatalog("Radiohead", ProviderCatalogScope.Album, limit = 2)
        assertEquals(ProviderDiscoveryStatus.Ready, albums.status)
        assertTrue(albums.albums.isNotEmpty())
        // Album text search matches release titles, not necessarily releases by this artist.
        // Use the existing artist discography for a known, cacheable release-detail probe.
        val artist = discovery.artistDetails(ProviderDiscoveryArtist(
            "a74b1b7f-71a5-4011-9441-d0b5e4122711", "Radiohead", detailsAvailable = true))
        assertEquals(ProviderDiscoveryStatus.Ready, artist.status)
        val release = assertNotNull(artist.details).releases.first().releaseGroupMbid!!
        val details = discovery.releaseDetails(release)
        fun expectedMetadataStatus(key: String) = if (metadataFailures[key] in setOf(404, 429, 500, 502, 503, 504))
            ProviderDiscoveryStatus.Unavailable else ProviderDiscoveryStatus.Ready
        assertEquals(expectedMetadataStatus("details"), details.status)
        if (details.status == ProviderDiscoveryStatus.Ready) {
            assertEquals(release.value, details.release?.id)
            assertTrue(details.credits.isNotEmpty())
        }
        val tracks = discovery.releaseTracks(release)
        assertEquals(expectedMetadataStatus("tracks"), tracks.status)
        if (tracks.status == ProviderDiscoveryStatus.Ready) {
            assertTrue(tracks.tracks.isNotEmpty())
            assertTrue(tracks.tracks.any { it.recordingMbid != null })
        }
        val similar = discovery.similarArtists(DiscoveryArtistMbid("a74b1b7f-71a5-4011-9441-d0b5e4122711"))
        assertEquals(expectedMetadataStatus("similar"), similar.status)
        println("Aurral similar live result: status=${similar.status}, count=${similar.artists.size}")
        println("Aurral catalog live result: recommendationCount=${first.totalCount}, artistCount=${artists.totalCount}, release=${details.status}, tracks=${tracks.status}")
        assertEquals(ProviderDiscoveryStatus.Ready, discovery.requestActivity().status)
        assertEquals(1, loginCount)
    }

    @Test fun nativeSamplesAndReleaseCoversUseTheProviderTransport() = runBlocking {
        val connection = liveConnection() ?: return@runBlocking
        val provider = NavidromeProvider(connection)
        // A public artist with known preview coverage; arbitrary owned artists may legitimately have none.
        val artist = ProviderDiscoveryArtist("a74b1b7f-71a5-4011-9441-d0b5e4122711", "Radiohead", detailsAvailable = true)
        val details = provider.discovery!!.artistDetails(artist).details!!
        assertTrue(details.releases.isNotEmpty())
        val images = details.releases.take(3).mapNotNull { it.imageUrl?.let { url -> provider.bytesForOwnedUrl(url) } }
        assertTrue(images.any { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(it)) != null })
        val samples = provider.discovery!!.samples(artist)
        assertEquals(ProviderDiscoveryStatus.Ready, samples.status)
        val sample = samples.tracks.first()
        val url = provider.streamUrl(StreamRequest(sample.playbackTrack(artist.name).id, StreamQuality.Original))
        val audio = assertNotNull(provider.bytesForOwnedUrl(url))
        assertTrue(audio.size > 1000)
        assertTrue(audio.take(3) == listOf<Byte>(73, 68, 51) || (audio[0].toInt() and 0xff) == 0xff,
            "Sample media must be MP3 audio rather than metadata")
    }

    @Test fun discoveryArtistCoverFallbackReturnsRealImageBytes() = runBlocking {
        val provider = NavidromeProvider(liveConnection() ?: return@runBlocking)
        val feed = provider.discovery!!.load()
        val candidates = (feed.trending + feed.recommendations).filter { it.imageUrl?.contains("/cover?artistName=") == true }
        assertTrue(candidates.isNotEmpty(), "The live fixture should include missing discovery artwork")
        val images = candidates.take(3).mapNotNull { provider.bytesForOwnedUrl(it.imageUrl!!) }
        assertTrue(images.any { it.size > 100 && javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(it)) != null },
            "At least one discovery portrait must resolve and decode as an image")
    }

    @Test fun nativeArtistDetailsBridgeAnOwnedArtistThroughVerifiedSubsonicIdentity() = runBlocking {
        val connection = liveConnection() ?: return@runBlocking
        val provider = NavidromeProvider(connection)
        provider.validateConnection()
        val owned = provider.artists(20).first { it.id.value.startsWith("artist:mbid:") }
        val mbid = owned.id.value.removePrefix("artist:mbid:")
        val result = provider.discovery!!.artistDetails(ProviderDiscoveryArtist(mbid, owned.name, detailsAvailable = true))
        assertEquals(ProviderDiscoveryStatus.Ready, result.status)
        assertEquals(owned.id, result.details?.libraryArtist?.id)
        assertTrue(result.details!!.feedbackAvailable)
    }

    @Test fun feedbackCanBeChangedAndRemovedWithoutTouchingExistingEntries() = runBlocking {
        if (System.getenv("NAVIAMP_AURRAL_TEST_FEEDBACK") != "true") return@runBlocking
        val connection = liveConnection() ?: return@runBlocking
        val discovery = NavidromeProvider(connection).discovery!!
        val probe = java.util.UUID.randomUUID().toString().let { ProviderDiscoveryArtist(it, "Naviamp temporary verification $it") }
        try {
            val more = discovery.toggleFeedback(probe, ProviderDiscoveryFeedbackAction.MoreLikeThis)
            assertEquals(ProviderDiscoveryStatus.Ready, more.status)
            assertEquals(setOf(ProviderDiscoveryFeedbackAction.MoreLikeThis), more.selected)
            val less = discovery.toggleFeedback(probe, ProviderDiscoveryFeedbackAction.LessLikeThis)
            assertEquals(ProviderDiscoveryStatus.Ready, less.status)
            assertEquals(setOf(ProviderDiscoveryFeedbackAction.LessLikeThis), less.selected)
        } finally {
            // Even a lost POST response can have saved an entry. Remove only this random probe ID.
            val http = createDefaultNavidromeHttpClient(connection.tlsSettings)
            val login = http.postJson(connection.normalizedBaseUrl + "/api/auth/login", buildJsonObject {
                put("username", connection.username); put("password", connection.password)
            }.toString())
            val token = Json.parseToJsonElement(login).jsonObject.getValue("token").jsonPrimitive.content
            val headers = mapOf("Authorization" to "Bearer $token")
            suspend fun entries() = Json.parseToJsonElement(http.get(connection.normalizedBaseUrl + "/api/discover/feedback", headers))
                .jsonObject.getValue("feedback").jsonArray.filter { it.jsonObject["artistId"]?.jsonPrimitive?.content == probe.id }
            entries().forEach {
                val id = it.jsonObject.getValue("id").jsonPrimitive.content
                http.deleteJson(connection.normalizedBaseUrl + "/api/discover/feedback/" + id.urlEncode(), headers)
            }
            assertTrue(entries().isEmpty())
        }
    }

    @Test fun libraryLyricsArtworkAndPlaylistReadsUseTheNormalAccount() = runBlocking {
        val connection = liveConnection() ?: return@runBlocking
        val provider = NavidromeProvider(connection, readOnlyTransport(connection))
        provider.validateConnection()
        val track = provider.search("Rhythm Is a Dancer", 30).tracks.first {
            it.artistName == "Snap!" && it.title == "Rhythm Is a Dancer" && it.albumTitle == "The Madman’s Return"
        }
        val lyrics = assertNotNull(provider.lyrics(track.id), "The known vocal track should provide server lyrics")
        assertTrue(lyrics.lines.any { it.text.isNotBlank() })
        val artwork = assertNotNull(provider.bytesForOwnedUrl(provider.coverArtUrl(assertNotNull(track.coverArtId))))
        assertTrue(artwork.size > 1000)
        val playlists = provider.playlists(20)
        for (playlist in playlists.take(3)) {
            provider.playlistTracks(playlist.id)
            playlist.coverArtId?.let { assertNotNull(provider.bytesForOwnedUrl(provider.coverArtUrl(it))) }
        }
        println("Normal-account library reads: lyricsLines=${lyrics.lines.size}, timed=${lyrics.synced}, artworkBytes=${artwork.size}, playlists=${playlists.size}; no writes")
    }

    @Test fun freshUserSessionsReconcileAnExistingOwnedReleaseWithoutSubmission() = runBlocking {
        val connection = liveConnection() ?: return@runBlocking
        val release = DiscoveryReleaseGroupMbid("5065f9ad-d1dd-43c9-908f-0dde0a5f0104")
        val recording = DiscoveryRecordingMbid("4791bbf4-41e5-4c54-97be-8d25680f3d5f")
        var previousLibraryId: String? = null
        repeat(2) {
            // Recreate both the provider and native-session client, retaining only saved credentials.
            val provider = NavidromeProvider(connection, readOnlyTransport(connection))
            provider.validateConnection()
            val discovery = assertNotNull(provider.discovery)
            val history = discovery.requestActivity()
            assertEquals(ProviderDiscoveryStatus.Ready, history.status)
            val ownership = discovery.releaseOwnership(release)
            assertEquals(ProviderDiscoveryStatus.Ready, ownership.status)
            assertTrue(recording in ownership.ownedRecordings)
            val album = assertNotNull(ownership.libraryAlbum)
            assertTrue(provider.album(album.id).tracks.isNotEmpty())
            if (previousLibraryId != null) assertEquals(previousLibraryId, album.id.value)
            previousLibraryId = album.id.value
            println("Fresh native session ${it + 1}: activity=${history.items.size}, existing recording ownership and Library identity validated")
        }
    }

    @Test fun missingFlowPermissionBlocksChartSavingBeforeAnyMutation() = runBlocking {
        val connection = liveConnection() ?: return@runBlocking
        var writes = 0
        val transport = readOnlyTransport(connection) { writes++ }
        val discovery = app.naviamp.provider.aurral.AurralDiscoveryClient(connection, transport)
        val charts = discovery.charts()
        assertEquals(ProviderDiscoveryStatus.Ready, charts.status)
        assertFalse(charts.canSave)
        val result = discovery.saveChartPlaylist(assertNotNull(charts.playlistId))
        assertEquals(ProviderDiscoveryOperationStatus.Forbidden, result.status)
        assertEquals(0, writes)
        println("Normal-account chart saving: Flow permission absent, Forbidden before any write; positive save remains not run")
    }

    private fun readOnlyTransport(connection: NavidromeConnection, unexpectedWrite: () -> Unit = {}): NavidromeHttpClient {
        val transport = createDefaultNavidromeHttpClient(connection.tlsSettings)
        return object : NavidromeHttpClient by transport {
            override suspend fun get(url: String, headers: Map<String, String>): String {
                check(listOf("scrobble", "star.view", "unstar.view", "setRating", "createPlaylist", "updatePlaylist", "deletePlaylist")
                    .none { it in url }) { "Read-only acceptance prohibits mutating Subsonic routes" }
                return transport.get(url, headers)
            }
            override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
                if (url != connection.normalizedBaseUrl + "/api/auth/login" &&
                    url != connection.normalizedBaseUrl + "/api/library/albums/lookup/batch") {
                    unexpectedWrite()
                    error("Read-only acceptance prohibits this POST")
                }
                return transport.postJson(url, body, headers)
            }
            override suspend fun postJsonResponse(url: String, body: String, headers: Map<String, String>) =
                NavidromeHttpResponse(postJson(url, body, headers))
            override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String = error("Read-only acceptance")
            override suspend fun deleteJson(url: String, headers: Map<String, String>): String = error("Read-only acceptance")
        }
    }

    private fun liveConnection(): NavidromeConnection? {
        val url = System.getenv("NAVIAMP_AURRAL_TEST_URL") ?: return null
        return NavidromeConnection.fromPassword(providerId = ProviderIdAurral, baseUrl = url,
            username = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_USER")),
            password = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_PASSWORD")),
            tlsSettings = NavidromeTlsSettings(insecureSkipTlsVerification =
                System.getenv("NAVIAMP_AURRAL_TEST_INSECURE") == "true"))
    }

    @Test fun nativeDiscoveryAndOpenSubsonicUseTheSameConnectionAndTlsPolicy() = runBlocking {
        val url = System.getenv("NAVIAMP_AURRAL_TEST_URL") ?: return@runBlocking
        val connection = NavidromeConnection.fromPassword(providerId = ProviderIdAurral, baseUrl = url,
            username = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_USER")),
            password = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_PASSWORD")),
            tlsSettings = NavidromeTlsSettings(insecureSkipTlsVerification =
                System.getenv("NAVIAMP_AURRAL_TEST_INSECURE") == "true"))
        val provider = NavidromeProvider(connection)
        assertNotNull(provider.validateConnection().serverVersion)
        assertTrue(provider.artists(3).isNotEmpty())
        val albums = provider.recentlyAddedAlbums(3)
        assertTrue(albums.isNotEmpty())
        val album = provider.album(albums.first().id)
        assertTrue(album.tracks.isNotEmpty())
        val track = album.tracks.first()
        val feed = provider.discovery!!.load()
        assertTrue(feed.status in setOf(ProviderDiscoveryStatus.Ready, ProviderDiscoveryStatus.Building))
        assertTrue(feed.trending.isNotEmpty() || feed.recommendations.isNotEmpty())
        assertTrue(feed.trending.all { it.externalUrl?.startsWith(url.trimEnd('/') + "/artist/") == true })
        assertNotNull(provider.bytesForOwnedUrl(provider.coverArtUrl(albums.first().coverArtId!!)))
        var audioStatus: Int? = null
        assertTrue(provider.streamTrackBytes(StreamRequest(track.id, StreamQuality.Original),
            rangeHeader = "bytes=0-1023", headOnly = true,
            onResponse = { audioStatus = it.statusCode }, writeChunk = { _, _ -> }))
        assertTrue(audioStatus in listOf(200, 206))
        // No second native scrobble endpoint: listen reporting stays on the shared Subsonic path.
        assertTrue(provider.capabilities.supportsListenSubmission)
        assertFalse(provider.capabilities.supportsDownloadTranscode)
    }
}
