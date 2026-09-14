package app.naviamp.presentation

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.TrackId
import app.naviamp.domain.connect.NaviampConnectMediaType
import app.naviamp.domain.connect.NaviampConnectQueueMedia
import app.naviamp.domain.connect.NaviampConnectQueuePlacement
import app.naviamp.domain.connect.NaviampConnectStartMedia

/** Resolves provider IDs on the target; controller metadata is never trusted as catalog data. */
class NaviampCoreConnectCatalogController(
    private val providerSource: NaviampCoreMediaProviderSource,
    private val media: NaviampCoreMediaTransactions,
    private val radio: NaviampCoreInternetRadioController,
    private val registry: NaviampCoreMediaRegistry,
) {
    suspend fun start(command: NaviampConnectStartMedia): Boolean {
        if (command.mediaType == NaviampConnectMediaType.InternetRadioStation) {
            return radio.startConnectStation(command.mediaId)
        }
        val provider = providerSource.current() ?: return false
        return runCatching {
            when (command.mediaType) {
                NaviampConnectMediaType.Track -> {
                    val track = provider.track(TrackId(command.mediaId)) ?: return false
                    if (command.startRadio) media.startTrackRadio(track) else media.play(listOf(track))
                }
                NaviampConnectMediaType.Album -> {
                    val details = provider.album(AlbumId(command.mediaId))
                    if (command.startRadio) media.startAlbumRadio(details.album)
                    else media.playAlbum(details.album, details.tracks, shuffle = command.shuffle)
                }
                NaviampConnectMediaType.Artist -> {
                    val details = provider.artist(ArtistId(command.mediaId))
                    if (command.startRadio) {
                        media.startArtistRadio(details.artist)
                    } else {
                        val tracks = buildList {
                            details.albums.forEach { album -> addAll(provider.album(album.id).tracks) }
                        }
                        media.play(tracks, shuffle = command.shuffle)
                    }
                }
                NaviampConnectMediaType.Playlist ->
                    media.play(provider.playlistTracks(command.mediaId), shuffle = command.shuffle)
                NaviampConnectMediaType.InternetRadioStation -> error("Handled above")
            }
            true
        }.getOrDefault(false)
    }

    suspend fun queue(command: NaviampConnectQueueMedia): Boolean {
        val provider = providerSource.current() ?: return false
        return runCatching {
            if (command.placement == NaviampConnectQueuePlacement.AddToQueue) {
                when (command.mediaType) {
                    NaviampConnectMediaType.Album -> {
                        val details = provider.album(AlbumId(command.mediaId))
                        if (details.tracks.isEmpty()) return false
                        media.addAlbumToQueue(details.album, details.tracks)
                        return true
                    }
                    NaviampConnectMediaType.Playlist -> {
                        val tracks = provider.playlistTracks(command.mediaId)
                        if (tracks.isEmpty()) return false
                        media.addPlaylistToQueue(
                            playlistId = command.mediaId,
                            playlistName = registry.playlist(command.mediaId)?.name ?: "Playlist",
                            tracks = tracks,
                        )
                        return true
                    }
                    else -> Unit
                }
            }
            val tracks = when (command.mediaType) {
                NaviampConnectMediaType.Track -> listOfNotNull(provider.track(TrackId(command.mediaId)))
                NaviampConnectMediaType.Album -> provider.album(AlbumId(command.mediaId)).tracks
                NaviampConnectMediaType.Artist -> provider.artist(ArtistId(command.mediaId)).albums.flatMap {
                    provider.album(it.id).tracks
                }
                NaviampConnectMediaType.Playlist -> provider.playlistTracks(command.mediaId)
                NaviampConnectMediaType.InternetRadioStation -> return false
            }
            if (tracks.isEmpty()) return false
            when (command.placement) {
                NaviampConnectQueuePlacement.AddToQueue -> media.addToQueue(tracks)
                NaviampConnectQueuePlacement.PlayNext -> media.playNext(tracks)
                NaviampConnectQueuePlacement.PlayNextTrack ->
                    tracks.asReversed().forEach(media::playNextTrack)
            }
            true
        }.getOrDefault(false)
    }
}
