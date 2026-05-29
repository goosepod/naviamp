package app.naviamp.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.naviamp.android.playback.AndroidBassLoadReport
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.app.NaviampContentState
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.label
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.AndroidTrackRowUi
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NowPlayingRadioUiConfig
import app.naviamp.ui.NowPlayingTrackUiConfig
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedHomeUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedSearchResultsUi
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.toDownloadedTrackUi
import app.naviamp.ui.toNowPlayingItemUi
import app.naviamp.ui.toNowPlayingStationUi
import app.naviamp.ui.toNowPlayingUi
import app.naviamp.ui.toPlaylistChoiceUi
import app.naviamp.ui.toSharedAlbumDetailUi
import app.naviamp.ui.toSharedArtistDetailUi
import app.naviamp.ui.toSharedHomeUi
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedPlaylistDetailUi
import app.naviamp.ui.toSharedSearchResultsUi
import app.naviamp.ui.toSharedRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
data class AndroidShellModels(
    val connectionForm: ConnectionFormState,
    val home: SharedHomeUi,
    val searchResults: SharedSearchResultsUi,
    val libraryArtists: List<SharedMediaItemUi>,
    val librarySyncStatus: NaviampLibrarySyncStatusUi,
    val downloads: List<NaviampDownloadedTrackUi>,
    val playlistItems: List<SharedMediaItemUi>,
    val playlistChoices: List<NaviampPlaylistChoiceUi>,
    val radioStationItems: List<SharedMediaItemUi>,
    val albumDetail: SharedAlbumDetailUi?,
    val artistDetail: SharedArtistDetailUi?,
    val playlistDetail: SharedPlaylistDetailUi?,
)

fun Track.coverArtUrl(provider: NavidromeProvider?): String? =
    (coverArtId ?: albumId?.value)?.let { provider?.coverArtUrl(it) }

@Composable
fun rememberAndroidShellModels(
    connectionName: String,
    serverUrl: String,
    username: String,
    password: String,
    skipTlsVerification: Boolean,
    customCertificatePath: String,
    clientCertificatePath: String,
    clientCertificatePassword: String,
    provider: NavidromeProvider?,
    homeState: HomeContent,
    playlistTracksById: Map<String, List<Track>>,
    searchResults: MediaSearchResults,
    libraryStatus: String?,
    isLibrarySyncing: Boolean,
    downloadedTracks: List<AndroidDownloadedTrack>,
    selectedPlaylistTracks: List<Track>,
    selectedPlaylist: Playlist?,
    albumDetail: AlbumDetails?,
    artistDetail: ArtistDetails?,
    artistPopularTracksByArtistId: Map<String, List<Track>>,
    artistPopularTracksStatusByArtistId: Map<String, String?>,
    artistSimilarArtistsByArtistId: Map<String, List<SimilarArtistMatch>>,
    artistSimilarArtistsStatusByArtistId: Map<String, String?>,
): AndroidShellModels =
    remember(
        connectionName,
        serverUrl,
        username,
        password,
        skipTlsVerification,
        customCertificatePath,
        clientCertificatePath,
        clientCertificatePassword,
        provider,
        homeState,
        playlistTracksById,
        searchResults,
        libraryStatus,
        isLibrarySyncing,
        downloadedTracks,
        selectedPlaylistTracks,
        selectedPlaylist,
        albumDetail,
        artistDetail,
        artistPopularTracksByArtistId,
        artistPopularTracksStatusByArtistId,
        artistSimilarArtistsByArtistId,
        artistSimilarArtistsStatusByArtistId,
    ) {
        androidShellModels(
            connectionName = connectionName,
            serverUrl = serverUrl,
            username = username,
            password = password,
            skipTlsVerification = skipTlsVerification,
            customCertificatePath = customCertificatePath,
            clientCertificatePath = clientCertificatePath,
            clientCertificatePassword = clientCertificatePassword,
            provider = provider,
            homeState = homeState,
            playlistTracksById = playlistTracksById,
            searchResults = searchResults,
            libraryStatus = libraryStatus,
            isLibrarySyncing = isLibrarySyncing,
            downloadedTracks = downloadedTracks,
            selectedPlaylistTracks = selectedPlaylistTracks,
            selectedPlaylist = selectedPlaylist,
            albumDetail = albumDetail,
            artistDetail = artistDetail,
            artistPopularTracksByArtistId = artistPopularTracksByArtistId,
            artistPopularTracksStatusByArtistId = artistPopularTracksStatusByArtistId,
            artistSimilarArtistsByArtistId = artistSimilarArtistsByArtistId,
            artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId,
        )
    }

fun androidShellModels(
    connectionName: String,
    serverUrl: String,
    username: String,
    password: String,
    skipTlsVerification: Boolean,
    customCertificatePath: String,
    clientCertificatePath: String,
    clientCertificatePassword: String,
    provider: NavidromeProvider?,
    homeState: HomeContent,
    playlistTracksById: Map<String, List<Track>>,
    searchResults: MediaSearchResults,
    libraryStatus: String?,
    isLibrarySyncing: Boolean,
    downloadedTracks: List<AndroidDownloadedTrack>,
    selectedPlaylistTracks: List<Track>,
    selectedPlaylist: Playlist?,
    albumDetail: AlbumDetails?,
    artistDetail: ArtistDetails?,
    artistPopularTracksByArtistId: Map<String, List<Track>>,
    artistPopularTracksStatusByArtistId: Map<String, String?>,
    artistSimilarArtistsByArtistId: Map<String, List<SimilarArtistMatch>>,
    artistSimilarArtistsStatusByArtistId: Map<String, String?>,
): AndroidShellModels {
    val coverArtUrl: (String?) -> String? = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } }
    val playlistChoices = homeState.playlists.map { it.toPlaylistChoiceUi() }
    return AndroidShellModels(
        connectionForm = ConnectionFormState(
            displayName = connectionName,
            serverUrl = serverUrl,
            username = username,
            password = password,
            skipTlsVerification = skipTlsVerification,
            customCertificatePath = customCertificatePath,
            clientCertificatePath = clientCertificatePath,
            clientCertificatePassword = clientCertificatePassword,
        ),
        home = homeState.toSharedHomeUi(
            coverArtUrl = coverArtUrl,
            playlistTracksById = playlistTracksById,
        ),
        searchResults = searchResults.toSharedSearchResultsUi(coverArtUrl),
        libraryArtists = homeState.artists.map { it.toSharedMediaItemUi(coverArtUrl) },
        librarySyncStatus = NaviampLibrarySyncStatusUi(
            message = libraryStatus,
            isSyncing = isLibrarySyncing,
        ),
        downloads = downloadedTracks.map { download ->
            download.track.toDownloadedTrackUi(
                id = download.file.absolutePath,
                sizeBytes = download.sizeBytes,
                coverArtUrl = coverArtUrl,
            )
        },
        playlistItems = homeState.playlists.map {
            it.toSharedMediaItemUi(
                coverArtUrl = coverArtUrl,
                tracks = playlistTracksById[it.id].orEmpty(),
            )
        },
        playlistChoices = playlistChoices,
        radioStationItems = homeState.radioStations.map { it.toSharedMediaItemUi() },
        albumDetail = albumDetail?.let { detail ->
            detail.toSharedAlbumDetailUi(
                coverArtUrl = coverArtUrl,
                popularTrackIds = detail.tracks
                    .mapNotNull { it.artistId?.value }
                    .distinct()
                    .flatMap { artistId -> artistPopularTracksByArtistId[artistId].orEmpty() }
                    .map { it.id.value }
                    .toSet(),
            )
        },
        artistDetail = artistDetail?.toSharedArtistDetailUi(
            coverArtUrl = coverArtUrl,
            popularTracks = artistPopularTracksByArtistId[artistDetail.artist.id.value].orEmpty(),
            popularTracksStatus = artistPopularTracksStatusByArtistId[artistDetail.artist.id.value],
            similarArtists = artistSimilarArtistsByArtistId[artistDetail.artist.id.value].orEmpty(),
            similarArtistsStatus = artistSimilarArtistsStatusByArtistId[artistDetail.artist.id.value],
        ),
        playlistDetail = selectedPlaylist?.toSharedPlaylistDetailUi(
            tracks = selectedPlaylistTracks,
            coverArtUrl = coverArtUrl,
        ),
    )
}

fun androidNowPlayingUi(
    nowPlaying: Track?,
    nowPlayingStation: InternetRadioStation?,
    nowPlayingStreamMetadata: PlaybackStreamMetadata,
    provider: NavidromeProvider?,
    playbackEngine: AndroidPlaybackEngine,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    visualizerVisible: Boolean,
    volumePercent: Int,
    knownTracks: List<Track>,
    repeatMode: RepeatMode,
    shuffledUpNextSnapshot: List<Track>?,
    waveformByTrackId: Map<String, AudioWaveform>,
    lyricsByTrackId: Map<String, Lyrics?>,
    lyricsStatusByTrackId: Map<String, String?>,
    lyricsVisible: Boolean,
    nowPlayingOpen: Boolean,
    streamQuality: StreamQuality,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
    relatedTracks: List<Track>,
    radioStations: List<InternetRadioStation>,
): NowPlayingUi? =
    nowPlaying?.let { track ->
        val currentIndex = knownTracks.indexOfFirst { it.id == track.id }
        val coverArtUrl: (String?) -> String? = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } }
        track.toNowPlayingUi(
            NowPlayingTrackUiConfig(
                stateLabel = "${playbackState.label()} ${playbackProgress.positionSeconds?.toInt() ?: 0}s",
                coverArtUrl = track.coverArtUrl(provider),
                waveform = waveformByTrackId[track.id.value],
                visualizerAvailable = (playbackEngine as? VisualizerPlaybackEngine)?.supportsVisualizer == true,
                visualizerVisible = visualizerVisible,
                positionSeconds = playbackProgress.positionSeconds,
                durationSeconds = playbackProgress.durationSeconds,
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
                repeatMode = repeatMode,
                canRepeat = knownTracks.isNotEmpty(),
                canStartRadio = provider?.capabilities?.supportsTrackRadio == true,
                canAddToPlaylist = true,
                canFavorite = provider?.capabilities?.supportsTrackFavorites == true,
                canRate = provider?.capabilities?.supportsTrackRatings == true,
                lyricsAvailable = true,
                lyricsVisible = lyricsVisible && nowPlayingOpen,
                lyricsStatus = lyricsStatusByTrackId[track.id.value],
                lyrics = lyricsByTrackId[track.id.value],
                menuEnabled = true,
                streamQuality = streamQuality,
                playlistChoices = playlistChoices,
                playlistActionStatus = playlistActionStatus,
                backTo = knownTracks
                    .take(currentIndex.coerceAtLeast(0))
                    .asReversed()
                    .map { it.toNowPlayingItemUi(coverArtUrl) },
                upNext = if (currentIndex >= 0) {
                    knownTracks.drop(currentIndex + 1).map { it.toNowPlayingItemUi(coverArtUrl) }
                } else {
                    emptyList()
                },
                related = relatedTracks.map { it.toNowPlayingItemUi(coverArtUrl) },
            ),
        )
    } ?: nowPlayingStation?.let { station ->
        station.toNowPlayingUi(
            NowPlayingRadioUiConfig(
                streamTitle = nowPlayingStreamMetadata.title,
                stateLabel = playbackState.label(),
                volumePercent = volumePercent,
                isPlaying = playbackState == PlaybackState.Playing,
                isPaused = playbackState == PlaybackState.Paused,
                canChangeVolume = false,
                radioStations = radioStations.map { it.toNowPlayingStationUi() },
            ),
        )
    }

fun Context.isActiveNetworkMobileData(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

suspend fun MediaProvider.tracksForArtist(artistId: ArtistId, limit: Long): List<Track> {
    val tracks = mutableListOf<Track>()
    artist(artistId)
        .albums
        .take(ProviderArtistPopularTrackAlbumFallbackLimit)
        .forEach { albumSummary ->
            if (tracks.size >= limit) return@forEach
            val albumTracks = runCatching { album(albumSummary.id).tracks }.getOrDefault(emptyList())
            tracks += albumTracks.take((limit - tracks.size).toInt())
        }
    return tracks
}

fun deleteDirectoryContents(directory: File) {
    if (!directory.exists()) return
    directory.walkBottomUp()
        .filter { it != directory }
        .forEach { it.delete() }
}

fun Double.secondsToMillis(): Long =
    (coerceAtLeast(0.0) * 1_000.0).toLong()

fun normalizeAndroidAutoSeekPositionMillis(
    rawPositionMillis: Long,
    durationSeconds: Double?,
): Long {
    val raw = rawPositionMillis.coerceAtLeast(0L)
    val duration = durationSeconds ?: return raw
    if (raw == 0L) return 0L
    val durationMillis = duration.secondsToMillis()
    return if (raw <= duration.toLong() + 1L && durationMillis > 1_000L) {
        raw.toDouble().secondsToMillis()
    } else {
        raw
    }.coerceIn(0L, durationMillis)
}
