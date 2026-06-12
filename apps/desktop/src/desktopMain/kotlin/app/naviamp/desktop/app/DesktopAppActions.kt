package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.radio.RecentRadioAction
import app.naviamp.domain.radio.RadioRequest
import app.naviamp.domain.radio.SeededRadioRequest
import app.naviamp.domain.radio.homeStationRadioAction
import app.naviamp.domain.radio.recentRadioAction
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.provider.navidrome.NavidromeProvider

class DesktopAppActions(
    private val connectionLifecycleController: DesktopConnectionLifecycleController,
    private val albumController: DesktopAlbumController,
    private val artistController: DesktopArtistController,
    private val mediaActionsController: DesktopMediaActionsController,
    private val downloadsController: DesktopDownloadsController,
    private val radioController: DesktopRadioController,
    private val internetRadioController: DesktopInternetRadioController,
    private val playlistsController: DesktopPlaylistsController,
    private val libraryController: DesktopLibraryController,
    private val homeContent: () -> HomeContent,
    private val playlists: () -> List<Playlist>,
    private val internetRadioStations: () -> List<InternetRadioStation>,
    private val selectedAlbum: () -> Album?,
    private val selectedAlbumDetails: () -> AlbumDetails?,
    private val selectedArtistPopularTracks: () -> List<Track>,
    private val selectedPlaylist: () -> Playlist?,
    private val selectedPlaylistTracks: () -> List<Track>,
) {
    fun connectToServer(restoreSavedSession: Boolean = false) {
        connectionLifecycleController.connectToServer(restoreSavedSession)
    }

    fun openAlbumDetails(album: Album, backRouteOverride: DesktopAppRoute? = null) {
        albumController.openAlbumDetails(album, backRouteOverride)
    }

    fun playAlbumDetails(shuffle: Boolean = false, index: Int = 0) {
        mediaActionsController.playAlbumDetails(shuffle = shuffle, index = index)
    }

    fun playSearchTrack(index: Int) {
        mediaActionsController.playSearchTrack(index)
    }

    fun playSearchTrackRadio(index: Int) {
        mediaActionsController.searchTrackAt(index)?.let(::playTrackRadio)
    }

    fun downloadSearchTrack(index: Int) {
        mediaActionsController.searchTrackAt(index)?.let(::downloadTrack)
    }

    fun addSearchTrackToQueue(index: Int) {
        mediaActionsController.searchTrackAt(index)?.let(playlistsController::addTrackToQueue)
    }

    fun openSearchTrackAddToPlaylist(index: Int) {
        mediaActionsController.searchTrackAt(index)?.let(playlistsController::openTrackAddToPlaylist)
    }

    fun playRelatedTrack(index: Int) {
        mediaActionsController.playRelatedTrack(index)
    }

    fun playPopularTracks(tracks: List<Track>, index: Int = 0) {
        mediaActionsController.playPopularTracks(tracks, index)
    }

    fun addPopularTracksToQueue(tracks: List<Track>) {
        mediaActionsController.addPopularTracksToQueue(tracks)
    }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        mediaActionsController.applyTrackMetadataUpdate(updatedTrack)
    }

    fun toggleTrackFavorite(track: Track) {
        mediaActionsController.toggleTrackFavorite(track)
    }

    fun toggleArtistFavorite(artist: Artist) {
        mediaActionsController.toggleArtistFavorite(artist)
    }

    fun toggleAlbumFavorite(album: Album) {
        mediaActionsController.toggleAlbumFavorite(album)
    }

    fun setTrackRating(track: Track, rating: Int?) {
        mediaActionsController.setTrackRating(track, rating)
    }

    fun downloadTracks(label: String, tracks: List<Track>) {
        downloadsController.downloadTracks(label, tracks)
    }

    fun downloadTrack(track: Track) {
        downloadsController.downloadTrack(track)
    }

    fun downloadAlbum(album: Album) {
        downloadsController.downloadAlbum(album)
    }

    fun downloadPlaylist(playlist: Playlist) {
        downloadsController.downloadPlaylist(playlist)
    }

    fun removeDownloadedTrack(download: DownloadedTrack) {
        downloadsController.removeDownloadedTrack(download)
    }

    fun playDownloadedTrack(downloads: List<DownloadedTrack>, index: Int) {
        downloadsController.playDownloadedTrack(downloads, index)
    }

    fun playRadio(request: RadioRequest) {
        radioController.play(request)
    }

    fun startSeededRadio(provider: NavidromeProvider, request: SeededRadioRequest) {
        radioController.startSeeded(provider, request)
    }

    fun playPopularTracksRadio(tracks: List<Track>) {
        radioController.playPopularTracks(tracks)
    }

    fun playTrackRadio(track: Track) {
        radioController.playTrack(track)
    }

    fun convertCurrentTrackToRadio(track: Track) {
        radioController.convertCurrentTrackToRadio(track, ::playTrackRadio)
    }

    fun playLibraryRadio() {
        radioController.playLibrary()
    }

    fun playGenreRadio(genre: Genre) {
        radioController.playGenre(genre)
    }

    fun playDecadeRadio(fromYear: Int, toYear: Int) {
        radioController.playDecade(fromYear, toYear)
    }

    fun playRandomAlbumRadio() {
        radioController.playRandomAlbum()
    }

    fun playArtistRadio(artist: Artist) {
        radioController.playArtist(artist)
    }

    fun playAlbumRadio(album: Album) {
        val loadedAlbumTracks = if (selectedAlbum()?.id == album.id || selectedAlbumDetails()?.album?.id == album.id) {
            selectedAlbumDetails()?.tracks.orEmpty()
        } else {
            emptyList()
        }
        radioController.playAlbum(album, loadedAlbumTracks)
    }

    fun playCurrentAlbumRadio() {
        currentAlbum()?.let(::playAlbumRadio)
    }

    fun downloadCurrentAlbum() {
        selectedAlbumDetails()?.let { downloadTracks(it.album.title, it.tracks) }
            ?: selectedAlbum()?.let(::downloadAlbum)
    }

    fun addCurrentAlbumToQueue() {
        currentAlbum()?.let(playlistsController::addAlbumToQueue)
    }

    fun openCurrentAlbumAddToPlaylist() {
        currentAlbum()?.let(playlistsController::openAlbumAddToPlaylist)
    }

    fun playSelectedPopularTrack(track: Track) {
        val tracks = selectedArtistPopularTracks()
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playPopularTracks(tracks, index)
    }

    fun downloadSelectedPlaylist() {
        selectedPlaylist()?.let { playlist -> downloadTracks(playlist.name, selectedPlaylistTracks()) }
    }

    fun playRecentRadio(stream: RecentRadioStream) {
        when (val action = recentRadioAction(stream) ?: return) {
            RecentRadioAction.PlayLibrary -> playLibraryRadio()
            RecentRadioAction.PlayRandomAlbum -> playRandomAlbumRadio()
            is RecentRadioAction.PlayGenre -> playGenreRadio(action.genre)
            is RecentRadioAction.PlayDecade -> playDecadeRadio(action.fromYear, action.toYear)
            is RecentRadioAction.PlayArtist -> playArtistRadio(action.artist)
            is RecentRadioAction.PlayAlbum -> playAlbumRadio(action.album)
            is RecentRadioAction.PlayTrack -> playTrackRadio(action.track)
        }
    }

    fun openHomeAlbum(itemId: String) {
        homeAlbums().firstOrNull { it.id.value == itemId }?.let(::openAlbumDetails)
    }

    fun toggleHomeAlbumFavorite(itemId: String) {
        homeAlbums().firstOrNull { it.id.value == itemId }?.let(::toggleAlbumFavorite)
    }

    fun openHomePlaylist(itemId: String) {
        homePlaylists().firstOrNull { it.id == itemId }?.let(::openPlaylistDetails)
    }

    fun playHomeRecentRadio(itemId: String) {
        homeContent().recentRadioStreams.firstOrNull { it.id == itemId }?.let(::playRecentRadio)
    }

    fun playHomeInternetRadio(itemId: String) {
        homeInternetRadioStations().firstOrNull { it.id == itemId }?.let(internetRadioController::playStation)
    }

    fun playHomeStation(stationId: String) {
        when (val action = homeStationRadioAction(stationId) ?: return) {
            RecentRadioAction.PlayLibrary -> playLibraryRadio()
            RecentRadioAction.PlayRandomAlbum -> playRandomAlbumRadio()
            is RecentRadioAction.PlayGenre -> playGenreRadio(action.genre)
            is RecentRadioAction.PlayDecade -> playDecadeRadio(action.fromYear, action.toYear)
            is RecentRadioAction.PlayArtist,
            is RecentRadioAction.PlayAlbum,
            is RecentRadioAction.PlayTrack,
            -> Unit
        }
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        playlistsController.playPlaylist(playlist, shuffle)
    }

    fun openPlaylistDetails(playlist: Playlist) {
        playlistsController.openPlaylistDetails(playlist)
    }

    fun playPlaylistDetails(index: Int = 0, shuffle: Boolean = false) {
        playlistsController.playPlaylistDetails(index, shuffle)
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        playlistsController.renamePlaylist(playlist, name)
    }

    fun deletePlaylist(playlist: Playlist) {
        playlistsController.deletePlaylist(playlist)
    }

    fun clearCacheData() {
        libraryController.clearCacheData()
    }

    fun clearLibraryData() {
        libraryController.clearLibraryData()
    }

    fun clearActiveConnectionState() {
        connectionLifecycleController.clearActiveConnectionState()
    }

    fun resetDatabase() {
        connectionLifecycleController.resetDatabase()
    }

    fun deleteConnection(source: SavedMediaSource) {
        connectionLifecycleController.deleteConnection(source)
    }

    fun openExternalArtistUrl(url: String) {
        artistController.openExternalArtistUrl(url)
    }

    fun findSimilarArtists(artist: Artist) {
        artistController.toggleSimilarArtists(artist)
    }

    fun openArtistDetails(
        artist: Artist,
        backRouteOverride: DesktopAppRoute? = null,
        pushCurrentArtist: Boolean = true,
    ) {
        artistController.openArtistDetails(artist, backRouteOverride, pushCurrentArtist)
    }

    fun openTrackArtistDetails(track: Track, backRouteOverride: DesktopAppRoute = DesktopAppRoute.Player) {
        artistController.openTrackArtistDetails(track, backRouteOverride)
    }

    fun closeArtistDetails() {
        artistController.closeArtistDetails()
    }

    fun openTrackAlbumDetails(track: Track) {
        albumController.openTrackAlbumDetails(track)
    }

    private fun homeAlbums(): List<Album> {
        val content = homeContent()
        return (
            content.recentlyAddedAlbums +
                content.mixAlbums +
                content.recentAlbums +
                content.frequentAlbums +
                content.randomAlbums +
                content.genreSpotlightAlbums +
                content.decadeAlbums
            ).distinctBy { it.id }
    }

    private fun homePlaylists(): List<Playlist> =
        (homeContent().playlists + playlists()).distinctBy { it.id }

    private fun homeInternetRadioStations(): List<InternetRadioStation> {
        val content = homeContent()
        return (
            content.recentInternetRadioStations +
                content.radioStations +
                internetRadioStations()
            ).distinctBy { it.id }
    }

    private fun currentAlbum(): Album? =
        selectedAlbumDetails()?.album ?: selectedAlbum()
}
