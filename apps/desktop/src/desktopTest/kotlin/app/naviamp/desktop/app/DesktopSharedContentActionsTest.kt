package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.popular.SimilarArtistCandidate
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.SharedSimilarArtistUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSharedContentActionsTest {
    @Test
    fun mediaActionsResolveByKindAndSharedId() {
        val artist = Artist(ArtistId("shared-id"), "Artist")
        val album = Album(
            id = AlbumId("shared-id"),
            title = "Album",
            artistName = artist.name,
            coverArtId = null,
            recentlyAddedAtIso8601 = null,
        )
        var resolvedArtist: Artist? = null
        var resolvedAlbum: Album? = null

        resolveDesktopMediaItemAction(
            request = SharedMediaItemActionRequest(
                item = SharedMediaItemUi("shared-id", "Artist", "Artist"),
                action = SharedMediaItemAction.Select,
                kind = SharedMediaItemKind.Artist,
            ),
            artists = listOf(artist),
            albums = listOf(album),
            onArtistAction = { _, selected -> resolvedArtist = selected },
            onAlbumAction = { _, selected -> resolvedAlbum = selected },
        )

        assertEquals(artist, resolvedArtist)
        assertNull(resolvedAlbum)
    }

    @Test
    fun trackActionsPreserveTheDomainResultIndex() {
        val tracks = listOf(track("first"), track("target"), track("last"))
        var resolvedIndex: Int? = null
        var resolvedTrack: Track? = null

        resolveDesktopTrackAction(
            request = SharedTrackRowActionRequest(
                track = SharedTrackRowUi("target", "Target", "Artist"),
                action = SharedTrackRowAction.Select,
            ),
            tracks = tracks,
        ) { _, index, selected ->
            resolvedIndex = index
            resolvedTrack = selected
        }

        assertEquals(1, resolvedIndex)
        assertEquals(tracks[1], resolvedTrack)
    }

    @Test
    fun detailActionSourcesResolveSharedIdsWithoutLeakingDomainModelsToPanels() {
        val selectedAlbum = album("selected")
        val detailAlbum = album("detail")
        val artistAlbum = album("artist-album")
        val selectedArtist = Artist(ArtistId("selected-artist"), "Selected Artist")
        val detailArtist = Artist(ArtistId("detail-artist"), "Detail Artist")
        val detailTracks = listOf(track("first"), track("second"))
        val popularTrack = track("popular")
        val similarArtist = Artist(ArtistId("similar-local"), "Similar")
        val similarMatch = SimilarArtistMatch(
            candidate = SimilarArtistCandidate(
                source = "test",
                sourceArtistId = "similar-remote",
                name = similarArtist.name,
                externalUrl = "https://example.test/similar",
            ),
            matchedArtist = similarArtist,
        )
        val sources = DesktopDetailActionSources(
            selectedAlbum = selectedAlbum,
            albumDetail = AlbumDetails(detailAlbum, detailTracks),
            selectedArtist = selectedArtist,
            artistDetail = ArtistDetails(detailArtist, listOf(artistAlbum)),
            artistPopularTracks = listOf(popularTrack),
            artistSimilarArtists = listOf(similarMatch),
        )

        assertEquals(detailAlbum, sources.album(detailAlbum.id.value))
        assertEquals(artistAlbum, sources.album(artistAlbum.id.value))
        assertEquals(detailArtist, sources.artist(detailArtist.id.value))
        assertEquals(1 to detailTracks[1], sources.albumTrack("second"))
        assertEquals(popularTrack, sources.popularTrack("popular"))
        assertEquals(listOf(artistAlbum), sources.artistAlbums(listOf("artist-album", "missing")))
        assertEquals(
            similarArtist to "https://example.test/similar",
            sources.similarArtist(
                SharedSimilarArtistUi(
                    id = "similar-remote",
                    title = "Similar",
                    subtitle = "In library",
                    localArtistId = "similar-local",
                ),
            ),
        )
    }

    @Test
    fun playlistActionSourcesPreserveTrackOrderAndRejectStaleRows() {
        val playlist = Playlist("playlist", "Playlist", trackCount = 3)
        val tracks = listOf(track("first"), track("second"), track("third"))
        val sources = DesktopPlaylistActionSources(
            playlists = listOf(playlist),
            playlistTracksById = mapOf(playlist.id to tracks),
            selectedPlaylist = playlist,
            selectedPlaylistTracks = tracks,
        )

        assertEquals(playlist, sources.playlist("playlist"))
        assertEquals(1 to tracks[1], sources.selectedTrack("second"))
        assertEquals(
            listOf(tracks[2], tracks[0]),
            sources.selectedTracks(listOf(sharedTrack("third"), sharedTrack("first"))),
        )
        assertNull(sources.selectedTracks(listOf(sharedTrack("first"), sharedTrack("missing"))))
    }

    private fun album(id: String): Album = Album(
        id = AlbumId(id),
        title = id,
        artistName = "Artist",
        coverArtId = null,
        recentlyAddedAtIso8601 = null,
    )

    private fun track(id: String): Track = Track(
        id = TrackId(id),
        title = id,
        artistName = "Artist",
        albumTitle = null,
        durationSeconds = null,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )

    private fun sharedTrack(id: String): SharedTrackRowUi =
        SharedTrackRowUi(id = id, title = id, subtitle = "Artist")
}
