package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.*
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/** Opt-in read-only acceptance; credentials and private server addresses never enter the repository. */
class AurralLiveTest {
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
