package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.Album
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.home.HomeStationLibrary
import app.naviamp.domain.home.HomeStationRandomAlbum
import app.naviamp.domain.home.homeDecadeStationId
import app.naviamp.domain.home.homeGenreStationId
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.radio.RecentRadioAction
import app.naviamp.domain.radio.recentRadioAction
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedTrackRowUi
import kotlinx.coroutines.CoroutineScope

internal class AndroidShellMediaController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
    private val settingsStore: AndroidSettingsStore,
    private val queueController: PlaybackQueueController,
    private val playbackEngine: AndroidPlaybackEngine,
    private val internetRadioStationManager: app.naviamp.domain.radio.InternetRadioStationManager,
    private val activeQueue: () -> List<Track>,
    private val findKnownTrack: (String) -> Track?,
    private val playTrack: (Track, List<Track>?) -> Unit,
    private val playRadioTrack: (Track, List<Track>) -> Unit,
    private val playInternetRadioStation: (InternetRadioStation) -> Unit,
    private val startTrackRadio: (Track) -> Unit,
    private val startAlbumRadio: (Album, List<Track>) -> Unit,
    private val openArtistDetails: (app.naviamp.domain.ArtistId, String?) -> Unit,
    private val rememberRecentRadioStream: (RecentRadioStream) -> Unit,
) {
    fun handleShellTrackSelected(selectedTrack: SharedTrackRowUi) {
        val playback = selectedAndroidTrackPlayback(state, selectedTrack.id, activeQueue())
        if (playback == null) {
            state.status = "Track not found."
            return
        }
        val (track, currentTracks) = playback
        playTrack(track, currentTracks)
    }

    fun handleShellAlbumSelected(selectedAlbum: SharedMediaItemUi) {
        openAndroidAlbumDetails(
            scope = scope,
            state = state,
            libraryIndexRepository = storage,
            providerResponseCacheRepository = storage,
            selectedAlbum = selectedAlbum,
        )
    }

    fun handleShellHomeStationSelected(station: SharedHomeStationUi) {
        startAndroidHomeStationRadio(
            scope = scope,
            state = state,
            stationId = station.id,
            stationTitle = station.title,
            playTrack = { track, queue -> playTrack(track, queue) },
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = rememberRecentRadioStream,
        )
    }

    fun handleShellRecentRadioSelected(item: SharedMediaItemUi) {
        val stream = state.homeState.recentRadioStreams.firstOrNull { it.id == item.id }
            ?: settingsStore.loadRecentRadioStreams().firstOrNull { it.id == item.id }
            ?: return
        when (val action = recentRadioAction(stream) ?: return) {
            RecentRadioAction.PlayLibrary -> handleShellHomeStationSelected(
                SharedHomeStationUi(
                    id = HomeStationLibrary,
                    title = "Library Radio",
                    subtitle = "Random tracks from your full library",
                ),
            )
            RecentRadioAction.PlayRandomAlbum -> handleShellHomeStationSelected(
                SharedHomeStationUi(
                    id = HomeStationRandomAlbum,
                    title = "Random Album Radio",
                    subtitle = "Start from a random album",
                ),
            )
            is RecentRadioAction.PlayGenre -> handleShellHomeStationSelected(
                SharedHomeStationUi(
                    id = homeGenreStationId(action.genre.name),
                    title = "${action.genre.name} Radio",
                    subtitle = "A random ${action.genre.name} station",
                ),
            )
            is RecentRadioAction.PlayDecade -> handleShellHomeStationSelected(
                SharedHomeStationUi(
                    id = homeDecadeStationId(action.fromYear, action.toYear),
                    title = "${action.fromYear}s Radio",
                    subtitle = "Random songs from ${action.fromYear}s",
                ),
            )
            is RecentRadioAction.PlayArtist -> startAndroidArtistRadio(
                scope = scope,
                state = state,
                queueController = queueController,
                artistId = action.artist.id,
                artistTitle = action.artist.name,
                artist = action.artist,
                playTrack = playRadioTrack,
                providerResponseCacheRepository = storage,
                rememberRecentRadioStream = rememberRecentRadioStream,
            )
            is RecentRadioAction.PlayAlbum -> startAlbumRadio(action.album, emptyList())
            is RecentRadioAction.PlayTrack -> startTrackRadio(action.track)
        }
    }

    fun handleShellGoToAlbum() {
        openAndroidNowPlayingAlbumDetails(scope, state, storage, storage)
    }

    fun handleTrackGoToAlbum(track: Track) {
        val albumId = track.albumId ?: return
        openAndroidAlbumDetails(
            scope = scope,
            state = state,
            libraryIndexRepository = storage,
            providerResponseCacheRepository = storage,
            selectedAlbum = SharedMediaItemUi(
                id = albumId.value,
                title = track.albumTitle.orEmpty(),
                subtitle = track.artistName.orEmpty(),
            ),
        )
    }

    fun handleShellRatingSelected(rating: Int?) {
        setAndroidCurrentTrackRating(scope, state, playbackEngine, rating)
    }

    fun handleMixAlbumSelected(selectedAlbum: SharedMediaItemUi) {
        state.homeState.mixAlbums.firstOrNull { it.id.value == selectedAlbum.id }
            ?.let { startAlbumRadio(it, emptyList()) }
            ?: run { state.status = "Album not found." }
    }

    fun handleShellAlbumPlay(shuffle: Boolean) {
        val albumTracks = state.albumDetail?.tracks.orEmpty()
        val queue = if (shuffle) albumTracks.shuffled() else albumTracks
        queue.firstOrNull()?.let { playTrack(it, queue) }
            ?: run { state.status = "Album is empty." }
    }

    fun handleShellAlbumTrackSelected(selectedTrack: SharedTrackRowUi) {
        val track = state.albumDetail?.tracks?.firstOrNull { it.id.value == selectedTrack.id }
            ?: findKnownTrack(selectedTrack.id)
        if (track == null) {
            state.status = "Track not found."
        } else {
            startTrackRadio(track)
        }
    }

    fun handleShellAlbumRadio() {
        val loadedAlbumTracks = state.albumDetail?.tracks.orEmpty()
        val album = state.albumDetail?.album ?: return
        startAlbumRadio(album, loadedAlbumTracks)
    }

    fun handleRadioStationSelected(station: InternetRadioStation) {
        playInternetRadioStation(station)
    }

    fun saveInternetRadioStation(station: InternetRadioStation) {
        saveAndroidInternetRadioStation(scope, state, internetRadioStationManager, station)
    }

    fun refreshInternetRadioStations() {
        refreshAndroidInternetRadioStations(scope, state, internetRadioStationManager)
    }

    fun deleteInternetRadioStation(station: InternetRadioStation) {
        deleteAndroidInternetRadioStation(scope, state, internetRadioStationManager, station)
    }

    fun handleShellGoToArtist() {
        val currentTrack = state.nowPlaying ?: return
        currentTrack.artistId?.let { openArtistDetails(it, currentTrack.artistName) }
    }

    fun handleTrackGoToArtist(track: Track) {
        track.artistId?.let { openArtistDetails(it, track.artistName) }
    }
}
