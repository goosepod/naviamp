package app.naviamp.presentation

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.radio.RecentRadioAction
import app.naviamp.domain.radio.homeStationRadioAction
import app.naviamp.ui.NaviampAlbumDetailCommand
import app.naviamp.ui.NaviampArtistAlbumCommand
import app.naviamp.ui.NaviampArtistDetailCommand
import app.naviamp.ui.NaviampArtistMediaCommand
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.SharedMediaItemUi

/** Owns album, artist, and Home-station actions independently of every platform host. */
class NaviampCoreCollectionActionController(
    private val providerSource: NaviampCoreMediaProviderSource,
    private val registry: NaviampCoreMediaRegistry,
    private val transactions: NaviampCoreMediaTransactions,
    private val mediaDetails: NaviampCoreMediaDetailController,
) : NaviampCoreCommandController {
    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.Home.SelectStation,
        is NaviampCoreCommand.Detail.Album,
        -> NaviampCoreImmediateCommandResult.Deferred
        is NaviampCoreCommand.Detail.Artist -> NaviampCoreImmediateCommandResult.Deferred
        is NaviampCoreCommand.Detail.ArtistAlbum ->
            if (command.request.command == NaviampArtistAlbumCommand.Select) NaviampCoreImmediateCommandResult.Unhandled
            else NaviampCoreImmediateCommandResult.Deferred
        is NaviampCoreCommand.Media.ItemAction -> when (val item = command.request.command) {
            is NaviampMediaItemCommand.Album ->
                if (item.command == NaviampArtistAlbumCommand.Select) NaviampCoreImmediateCommandResult.Unhandled
                else NaviampCoreImmediateCommandResult.Deferred
            is NaviampMediaItemCommand.Artist ->
                if (item.command == NaviampArtistMediaCommand.Select) NaviampCoreImmediateCommandResult.Unhandled
                else NaviampCoreImmediateCommandResult.Deferred
            NaviampMediaItemCommand.PlayAlbum -> NaviampCoreImmediateCommandResult.Deferred
            is NaviampMediaItemCommand.Playlist -> NaviampCoreImmediateCommandResult.Unhandled
        }
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.Home.SelectStation -> executeStation(command.station.id)
            is NaviampCoreCommand.Detail.Album -> executeAlbum(command.request.album.id, command.request.command)
            is NaviampCoreCommand.Detail.Artist -> executeArtist(command.request.artist.id, command.request.artist, command.request.command)
            is NaviampCoreCommand.Detail.ArtistAlbum -> executeAlbum(command.request.album.id, command.request.command)
            is NaviampCoreCommand.Media.ItemAction -> when (val item = command.request.command) {
                is NaviampMediaItemCommand.Album -> executeAlbum(command.request.item.id, item.command)
                is NaviampMediaItemCommand.Artist -> executeArtistMedia(command.request.item.id, command.request.item, item.command)
                NaviampMediaItemCommand.PlayAlbum -> playAlbum(command.request.item.id)
                is NaviampMediaItemCommand.Playlist -> return null
            }
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    private suspend fun executeStation(id: String) {
        when (val action = homeStationRadioAction(id)) {
            RecentRadioAction.PlayLibrary -> transactions.startLibraryRadio()
            RecentRadioAction.PlayRandomAlbum -> transactions.startRandomAlbumRadio()
            is RecentRadioAction.PlayGenre -> transactions.startGenreRadio(action.genre.name)
            is RecentRadioAction.PlayDecade -> transactions.startDecadeRadio(action.fromYear, action.toYear)
            is RecentRadioAction.PlayArtist -> transactions.startArtistRadio(action.artist)
            is RecentRadioAction.PlayAlbum -> transactions.startAlbumRadio(action.album)
            is RecentRadioAction.PlayTrack -> transactions.startTrackRadio(action.track)
            null -> transactions.publish("Home station is no longer available.")
        }
    }

    private suspend fun executeAlbum(id: String, command: NaviampAlbumDetailCommand) {
        val album = registry.album(id) ?: return transactions.publish("Album not found.")
        executeAlbum(album, command)
    }

    private suspend fun executeAlbum(id: String, command: NaviampArtistAlbumCommand) {
        val album = registry.album(id) ?: return transactions.publish("Album not found.")
        when (command) {
            NaviampArtistAlbumCommand.Select -> Unit
            NaviampArtistAlbumCommand.StartRadio -> transactions.startAlbumRadio(album)
            NaviampArtistAlbumCommand.Download -> albumTracks(album).also { transactions.download(album.title, it) }
            NaviampArtistAlbumCommand.AddToQueue -> transactions.addToQueue(albumTracks(album))
            is NaviampArtistAlbumCommand.AddToPlaylist -> transactions.addToPlaylist(albumTracks(album), command.choice)
            is NaviampArtistAlbumCommand.CreatePlaylistAndAdd -> transactions.createPlaylist(albumTracks(album), command.name)
            NaviampArtistAlbumCommand.ToggleFavorite -> transactions.toggleFavorite(album)
        }
    }

    private suspend fun playAlbum(id: String) {
        val album = registry.album(id) ?: return transactions.publish("Album not found.")
        transactions.play(albumTracks(album))
    }

    private suspend fun executeAlbum(album: Album, command: NaviampAlbumDetailCommand) {
        when (command) {
            is NaviampAlbumDetailCommand.Play -> transactions.play(albumTracks(album), shuffle = command.shuffle)
            NaviampAlbumDetailCommand.StartRadio -> transactions.startAlbumRadio(album)
            NaviampAlbumDetailCommand.Download -> albumTracks(album).also { transactions.download(album.title, it) }
            NaviampAlbumDetailCommand.AddToQueue -> transactions.addToQueue(albumTracks(album))
            is NaviampAlbumDetailCommand.AddToPlaylist -> transactions.addToPlaylist(albumTracks(album), command.choice)
            is NaviampAlbumDetailCommand.CreatePlaylistAndAdd -> transactions.createPlaylist(albumTracks(album), command.name)
            NaviampAlbumDetailCommand.ToggleFavorite -> transactions.toggleFavorite(album)
        }
    }

    private suspend fun executeArtist(id: String, item: SharedMediaItemUi, command: NaviampArtistDetailCommand) {
        val artist = registry.artist(id) ?: return transactions.publish("Artist not found.")
        when (command) {
            is NaviampArtistDetailCommand.PlayCatalog -> transactions.play(catalogTracks(command.albums), shuffle = command.shuffle)
            NaviampArtistDetailCommand.StartRadio -> transactions.startArtistRadio(artist)
            NaviampArtistDetailCommand.AddToQueue -> transactions.addToQueue(catalogTracks())
            is NaviampArtistDetailCommand.AddToPlaylist -> transactions.addToPlaylist(catalogTracks(), command.choice)
            is NaviampArtistDetailCommand.CreatePlaylistAndAdd -> transactions.createPlaylist(catalogTracks(), command.name)
            NaviampArtistDetailCommand.ToggleFavorite -> transactions.toggleFavorite(artist)
            NaviampArtistDetailCommand.PlayPopular -> transactions.play(registry.artistPopularTracks)
            NaviampArtistDetailCommand.StartPopularRadio -> registry.artistPopularTracks.firstOrNull()?.let { transactions.startTrackRadio(it) }
                ?: transactions.publish("No popular tracks are available.")
            NaviampArtistDetailCommand.AddPopularToQueue -> transactions.addToQueue(registry.artistPopularTracks)
            NaviampArtistDetailCommand.FindSimilar -> mediaDetails.toggleSimilarArtists(item)
            is NaviampArtistDetailCommand.SelectSimilar -> {
                val similar = registry.artistSimilarArtists.firstOrNull { match ->
                    match.candidate.sourceArtistId == command.artist.id ||
                        match.matchedArtist?.id?.value == command.artist.localArtistId
                }
                val localArtist = similar?.matchedArtist
                val resolvedExternalUrl = similar?.candidate?.externalUrl ?: command.artist.externalUrl
                when {
                    localArtist != null -> mediaDetails.selectArtist(localArtist.toItem())
                    resolvedExternalUrl != null -> transactions.openExternal(resolvedExternalUrl)
                    else -> transactions.publish("Artist is not available.")
                }
            }
            is NaviampArtistDetailCommand.OpenSimilarExternal -> transactions.openExternal(command.url)
        }
    }

    private suspend fun executeArtistMedia(id: String, item: SharedMediaItemUi, command: NaviampArtistMediaCommand) {
        val artist = registry.artist(id) ?: return transactions.publish("Artist not found.")
        when (command) {
            NaviampArtistMediaCommand.Select -> Unit
            NaviampArtistMediaCommand.StartRadio -> transactions.startArtistRadio(artist)
            NaviampArtistMediaCommand.FindSimilar -> mediaDetails.toggleSimilarArtists(item)
            NaviampArtistMediaCommand.AddToQueue -> transactions.addToQueue(artistTracks(artist))
            is NaviampArtistMediaCommand.AddToPlaylist -> transactions.addToPlaylist(artistTracks(artist), command.choice)
            is NaviampArtistMediaCommand.CreatePlaylistAndAdd -> transactions.createPlaylist(artistTracks(artist), command.name)
            NaviampArtistMediaCommand.ToggleFavorite -> transactions.toggleFavorite(artist)
        }
    }

    private suspend fun albumTracks(album: Album): List<Track> =
        registry.albumDetails?.takeIf { it.album.id == album.id }?.tracks
            ?: providerSource.current()?.album(album.id)?.tracks.orEmpty()

    private suspend fun catalogTracks(items: List<SharedMediaItemUi> = registry.artistDetails?.albums.orEmpty().map {
        SharedMediaItemUi(it.id.value, it.title, it.artistName)
    }): List<Track> = items.mapNotNull { registry.album(it.id) }.flatMap { albumTracks(it) }

    private suspend fun artistTracks(artist: Artist): List<Track> {
        val albums = registry.artistDetails?.takeIf { it.artist.id == artist.id }?.albums
            ?: providerSource.current()?.artist(artist.id)?.albums.orEmpty()
        return albums.flatMap { albumTracks(it) }
    }

    private fun Artist.toItem() = SharedMediaItemUi(id.value, name, "")
}
