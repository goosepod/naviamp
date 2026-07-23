package app.naviamp.presentation

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Playlist
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.provider.SonicPathMatch
import app.naviamp.domain.provider.SonicSimilarTrack

internal class FakeCoreMediaProvider(
    supportsSonicSimilarity: Boolean = false,
) : MediaProvider {
    val artist = Artist(ArtistId("core-artist"), "Core Artist")
    val album = Album(
        id = AlbumId("core-album"),
        title = "Core Album",
        artistName = artist.name,
        coverArtId = null,
        recentlyAddedAtIso8601 = null,
        releaseYear = 2026,
    )
    val track = Track(
        id = TrackId("core-track"),
        title = "Core Track",
        artistId = artist.id,
        artistName = artist.name,
        albumId = album.id,
        albumTitle = album.title,
        albumReleaseYear = album.releaseYear,
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
    val playlist = Playlist(
        id = "core-playlist",
        name = "Core Playlist",
        trackCount = 1,
    )

    override val id = ProviderId("fake-core")
    override val displayName = "Fake Core Provider"
    override val capabilities = ProviderCapabilities(
        supportsStreamingTranscode = false,
        supportsDownloadTranscode = false,
        supportsArtistRadio = false,
        supportsAlbumRadio = false,
        supportsTrackRadio = false,
        supportsSonicSimilarity = supportsSonicSimilarity,
    )

    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = listOf(album)
    override suspend fun album(albumId: AlbumId) = AlbumDetails(album, listOf(track))
    override suspend fun artist(artistId: ArtistId) = ArtistDetails(artist, listOf(album))
    override suspend fun artists(limit: Int) = listOf(artist)
    override suspend fun tracks(limit: Int) = listOf(track)
    override suspend fun search(query: String, limit: Int) = MediaSearchResults(
        artists = listOf(artist),
        albums = listOf(album),
        tracks = listOf(
            if (capabilities.supportsSonicSimilarity && query in setOf("start", "end")) {
                track.copy(id = TrackId(query), title = query)
            } else {
                track
            },
        ),
    )
    override suspend fun playlists(limit: Int) = listOf(playlist)
    override suspend fun playlistTracks(playlistId: String) = listOf(track)
    override suspend fun randomSongs(
        limit: Int,
        genre: String?,
        fromYear: Int?,
        toYear: Int?,
    ) = listOf(track)
    override suspend fun findSonicPath(
        startTrackId: TrackId,
        endTrackId: TrackId,
        count: Int,
    ) = if (capabilities.supportsSonicSimilarity) listOf(SonicPathMatch(track)) else emptyList()
    override suspend fun sonicSimilarTrackMatches(trackId: TrackId, count: Int) =
        if (capabilities.supportsSonicSimilarity) {
            listOf(
                SonicSimilarTrack(track, 1.0),
                SonicSimilarTrack(track.copy(id = TrackId("sonic-related"), title = "Sonic Related"), 0.87),
            )
        } else {
            emptyList()
        }
    override suspend fun streamUrl(request: StreamRequest) = "https://example.test/${request.trackId.value}"
    override fun coverArtUrl(coverArtId: String) = "https://example.test/art/$coverArtId"
}
