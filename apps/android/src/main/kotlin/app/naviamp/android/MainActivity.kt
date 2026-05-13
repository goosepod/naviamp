package app.naviamp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.naviamp.android.playback.AndroidAudioWaveformAnalyzer
import app.naviamp.android.playback.AndroidMedia3PlaybackEngine
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.label
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.ui.AndroidTrackRowUi
import app.naviamp.ui.ConnectionFormState
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
import app.naviamp.ui.SharedHomeUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var skipTlsVerification by remember { mutableStateOf(false) }
    var customCertificatePath by remember { mutableStateOf("") }
    var clientCertificatePath by remember { mutableStateOf("") }
    var clientCertificatePassword by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var homeState by remember { mutableStateOf(AndroidBrowseState()) }
    var searchResults by remember { mutableStateOf(MediaSearchResults()) }
    var albumDetail by remember { mutableStateOf<AlbumDetails?>(null) }
    var editingConnection by remember { mutableStateOf(false) }
    var selectedRoute by remember { mutableStateOf(SharedRoute.Home) }
    var provider by remember { mutableStateOf<NavidromeProvider?>(null) }
    var activeTlsSettings by remember { mutableStateOf(NavidromeTlsSettings()) }
    var validation by remember { mutableStateOf<ConnectionValidation?>(null) }
    var status by remember { mutableStateOf("Connect to Navidrome to start Android playback.") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var nowPlaying by remember { mutableStateOf<Track?>(null) }
    var nowPlayingStation by remember { mutableStateOf<InternetRadioStation?>(null) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    var playbackProgress by remember { mutableStateOf(PlaybackProgress.Unknown) }
    var volumePercent by remember { mutableStateOf(100) }
    var waveformByTrackId by remember { mutableStateOf<Map<String, List<Float>>>(emptyMap()) }

    DisposableEffect(playbackEngine) {
        onDispose {
            playbackEngine.release()
        }
    }

    fun playTrack(track: Track) {
        val activeProvider = provider
        if (activeProvider == null) {
            status = "Connect before playing a track."
            return
        }
        scope.launch {
            status = "Loading ${track.title}..."
            runCatching {
                activeProvider.streamUrl(StreamRequest(track.id, StreamQuality.Original))
            }.onSuccess { streamUrl ->
                nowPlaying = track
                nowPlayingStation = null
                nowPlayingOpen = true
                playbackProgress = PlaybackProgress.Unknown
                playbackEngine.applyTlsSettings(activeTlsSettings)
                playbackEngine.play(
                    scope = scope,
                    request = PlaybackRequest(streamUrl),
                    onStateChanged = { state ->
                        playbackState = state
                        if (state is PlaybackState.Error) {
                            status = state.message
                        }
                    },
                    onProgressChanged = { playbackProgress = it },
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

    fun playAdjacentTrack(offset: Int) {
        val currentTrack = nowPlaying ?: return
        val knownTracks = allKnownTracks(searchResults, albumDetail)
        val currentIndex = knownTracks.indexOfFirst { it.id == currentTrack.id }
        val nextTrack = knownTracks.getOrNull(currentIndex + offset) ?: return
        playTrack(nextTrack)
    }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        nowPlaying = nowPlaying?.let { if (it.id == updatedTrack.id) updatedTrack else it }
        tracks = tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        searchResults = searchResults.copy(
            tracks = searchResults.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it },
        )
        albumDetail = albumDetail?.copy(
            tracks = albumDetail?.tracks.orEmpty().map { if (it.id == updatedTrack.id) updatedTrack else it },
        )
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
        query = query,
        home = homeState.toSharedHome(provider),
        searchResults = searchResults.toSharedSearchResults(provider),
        libraryArtists = homeState.artists.map { it.toSharedMediaItem() },
        playlistItems = homeState.playlists.map { it.toSharedMediaItem() },
        radioStationItems = homeState.radioStations.map { it.toSharedMediaItem() },
        albumDetail = albumDetail?.toSharedAlbumDetail(provider),
        nowPlaying = nowPlaying?.let { track ->
            val knownTracks = allKnownTracks(searchResults, albumDetail)
            val currentIndex = knownTracks.indexOfFirst { it.id == track.id }
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
                audioInfo = track.audioInfo?.androidAudioInfo().orEmpty(),
                waveformAmplitudes = waveformByTrackId[track.id.value].orEmpty(),
                positionSeconds = playbackProgress.positionSeconds,
                durationSeconds = track.durationSeconds?.toDouble() ?: playbackProgress.durationSeconds,
                volumePercent = volumePercent,
                isPlaying = playbackState == PlaybackState.Playing,
                isPaused = playbackState == PlaybackState.Paused,
                canSeek = true,
                canChangeVolume = false,
                hasPrevious = currentIndex > 0,
                hasNext = currentIndex >= 0 && currentIndex < knownTracks.lastIndex,
                shuffleEnabled = knownTracks.size > 2,
                canRepeat = knownTracks.isNotEmpty(),
                canStartRadio = true,
                canAddToPlaylist = true,
                favoriteActive = track.favoritedAtIso8601 != null,
                canFavorite = provider?.capabilities?.supportsTrackFavorites == true,
                userRating = track.userRating,
                canRate = provider?.capabilities?.supportsTrackRatings == true,
                menuEnabled = true,
                backTo = knownTracks
                    .take(currentIndex.coerceAtLeast(0))
                    .asReversed()
                    .map { it.toNowPlayingItemUi(provider) },
                upNext = if (currentIndex >= 0) {
                    knownTracks.drop(currentIndex + 1).map { it.toNowPlayingItemUi(provider) }
                } else {
                    emptyList()
                },
            )
        } ?: nowPlayingStation?.let { station ->
            NowPlayingUi(
                id = station.id,
                title = station.name,
                subtitle = "Internet radio",
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
        onRouteSelected = { selectedRoute = it },
        onConnectionFormChanged = { form ->
            serverUrl = form.serverUrl
            username = form.username
            password = form.password
            skipTlsVerification = form.skipTlsVerification
            customCertificatePath = form.customCertificatePath
            clientCertificatePath = form.clientCertificatePath
            clientCertificatePassword = form.clientCertificatePassword
        },
        onConnect = {
            scope.launch {
                status = "Connecting..."
                runCatching {
                    val tlsSettings = NavidromeTlsSettings(
                        insecureSkipTlsVerification = skipTlsVerification,
                        customCertificatePath = customCertificatePath.trim().takeIf { it.isNotEmpty() },
                        clientCertificateKeyStorePath = clientCertificatePath.trim().takeIf { it.isNotEmpty() },
                        clientCertificateKeyStorePassword = clientCertificatePassword
                            .takeIf { clientCertificatePath.trim().isNotEmpty() },
                    )
                    val connection = NavidromeConnection.fromPassword(
                        baseUrl = serverUrl,
                        username = username,
                        password = password,
                        tlsSettings = tlsSettings,
                    )
                    val nextProvider = NavidromeProvider(connection)
                    playbackEngine.applyTlsSettings(tlsSettings)
                    validation = nextProvider.validateConnection()
                    homeState = loadBrowseState(nextProvider)
                    provider = nextProvider
                    activeTlsSettings = tlsSettings
                }.onSuccess {
                    status = "Connected."
                    editingConnection = false
                    selectedRoute = SharedRoute.Home
                }.onFailure { error ->
                    status = error.message ?: "Connection failed."
                    provider = null
                    validation = null
                }
            }
        },
        onEditConnection = { editingConnection = true },
        onCancelEditConnection = { editingConnection = false },
        onQueryChanged = { query = it },
        onSearch = {
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Searching..."
                runCatching {
                    activeProvider.search(query, limit = 20)
                }.onSuccess { results ->
                    searchResults = results
                    tracks = results.tracks
                    albumDetail = null
                    status = if (results.isEmpty) "No matches found." else "Found ${results.totalCount()} matches."
                }.onFailure { error ->
                    status = error.message ?: "Search failed."
                }
            }
        },
        onTrackSelected = { selectedTrack ->
            val track = allKnownTracks(searchResults, albumDetail).firstOrNull { it.id.value == selectedTrack.id }
            if (track == null) {
                status = "Track not found."
                return@NaviampSharedAppShell
            }
            playTrack(track)
        },
        onAlbumSelected = { selectedAlbum ->
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading ${selectedAlbum.title}..."
                runCatching {
                    activeProvider.album(app.naviamp.domain.AlbumId(selectedAlbum.id))
                }.onSuccess { detail ->
                    albumDetail = detail
                    tracks = detail.tracks
                    nowPlayingOpen = false
                    status = "Connected."
                }.onFailure { error ->
                    status = error.message ?: "Album failed to load."
                }
            }
        },
        onArtistSelected = {
            status = "Artist details are next for Android."
        },
        onPlaylistSelected = {
            status = "Playlist details are next for Android."
        },
        onRadioStationSelected = { selectedStation ->
            val station = homeState.radioStations.firstOrNull { it.id == selectedStation.id }
            if (station == null) {
                status = "Station not found."
                return@NaviampSharedAppShell
            }
            nowPlaying = null
            nowPlayingStation = station
            nowPlayingOpen = true
            status = "Loading ${station.name}..."
            playbackEngine.applyTlsSettings(activeTlsSettings)
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
                        onProgressChanged = { playbackProgress = it },
                    )
                }.onFailure { error ->
                    status = error.message ?: "Radio stream failed."
                }
            }
        },
        onOpenNowPlaying = { nowPlayingOpen = true },
        onCloseNowPlaying = {
            nowPlayingOpen = false
            albumDetail = null
        },
        onPause = playbackEngine::pause,
        onResume = playbackEngine::resume,
        onStop = playbackEngine::stop,
        onPrevious = { playAdjacentTrack(-1) },
        onNext = { playAdjacentTrack(1) },
        onSeek = playbackEngine::seek,
        onVolumeChanged = { percent ->
            volumePercent = percent
            playbackEngine.setVolume(percent)
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

private data class AndroidBrowseState(
    val recentlyAddedAlbums: List<Album> = emptyList(),
    val mixAlbums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val radioStations: List<InternetRadioStation> = emptyList(),
)

private suspend fun loadBrowseState(provider: NavidromeProvider): AndroidBrowseState =
    AndroidBrowseState(
        recentlyAddedAlbums = runCatching { provider.recentlyAddedAlbums(limit = 12) }.getOrDefault(emptyList()),
        mixAlbums = runCatching { provider.albumList(AlbumListType.Random, limit = 6) }.getOrDefault(emptyList()),
        artists = runCatching { provider.artists(limit = 50) }.getOrDefault(emptyList()),
        playlists = runCatching { provider.playlists(limit = 50) }.getOrDefault(emptyList()),
        radioStations = runCatching { provider.internetRadioStations() }.getOrDefault(emptyList()),
    )

private fun AndroidBrowseState.toSharedHome(provider: NavidromeProvider?): SharedHomeUi =
    SharedHomeUi(
        recentlyAddedAlbums = recentlyAddedAlbums.map { it.toSharedMediaItem(provider) },
        mixAlbums = mixAlbums.map { it.toSharedMediaItem(provider) },
        playlists = playlists.map { it.toSharedMediaItem() },
        radioStations = radioStations.map { it.toSharedMediaItem() },
    )

private fun MediaSearchResults.toSharedSearchResults(provider: NavidromeProvider?) =
    app.naviamp.ui.SharedSearchResultsUi(
        artists = artists.map { it.toSharedMediaItem() },
        albums = albums.map { it.toSharedMediaItem(provider) },
        tracks = tracks.map { it.toAndroidTrackRowUi(provider) },
    )

private fun Artist.toSharedMediaItem(): SharedMediaItemUi =
    SharedMediaItemUi(id = id.value, title = name, subtitle = "Artist")

private fun Playlist.toSharedMediaItem(): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id,
        title = name,
        subtitle = "$trackCount tracks",
        meta = durationSeconds?.durationLabel().orEmpty(),
    )

private fun InternetRadioStation.toSharedMediaItem(): SharedMediaItemUi =
    SharedMediaItemUi(id = id, title = name, subtitle = homePageUrl ?: "Internet radio")

private fun AlbumDetails.toSharedAlbumDetail(provider: NavidromeProvider?): SharedAlbumDetailUi =
    SharedAlbumDetailUi(
        album = album.toSharedMediaItem(provider),
        tracks = tracks.map { it.toAndroidTrackRowUi(provider) },
    )

private fun Album.toSharedMediaItem(provider: NavidromeProvider?): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id.value,
        title = title,
        subtitle = artistName,
        meta = releaseYear?.toString().orEmpty(),
        coverArtUrl = coverArtId?.let { provider?.coverArtUrl(it) },
    )

private fun Track.toAndroidTrackRowUi(provider: NavidromeProvider?): AndroidTrackRowUi =
    AndroidTrackRowUi(
        id = id.value,
        title = title,
        subtitle = listOfNotNull(artistName, albumTitle).joinToString(" - "),
        coverArtUrl = coverArtUrl(provider),
        meta = durationSeconds?.durationLabel().orEmpty(),
    )

private fun Track.coverArtUrl(provider: NavidromeProvider?): String? =
    coverArtId?.let { provider?.coverArtUrl(it) }

private fun Track.toNowPlayingItemUi(provider: NavidromeProvider?): NaviampNowPlayingItemUi =
    NaviampNowPlayingItemUi(
        id = id.value,
        title = title,
        subtitle = listOfNotNull(artistName, albumTitle).joinToString(" - "),
        meta = durationSeconds?.durationLabel().orEmpty(),
        coverArtUrl = coverArtUrl(provider),
    )

private fun InternetRadioStation.toNowPlayingStationUi(): NaviampNowPlayingItemUi =
    NaviampNowPlayingItemUi(
        id = id,
        title = name,
        subtitle = homePageUrl ?: "Internet radio",
    )

private fun AudioInfo.androidAudioInfo(): String =
    buildList {
        val normalizedCodec = codec?.takeIf { it.isNotBlank() }
        normalizedCodec?.let(::add)
        if (!normalizedCodec.equals("FLAC", ignoreCase = true)) {
            bitrateKbps?.takeIf { it > 0 }?.let { add("${it} kbps") }
        }
        samplingRateHz?.takeIf { it > 0 }?.let { add("${it / 1000.0} kHz") }
        bitDepth?.takeIf { it > 0 }?.let { add("$it-bit") }
    }.joinToString("  ")

private fun allKnownTracks(
    searchResults: MediaSearchResults,
    albumDetail: AlbumDetails?,
): List<Track> =
    albumDetail?.tracks ?: searchResults.tracks

private fun MediaSearchResults.totalCount(): Int =
    artists.size + albums.size + tracks.size

private fun Int.durationLabel(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

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
