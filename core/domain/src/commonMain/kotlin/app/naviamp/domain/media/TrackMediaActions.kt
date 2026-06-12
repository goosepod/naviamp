package app.naviamp.domain.media

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.queue.PlaybackQueue

data class TrackPlaybackSelection(
    val tracks: List<Track>,
    val index: Int,
)

data class MediaTrackLookupSources(
    val primaryTracks: List<Track> = emptyList(),
    val selectedPlaylistTracks: List<Track> = emptyList(),
    val relatedTracks: List<Track> = emptyList(),
    val artistPopularTracks: List<Track> = emptyList(),
    val extraTracks: List<Track> = emptyList(),
    val fallbackTracks: List<Track> = emptyList(),
)

data class SelectedTrackPlayback(
    val track: Track,
    val tracks: List<Track>,
)

fun trackPlaybackSelection(
    tracks: List<Track>,
    index: Int,
    shuffle: Boolean = false,
): TrackPlaybackSelection? {
    if (tracks.isEmpty() || index !in tracks.indices) return null
    return TrackPlaybackSelection(
        tracks = if (shuffle) tracks.shuffled() else tracks,
        index = if (shuffle) 0 else index,
    )
}

fun knownTracksForMediaActions(sources: MediaTrackLookupSources): List<Track> =
    sources.primaryTracks +
        sources.selectedPlaylistTracks +
        sources.relatedTracks +
        sources.artistPopularTracks +
        sources.extraTracks +
        sources.fallbackTracks

fun findKnownTrack(
    trackId: String,
    sources: MediaTrackLookupSources,
): Track? =
    knownTracksForMediaActions(sources).firstOrNull { track -> track.id.value == trackId }

fun selectedTrackPlayback(
    trackId: String,
    sources: MediaTrackLookupSources,
): SelectedTrackPlayback? {
    val currentTracks = sources.primaryTracks.takeIf { tracks -> tracks.any { it.id.value == trackId } }
        ?: sources.relatedTracks.takeIf { tracks -> tracks.any { it.id.value == trackId } }
        ?: sources.artistPopularTracks.takeIf { tracks -> tracks.any { it.id.value == trackId } }
        ?: sources.fallbackTracks
    val track = currentTracks.firstOrNull { it.id.value == trackId }
        ?: findKnownTrack(trackId, sources)
        ?: return null
    return SelectedTrackPlayback(track = track, tracks = currentTracks)
}

fun searchOrAlbumTracksForMediaActions(
    searchResults: MediaSearchResults,
    albumDetail: AlbumDetails?,
): List<Track> =
    albumDetail?.tracks ?: searchResults.tracks

fun Track?.withUpdatedTrack(updatedTrack: Track): Track? =
    this?.let { track ->
        if (track.id == updatedTrack.id) updatedTrack else track
    }

fun List<Track>.withUpdatedTrack(updatedTrack: Track): List<Track> =
    map { track ->
        if (track.id == updatedTrack.id) updatedTrack else track
    }

fun MediaSearchResults.withUpdatedTrack(updatedTrack: Track): MediaSearchResults =
    copy(tracks = tracks.withUpdatedTrack(updatedTrack))

fun AlbumDetails.withUpdatedTrack(updatedTrack: Track): AlbumDetails =
    copy(tracks = tracks.withUpdatedTrack(updatedTrack))

fun PlaybackQueue.withUpdatedTrack(updatedTrack: Track): PlaybackQueue =
    copy(tracks = tracks.withUpdatedTrack(updatedTrack))

fun Artist?.withUpdatedArtist(updatedArtist: Artist): Artist? =
    this?.let { artist ->
        if (artist.id == updatedArtist.id) updatedArtist else artist
    }

fun List<Artist>.withUpdatedArtist(updatedArtist: Artist): List<Artist> =
    map { artist ->
        if (artist.id == updatedArtist.id) updatedArtist else artist
    }

fun Album?.withUpdatedAlbum(updatedAlbum: Album): Album? =
    this?.let { album ->
        if (album.id == updatedAlbum.id) updatedAlbum else album
    }

fun List<Album>.withUpdatedAlbum(updatedAlbum: Album): List<Album> =
    map { album ->
        if (album.id == updatedAlbum.id) updatedAlbum else album
    }

fun MediaSearchResults.withUpdatedArtist(updatedArtist: Artist): MediaSearchResults =
    copy(artists = artists.withUpdatedArtist(updatedArtist))

fun MediaSearchResults.withUpdatedAlbum(updatedAlbum: Album): MediaSearchResults =
    copy(albums = albums.withUpdatedAlbum(updatedAlbum))

fun AlbumDetails.withUpdatedAlbum(updatedAlbum: Album): AlbumDetails =
    copy(album = album.withUpdatedAlbum(updatedAlbum) ?: album)

fun ArtistDetails.withUpdatedArtist(updatedArtist: Artist): ArtistDetails =
    copy(artist = artist.withUpdatedArtist(updatedArtist) ?: artist)

fun ArtistDetails.withUpdatedAlbum(updatedAlbum: Album): ArtistDetails =
    copy(albums = albums.withUpdatedAlbum(updatedAlbum))

fun HomeContent.withUpdatedArtist(updatedArtist: Artist): HomeContent =
    copy(artists = artists.withUpdatedArtist(updatedArtist))

fun HomeContent.withUpdatedAlbum(updatedAlbum: Album): HomeContent =
    copy(
        recentlyAddedAlbums = recentlyAddedAlbums.withUpdatedAlbum(updatedAlbum),
        mixAlbums = mixAlbums.withUpdatedAlbum(updatedAlbum),
        recentAlbums = recentAlbums.withUpdatedAlbum(updatedAlbum),
        frequentAlbums = frequentAlbums.withUpdatedAlbum(updatedAlbum),
        randomAlbums = randomAlbums.withUpdatedAlbum(updatedAlbum),
        genreSpotlightAlbums = genreSpotlightAlbums.withUpdatedAlbum(updatedAlbum),
        decadeAlbums = decadeAlbums.withUpdatedAlbum(updatedAlbum),
    )

fun knownArtistsForMetadata(
    homeContent: HomeContent,
    searchResults: MediaSearchResults,
    artistDetails: ArtistDetails?,
    extraArtists: List<Artist> = emptyList(),
): List<Artist> =
    homeContent.artists +
        searchResults.artists +
        listOfNotNull(artistDetails?.artist) +
        extraArtists

fun knownAlbumsForMetadata(
    homeContent: HomeContent,
    searchResults: MediaSearchResults,
    albumDetails: AlbumDetails?,
    artistDetails: ArtistDetails?,
    extraAlbums: List<Album> = emptyList(),
): List<Album> =
    homeContent.recentlyAddedAlbums +
        homeContent.mixAlbums +
        homeContent.recentAlbums +
        homeContent.frequentAlbums +
        homeContent.randomAlbums +
        homeContent.genreSpotlightAlbums +
        homeContent.decadeAlbums +
        searchResults.albums +
        listOfNotNull(albumDetails?.album) +
        artistDetails?.albums.orEmpty() +
        extraAlbums

suspend fun favoriteTrackUpdate(
    provider: MediaProvider,
    track: Track,
    favoritedAtIso8601: String?,
): Track? {
    if (!provider.capabilities.supportsTrackFavorites) return null
    val favorite = track.favoritedAtIso8601 == null
    provider.setTrackFavorite(track.id, favorite)
    return track.copy(favoritedAtIso8601 = if (favorite) favoritedAtIso8601 else null)
}

suspend fun favoriteArtistUpdate(
    provider: MediaProvider,
    artist: Artist,
    favoritedAtIso8601: String?,
): Artist? {
    if (!provider.capabilities.supportsArtistFavorites) return null
    val favorite = artist.favoritedAtIso8601 == null
    provider.setArtistFavorite(artist.id, favorite)
    return artist.copy(favoritedAtIso8601 = if (favorite) favoritedAtIso8601 else null)
}

suspend fun favoriteAlbumUpdate(
    provider: MediaProvider,
    album: Album,
    favoritedAtIso8601: String?,
): Album? {
    if (!provider.capabilities.supportsAlbumFavorites) return null
    val favorite = album.favoritedAtIso8601 == null
    provider.setAlbumFavorite(album.id, favorite)
    return album.copy(favoritedAtIso8601 = if (favorite) favoritedAtIso8601 else null)
}

suspend fun ratedTrackUpdate(
    provider: MediaProvider,
    track: Track,
    rating: Int?,
): Track? {
    if (!provider.capabilities.supportsTrackRatings) return null
    provider.setTrackRating(track.id, rating)
    return track.copy(userRating = rating)
}
