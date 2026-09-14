package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectMediaType
import app.naviamp.domain.connect.NaviampConnectQueueMedia
import app.naviamp.domain.connect.NaviampConnectQueuePlacement
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

internal data class NaviampCoreConnectQueueSelection(
    val type: NaviampConnectMediaType,
    val id: String,
    val placement: NaviampConnectQueuePlacement,
) {
    fun toCommand(sourceIdentity: NaviampConnectSourceIdentity) = NaviampConnectQueueMedia(
        mediaType = type,
        mediaId = id,
        placement = placement,
        sourceIdentity = sourceIdentity,
    )
}

internal fun NaviampCoreCommand.connectQueueSelectionOrNull(): NaviampCoreConnectQueueSelection? = when (this) {
    is NaviampCoreCommand.Media.TrackAction -> request.connectQueueSelectionOrNull()
    is NaviampCoreCommand.Detail.AlbumTrack -> request.connectQueueSelectionOrNull()
    is NaviampCoreCommand.Detail.ArtistPopularTrack -> request.connectQueueSelectionOrNull()
    is NaviampCoreCommand.Detail.PlaylistTrack -> request.connectQueueSelectionOrNull()
    is NaviampCoreCommand.Home.RecentTrackAction -> request.connectQueueSelectionOrNull()
    is NaviampCoreCommand.Home.SonicTrackAction -> connectQueueSelectionOrNull(request.track.id, request.action)
    is NaviampCoreCommand.Media.ItemAction -> when (val itemCommand = request.command) {
        is NaviampMediaItemCommand.Album -> itemCommand.command.albumQueueSelection(request.item.id)
        is NaviampMediaItemCommand.Artist -> itemCommand.command.artistQueueSelection(request.item.id)
        is NaviampMediaItemCommand.Playlist -> when (val playlist = itemCommand.command) {
            is NaviampPlaylistMediaCommand.Detail -> playlist.command.playlistQueueSelection(request.item.id)
            else -> null
        }
        NaviampMediaItemCommand.PlayAlbum -> null
    }
    is NaviampCoreCommand.Detail.Album -> request.command.albumQueueSelection(request.album.id)
    is NaviampCoreCommand.Detail.Artist -> request.command.artistQueueSelection(request.artist.id)
    is NaviampCoreCommand.Detail.ArtistAlbum -> request.command.albumQueueSelection(request.album.id)
    is NaviampCoreCommand.Playlists.Detail -> request.command.playlistQueueSelection(request.playlist.id)
    else -> null
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

private fun app.naviamp.ui.SharedTrackRowActionRequest.connectQueueSelectionOrNull() =
    connectQueueSelectionOrNull(track.id, action)

private fun connectQueueSelectionOrNull(
    trackId: String,
    action: SharedTrackRowAction,
): NaviampCoreConnectQueueSelection? = when (action) {
        SharedTrackRowAction.AddToQueue -> NaviampConnectQueuePlacement.AddToQueue
        SharedTrackRowAction.PlayNext -> NaviampConnectQueuePlacement.PlayNext
        SharedTrackRowAction.PlayNextTrack -> NaviampConnectQueuePlacement.PlayNextTrack
        else -> null
    }?.let { placement ->
        NaviampCoreConnectQueueSelection(
            type = NaviampConnectMediaType.Track,
            id = trackId,
            placement = placement,
        )
    }

private fun NaviampAlbumDetailCommand.albumQueueSelection(id: String) =
    NaviampCoreConnectQueueSelection(
        type = NaviampConnectMediaType.Album,
        id = id,
        placement = NaviampConnectQueuePlacement.AddToQueue,
    ).takeIf { this == NaviampAlbumDetailCommand.AddToQueue }

private fun NaviampArtistAlbumCommand.albumQueueSelection(id: String) =
    NaviampCoreConnectQueueSelection(
        type = NaviampConnectMediaType.Album,
        id = id,
        placement = NaviampConnectQueuePlacement.AddToQueue,
    ).takeIf { this == NaviampArtistAlbumCommand.AddToQueue }

private fun NaviampArtistDetailCommand.artistQueueSelection(id: String) =
    NaviampCoreConnectQueueSelection(
        type = NaviampConnectMediaType.Artist,
        id = id,
        placement = NaviampConnectQueuePlacement.AddToQueue,
    ).takeIf { this == NaviampArtistDetailCommand.AddToQueue }

private fun NaviampArtistMediaCommand.artistQueueSelection(id: String) =
    NaviampCoreConnectQueueSelection(
        type = NaviampConnectMediaType.Artist,
        id = id,
        placement = NaviampConnectQueuePlacement.AddToQueue,
    ).takeIf { this == NaviampArtistMediaCommand.AddToQueue }

private fun NaviampPlaylistDetailCommand.playlistQueueSelection(id: String) =
    NaviampCoreConnectQueueSelection(
        type = NaviampConnectMediaType.Playlist,
        id = id,
        placement = NaviampConnectQueuePlacement.AddToQueue,
    ).takeIf { this == NaviampPlaylistDetailCommand.AddToQueue }

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
