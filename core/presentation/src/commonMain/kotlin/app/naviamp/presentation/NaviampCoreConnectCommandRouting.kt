package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectMediaType
import app.naviamp.domain.connect.NaviampConnectPlaybackContext
import app.naviamp.domain.connect.NaviampConnectPlaybackProfile
import app.naviamp.domain.connect.NaviampConnectQueueMedia
import app.naviamp.domain.connect.NaviampConnectQueuePlacement
import app.naviamp.domain.connect.NaviampConnectSourceIdentity
import app.naviamp.domain.connect.NaviampConnectStartMedia
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
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
    /** The catalog list a plain track selection came from, so the target can queue all of it. */
    val context: NaviampConnectPlaybackContext? = null,
) {
    fun toCommand(
        sourceIdentity: NaviampConnectSourceIdentity,
        playbackProfile: PlaybackProfile? = null,
    ) = NaviampConnectStartMedia(
        mediaType = type,
        mediaId = id,
        startRadio = startRadio,
        shuffle = shuffle,
        sourceIdentity = sourceIdentity,
        context = context,
        playbackProfile = playbackProfile?.let(NaviampConnectPlaybackProfile::from),
    )

    /**
     * The album or playlist whose playback profile governs this start, matching the local launch
     * paths: a direct album or playlist start, or a track chosen inside one. Radio has no profile.
     */
    fun playbackProfileTarget(): PlaybackProfileTarget? {
        if (startRadio) return null
        val (mediaType, mediaId) = when (type) {
            NaviampConnectMediaType.Album, NaviampConnectMediaType.Playlist -> type to id
            NaviampConnectMediaType.Track -> context?.let { it.mediaType to it.mediaId } ?: return null
            else -> return null
        }
        return collectionProfileTarget(mediaType, mediaId)
    }

    /** Attaches the selection's list only to a plain track selection; radio and collections stay as-is. */
    fun withTrackListContext(context: NaviampConnectPlaybackContext?) =
        if (type == NaviampConnectMediaType.Track && !startRadio && context != null) copy(context = context) else this
}

private fun collectionProfileTarget(type: NaviampConnectMediaType, id: String): PlaybackProfileTarget? = when (type) {
    NaviampConnectMediaType.Album -> PlaybackProfileTarget(PlaybackProfileTargetType.Album, id)
    NaviampConnectMediaType.Playlist -> PlaybackProfileTarget(PlaybackProfileTargetType.Playlist, id)
    else -> null
}?.normalized()

/**
 * Names the catalog list that local track selection would queue for [command], using the same
 * registry lists as [NaviampCoreTrackActionController]. Only lists a target can resolve by provider
 * ID are named; other track lists keep single-track remote selection.
 */
internal fun NaviampCoreMediaRegistry.connectTrackSelectionContext(
    command: NaviampCoreCommand,
): NaviampConnectPlaybackContext? = when (command) {
    is NaviampCoreCommand.Detail.AlbumTrack -> albumDetails
        ?.takeIf { command.request.action == SharedTrackRowAction.Select }
        ?.takeIf { details -> details.tracks.any { it.id.value == command.request.track.id } }
        ?.let { NaviampConnectPlaybackContext(NaviampConnectMediaType.Album, it.album.id.value) }
    is NaviampCoreCommand.Detail.PlaylistTrack -> selectedPlaylist
        ?.takeIf { command.request.action == SharedTrackRowAction.Select }
        ?.takeIf { selectedPlaylistTracks.any { it.id.value == command.request.track.id } }
        ?.let { NaviampConnectPlaybackContext(NaviampConnectMediaType.Playlist, it.id) }
    else -> null
}

internal data class NaviampCoreConnectQueueSelection(
    val type: NaviampConnectMediaType,
    val id: String,
    val placement: NaviampConnectQueuePlacement,
) {
    fun toCommand(
        sourceIdentity: NaviampConnectSourceIdentity,
        playbackProfile: PlaybackProfile? = null,
    ) = NaviampConnectQueueMedia(
        mediaType = type,
        mediaId = id,
        placement = placement,
        sourceIdentity = sourceIdentity,
        playbackProfile = playbackProfile?.let(NaviampConnectPlaybackProfile::from),
    )

    /** Only an album or playlist added as a whole forms a profile group on the target. */
    fun playbackProfileTarget(): PlaybackProfileTarget? =
        if (placement != NaviampConnectQueuePlacement.AddToQueue) null
        else collectionProfileTarget(type, id)
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
        NaviampMediaItemCommand.PlayAlbum,
        NaviampMediaItemCommand.PlayAlbumMix,
        -> null
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
        // Not routed until Core resolves the seed: see NaviampCoreCommand.Home.StartAlbumMix.
        NaviampMediaItemCommand.PlayAlbumMix -> null
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
    // Existing protocol intent: every target, old or new, plays seed-track radio (similar songs),
    // which has no album fallback, so a remote mix can never become the seed album.
    is NaviampCoreCommand.Home.StartAlbumMix ->
        NaviampCoreConnectMediaSelection(NaviampConnectMediaType.Track, seed.id.value, startRadio = true)
    is NaviampCoreCommand.Home.StartSomethingNewRadio ->
        NaviampCoreConnectMediaSelection(NaviampConnectMediaType.Track, seed.id.value, startRadio = startRadio)
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
    private val beforeMainPlayback: () -> Unit,
    private val onPlaybackIntent: () -> Unit,
    private val closeDelegate: () -> Unit,
) : NaviampCoreCommandHandler {
    constructor(delegate: NaviampCoreCommandHandler, connect: NaviampCoreConnectController?, beforeMainPlayback: () -> Unit = {}) :
        this(delegate, connect, beforeMainPlayback, {}, {})

    private var closed = false
    fun close() { if (!closed) { closed = true; closeDelegate() } }

    override fun dispatch(command: NaviampCoreCommand) {
        if (closed) return
        preparePlayback(command)
        if (connect?.routeProductCommand(command) != true) delegate.dispatch(command)
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult {
        if (closed) throw kotlinx.coroutines.CancellationException("Core playback command owner is closed")
        preparePlayback(command)
        return if (connect?.routeProductCommand(command) == true) NaviampCoreCommandResult.Completed
        else delegate.execute(command)
    }

    private fun preparePlayback(command: NaviampCoreCommand) {
        if (command.invalidatesInterruptionResume()) onPlaybackIntent()
        val action = (command as? NaviampCoreCommand.NowPlaying.Playback)?.request?.action
        if (command.connectMediaSelectionOrNull() != null || action in setOf(
            app.naviamp.ui.NowPlayingPlaybackAction.TogglePlayPause, app.naviamp.ui.NowPlayingPlaybackAction.Resume,
            app.naviamp.ui.NowPlayingPlaybackAction.PlayCurrent, app.naviamp.ui.NowPlayingPlaybackAction.Next,
            app.naviamp.ui.NowPlayingPlaybackAction.Previous)) beforeMainPlayback()
    }
}

/** Intent is recorded before deferred work, so a later OS callback cannot overtake a phone/car click. */
private fun NaviampCoreCommand.invalidatesInterruptionResume(): Boolean = when (this) {
    is NaviampCoreCommand.NowPlaying.Playback -> request.action in setOf(
        app.naviamp.ui.NowPlayingPlaybackAction.Pause, app.naviamp.ui.NowPlayingPlaybackAction.Stop,
        app.naviamp.ui.NowPlayingPlaybackAction.Resume, app.naviamp.ui.NowPlayingPlaybackAction.PlayCurrent,
        app.naviamp.ui.NowPlayingPlaybackAction.TogglePlayPause, app.naviamp.ui.NowPlayingPlaybackAction.Seek,
        app.naviamp.ui.NowPlayingPlaybackAction.Next, app.naviamp.ui.NowPlayingPlaybackAction.Previous)
    is NaviampCoreCommand.NowPlaying.Selection, is NaviampCoreCommand.NowPlaying.Queue,
    is NaviampCoreCommand.NowPlaying.SleepTimer -> true
    is NaviampCoreCommand.NowPlaying.CurrentTrack -> request.action == app.naviamp.ui.NowPlayingCurrentTrackAction.StartRadio
    is NaviampCoreCommand.NowPlaying.QueueItem -> request.action in setOf(
        app.naviamp.ui.NowPlayingItemAction.StartRadio, app.naviamp.ui.NowPlayingItemAction.RemoveFromQueue)
    is NaviampCoreCommand.NowPlaying.QueueUndo -> request.action == app.naviamp.ui.NaviampQueueUndoAction.Undo
    NaviampCoreCommand.Connection.Connect, is NaviampCoreCommand.Connection.ConnectSaved,
    is NaviampCoreCommand.Connection.SwitchAccount, is NaviampCoreCommand.Connection.SelectSavedSource -> true
    else -> connectMediaSelectionOrNull() != null
}
