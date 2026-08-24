package app.naviamp.presentation

import app.naviamp.ui.SharedTrackRowActionRequest

/** Routes every non-Now-Playing track row through the same Core-owned transaction policy. */
class NaviampCoreTrackActionController(
    private val registry: NaviampCoreMediaRegistry,
    private val media: NaviampCoreMediaTransactions,
) : NaviampCoreCommandController {
    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.Media.TrackAction,
        is NaviampCoreCommand.Detail.AlbumTrack,
        is NaviampCoreCommand.Detail.ArtistPopularTrack,
        is NaviampCoreCommand.Detail.PlaylistTrack,
        is NaviampCoreCommand.Home.SonicTrackAction,
        is NaviampCoreCommand.Home.RecentTrackAction,
        -> NaviampCoreImmediateCommandResult.Deferred
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        val (request, tracks) = when (command) {
            is NaviampCoreCommand.Media.TrackAction -> command.request to registry.search.tracks
            is NaviampCoreCommand.Detail.AlbumTrack -> command.request to registry.albumDetails?.tracks.orEmpty()
            is NaviampCoreCommand.Detail.ArtistPopularTrack -> command.request to registry.artistPopularTracks
            is NaviampCoreCommand.Detail.PlaylistTrack -> command.request to registry.selectedPlaylistTracks
            is NaviampCoreCommand.Home.RecentTrackAction -> command.request to registry.home.recentlyPlayedTracks
            is NaviampCoreCommand.Home.SonicTrackAction -> SharedTrackRowActionRequest(
                track = command.request.track,
                action = command.request.action,
                artistId = command.request.artistId,
                artistName = command.request.artistName,
            ) to registry.sonicRows.rows.firstOrNull { it.id.value == command.request.rowId }?.tracks.orEmpty()
            else -> return null
        }
        val index = tracks.indexOfFirst { it.id.value == request.track.id }
        val track = tracks.getOrNull(index)
        if (track == null) {
            media.publish("Track is no longer available.")
            return NaviampCoreCommandResult.Completed
        }
        when (request.action) {
            app.naviamp.ui.SharedTrackRowAction.Select -> media.play(tracks, index)
            app.naviamp.ui.SharedTrackRowAction.PlayNext -> media.playNext(listOf(track))
            app.naviamp.ui.SharedTrackRowAction.PlayNextTrack -> media.playNextTrack(track)
            app.naviamp.ui.SharedTrackRowAction.StartRadio -> media.startTrackRadio(track)
            app.naviamp.ui.SharedTrackRowAction.PlayTrackRadioNext -> media.addTrackRadio(track, true)
            app.naviamp.ui.SharedTrackRowAction.AddTrackRadioToQueue -> media.addTrackRadio(track, false)
            app.naviamp.ui.SharedTrackRowAction.AddToQueue -> media.addToQueue(listOf(track))
            app.naviamp.ui.SharedTrackRowAction.Download -> media.download(track.title, listOf(track))
            app.naviamp.ui.SharedTrackRowAction.AddToPlaylist -> request.playlistChoice?.let { media.addToPlaylist(listOf(track), it) }
                ?: media.publish("Choose a playlist first.")
            app.naviamp.ui.SharedTrackRowAction.CreatePlaylistAndAdd -> request.playlistName?.let { media.createPlaylist(listOf(track), it) }
                ?: media.publish("Playlist name is missing.")
            app.naviamp.ui.SharedTrackRowAction.ToggleFavorite -> media.toggleFavorite(track)
            app.naviamp.ui.SharedTrackRowAction.GoToAlbum -> media.openAlbum(track)
            app.naviamp.ui.SharedTrackRowAction.GoToArtist -> media.openArtist(track, request.artistId, request.artistName)
        }
        return NaviampCoreCommandResult.Completed
    }
}
