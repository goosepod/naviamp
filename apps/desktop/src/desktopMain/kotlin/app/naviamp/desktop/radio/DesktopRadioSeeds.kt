package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.radio.selectAlbumRadioSeedTrack
import app.naviamp.domain.radio.selectArtistRadioSeedTrack

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
