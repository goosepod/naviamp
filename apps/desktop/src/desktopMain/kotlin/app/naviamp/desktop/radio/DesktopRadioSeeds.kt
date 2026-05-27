package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider

suspend fun artistRadioSeedTrack(
    cache: DesktopCache,
    provider: MediaProvider,
    artist: Artist,
    sourceId: String?,
): Track? =
    selectArtistRadioSeedTrack(
        artist = artist,
        sourceId = sourceId,
        randomLibraryTrackForArtist = { localSourceId, artistId ->
            cache.randomLibraryTrackForArtist(localSourceId, artistId)
        },
        artistDetails = { cache.artist(provider, artist.id) },
        albumDetails = { album -> cache.album(provider, album.id) },
    )

suspend fun albumRadioSeedTrack(
    cache: DesktopCache,
    provider: MediaProvider,
    album: Album,
    sourceId: String?,
    loadedAlbumTracks: List<Track> = emptyList(),
): Track? =
    selectAlbumRadioSeedTrack(
        album = album,
        sourceId = sourceId,
        loadedAlbumTracks = loadedAlbumTracks,
        randomLibraryTrackForAlbum = { localSourceId, albumId ->
            cache.randomLibraryTrackForAlbum(localSourceId, albumId)
        },
        albumDetails = { cache.album(provider, album.id) },
    )

suspend fun selectArtistRadioSeedTrack(
    artist: Artist,
    sourceId: String?,
    randomLibraryTrackForArtist: suspend (String, ArtistId) -> Track?,
    artistDetails: suspend () -> ArtistDetails,
    albumDetails: suspend (Album) -> AlbumDetails,
): Track? =
    runCatching {
        sourceId?.let { localSourceId ->
            randomLibraryTrackForArtist(localSourceId, artist.id)?.let { return@runCatching it }
        }
        artistDetails().albums.shuffled().forEach { album ->
            val tracks = albumDetails(album).tracks
            tracks.filter { it.artistId == artist.id }
                .randomOrNull()
                ?.let { return@runCatching it }
            tracks.filter { it.artistName.equals(artist.name, ignoreCase = true) }
                .randomOrNull()
                ?.let { return@runCatching it }
        }
        null
    }.getOrNull()

suspend fun selectAlbumRadioSeedTrack(
    album: Album,
    sourceId: String?,
    loadedAlbumTracks: List<Track> = emptyList(),
    randomLibraryTrackForAlbum: suspend (String, AlbumId) -> Track?,
    albumDetails: suspend () -> AlbumDetails,
): Track? =
    runCatching {
        loadedAlbumTracks.randomOrNull()?.let { return@runCatching it }
        sourceId?.let { localSourceId ->
            randomLibraryTrackForAlbum(localSourceId, album.id)?.let { return@runCatching it }
        }
        albumDetails().tracks.randomOrNull()
    }.getOrNull()
