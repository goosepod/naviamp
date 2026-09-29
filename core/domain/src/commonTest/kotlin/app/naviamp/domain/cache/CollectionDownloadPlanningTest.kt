package app.naviamp.domain.cache

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.toMediaPage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CollectionDownloadPlanningTest {
    @Test
    fun albumPreviewCountsExistingAudioOnceAndEstimatesOnlyNewBytes() = runTest {
        val result = planAlbumDownload(
            albumId = AlbumId("album"),
            quality = StreamQuality.Transcoded(app.naviamp.domain.AudioCodec.Mp3, 128),
            downloadedTrackIds = setOf(TrackId("one")),
            currentDownloadBytes = 1_000,
            maxDownloadBytes = 5_000_000,
            loadTracksPage = { _, request -> request.toMediaPage(listOf(track("one"), track("two"))
                .drop(request.offset).take(request.limit)) },
        )

        val plan = assertIs<CollectionDownloadPlanningResult.Ready>(result).plan
        assertEquals(2, plan.tracks.size)
        assertEquals(1, plan.alreadyDownloadedCount)
        assertEquals(listOf("two"), plan.newTracks.map { it.id.value })
        assertEquals(2_880_000L, plan.estimatedNewBytes)
        assertEquals(4_999_000L, plan.remainingBudgetBytes)
    }

    @Test
    fun artistPreviewDeduplicatesTracksAcrossAlbums() = runTest {
        val albums = listOf(album("first"), album("second"))
        val result = planArtistDownload(
            artistId = ArtistId("artist"),
            quality = StreamQuality.Original,
            downloadedTrackIds = emptySet(),
            currentDownloadBytes = 0,
            maxDownloadBytes = 1_000_000,
            loadAlbumsPage = { _, request -> request.toMediaPage(albums.drop(request.offset).take(request.limit)) },
            loadTracksPage = { id, request ->
                val tracks = if (id.value == "first") listOf(track("shared"), track("one"))
                    else listOf(track("shared"), track("two"))
                request.toMediaPage(tracks.drop(request.offset).take(request.limit))
            },
        )

        val plan = assertIs<CollectionDownloadPlanningResult.Ready>(result).plan
        assertEquals(2, plan.albumCount)
        assertEquals(listOf("shared", "one", "two"), plan.tracks.map { it.id.value })
        assertEquals(3, plan.unknownSizeCount)
        assertEquals(null, plan.estimatedNewBytes)
    }

    @Test
    fun unsupportedMemberPagingCannotProduceAPartialPlan() = runTest {
        val result = planAlbumDownload(
            AlbumId("album"), StreamQuality.Original, emptySet(), 0, 100,
            loadTracksPage = { _, _: MediaPageRequest -> null },
        )
        assertEquals(CollectionDownloadPlanningResult.Unsupported, result)
    }

    @Test
    fun artistCatalogBeyondBoundIsRejectedBeforeTrackLookup() = runTest {
        var tracksRequested = false
        val albums = (1..201).map { album("album-$it") }
        val result = planArtistDownload(
            ArtistId("artist"), StreamQuality.Original, emptySet(), 0, Long.MAX_VALUE,
            loadAlbumsPage = { _, request -> request.toMediaPage(albums.drop(request.offset).take(request.limit)) },
            loadTracksPage = { _, _ -> tracksRequested = true; error("Should not load tracks") },
        )
        assertEquals(CollectionDownloadPlanningResult.TooLarge(MaximumSubscribedArtistAlbums), result)
        assertTrue(!tracksRequested)
    }

    private fun album(id: String) = Album(AlbumId(id), id, "Artist", null, null)

    private fun track(id: String) = Track(
        id = TrackId(id), title = id, artistId = ArtistId("artist"), artistName = "Artist",
        albumTitle = "Album", durationSeconds = 180, coverArtId = null, audioInfo = null,
        replayGain = null,
    )
}
