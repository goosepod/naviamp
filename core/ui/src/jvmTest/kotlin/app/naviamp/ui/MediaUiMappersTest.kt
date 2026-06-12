package app.naviamp.ui

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ArtistInfo
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaUiMappersTest {
    @Test
    fun downloadedTrackUiUsesSharedTrackRowMapping() {
        val track = Track(
            id = TrackId("track-1"),
            title = "Track One",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 125,
            coverArtId = "cover-1",
            audioInfo = null,
            replayGain = null,
        )

        val ui = track.toDownloadedTrackUi(
            id = "/downloads/track-1.mp3",
            sizeBytes = 12_345L,
            coverArtUrl = { coverArtId -> coverArtId?.let { "cover://$it" } },
        )

        assertEquals("/downloads/track-1.mp3", ui.id)
        assertEquals("track-1", ui.track.id)
        assertEquals("Track One", ui.track.title)
        assertEquals("Artist - Album", ui.track.subtitle)
        assertEquals("2:05", ui.track.meta)
        assertEquals("cover://cover-1", ui.track.coverArtUrl)
        assertEquals(12_345L, ui.sizeBytes)
    }

    @Test
    fun artistDetailUiPrefersArtistInfoImage() {
        val ui = ArtistDetails(
            artist = Artist(ArtistId("artist-1"), "Artist One"),
            albums = emptyList(),
            info = ArtistInfo(
                biography = "Artist biography",
                smallImageUrl = "https://images.test/small.jpg",
                mediumImageUrl = "https://images.test/medium.jpg",
                largeImageUrl = "https://images.test/large.jpg",
            ),
        ).toSharedArtistDetailUi(
            coverArtUrl = { coverArtId -> coverArtId?.let { "cover://$it" } },
        )

        assertEquals("https://images.test/large.jpg", ui.artist.coverArtUrl)
        assertEquals("Artist biography", ui.biography)
    }

    @Test
    fun nowPlayingItemTargetResolvesQueueRelatedAndTrackIds() {
        val queueTrack = track("queue-track")
        val relatedTrack = track("related-track")
        val knownTrack = track("known-track")

        assertEquals(
            queueTrack,
            resolveNowPlayingItemTrack(
                item = item(nowPlayingQueueItemId(1)),
                queueTracks = listOf(track("before"), queueTrack),
            ),
        )
        assertEquals(
            relatedTrack,
            resolveNowPlayingItemTrack(
                item = item(nowPlayingRelatedItemId(0)),
                relatedTracks = listOf(relatedTrack),
            ),
        )
        assertEquals(
            knownTrack,
            resolveNowPlayingItemTrack(
                item = item("known-track"),
                knownTracks = listOf(knownTrack),
            ),
        )
        assertNull(
            resolveNowPlayingItemTrack(
                item = item(nowPlayingQueueItemId(4)),
                queueTracks = listOf(queueTrack),
            ),
        )
    }

    @Test
    fun nowPlayingItemActionRequestCarriesActionTargetAndPlaylistData() {
        val request = nowPlayingItemActionRequest(
            item = item(nowPlayingRelatedItemId(2)),
            action = NowPlayingItemAction.CreatePlaylistAndAdd,
            playlistName = "Road Mix",
        )

        assertEquals(NowPlayingItemAction.CreatePlaylistAndAdd, request.action)
        assertEquals(NowPlayingItemTarget.RelatedIndex(2), request.target)
        assertEquals("Road Mix", request.playlistName)
    }

    @Test
    fun nowPlayingItemActionResolvesSourceAndTrack() {
        val relatedTrack = track("related-track")
        val request = nowPlayingItemActionRequest(
            item = item(nowPlayingRelatedItemId(0)),
            action = NowPlayingItemAction.Download,
        )

        val action = request.resolveAction(relatedTracks = listOf(relatedTrack))

        assertEquals(NowPlayingItemSource.Related, action.source)
        assertEquals(NowPlayingItemAction.Download, action.action)
        assertEquals(relatedTrack, action.track)
        assertEquals(true, action.isRelated)
    }

    @Test
    fun nowPlayingItemActionUsesFallbackTrackWhenPlatformResolverAlreadyResolvedItem() {
        val fallbackTrack = track("fallback-track")
        val request = nowPlayingItemActionRequest(
            item = item(nowPlayingQueueItemId(3)),
            action = NowPlayingItemAction.AddToPlaylist,
        )

        val action = request.resolveAction(fallbackTrack = fallbackTrack)

        assertEquals(NowPlayingItemSource.Queue, action.source)
        assertEquals(fallbackTrack, action.track)
    }

    @Test
    fun sharedTrackRowActionResolvesKnownTrackAndPlaylistData() {
        val request = SharedTrackRowActionRequest(
            track = SharedTrackRowUi(id = "track-two", title = "Track Two", subtitle = "Artist"),
            action = SharedTrackRowAction.AddToPlaylist,
            playlistChoice = NaviampPlaylistChoiceUi(id = "playlist-1", name = "Road Mix"),
        )

        val action = request.resolveAction(knownTracks = listOf(track("track-one"), track("track-two")))

        assertEquals(SharedTrackRowAction.AddToPlaylist, action.action)
        assertEquals("track-two", action.track?.id?.value)
        assertEquals("Road Mix", action.playlistChoice?.name)
    }

    @Test
    fun sharedTrackRowActionUsesFallbackTrack() {
        val fallbackTrack = track("fallback-track")
        val request = SharedTrackRowActionRequest(
            track = SharedTrackRowUi(id = "missing-track", title = "Missing", subtitle = "Artist"),
            action = SharedTrackRowAction.CreatePlaylistAndAdd,
            playlistName = "New Mix",
        )

        val action = request.resolveAction(fallbackTrack = fallbackTrack)

        assertEquals(fallbackTrack, action.track)
        assertEquals("New Mix", action.playlistName)
    }

    @Test
    fun sharedMediaItemActionDispatchesPlaylistPayload() {
        var received: Pair<String, String?>? = null
        val playlist = SharedMediaItemUi(id = "playlist-1", title = "Playlist", subtitle = "4 tracks")
        val choice = NaviampPlaylistChoiceUi(id = "target-1", name = "Target")

        handleSharedMediaItemAction(
            SharedMediaItemActionRequest(
                item = playlist,
                action = SharedMediaItemAction.AddToPlaylist,
                playlistChoice = choice,
            ),
            SharedMediaItemActionHandlers(
                onAddToPlaylist = { item, playlistChoice ->
                    received = item.id to playlistChoice?.id
                },
            ),
        )

        assertEquals("playlist-1" to "target-1", received)
    }

    @Test
    fun sharedMediaItemActionRequestHelperDefaultsShuffleFlag() {
        val playlist = SharedMediaItemUi(id = "playlist-1", title = "Playlist", subtitle = "4 tracks")

        val play = playlist.actionRequest(SharedMediaItemAction.Play, kind = SharedMediaItemKind.Playlist)
        val shuffle = playlist.actionRequest(SharedMediaItemAction.Shuffle, kind = SharedMediaItemKind.Playlist)

        assertFalse(play.shuffle)
        assertTrue(shuffle.shuffle)
        assertEquals(SharedMediaItemKind.Playlist, shuffle.kind)
    }

    @Test
    fun downloadedTrackActionDispatchesCreatePlaylistPayload() {
        var received: Pair<String, String>? = null
        val download = NaviampDownloadedTrackUi(
            id = "/downloads/track.mp3",
            track = SharedTrackRowUi(id = "track-1", title = "Track", subtitle = "Artist"),
            sizeBytes = 123L,
        )

        handleDownloadedTrackAction(
            DownloadedTrackActionRequest(
                download = download,
                action = DownloadedTrackAction.CreatePlaylistAndAdd,
                playlistName = "New Mix",
            ),
            DownloadedTrackActionHandlers(
                onCreatePlaylistAndAdd = { item, name -> received = item.id to name },
            ),
        )

        assertEquals("/downloads/track.mp3" to "New Mix", received)
    }

    @Test
    fun stationRowActionDispatchesDelete() {
        var deletedId: String? = null
        val station = SharedMediaItemUi(id = "station-1", title = "Station", subtitle = "Radio")

        handleStationRowAction(
            StationRowActionRequest(station, StationRowAction.Delete),
            StationRowActionHandlers(onDelete = { item -> deletedId = item.id }),
        )

        assertEquals("station-1", deletedId)
    }

    @Test
    fun nowPlayingSectionsUseTrackIdsForTrackListSources() {
        val tracks = listOf(track("one"), track("two"), track("three"))
        val related = listOf(track("related"))

        val sections = nowPlayingSectionsUi(
            tracks = tracks,
            currentTrack = tracks[1],
            relatedTracks = related,
            coverArtUrl = { "cover://${it.id.value}" },
            sonicSimilarityEnabled = false,
            repeatMode = RepeatMode.Off,
            itemIds = NowPlayingSectionItemIds.TrackIds,
        )

        assertEquals(listOf("one"), sections.backTo.map { it.id })
        assertEquals(listOf("three"), sections.upNext.map { it.id })
        assertEquals(listOf("related"), sections.related.map { it.id })
        assertEquals(true, sections.hasPrevious)
        assertEquals(true, sections.hasNext)
        assertEquals(false, sections.shuffleEnabled)
        assertEquals("RELATED", sections.relatedLabels.tabLabel)
    }

    @Test
    fun nowPlayingSectionsUseQueueAndRelatedIndexesForQueueSources() {
        val tracks = listOf(track("one"), track("two"), track("three"), track("four"))

        val sections = PlaybackQueue(tracks = tracks, currentIndex = 2).toNowPlayingSectionsUi(
            relatedTracks = listOf(track("related")),
            coverArtUrl = { "cover://${it.id.value}" },
            sonicSimilarityEnabled = true,
            relatedTracksSource = RelatedTracksSource.SonicSimilarity,
            relatedSimilarityByTrackId = mapOf(TrackId("related") to 0.92),
            repeatMode = RepeatMode.Queue,
        )

        assertEquals(listOf(nowPlayingQueueItemId(1), nowPlayingQueueItemId(0)), sections.backTo.map { it.id })
        assertEquals(listOf(nowPlayingQueueItemId(3)), sections.upNext.map { it.id })
        assertEquals(listOf(nowPlayingRelatedItemId(0)), sections.related.map { it.id })
        assertEquals(true, sections.hasPrevious)
        assertEquals(true, sections.hasNext)
        assertEquals(false, sections.shuffleEnabled)
        assertEquals("SONIC", sections.relatedLabels.tabLabel)
        assertEquals("92% match", sections.related.single().meta)
    }

    @Test
    fun nowPlayingSectionsLabelFallbackRelatedTracksByActualSource() {
        val tracks = listOf(track("one"), track("two"))

        val sections = PlaybackQueue(tracks = tracks, currentIndex = 0).toNowPlayingSectionsUi(
            relatedTracks = listOf(track("related")),
            coverArtUrl = { "cover://${it.id.value}" },
            sonicSimilarityEnabled = true,
            relatedTracksSource = RelatedTracksSource.ProviderRadio,
            relatedSimilarityByTrackId = mapOf(TrackId("related") to 0.92),
            repeatMode = RepeatMode.Queue,
        )

        assertEquals("RELATED", sections.relatedLabels.tabLabel)
        assertEquals("", sections.related.single().meta)
    }

    private fun item(id: String): NaviampNowPlayingItemUi =
        NaviampNowPlayingItemUi(id = id, title = id, subtitle = "")

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = id,
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 125,
            coverArtId = "cover-1",
            audioInfo = null,
            replayGain = null,
        )
}
