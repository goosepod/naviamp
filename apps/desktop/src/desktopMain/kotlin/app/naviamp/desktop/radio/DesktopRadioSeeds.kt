package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.radio.selectAlbumRadioSeedTrack
import app.naviamp.domain.radio.selectArtistRadioSeedTrack

suspend fun artistRadioSeedTrack(
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseService: ProviderResponseService,
    provider: MediaProvider,
    artist: Artist,
    sourceId: String?,
): Track? =
    selectArtistRadioSeedTrack(
        artist = artist,
        sourceId = sourceId,
        randomLibraryTrackForArtist = { localSourceId, artistId ->
            libraryIndexRepository.randomLibraryTrackForArtist(localSourceId, artistId)
        },
        artistDetails = { providerResponseService.artist(provider, artist.id) },
        albumDetails = { album -> providerResponseService.album(provider, album.id) },
    )

suspend fun albumRadioSeedTrack(
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseService: ProviderResponseService,
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
            libraryIndexRepository.randomLibraryTrackForAlbum(localSourceId, albumId)
        },
        albumDetails = { providerResponseService.album(provider, album.id) },
    )
