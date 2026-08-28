package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectMediaType
import app.naviamp.domain.connect.NaviampConnectSourceIdentity
import app.naviamp.domain.connect.NaviampConnectStartMedia
import app.naviamp.ui.NaviampAlbumDetailCommand
import app.naviamp.ui.NaviampArtistAlbumCommand
import app.naviamp.ui.NaviampArtistDetailCommand
import app.naviamp.ui.NaviampArtistMediaCommand
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampPlaylistDetailCommand
import app.naviamp.ui.NaviampPlaylistMediaCommand
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.StationRowAction

internal data class NaviampCoreConnectMediaSelection(
    val type: NaviampConnectMediaType,
    val id: String,
    val startRadio: Boolean = false,
    val shuffle: Boolean = false,
) {
    fun toCommand(sourceIdentity: NaviampConnectSourceIdentity) = NaviampConnectStartMedia(
        mediaType = type,
        mediaId = id,
        startRadio = startRadio,
        shuffle = shuffle,
        sourceIdentity = sourceIdentity,
    )
}

/** Maps only playback intents; browsing, editing, downloads, and favorites remain local. */
internal fun NaviampCoreCommand.connectMediaSelectionOrNull(): NaviampCoreConnectMediaSelection? = when (this) {
    is NaviampCoreCommand.Media.TrackAction -> request.connectSelectionOrNull()
    is NaviampCoreCommand.Detail.AlbumTrack -> request.connectSelectionOrNull()
    is NaviampCoreCommand.Detail.ArtistPopularTrack -> request.connectSelectionOrNull()
    is NaviampCoreCommand.Detail.PlaylistTrack -> request.connectSelectionOrNull()
    is NaviampCoreCommand.Home.RecentTrackAction -> request.connectSelectionOrNull()
    is NaviampCoreCommand.Home.SonicTrackAction -> when (request.action) {
        SharedTrackRowAction.Select -> NaviampCoreConnectMediaSelection(
            NaviampConnectMediaType.Track,
            request.track.id,
        )
        SharedTrackRowAction.StartRadio -> NaviampCoreConnectMediaSelection(
            NaviampConnectMediaType.Track,
            request.track.id,
            startRadio = true,
        )
        else -> null
    }
    is NaviampCoreCommand.Media.ItemAction -> when (val itemCommand = request.command) {
        NaviampMediaItemCommand.PlayAlbum -> NaviampCoreConnectMediaSelection(
            NaviampConnectMediaType.Album,
            request.item.id,
        )
        is NaviampMediaItemCommand.Album -> when (itemCommand.command) {
            NaviampArtistAlbumCommand.StartRadio -> NaviampCoreConnectMediaSelection(
                NaviampConnectMediaType.Album,
                request.item.id,
                startRadio = true,
            )
            else -> null
        }
        is NaviampMediaItemCommand.Artist -> when (itemCommand.command) {
            NaviampArtistMediaCommand.StartRadio -> NaviampCoreConnectMediaSelection(
                NaviampConnectMediaType.Artist,
                request.item.id,
                startRadio = true,
            )
            else -> null
        }
        is NaviampMediaItemCommand.Playlist -> when (val playlist = itemCommand.command) {
            is NaviampPlaylistMediaCommand.Detail -> playlist.command.playlistSelection(request.item.id)
            else -> null
        }
    }
    is NaviampCoreCommand.Detail.Album -> when (val detail = request.command) {
        is NaviampAlbumDetailCommand.Play -> NaviampCoreConnectMediaSelection(
            NaviampConnectMediaType.Album,
            request.album.id,
            shuffle = detail.shuffle,
        )
        NaviampAlbumDetailCommand.StartRadio -> NaviampCoreConnectMediaSelection(
            NaviampConnectMediaType.Album,
            request.album.id,
            startRadio = true,
        )
        else -> null
    }
    is NaviampCoreCommand.Detail.Artist -> when (val detail = request.command) {
        is NaviampArtistDetailCommand.PlayCatalog -> NaviampCoreConnectMediaSelection(
            NaviampConnectMediaType.Artist,
            request.artist.id,
            shuffle = detail.shuffle,
        )
        NaviampArtistDetailCommand.StartRadio -> NaviampCoreConnectMediaSelection(
            NaviampConnectMediaType.Artist,
            request.artist.id,
            startRadio = true,
        )
        else -> null
    }
    is NaviampCoreCommand.Detail.ArtistAlbum -> when (request.command) {
        NaviampArtistAlbumCommand.StartRadio -> NaviampCoreConnectMediaSelection(
            NaviampConnectMediaType.Album,
            request.album.id,
            startRadio = true,
        )
        else -> null
    }
    is NaviampCoreCommand.Playlists.Detail -> request.command.playlistSelection(request.playlist.id)
    is NaviampCoreCommand.Radio.StationAction -> if (request.action == StationRowAction.Select) {
        NaviampCoreConnectMediaSelection(NaviampConnectMediaType.InternetRadioStation, request.station.id)
    } else {
        null
    }
    is NaviampCoreCommand.Home.SelectInternetRadio ->
        NaviampCoreConnectMediaSelection(NaviampConnectMediaType.InternetRadioStation, item.id)
    else -> null
}

private fun app.naviamp.ui.SharedTrackRowActionRequest.connectSelectionOrNull() = when (action) {
    SharedTrackRowAction.Select -> NaviampCoreConnectMediaSelection(NaviampConnectMediaType.Track, track.id)
    SharedTrackRowAction.StartRadio -> NaviampCoreConnectMediaSelection(
        NaviampConnectMediaType.Track,
        track.id,
        startRadio = true,
    )
    else -> null
}

private fun NaviampPlaylistDetailCommand.playlistSelection(id: String) = when (this) {
    is NaviampPlaylistDetailCommand.Play -> NaviampCoreConnectMediaSelection(
        NaviampConnectMediaType.Playlist,
        id,
        shuffle = shuffle,
    )
    else -> null
}

internal class NaviampCoreConnectCommandHandler(
    private val delegate: NaviampCoreCommandHandler,
    private val connect: NaviampCoreConnectController?,
) : NaviampCoreCommandHandler {
    override fun dispatch(command: NaviampCoreCommand) {
        if (connect?.routeProductCommand(command) != true) delegate.dispatch(command)
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult =
        if (connect?.routeProductCommand(command) == true) NaviampCoreCommandResult.Completed
        else delegate.execute(command)
}
