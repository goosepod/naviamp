package app.naviamp.android

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.naviamp.android.playback.AndroidAudioWaveformAnalyzer
import app.naviamp.android.playback.AndroidMedia3PlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.app.NaviampContentState
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeService
import app.naviamp.domain.home.HomeStationLibrary
import app.naviamp.domain.home.HomeStationRandomAlbum
import app.naviamp.domain.home.parseHomeDecadeStationId
import app.naviamp.domain.home.parseHomeGenreStationId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.label
import app.naviamp.domain.lyrics.selectPreferredLyrics
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.ui.AndroidTrackRowUi
import app.naviamp.ui.NaviampDetailSectionUi
import app.naviamp.ui.NaviampLyricLineUi
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampRepeatMode
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.compactLabel
import app.naviamp.ui.durationLabel
import app.naviamp.ui.ratingLabel
import app.naviamp.ui.sixDecimalLabel
import app.naviamp.ui.toAndroidTrackRowUi
import app.naviamp.ui.toNowPlayingItemUi
import app.naviamp.ui.toNowPlayingStationUi
import app.naviamp.ui.toPlaylistChoiceUi
import app.naviamp.ui.toSharedAlbumDetailUi
import app.naviamp.ui.toSharedArtistDetailUi
import app.naviamp.ui.toSharedHomeUi
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedPlaylistDetailUi
import app.naviamp.ui.toSharedSearchResultsUi
import app.naviamp.ui.twoDecimalLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            NaviampAndroidApp()
        }
    }
}

@Composable
private fun NaviampAndroidApp() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playbackEngine = remember { AndroidMedia3PlaybackEngine(context) }
    val waveformAnalyzer = remember { AndroidAudioWaveformAnalyzer(context) }
    val lrclibLyricsClient = remember { AndroidLrclibLyricsClient() }
    val storage = remember { AndroidStorage(context) }
    val settingsStore = remember { AndroidSettingsStore(context) }
    val savedProviderSource = remember { storage.latestNavidromeSource() }
    val savedProviderConnection = savedProviderSource?.connection
    val savedConnection = remember { settingsStore.loadConnection(savedProviderConnection) }
    val savedPlaybackSettings = remember { settingsStore.loadPlaybackSettings() }
    var serverUrl by remember { mutableStateOf(savedConnection.serverUrl) }
    var username by remember { mutableStateOf(savedConnection.username) }
    var password by remember { mutableStateOf(savedConnection.password) }
    var skipTlsVerification by remember { mutableStateOf(savedConnection.skipTlsVerification) }
    var customCertificatePath by remember { mutableStateOf(savedConnection.customCertificatePath) }
    var clientCertificatePath by remember { mutableStateOf(savedConnection.clientCertificatePath) }
    var clientCertificatePassword by remember { mutableStateOf(savedConnection.clientCertificatePassword) }
    var playbackSettings by remember { mutableStateOf(savedPlaybackSettings) }
    var homeState by remember { mutableStateOf(HomeContent()) }
    var contentState by remember { mutableStateOf(NaviampContentState()) }
    val query = contentState.searchQuery
    val searchResults = contentState.searchResults
    val albumDetail = contentState.albumDetail
    val artistDetail = contentState.artistDetail
    val selectedPlaylist = contentState.selectedPlaylist
    val selectedPlaylistTracks = contentState.selectedPlaylistTracks
    var playlistSortMode by remember { mutableStateOf(SharedPlaylistSortMode.Alphabetical) }
    var recentPlaylistIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlistTracksById by remember { mutableStateOf<Map<String, List<Track>>>(emptyMap()) }
    var editingConnection by remember { mutableStateOf(false) }
    var navigationState by remember { mutableStateOf(NaviampNavigationState()) }
    val selectedRoute = navigationState.route.toSharedRoute()
    var provider by remember { mutableStateOf<NavidromeProvider?>(null) }
    var activeSourceId by remember { mutableStateOf<String?>(savedProviderSource?.id) }
    var restoredStartPositionSeconds by remember { mutableStateOf<Double?>(null) }
    var activeTlsSettings by remember { mutableStateOf(NavidromeTlsSettings()) }
    var validation by remember { mutableStateOf<ConnectionValidation?>(null) }
    var status by remember { mutableStateOf("Connect to Navidrome to start Android playback.") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var nowPlaying by remember { mutableStateOf<Track?>(null) }
    var nowPlayingStation by remember { mutableStateOf<InternetRadioStation?>(null) }
    var nowPlayingStreamMetadata by remember { mutableStateOf(PlaybackStreamMetadata()) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    var playbackProgress by remember { mutableStateOf(PlaybackProgress.Unknown) }
    var pendingSeekPositionSeconds by remember { mutableStateOf<Double?>(null) }
    var pendingSeekIssuedAtMillis by remember { mutableStateOf<Long?>(null) }
    var playbackSessionToken by remember { mutableStateOf(0L) }
    var volumePercent by remember { mutableStateOf(100) }
    var waveformByTrackId by remember { mutableStateOf<Map<String, AudioWaveform>>(emptyMap()) }
    var playbackQueue by remember { mutableStateOf(PlaybackQueue()) }
    var shuffledUpNextSnapshot by remember { mutableStateOf<List<Track>?>(null) }
    var repeatMode by remember { mutableStateOf(RepeatMode.Off) }
    var relatedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var lyricsVisible by remember { mutableStateOf(false) }
    var lyricsByTrackId by remember { mutableStateOf<Map<String, Lyrics?>>(emptyMap()) }
    var lyricsStatusByTrackId by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var playlistActionStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(playbackEngine) {
        onDispose {
            AndroidPlaybackNotificationControls.clear()
            playbackEngine.release()
        }
    }

    fun activeQueue(): List<Track> =
        playbackQueue.tracks.ifEmpty { allKnownTracks(searchResults, albumDetail) }

    fun findKnownTrack(trackId: String): Track? =
        (activeQueue() + selectedPlaylistTracks + relatedTracks + allKnownTracks(searchResults, albumDetail))
            .firstOrNull { it.id.value == trackId }

    fun loadLyrics(track: Track) {
        val activeProvider = provider ?: return
        if (lyricsByTrackId.containsKey(track.id.value) || lyricsStatusByTrackId[track.id.value] != null) return
        lyricsStatusByTrackId = lyricsStatusByTrackId + (track.id.value to "Grabbing lyrics")
        scope.launch {
            runCatching {
                val localLyrics = activeProvider.lyrics(track.id)
                val onlineLyrics = if (playbackSettings.lrclibLyricsEnabled && (localLyrics == null || !localLyrics.synced)) {
                    lrclibLyricsClient.lyrics(track)
                } else {
                    null
                }
                selectPreferredLyrics(
                    providerLyrics = localLyrics,
                    embeddedLyrics = null,
                    onlineLyrics = onlineLyrics,
                )
            }
                .onSuccess { lyrics ->
                    lyricsByTrackId = lyricsByTrackId + (track.id.value to lyrics)
                    lyricsStatusByTrackId = lyricsStatusByTrackId + (track.id.value to null)
                }
                .onFailure { error ->
                    lyricsByTrackId = lyricsByTrackId + (track.id.value to null)
                    lyricsStatusByTrackId = lyricsStatusByTrackId + (track.id.value to (error.message ?: "Lyrics unavailable"))
                }
        }
    }

    fun loadRelatedTracks(track: Track) {
        val activeProvider = provider ?: return
        if (!activeProvider.capabilities.supportsTrackRadio) {
            relatedTracks = emptyList()
            return
        }
        scope.launch {
            runCatching { RadioService(activeProvider, count = 20).trackRadio(track.id) }
                .onSuccess { relatedTracks = it }
                .onFailure { relatedTracks = emptyList() }
        }
    }

    fun beginPlaybackSession(): Long {
        playbackSessionToken += 1
        pendingSeekPositionSeconds = null
        pendingSeekIssuedAtMillis = null
        playbackProgress = PlaybackProgress.Unknown
        return playbackSessionToken
    }

    fun handlePlaybackProgressChanged(sessionToken: Long, progress: PlaybackProgress) {
        if (sessionToken != playbackSessionToken) return
        if (progress.positionSeconds == null && progress.durationSeconds == null) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
            playbackProgress = PlaybackProgress.Unknown
            return
        }
        val pendingSeek = pendingSeekPositionSeconds
        val pendingSeekIssuedAt = pendingSeekIssuedAtMillis
        val progressPosition = progress.positionSeconds
        if (
            pendingSeek != null &&
            pendingSeekIssuedAt != null &&
            progressPosition != null &&
            abs(progressPosition - pendingSeek) > PendingSeekToleranceSeconds &&
            System.currentTimeMillis() - pendingSeekIssuedAt < PendingSeekStaleProgressWindowMillis
        ) {
            return
        }
        if (
            pendingSeek != null &&
            (progressPosition == null ||
                pendingSeekIssuedAt == null ||
                abs(progressPosition - pendingSeek) <= PendingSeekToleranceSeconds ||
                System.currentTimeMillis() - pendingSeekIssuedAt >= PendingSeekStaleProgressWindowMillis)
        ) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
        }
        playbackProgress = progress.mergeForAndroidPlayback(playbackProgress)
    }

    fun playTrack(
        track: Track,
        queue: List<Track>? = null,
        openNowPlaying: Boolean = true,
        startPositionSeconds: Double? = null,
    ) {
        val activeProvider = provider
        if (activeProvider == null) {
            status = "Connect before playing a track."
            return
        }
        val nextQueue = queue
            ?.takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: activeQueue().takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: listOf(track)
        scope.launch {
            status = "Loading ${track.title}..."
            runCatching {
                activeProvider.streamUrl(StreamRequest(track.id, StreamQuality.Original))
            }.onSuccess { streamUrl ->
                playbackEngine.applyTlsSettings(activeTlsSettings)
                playbackQueue = PlaybackQueue(
                    tracks = nextQueue,
                    currentIndex = nextQueue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0,
                )
                shuffledUpNextSnapshot = null
                nowPlaying = track
                nowPlayingStation = null
                nowPlayingStreamMetadata = PlaybackStreamMetadata()
                if (openNowPlaying) {
                    nowPlayingOpen = true
                }
                val sessionToken = beginPlaybackSession()
                loadRelatedTracks(track)
                if (lyricsVisible) loadLyrics(track)
                playbackEngine.updateNotificationMetadata(
                    title = track.title,
                    subtitle = track.artistName,
                    coverArtUrl = track.coverArtUrl(activeProvider),
                )
                playbackEngine.play(
                    scope = scope,
                    request = PlaybackRequest(streamUrl, startPositionSeconds = startPositionSeconds),
                    onStateChanged = { state ->
                        playbackState = state
                        if (state is PlaybackState.Error) {
                            status = state.message
                        }
                    },
                    onProgressChanged = { progress -> handlePlaybackProgressChanged(sessionToken, progress) },
                )
                status = "Loading ${track.title}..."
                scope.launch {
                    waveformAnalyzer.analyze(track.id.value, streamUrl)?.let { amplitudes ->
                        waveformByTrackId = waveformByTrackId + (track.id.value to amplitudes)
                    }
                }
            }.onFailure { error ->
                status = error.message ?: "Playback failed."
            }
        }
    }

    fun playInternetRadioStation(station: InternetRadioStation) {
        val sessionToken = beginPlaybackSession()
        nowPlaying = null
        nowPlayingStation = station
        nowPlayingStreamMetadata = PlaybackStreamMetadata()
        nowPlayingOpen = true
        status = "Loading ${station.name}..."
        playbackEngine.applyTlsSettings(activeTlsSettings)
        playbackEngine.updateNotificationMetadata(
            title = station.name,
            subtitle = "Internet radio",
            coverArtUrl = null,
        )
        scope.launch {
            runCatching {
                resolveInternetRadioStreamUrl(station.streamUrl.trim())
            }.onSuccess { streamUrl ->
                playbackEngine.play(
                    scope = scope,
                    request = PlaybackRequest(streamUrl),
                    onStateChanged = { state ->
                        playbackState = state
                        if (state is PlaybackState.Error) {
                            status = state.message
                        }
                    },
                    onProgressChanged = { progress -> handlePlaybackProgressChanged(sessionToken, progress) },
                    onMetadataChanged = { metadata ->
                        nowPlayingStreamMetadata = metadata
                        metadata.title?.takeIf { it.isNotBlank() }?.let { streamTitle ->
                            playbackEngine.updateNotificationMetadata(
                                title = streamTitle,
                                subtitle = station.name,
                                coverArtUrl = null,
                            )
                        }
                    },
                )
            }.onFailure { error ->
                status = error.message ?: "Radio stream failed."
            }
        }
    }

    fun performSeek(positionSeconds: Double) {
        restoredStartPositionSeconds = null
        val durationSeconds = playbackProgress.durationSeconds ?: nowPlaying?.durationSeconds?.toDouble()
        playbackProgress = PlaybackProgress(
            positionSeconds = positionSeconds.coerceAtLeast(0.0),
            durationSeconds = durationSeconds,
        )
        pendingSeekPositionSeconds = positionSeconds
        pendingSeekIssuedAtMillis = System.currentTimeMillis()
        playbackEngine.seek(positionSeconds)
    }

    fun playAdjacentTrack(offset: Int) {
        val currentTrack = nowPlaying ?: return
        val knownTracks = activeQueue()
        val currentIndex = knownTracks.indexOfFirst { it.id == currentTrack.id }
        if (currentIndex < 0) return
        val nextIndex = when {
            repeatMode == RepeatMode.Track -> currentIndex
            offset > 0 && currentIndex == knownTracks.lastIndex && repeatMode == RepeatMode.Queue -> 0
            offset < 0 && currentIndex == 0 && repeatMode == RepeatMode.Queue -> knownTracks.lastIndex
            else -> currentIndex + offset
        }
        val nextTrack = knownTracks.getOrNull(nextIndex) ?: return
        playTrack(nextTrack, knownTracks, openNowPlaying = false)
    }

    fun radioService(): RadioService? =
        provider?.let(::RadioService)

    fun playRadioTracks(statusLabel: String, loadTracks: suspend (RadioService) -> List<Track>) {
        val activeProvider = provider ?: return
        val service = RadioService(activeProvider)
        scope.launch {
            status = "Starting $statusLabel..."
            runCatching { loadTracks(service) }
                .onSuccess { radioTracks ->
                    val queue = radioTracks.distinctBy { it.id }
                    val firstTrack = queue.firstOrNull()
                    if (firstTrack == null) {
                        status = "No tracks found for $statusLabel."
                    } else {
                        playTrack(firstTrack, queue)
                    }
                }
                .onFailure { error -> status = error.message ?: "Could not start $statusLabel." }
        }
    }

    fun startSeededRadio(
        statusLabel: String,
        seedTrack: Track,
        loadRest: suspend (RadioService) -> List<Track>,
    ) {
        val activeProvider = provider ?: return
        val service = RadioService(activeProvider)
        val seedQueue = listOf(seedTrack)
        playTrack(seedTrack, seedQueue)
        scope.launch {
            runCatching { loadRest(service) }
                .onSuccess { fetchedTracks ->
                    val queue = service.queue(seedTrack, fetchedTracks)
                        .ifEmpty { seedQueue }
                    if (nowPlaying?.id == seedTrack.id) {
                        playbackQueue = PlaybackQueue(tracks = queue, currentIndex = 0)
                    }
                    status = "Playing $statusLabel."
                }
                .onFailure { error ->
                    if (nowPlaying?.id == seedTrack.id) {
                        status = error.message ?: "Could not build $statusLabel."
                    }
                }
        }
    }

    fun startAlbumRadio(album: Album, loadedAlbumTracks: List<Track> = emptyList()) {
        val service = radioService() ?: return
        scope.launch {
            status = "Starting ${album.title} radio..."
            val seedTrack = service.albumSeed(album, loadedAlbumTracks)
            if (seedTrack == null) {
                status = "${album.title} did not return any tracks."
            } else {
                startSeededRadio("${album.title} radio", seedTrack) { radioService ->
                    radioService.albumRadio(album.id, loadedAlbumTracks)
                }
            }
        }
    }

    AndroidPlaybackNotificationControls.onPlayPause = {
        when (playbackState) {
            PlaybackState.Playing -> playbackEngine.pause()
            else -> playbackEngine.resume()
        }
    }
    AndroidPlaybackNotificationControls.onPrevious = { playAdjacentTrack(-1) }
    AndroidPlaybackNotificationControls.onNext = { playAdjacentTrack(1) }
    AndroidPlaybackNotificationControls.onStop = { playbackEngine.stop() }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        nowPlaying = nowPlaying?.let { if (it.id == updatedTrack.id) updatedTrack else it }
        tracks = tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        contentState = contentState.copy(
            searchResults = searchResults.copy(
                tracks = searchResults.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it },
            ),
            albumDetail = albumDetail?.copy(
                tracks = albumDetail.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it },
            ),
        )
    }

    fun openArtistDetails(artistId: app.naviamp.domain.ArtistId, fallbackName: String? = null) {
        val activeProvider = provider ?: return
        scope.launch {
            status = "Loading ${fallbackName ?: "artist"}..."
            runCatching { activeProvider.artist(artistId) }
                .onSuccess { detail ->
                    contentState = contentState.showArtist(detail)
                    nowPlayingOpen = false
                    navigationState = navigationState.copy(route = NaviampRoute.Library)
                    status = "Connected."
                }
                .onFailure { error -> status = error.message ?: "Artist failed to load." }
        }
    }

    fun openPlaylistDetails(playlist: Playlist) {
        val activeProvider = provider ?: return
        contentState = contentState.showPlaylist(playlist)
        navigationState = navigationState.copy(route = NaviampRoute.Playlists)
        nowPlayingOpen = false
        recentPlaylistIds = (listOf(playlist.id) + recentPlaylistIds.filterNot { it == playlist.id }).take(20)
        scope.launch {
            status = "Loading ${playlist.name}..."
            runCatching { playlistTracksById[playlist.id] ?: activeProvider.playlistTracks(playlist.id) }
                .onSuccess { playlistTracks ->
                    playlistTracksById = playlistTracksById + (playlist.id to playlistTracks)
                    contentState = contentState.showPlaylist(playlist, playlistTracks)
                    tracks = playlistTracks
                    status = "Connected."
                }
                .onFailure { error ->
                    contentState = contentState.showPlaylist(playlist)
                    status = error.message ?: "Playlist failed to load."
                }
        }
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean) {
        val activeProvider = provider ?: return
        scope.launch {
            status = "Loading ${playlist.name}..."
            val playlistTracks = if (selectedPlaylist?.id == playlist.id && selectedPlaylistTracks.isNotEmpty()) {
                selectedPlaylistTracks
            } else {
                runCatching { playlistTracksById[playlist.id] ?: activeProvider.playlistTracks(playlist.id) }
                    .onSuccess {
                        playlistTracksById = playlistTracksById + (playlist.id to it)
                        contentState = contentState.showPlaylist(playlist, it)
                        tracks = it
                    }
                    .getOrDefault(emptyList())
            }
            val queue = if (shuffle) playlistTracks.shuffled() else playlistTracks
            queue.firstOrNull()?.let { firstTrack ->
                recentPlaylistIds = (listOf(playlist.id) + recentPlaylistIds.filterNot { it == playlist.id }).take(20)
                playTrack(firstTrack, queue)
            } ?: run {
                status = "Playlist is empty."
            }
        }
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        val activeProvider = provider ?: return
        scope.launch {
            status = "Renaming ${playlist.name}..."
            runCatching {
                activeProvider.renamePlaylist(playlist.id, name.trim())
                activeProvider.playlists(limit = 500)
            }.onSuccess { playlists ->
                homeState = homeState.copy(playlists = playlists)
                selectedPlaylist?.let { current ->
                    if (current.id == playlist.id) {
                        val renamed = playlists.firstOrNull { it.id == playlist.id } ?: current.copy(name = name.trim())
                        contentState = contentState.copy(selectedPlaylist = renamed)
                    }
                }
                status = "Renamed playlist."
            }.onFailure { error ->
                status = error.message ?: "Could not rename playlist."
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        val activeProvider = provider ?: return
        scope.launch {
            status = "Deleting ${playlist.name}..."
            runCatching {
                activeProvider.deletePlaylist(playlist.id)
                activeProvider.playlists(limit = 500)
            }.onSuccess { playlists ->
                homeState = homeState.copy(playlists = playlists)
                if (selectedPlaylist?.id == playlist.id) {
                    contentState = contentState.copy(
                        selectedPlaylist = null,
                        selectedPlaylistTracks = emptyList(),
                    )
                }
                playlistTracksById = playlistTracksById - playlist.id
                recentPlaylistIds = recentPlaylistIds.filterNot { it == playlist.id }
                status = "Deleted playlist."
            }.onFailure { error ->
                status = error.message ?: "Could not delete playlist."
            }
        }
    }

    fun preloadPlaylistTracks(activeProvider: NavidromeProvider, playlists: List<Playlist>) {
        scope.launch {
            playlists.take(100).forEach { playlist ->
                if (playlistTracksById[playlist.id].isNullOrEmpty()) {
                    runCatching { activeProvider.playlistTracks(playlist.id) }
                        .onSuccess { tracks ->
                            playlistTracksById = playlistTracksById + (playlist.id to tracks)
                        }
                }
            }
        }
    }

    fun refreshAndroidPlaylists() {
        val activeProvider = provider ?: return
        scope.launch {
            runCatching { activeProvider.playlists(limit = 500) }
                .onSuccess { playlists ->
                    homeState = homeState.copy(playlists = playlists)
                    preloadPlaylistTracks(activeProvider, playlists)
                }
        }
    }

    fun addTrackToPlaylist(track: Track, playlist: NaviampPlaylistChoiceUi?, newPlaylistName: String? = null) {
        val activeProvider = provider ?: return
        playlistActionStatus = "Adding to playlist..."
        scope.launch {
            runCatching {
                if (playlist == null) {
                    activeProvider.createPlaylist(newPlaylistName.orEmpty(), listOf(track.id))
                } else {
                    val existingIds = activeProvider.playlistTracks(playlist.id).map { it.id }.toSet()
                    if (track.id !in existingIds) {
                        activeProvider.addTracksToPlaylist(playlist.id, listOf(track.id))
                    }
                }
                activeProvider.playlists(limit = 500)
            }.onSuccess { playlists ->
                homeState = homeState.copy(playlists = playlists)
                playlistActionStatus = null
                status = "Added ${track.title} to playlist."
            }.onFailure { error ->
                playlistActionStatus = error.message ?: "Could not add track to playlist."
                status = playlistActionStatus.orEmpty()
            }
        }
    }

    fun downloadTrack(track: Track) {
        val activeProvider = provider ?: return
        scope.launch {
            status = "Preparing ${track.title} download..."
            runCatching {
                activeProvider.streamUrl(StreamRequest(track.id, StreamQuality.Original))
            }.onSuccess { streamUrl ->
                val extension = track.audioInfo?.codec?.lowercase()?.let { codec ->
                    when (codec) {
                        "mpeg", "mp3" -> "mp3"
                        "flac" -> "flac"
                        "opus" -> "opus"
                        "aac" -> "aac"
                        else -> codec
                    }
                } ?: "audio"
                val fileName = "${track.artistName} - ${track.title}"
                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
                    .take(120)
                    .ifBlank { track.id.value } + ".$extension"
                val request = DownloadManager.Request(Uri.parse(streamUrl))
                    .setTitle(track.title)
                    .setDescription(track.artistName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                status = "Downloading ${track.title}."
            }.onFailure { error ->
                status = error.message ?: "Could not start download."
            }
        }
    }

    fun restorePlaybackSession(sourceId: String): Boolean {
        val session = storage.loadPlaybackSession(sourceId) ?: return false
        session.internetRadioStation?.toStation()?.let { station ->
            nowPlaying = null
            nowPlayingStation = station
            nowPlayingStreamMetadata = PlaybackStreamMetadata()
            playbackQueue = PlaybackQueue()
            playbackProgress = PlaybackProgress.Unknown
            restoredStartPositionSeconds = null
            nowPlayingOpen = true
            status = "Restored ${station.name}. Press play to resume."
            return true
        }

        val sessionTracks = session.toTracks()
        val currentTrack = session.currentTrack() ?: return false
        playbackQueue = PlaybackQueue(
            tracks = sessionTracks,
            currentIndex = session.currentIndex.coerceIn(sessionTracks.indices),
        )
        tracks = sessionTracks
        nowPlaying = currentTrack
        nowPlayingStation = null
        nowPlayingStreamMetadata = PlaybackStreamMetadata()
        nowPlayingOpen = true
        playbackProgress = PlaybackProgress(
            positionSeconds = session.positionSeconds,
            durationSeconds = currentTrack.durationSeconds?.toDouble(),
        )
        restoredStartPositionSeconds = session.positionSeconds
        loadRelatedTracks(currentTrack)
        status = "Restored ${currentTrack.title}. Press play to resume."
        return true
    }

    fun savePlaybackSession() {
        val sourceId = activeSourceId ?: return
        val station = nowPlayingStation
        if (station != null) {
            storage.savePlaybackSession(
                sourceId = sourceId,
                session = PlaybackSessionSettings.fromInternetRadioStation(station),
            )
            return
        }

        val currentTrack = nowPlaying ?: return
        val queue = playbackQueue.takeIf { it.current?.id == currentTrack.id }
            ?: PlaybackQueue(tracks = listOf(currentTrack), currentIndex = 0)
        storage.savePlaybackSession(
            sourceId = sourceId,
            session = PlaybackSessionSettings.fromTracks(
                tracks = queue.tracks,
                currentIndex = queue.currentIndex,
                positionSeconds = playbackProgress.positionSeconds,
            ),
        )
    }

    fun currentConnectionForm(): ConnectionFormState =
        ConnectionFormState(
            serverUrl = serverUrl,
            username = username,
            password = password,
            skipTlsVerification = skipTlsVerification,
            customCertificatePath = customCertificatePath,
            clientCertificatePath = clientCertificatePath,
            clientCertificatePassword = clientCertificatePassword,
        )

    fun connectWithNavidromeConnection(connection: NavidromeConnection) {
        scope.launch {
            status = "Connecting..."
            runCatching {
                val tlsSettings = connection.tlsSettings
                val nextProvider = NavidromeProvider(connection)
                playbackEngine.applyTlsSettings(tlsSettings)
                validation = nextProvider.validateConnection()
                homeState = loadBrowseState(nextProvider)
                preloadPlaylistTracks(nextProvider, homeState.playlists)
                val mediaSource = storage.upsertNavidromeSource(
                    connection = connection,
                    cacheNamespace = nextProvider.cacheNamespace,
                    providerId = nextProvider.id.value,
                )
                provider = nextProvider
                activeSourceId = mediaSource.id
                activeTlsSettings = tlsSettings
                restorePlaybackSession(mediaSource.id)
            }.onSuccess {
                if (nowPlaying == null && nowPlayingStation == null) {
                    status = "Connected."
                }
                editingConnection = false
                navigationState = navigationState.copy(route = NaviampRoute.Home)
            }.onFailure { error ->
                status = error.message ?: "Connection failed."
                provider = null
                validation = null
            }
        }
    }

    fun connectToNavidrome() {
        val connectionForm = currentConnectionForm()
        val tlsSettings = NavidromeTlsSettings(
            insecureSkipTlsVerification = connectionForm.skipTlsVerification,
            customCertificatePath = connectionForm.customCertificatePath.trim().takeIf { it.isNotEmpty() },
            clientCertificateKeyStorePath = connectionForm.clientCertificatePath.trim().takeIf { it.isNotEmpty() },
            clientCertificateKeyStorePassword = connectionForm.clientCertificatePassword
                .takeIf { connectionForm.clientCertificatePath.trim().isNotEmpty() },
        )
        scope.launch {
            runCatching {
                NavidromeConnection.fromPassword(
                    baseUrl = connectionForm.serverUrl,
                    username = connectionForm.username,
                    password = connectionForm.password,
                    tlsSettings = tlsSettings,
                )
            }.onSuccess { connection ->
                settingsStore.saveConnection(connectionForm)
                connectWithNavidromeConnection(connection)
            }.onFailure { error ->
                status = error.message ?: "Connection failed."
                provider = null
                validation = null
            }
        }
    }

    LaunchedEffect(Unit) {
        when {
            savedProviderConnection != null -> connectWithNavidromeConnection(savedProviderConnection)
            savedConnection.serverUrl.isNotBlank() &&
                savedConnection.username.isNotBlank() &&
                savedConnection.password.isNotBlank() -> {
                connectToNavidrome()
            }
        }
    }

    LaunchedEffect(
        activeSourceId,
        playbackQueue,
        nowPlaying?.id,
        nowPlayingStation?.id,
        playbackProgress.positionSeconds,
    ) {
        if (activeSourceId == null || (nowPlaying == null && nowPlayingStation == null)) return@LaunchedEffect
        delay(1_000)
        savePlaybackSession()
    }

    NaviampSharedAppShell(
        status = status,
        serverVersion = validation?.serverVersion,
        connected = provider != null,
        editingConnection = editingConnection,
        connectionForm = ConnectionFormState(
            serverUrl = serverUrl,
            username = username,
            password = password,
            skipTlsVerification = skipTlsVerification,
            customCertificatePath = customCertificatePath,
            clientCertificatePath = clientCertificatePath,
            clientCertificatePassword = clientCertificatePassword,
        ),
        playbackSettings = playbackSettings,
        query = query,
        home = homeState.toSharedHomeUi(
            coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
            playlistTracksById = playlistTracksById,
        ),
        searchResults = searchResults.toSharedSearchResultsUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
        libraryArtists = homeState.artists.map { it.toSharedMediaItemUi() },
        playlistItems = homeState.playlists.map {
            it.toSharedMediaItemUi(
                coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                tracks = playlistTracksById[it.id].orEmpty(),
            )
        },
        recentPlaylistIds = recentPlaylistIds,
        playlistSortMode = playlistSortMode,
        radioStationItems = homeState.radioStations.map { it.toSharedMediaItemUi() },
        albumDetail = albumDetail?.toSharedAlbumDetailUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
        artistDetail = artistDetail?.toSharedArtistDetailUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
        playlistDetail = selectedPlaylist?.toSharedPlaylistDetailUi(
            tracks = selectedPlaylistTracks,
            coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
        ),
        nowPlaying = nowPlaying?.let { track ->
            val knownTracks = activeQueue()
            val currentIndex = knownTracks.indexOfFirst { it.id == track.id }
            val lyrics = lyricsByTrackId[track.id.value]
            val lyricsStatus = lyricsStatusByTrackId[track.id.value]
            NowPlayingUi(
                id = track.id.value,
                title = track.title,
                subtitle = track.artistName,
                stateLabel = "${playbackState.label()} ${playbackProgress.positionSeconds?.toInt() ?: 0}s",
                coverArtUrl = track.coverArtUrl(provider),
                albumLine = listOfNotNull(
                    track.albumTitle,
                    track.albumReleaseYear?.toString(),
                ).joinToString(" - "),
                audioInfo = track.audioInfo?.compactLabel().orEmpty(),
                waveform = waveformByTrackId[track.id.value],
                positionSeconds = playbackProgress.positionSeconds,
                durationSeconds = track.durationSeconds?.toDouble() ?: playbackProgress.durationSeconds,
                volumePercent = volumePercent,
                isPlaying = playbackState == PlaybackState.Playing,
                isPaused = playbackState == PlaybackState.Paused,
                canSeek = true,
                canChangeVolume = false,
                hasPrevious = currentIndex > 0 || (repeatMode == RepeatMode.Queue && knownTracks.size > 1),
                hasNext = (currentIndex >= 0 && currentIndex < knownTracks.lastIndex) ||
                    (repeatMode == RepeatMode.Queue && knownTracks.size > 1),
                shuffleEnabled = knownTracks.drop(currentIndex + 1).size > 1,
                shuffleActive = shuffledUpNextSnapshot != null,
                repeatMode = repeatMode.toNaviampRepeatMode(),
                canRepeat = knownTracks.isNotEmpty(),
                canStartRadio = provider?.capabilities?.supportsTrackRadio == true,
                canAddToPlaylist = true,
                favoriteActive = track.favoritedAtIso8601 != null,
                canFavorite = provider?.capabilities?.supportsTrackFavorites == true,
                userRating = track.userRating,
                canRate = provider?.capabilities?.supportsTrackRatings == true,
                lyricsAvailable = true,
                lyricsVisible = lyricsVisible,
                lyricsStatus = lyricsStatus,
                lyricsOffsetMillis = lyrics?.offsetMillis ?: 0,
                lyricsLines = lyrics?.lines.orEmpty().map { line ->
                    NaviampLyricLineUi(startMillis = line.startMillis, text = line.text)
                },
                menuEnabled = true,
                detailSections = track.toDetailSections(),
                playlistChoices = homeState.playlists.map { it.toPlaylistChoiceUi() },
                playlistActionStatus = playlistActionStatus,
                backTo = knownTracks
                    .take(currentIndex.coerceAtLeast(0))
                    .asReversed()
                    .map { it.toNowPlayingItemUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } } },
                upNext = if (currentIndex >= 0) {
                    knownTracks.drop(currentIndex + 1).map {
                        it.toNowPlayingItemUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } }
                    }
                } else {
                    emptyList()
                },
                related = relatedTracks.map {
                    it.toNowPlayingItemUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } }
                },
            )
        } ?: nowPlayingStation?.let { station ->
            NowPlayingUi(
                id = station.id,
                title = nowPlayingStreamMetadata.title?.takeIf { it.isNotBlank() } ?: station.name,
                subtitle = if (nowPlayingStreamMetadata.title.isNullOrBlank()) {
                    "Internet radio"
                } else {
                    station.name
                },
                stateLabel = playbackState.label(),
                isLive = true,
                volumePercent = volumePercent,
                isPlaying = playbackState == PlaybackState.Playing,
                isPaused = playbackState == PlaybackState.Paused,
                canSeek = false,
                canChangeVolume = false,
                hasPrevious = false,
                hasNext = false,
                canRepeat = false,
                canStartRadio = false,
                canAddToPlaylist = false,
                menuEnabled = false,
                radioStations = homeState.radioStations.map { it.toNowPlayingStationUi() },
            )
        },
        nowPlayingOpen = nowPlayingOpen,
        selectedRoute = selectedRoute,
        onRouteSelected = { route -> navigationState = navigationState.copy(route = route.toNaviampRoute()) },
        onConnectionFormChanged = { form ->
            serverUrl = form.serverUrl
            username = form.username
            password = form.password
            skipTlsVerification = form.skipTlsVerification
            customCertificatePath = form.customCertificatePath
            clientCertificatePath = form.clientCertificatePath
            clientCertificatePassword = form.clientCertificatePassword
        },
        onConnect = { connectToNavidrome() },
        onEditConnection = { editingConnection = true },
        onCancelEditConnection = { editingConnection = false },
        onPlaybackSettingsChanged = { settings ->
            playbackSettings = settings
            settingsStore.savePlaybackSettings(settings)
            lyricsByTrackId = emptyMap()
            lyricsStatusByTrackId = emptyMap()
            if (lyricsVisible) nowPlaying?.let(::loadLyrics)
        },
        onQueryChanged = { contentState = contentState.copy(searchQuery = it) },
        onSearch = {
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Searching..."
                runCatching {
                    activeProvider.search(query, limit = 20)
                }.onSuccess { results ->
                    contentState = contentState.clearDetails().copy(searchResults = results)
                    tracks = results.tracks
                    status = if (results.isEmpty) "No matches found." else "Found ${results.totalCount()} matches."
                }.onFailure { error ->
                    status = error.message ?: "Search failed."
                }
            }
        },
        onTrackSelected = { selectedTrack ->
            val currentTracks = activeQueue().takeIf { queue -> queue.any { it.id.value == selectedTrack.id } }
                ?: relatedTracks.takeIf { queue -> queue.any { it.id.value == selectedTrack.id } }
                ?: allKnownTracks(searchResults, albumDetail)
            val track = currentTracks.firstOrNull { it.id.value == selectedTrack.id } ?: findKnownTrack(selectedTrack.id)
            if (track == null) {
                status = "Track not found."
                return@NaviampSharedAppShell
            }
            playTrack(track, currentTracks)
        },
        onAlbumSelected = { selectedAlbum ->
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading ${selectedAlbum.title}..."
                runCatching {
                    activeProvider.album(app.naviamp.domain.AlbumId(selectedAlbum.id))
                }.onSuccess { detail ->
                    contentState = contentState.showAlbum(detail)
                    tracks = detail.tracks
                    nowPlayingOpen = false
                    status = "Connected."
                }.onFailure { error ->
                    status = error.message ?: "Album failed to load."
                }
            }
        },
        onMixAlbumSelected = { selectedAlbum ->
            homeState.mixAlbums.firstOrNull { it.id.value == selectedAlbum.id }
                ?.let { startAlbumRadio(it) }
                ?: run { status = "Album not found." }
        },
        onAlbumPlay = { _, shuffle ->
            val albumTracks = albumDetail?.tracks.orEmpty()
            val queue = if (shuffle) albumTracks.shuffled() else albumTracks
            queue.firstOrNull()?.let { playTrack(it, queue) }
                ?: run { status = "Album is empty." }
        },
        onAlbumRadio = { detail ->
            val loadedAlbumTracks = albumDetail?.tracks.orEmpty()
            val album = albumDetail?.album ?: return@NaviampSharedAppShell
            startAlbumRadio(album, loadedAlbumTracks)
        },
        onArtistRadio = { detail ->
            val service = radioService() ?: return@NaviampSharedAppShell
            val artistId = app.naviamp.domain.ArtistId(detail.artist.id)
            val artist = artistDetail?.artist ?: Artist(artistId, detail.artist.title)
            scope.launch {
                status = "Starting ${detail.artist.title} radio..."
                runCatching { service.artistSeed(artist, artistDetail?.albums.orEmpty()) }
                    .onSuccess { seedTrack ->
                    if (seedTrack == null) {
                        status = "${detail.artist.title} radio did not find a seed track."
                    } else {
                        startSeededRadio("${detail.artist.title} radio", seedTrack) { radioService ->
                            radioService.artistRadio(artistId)
                        }
                    }
                }.onFailure { error ->
                    status = error.message ?: "Could not start artist radio."
                }
            }
        },
        onArtistSelected = { selectedArtist ->
            openArtistDetails(app.naviamp.domain.ArtistId(selectedArtist.id), selectedArtist.title)
        },
        onPlaylistSelected = { selectedPlaylist ->
            homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { openPlaylistDetails(it) }
                ?: run { status = "Playlist not found." }
        },
        onPlaylistSortModeChanged = { playlistSortMode = it },
        onPlaylistPlay = { selectedPlaylist, shuffle ->
            homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { playPlaylist(it, shuffle) }
                ?: run { status = "Playlist not found." }
        },
        onPlaylistRename = { selectedPlaylist, name ->
            homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { renamePlaylist(it, name) }
                ?: run { status = "Playlist not found." }
        },
        onPlaylistDelete = { selectedPlaylist ->
            homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { deletePlaylist(it) }
                ?: run { status = "Playlist not found." }
        },
        onPlaylistBack = {
            contentState = contentState.copy(
                selectedPlaylist = null,
                selectedPlaylistTracks = emptyList(),
            )
        },
        onPlaylistTrackSelected = { selectedTrack ->
            val track = selectedPlaylistTracks.firstOrNull { it.id.value == selectedTrack.id } ?: findKnownTrack(selectedTrack.id)
            if (track == null) {
                status = "Track not found."
                return@NaviampSharedAppShell
            }
            playTrack(track, selectedPlaylistTracks.ifEmpty { listOf(track) })
        },
        onRadioStationSelected = { selectedStation ->
            val station = homeState.radioStations.firstOrNull { it.id == selectedStation.id }
            if (station == null) {
                status = "Station not found."
                return@NaviampSharedAppShell
            }
            playInternetRadioStation(station)
        },
        onHomeStationSelected = { station ->
            when {
                station.id == HomeStationLibrary -> {
                    playRadioTracks("Library Radio") { radioService ->
                        radioService.libraryRadio()
                    }
                }
                station.id == HomeStationRandomAlbum -> {
                    playRadioTracks("Random Album Radio") { radioService ->
                        val album = homeState.randomAlbums.firstOrNull()
                            ?: provider?.albumList(AlbumListType.Random, limit = 1)?.firstOrNull()
                        album?.let { radioService.albumRadio(it.id) }.orEmpty()
                    }
                }
                parseHomeGenreStationId(station.id) != null -> {
                    val genre = parseHomeGenreStationId(station.id).orEmpty()
                    playRadioTracks("${genre} Radio") { radioService ->
                        radioService.genreRadio(genre)
                    }
                }
                parseHomeDecadeStationId(station.id) != null -> {
                    val decade = parseHomeDecadeStationId(station.id)
                    if (decade != null) {
                        playRadioTracks(station.title) { radioService ->
                            radioService.decadeRadio(decade.fromYear, decade.toYear)
                        }
                    }
                }
            }
        },
        onOpenNowPlaying = { nowPlayingOpen = true },
        onCloseNowPlaying = {
            nowPlayingOpen = false
            contentState = contentState.clearDetails()
        },
        onPause = playbackEngine::pause,
        onResume = {
            when (playbackState) {
                PlaybackState.Idle, PlaybackState.Stopped, PlaybackState.Finished -> {
                    nowPlaying?.let { track ->
                        playTrack(
                            track = track,
                            queue = playbackQueue.tracks.ifEmpty { listOf(track) },
                            startPositionSeconds = restoredStartPositionSeconds,
                        )
                        restoredStartPositionSeconds = null
                    } ?: nowPlayingStation?.let(::playInternetRadioStation)
                }
                else -> playbackEngine.resume()
            }
        },
        onStop = playbackEngine::stop,
        onPrevious = { playAdjacentTrack(-1) },
        onNext = { playAdjacentTrack(1) },
        onSeek = ::performSeek,
        onVolumeChanged = { percent ->
            volumePercent = percent
            playbackEngine.setVolume(percent)
        },
        onToggleShuffle = {
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            val queue = activeQueue()
            val currentIndex = queue.indexOfFirst { it.id == currentTrack.id }
            if (currentIndex < 0) return@NaviampSharedAppShell
            val snapshot = shuffledUpNextSnapshot
            val queueState = PlaybackQueue(tracks = queue, currentIndex = currentIndex)
            if (snapshot == null) {
                val shuffled = queueState.shuffleUpcoming() ?: return@NaviampSharedAppShell
                playbackQueue = shuffled.first
                shuffledUpNextSnapshot = shuffled.second
            } else {
                playbackQueue = queueState.restoreUpcoming(snapshot)
                shuffledUpNextSnapshot = null
            }
        },
        onCycleRepeatMode = {
            repeatMode = when (repeatMode) {
                RepeatMode.Off -> RepeatMode.Queue
                RepeatMode.Queue -> RepeatMode.Track
                RepeatMode.Track -> RepeatMode.Off
            }
        },
        onToggleLyrics = {
            lyricsVisible = !lyricsVisible
            if (lyricsVisible) {
                nowPlaying?.let(::loadLyrics)
            }
        },
        onTrackRadio = {
            val service = radioService() ?: return@NaviampSharedAppShell
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            if (provider?.capabilities?.supportsTrackRadio != true) return@NaviampSharedAppShell
            scope.launch {
                status = "Starting ${currentTrack.title} radio..."
                runCatching { service.trackRadio(currentTrack.id) }
                    .onSuccess { radioTracks ->
                        val queue = service.queue(currentTrack, radioTracks)
                        val deduped = queue.drop(1)
                        playbackQueue = PlaybackQueue(tracks = queue, currentIndex = 0)
                        relatedTracks = deduped
                        shuffledUpNextSnapshot = null
                        status = "Track radio loaded."
                    }
                    .onFailure { error ->
                        status = error.message ?: "Could not start track radio."
                    }
            }
        },
        onAddToPlaylist = { playlist ->
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            addTrackToPlaylist(currentTrack, playlist)
        },
        onCreatePlaylistAndAdd = { name ->
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            addTrackToPlaylist(currentTrack, playlist = null, newPlaylistName = name)
        },
        onDownloadTrack = {
            nowPlaying?.let(::downloadTrack)
        },
        onGoToAlbum = {
            val activeProvider = provider ?: return@NaviampSharedAppShell
            val albumId = nowPlaying?.albumId ?: return@NaviampSharedAppShell
            scope.launch {
                runCatching { activeProvider.album(albumId) }
                    .onSuccess { detail ->
                        contentState = contentState.showAlbum(detail)
                        nowPlayingOpen = false
                        navigationState = navigationState.copy(route = NaviampRoute.Library)
                    }
                    .onFailure { error -> status = error.message ?: "Album failed to load." }
            }
        },
        onGoToArtist = {
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            currentTrack.artistId?.let { openArtistDetails(it, currentTrack.artistName) }
        },
        onQueueItemRadio = { item ->
            findKnownTrack(item.id)?.let { track ->
                val service = radioService() ?: return@let
                if (provider?.capabilities?.supportsTrackRadio != true) return@let
                scope.launch {
                    runCatching { service.trackRadio(track.id) }
                        .onSuccess { radioTracks ->
                            val queue = service.queue(track, radioTracks)
                            playTrack(track, queue)
                        }
                        .onFailure { error -> status = error.message ?: "Could not start track radio." }
                }
            }
        },
        onQueueItemAddToPlaylist = { item, playlist ->
            findKnownTrack(item.id)?.let { addTrackToPlaylist(it, playlist) }
        },
        onQueueItemCreatePlaylistAndAdd = { item, name ->
            findKnownTrack(item.id)?.let { addTrackToPlaylist(it, playlist = null, newPlaylistName = name) }
        },
        onQueueItemDownload = { item ->
            findKnownTrack(item.id)?.let(::downloadTrack)
        },
        onToggleFavorite = {
            val activeProvider = provider ?: return@NaviampSharedAppShell
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            if (!activeProvider.capabilities.supportsTrackFavorites) return@NaviampSharedAppShell
            scope.launch {
                val favorite = currentTrack.favoritedAtIso8601 == null
                runCatching {
                    activeProvider.setTrackFavorite(currentTrack.id, favorite)
                }.onSuccess {
                    applyTrackMetadataUpdate(
                        currentTrack.copy(favoritedAtIso8601 = if (favorite) "android-local" else null),
                    )
                }.onFailure { error ->
                    status = error.message ?: "Could not update favorite."
                }
            }
        },
        onRatingSelected = { rating ->
            val activeProvider = provider ?: return@NaviampSharedAppShell
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            if (!activeProvider.capabilities.supportsTrackRatings) return@NaviampSharedAppShell
            scope.launch {
                runCatching {
                    activeProvider.setTrackRating(currentTrack.id, rating)
                }.onSuccess {
                    applyTrackMetadataUpdate(currentTrack.copy(userRating = rating))
                }.onFailure { error ->
                    status = error.message ?: "Could not update rating."
                }
            }
        },
    )
}

private suspend fun loadBrowseState(provider: NavidromeProvider): HomeContent {
    val today = LocalDate.now()
    return HomeService(
        provider = provider,
        date = HomeDate(year = today.year, dayOfYear = today.dayOfYear),
    ).load()
}

private fun Track.coverArtUrl(provider: NavidromeProvider?): String? =
    coverArtId?.let { provider?.coverArtUrl(it) }

private fun Track.toDetailSections(): List<NaviampDetailSectionUi> =
    listOf(
        NaviampDetailSectionUi(
            title = "Song",
            rows = listOfNotNull(
                "Title" to title,
                "Artist" to artistName,
                "Album" to albumTitle.orUnknown(),
                albumReleaseYear?.let { "Year" to it.toString() },
                durationSeconds?.let { "Duration" to it.durationLabel() },
            ),
        ),
        NaviampDetailSectionUi(
            title = "File",
            rows = listOfNotNull(
                "Codec" to audioInfo?.codec.orUnknown(),
                audioInfo?.bitrateKbps?.let { "Bitrate" to "$it kbps" },
                audioInfo?.samplingRateHz?.let { "Sample rate" to "$it Hz" },
                audioInfo?.bitDepth?.let { "Bit depth" to "$it bit" },
                "Content type" to audioInfo?.contentType.orUnknown(),
            ),
        ),
        NaviampDetailSectionUi(
            title = "Library",
            rows = listOfNotNull(
                "Track ID" to id.value,
                artistId?.let { "Artist ID" to it.value },
                albumId?.let { "Album ID" to it.value },
                "Favorite" to if (favoritedAtIso8601 != null) "Yes" else "No",
                userRating?.let { "Rating" to it.ratingLabel() },
            ),
        ),
        NaviampDetailSectionUi(
            title = "Replay gain",
            rows = listOfNotNull(
                replayGain?.trackGainDb?.let { "Track gain" to "${it.twoDecimalLabel()} dB" },
                replayGain?.albumGainDb?.let { "Album gain" to "${it.twoDecimalLabel()} dB" },
                replayGain?.trackPeak?.let { "Track peak" to it.sixDecimalLabel() },
                replayGain?.albumPeak?.let { "Album peak" to it.sixDecimalLabel() },
            ),
        ),
    ).filter { it.rows.isNotEmpty() }

private fun String?.orUnknown(): String =
    this?.takeIf { it.isNotBlank() } ?: "Unknown"

private fun allKnownTracks(
    searchResults: MediaSearchResults,
    albumDetail: AlbumDetails?,
): List<Track> =
    albumDetail?.tracks ?: searchResults.tracks

private fun MediaSearchResults.totalCount(): Int =
    artists.size + albums.size + tracks.size

private suspend fun resolveInternetRadioStreamUrl(stationUrl: String): String =
    withContext(Dispatchers.IO) {
        val connection = (URL(stationUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Naviamp Android")
            setRequestProperty("Icy-MetaData", "1")
        }
        val contentType = connection.contentType.orEmpty().lowercase()
        val resolvedUrl = connection.url.toString()
        if (!looksLikePlaylistUrl(resolvedUrl) &&
            !contentType.isPlaylistContentType() &&
            (contentType.startsWith("audio/") || contentType.contains("ogg"))
        ) {
            connection.disconnect()
            return@withContext resolvedUrl
        }

        val body = connection.inputStream.bufferedReader().use { it.readText().take(128_000) }
        connection.disconnect()

        parseRadioPlaylist(body)
            ?: if (looksLikeDirectAudioUrl(resolvedUrl)) resolvedUrl else stationUrl
    }

private fun parseRadioPlaylist(body: String): String? {
    val lines = body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()

    lines.firstNotNullOfOrNull { line ->
        val equalsIndex = line.indexOf('=')
        if (equalsIndex > 0 && line.substring(0, equalsIndex).trim().lowercase().startsWith("file")) {
            line.substring(equalsIndex + 1).trim().takeIf { it.startsWith("http", ignoreCase = true) }
        } else {
            null
        }
    }?.let { return it }

    lines.firstOrNull { line ->
        line.startsWith("http", ignoreCase = true) && !line.startsWith("#")
    }?.let { return it }

    Regex("<location>(.*?)</location>", RegexOption.IGNORE_CASE)
        .find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.startsWith("http", ignoreCase = true) }
        ?.let { return it }

    Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
        .find(body)
        ?.value
        ?.let { return it }

    return null
}

private fun looksLikeDirectAudioUrl(url: String): Boolean {
    val normalized = url.substringBefore('?').lowercase()
    return normalized.endsWith(".mp3") ||
        normalized.endsWith(".ogg") ||
        normalized.endsWith(".opus") ||
        normalized.endsWith(".aac") ||
        normalized.endsWith(".m4a") ||
        normalized.endsWith(".flac")
}

private fun looksLikePlaylistUrl(url: String): Boolean {
    val normalized = url.substringBefore('?').lowercase()
    return normalized.endsWith(".pls") ||
        normalized.endsWith(".m3u") ||
        normalized.endsWith(".m3u8") ||
        normalized.endsWith(".xspf") ||
        normalized.endsWith(".asx")
}

private fun String.isPlaylistContentType(): Boolean =
    contains("mpegurl") ||
        contains("scpls") ||
        contains("pls") ||
        contains("xspf") ||
        contains("asx") ||
        contains("text/plain") ||
        contains("text/html") ||
        contains("application/octet-stream")

private fun NaviampRoute.toSharedRoute(): SharedRoute =
    when (this) {
        NaviampRoute.Home -> SharedRoute.Home
        NaviampRoute.Playlists,
        NaviampRoute.PlaylistDetail,
        -> SharedRoute.Playlists
        NaviampRoute.Library -> SharedRoute.Library
        NaviampRoute.Search,
        NaviampRoute.AlbumDetail,
        NaviampRoute.ArtistDetail,
        NaviampRoute.Player,
        -> SharedRoute.Search
        NaviampRoute.Radio -> SharedRoute.Radio
        NaviampRoute.Downloads -> SharedRoute.Downloads
        NaviampRoute.Settings -> SharedRoute.Settings
    }

private fun SharedRoute.toNaviampRoute(): NaviampRoute =
    when (this) {
        SharedRoute.Home -> NaviampRoute.Home
        SharedRoute.Playlists -> NaviampRoute.Playlists
        SharedRoute.Library -> NaviampRoute.Library
        SharedRoute.Search -> NaviampRoute.Search
        SharedRoute.Radio -> NaviampRoute.Radio
        SharedRoute.Downloads -> NaviampRoute.Downloads
        SharedRoute.Settings -> NaviampRoute.Settings
    }

private fun RepeatMode.toNaviampRepeatMode(): NaviampRepeatMode =
    when (this) {
        RepeatMode.Off -> NaviampRepeatMode.Off
        RepeatMode.Queue -> NaviampRepeatMode.Queue
        RepeatMode.Track -> NaviampRepeatMode.Track
    }

private fun PlaybackProgress.mergeForAndroidPlayback(previous: PlaybackProgress): PlaybackProgress =
    PlaybackProgress(
        positionSeconds = positionSeconds ?: previous.positionSeconds,
        durationSeconds = durationSeconds ?: previous.durationSeconds,
    )

private const val PendingSeekToleranceSeconds = 2.0
private const val PendingSeekStaleProgressWindowMillis = 1_500L
