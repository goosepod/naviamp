package app.naviamp.presentation

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRows

/** Authoritative stable-ID lookup state shared by every Core feature controller. */
class NaviampCoreMediaRegistry {
    var home: HomeContent = HomeContent()
        private set
    var search: MediaSearchResults = MediaSearchResults()
        private set
    var libraryArtists: List<Artist> = emptyList()
        private set
    var albumDetails: AlbumDetails? = null
        private set
    var artistDetails: ArtistDetails? = null
        private set
    var artistPopularTracks: List<Track> = emptyList()
        private set
    var artistSimilarArtists: List<SimilarArtistMatch> = emptyList()
        private set
    var playlists: List<Playlist> = emptyList()
        private set
    var selectedPlaylist: Playlist? = null
        private set
    var selectedPlaylistTracks: List<Track> = emptyList()
        private set
    var sonicRows: SonicHomeDiscoveryRows = SonicHomeDiscoveryRows()
        private set

    fun updateHome(content: HomeContent, sonicRows: SonicHomeDiscoveryRows) {
        home = content
        this.sonicRows = sonicRows
    }

    fun updateSearch(results: MediaSearchResults) {
        search = results
    }

    fun updateLibraryArtists(artists: List<Artist>, replace: Boolean) {
        libraryArtists = if (replace) artists else (libraryArtists + artists).distinctBy { it.id }
    }

    fun updateAlbum(details: AlbumDetails?) {
        albumDetails = details
    }

    fun updateArtist(
        details: ArtistDetails?,
        popularTracks: List<Track> = emptyList(),
        similarArtists: List<SimilarArtistMatch> = emptyList(),
    ) {
        artistDetails = details
        artistPopularTracks = popularTracks
        artistSimilarArtists = similarArtists
    }

    fun updatePlaylists(playlists: List<Playlist>) {
        this.playlists = playlists
    }

    fun updateSelectedPlaylist(playlist: Playlist?, tracks: List<Track>) {
        selectedPlaylist = playlist
        selectedPlaylistTracks = tracks
    }

    fun album(id: String): Album? = sequenceOf(
        albumDetails?.album,
        artistDetails?.albums?.firstOrNull { it.id.value == id },
        homeAlbums().firstOrNull { it.id.value == id },
        search.albums.firstOrNull { it.id.value == id },
    ).filterNotNull().firstOrNull { it.id.value == id }

    fun artist(id: String): Artist? = sequenceOf(
        artistDetails?.artist,
        home.artists.firstOrNull { it.id.value == id },
        search.artists.firstOrNull { it.id.value == id },
        libraryArtists.firstOrNull { it.id.value == id },
    ).filterNotNull().firstOrNull { it.id.value == id }

    fun playlist(id: String): Playlist? =
        playlists.firstOrNull { it.id == id }
            ?: selectedPlaylist?.takeIf { it.id == id }
            ?: home.playlists.firstOrNull { it.id == id }

    fun tracks(): List<Track> = (
        home.recentlyPlayedTracks +
            search.tracks +
            albumDetails?.tracks.orEmpty() +
            artistPopularTracks +
            selectedPlaylistTracks +
            sonicRows.rows.flatMap { it.tracks }
        ).distinctBy { it.id }

    fun track(id: String): Track? = tracks().firstOrNull { it.id.value == id }

    fun sonicTrack(rowId: String, trackId: String): Track? =
        sonicRows.rows.firstOrNull { it.id.value == rowId }
            ?.tracks
            ?.firstOrNull { it.id.value == trackId }

    fun updateTrack(track: Track) {
        home = home.copy(recentlyPlayedTracks = home.recentlyPlayedTracks.replace(track))
        search = search.copy(tracks = search.tracks.replace(track))
        albumDetails = albumDetails?.copy(tracks = albumDetails!!.tracks.replace(track))
        artistPopularTracks = artistPopularTracks.replace(track)
        selectedPlaylistTracks = selectedPlaylistTracks.replace(track)
        sonicRows = sonicRows.copy(
            rows = sonicRows.rows.map { row -> row.copy(tracks = row.tracks.replace(track)) },
        )
    }

    fun updateAlbum(album: Album) {
        home = home.copy(
            recentlyAddedAlbums = home.recentlyAddedAlbums.replace(album),
            mixAlbums = home.mixAlbums.replace(album),
            recentAlbums = home.recentAlbums.replace(album),
            frequentAlbums = home.frequentAlbums.replace(album),
            randomAlbums = home.randomAlbums.replace(album),
            genreSpotlightAlbums = home.genreSpotlightAlbums.replace(album),
            decadeAlbums = home.decadeAlbums.replace(album),
        )
        search = search.copy(albums = search.albums.replace(album))
        albumDetails = albumDetails?.let { it.copy(album = if (it.album.id == album.id) album else it.album) }
        artistDetails = artistDetails?.copy(albums = artistDetails!!.albums.replace(album))
    }

    fun updateArtist(artist: Artist) {
        home = home.copy(artists = home.artists.replace(artist))
        search = search.copy(artists = search.artists.replace(artist))
        libraryArtists = libraryArtists.replace(artist)
        artistDetails = artistDetails?.let { it.copy(artist = if (it.artist.id == artist.id) artist else it.artist) }
    }

    private fun homeAlbums() = home.recentlyAddedAlbums + home.mixAlbums + home.recentAlbums +
        home.frequentAlbums + home.randomAlbums + home.genreSpotlightAlbums + home.decadeAlbums
}

private fun List<Track>.replace(updated: Track) = map { if (it.id == updated.id) updated else it }
private fun List<Album>.replace(updated: Album) = map { if (it.id == updated.id) updated else it }
private fun List<Artist>.replace(updated: Artist) = map { if (it.id == updated.id) updated else it }
