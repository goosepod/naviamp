package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
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
}
