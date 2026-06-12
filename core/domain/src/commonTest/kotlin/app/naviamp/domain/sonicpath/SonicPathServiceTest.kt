package app.naviamp.domain.sonicpath

import app.naviamp.domain.ArtistId
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.provider.SonicPathMatch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SonicPathServiceTest {
    @Test
    fun findPathReturnsPlainTracksWithStartAndEndSemantics() = runTest {
        val start = track("start")
        val end = track("end")
        val provider = FakeSonicPathProvider(
            matches = listOf(
                SonicPathMatch(start),
                SonicPathMatch(track("middle-one")),
                SonicPathMatch(track("middle-one")),
                SonicPathMatch(track("middle-two")),
                SonicPathMatch(end),
            ),
        )
        val service = SonicPathService(provider)

        val path = service.findPath(SonicPathRequest(start, end, count = 5))

        assertEquals(listOf("start", "middle-one", "middle-two", "end"), path.map { it.id.value })
        assertEquals(5, provider.requestedCount)
    }

    @Test
    fun findPathClampsRequestCount() = runTest {
        val start = track("start")
        val end = track("end")
        val provider = FakeSonicPathProvider(matches = listOf(SonicPathMatch(start), SonicPathMatch(end)))
        val service = SonicPathService(provider)

        service.findPath(SonicPathRequest(start, end, count = 500))

        assertEquals(SonicPathMaxCount, provider.requestedCount)
    }

    @Test
    fun findPathReturnsEmptyWhenSonicSimilarityIsUnavailable() = runTest {
        val provider = FakeSonicPathProvider(supportsSonicSimilarity = false)
        val service = SonicPathService(provider)

        val path = service.findPath(SonicPathRequest(track("start"), track("end")))

        assertEquals(emptyList(), path)
        assertEquals(null, provider.requestedCount)
    }

    @Test
    fun findPathReturnsEmptyWhenProviderReturnsNoPath() = runTest {
        val provider = FakeSonicPathProvider(matches = emptyList())
        val service = SonicPathService(provider)

        val path = service.findPath(SonicPathRequest(track("start"), track("end")))

        assertEquals(emptyList(), path)
        assertEquals(SonicPathDefaultCount, provider.requestedCount)
    }

    private class FakeSonicPathProvider(
        private val supportsSonicSimilarity: Boolean = true,
        private val matches: List<SonicPathMatch> = emptyList(),
    ) : MediaProvider {
        override val id: ProviderId = ProviderId("provider-one")
        override val displayName: String = "Provider One"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
            supportsSonicSimilarity = supportsSonicSimilarity,
        )
        var requestedCount: Int? = null

        override suspend fun validateConnection(): ConnectionValidation =
            error("unused")

        override suspend fun recentlyAddedAlbums(limit: Int) =
            error("unused")

        override suspend fun album(albumId: AlbumId): AlbumDetails =
            error("unused")

        override suspend fun artist(artistId: ArtistId): ArtistDetails =
            error("unused")

        override suspend fun artists(limit: Int) =
            error("unused")

        override suspend fun tracks(limit: Int) =
            error("unused")

        override suspend fun search(query: String, limit: Int): MediaSearchResults =
            error("unused")

        override suspend fun findSonicPath(
            startTrackId: TrackId,
            endTrackId: TrackId,
            count: Int,
        ): List<SonicPathMatch> {
            requestedCount = count
            return matches
        }

        override suspend fun streamUrl(request: StreamRequest): String =
            error("unused")

        override fun coverArtUrl(coverArtId: String): String =
            error("unused")
    }
}

private fun track(id: String): Track =
    Track(
        id = TrackId(id),
        title = id,
        artistId = ArtistId("artist-one"),
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
