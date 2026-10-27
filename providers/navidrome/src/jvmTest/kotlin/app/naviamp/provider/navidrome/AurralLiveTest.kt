package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.*
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Opt-in read-only acceptance; credentials and private server addresses never enter the repository. */
class AurralLiveTest {
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
