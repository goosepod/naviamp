package app.naviamp.android

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.naviamp.android.playback.AndroidBassLoadReport
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.Track
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.provider.allKnownTracks
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedSonicMixBuilderUi
import app.naviamp.ui.SharedSonicPathBuilderUi
import app.naviamp.ui.toSharedGenreMixItemUi
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toNaviampSleepTimerUi

@Composable
fun rememberAndroidAppShellUiState(
    state: AndroidAppState,
    modifier: Modifier,
    context: Context,
    bassLoadReport: AndroidBassLoadReport,
    playbackEngine: AndroidPlaybackEngine,
    sonicPathBuilder: SharedSonicPathBuilderUi,
    sonicMixBuilder: SharedSonicMixBuilderUi,
): AndroidAppShellUiState =
    with(state) {
        val activeQueueForUi = playbackQueue.tracks.ifEmpty { allKnownTracks(searchResults, albumDetail) }
        val streamQualityForUi = playbackSettings.streamQualityForNetwork(context.isActiveNetworkMobileData())
        val diagnostics = rememberAndroidDiagnostics(
            selectedRoute = selectedRoute,
            storageStats = storageStats,
            provider = provider,
            validation = validation,
            activeSourceId = activeSourceId,
            bassLoadReport = bassLoadReport,
            playbackEngine = playbackEngine,
            playbackState = playbackState,
            playbackProgress = playbackProgress,
            playbackQueue = playbackQueue,
            playbackSettings = playbackSettings,
            streamQuality = streamQualityForUi,
            nowPlaying = nowPlaying,
            nowPlayingStation = nowPlayingStation,
            nowPlayingStreamMetadata = nowPlayingStreamMetadata,
            nowPlayingOpen = nowPlayingOpen,
            visualizerVisible = visualizerVisible,
            activeTlsSettings = activeTlsSettings,
        )
        val shellModels = rememberAndroidShellModels(
            connectionName = connectionName,
            serverUrl = serverUrl,
            username = username,
            password = password,
            skipTlsVerification = skipTlsVerification,
            customCertificatePath = customCertificatePath,
            clientCertificatePath = clientCertificatePath,
            clientCertificatePassword = clientCertificatePassword,
            provider = provider,
            sonicSimilarityEnabled = playbackSettings.sonicSimilarityEnabled,
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
        val nowPlayingUi = androidNowPlayingUi(
            nowPlaying = nowPlaying,
            nowPlayingStation = nowPlayingStation,
            nowPlayingStreamMetadata = nowPlayingStreamMetadata,
            playbackEngine = playbackEngine,
            playbackState = playbackState,
            playbackProgress = playbackProgress,
            visualizerVisible = visualizerVisible,
            volumePercent = volumePercent,
            knownTracks = activeQueueForUi,
            repeatMode = repeatMode,
            shuffledUpNextSnapshot = shuffledUpNextSnapshot,
            waveformByTrackId = waveformByTrackId,
            audioTagsByTrackId = audioTagsByTrackId,
            lyricsByTrackId = lyricsByTrackId,
            lyricsStatusByTrackId = lyricsStatusByTrackId,
            lyricsVisible = lyricsVisible,
            nowPlayingOpen = nowPlayingOpen,
            streamQuality = streamQualityForUi,
            provider = provider,
            playlistChoices = shellModels.playlistChoices,
            playlistActionStatus = playlistActionStatus,
            canSaveQueueAsPlaylist = playbackQueue.tracks.isNotEmpty(),
            sleepTimer = sleepTimer.toNaviampSleepTimerUi(sleepTimerNowEpochMillis),
            relatedTracks = relatedTracks,
            relatedTracksSource = relatedTracksSource,
            relatedSimilarityByTrackId = relatedSimilarityByTrackId,
            sonicSimilarityEnabled = playbackSettings.sonicSimilarityEnabled,
            radioTrackArtworkByKey = radioTrackArtworkByKey,
            radioStations = homeState.radioStations,
        )

        AndroidAppShellUiState(
            modifier = modifier,
            status = status,
            serverVersion = validation?.serverVersion,
            connected = provider != null,
            editingConnection = editingConnection,
            restoringConnection = restoringConnection,
            connectionForm = shellModels.connectionForm,
            playbackSettings = playbackSettings,
            diagnostics = diagnostics,
            supportsReplayGain = playbackEngine.supportsReplayGain,
            supportsGapless = playbackEngine.supportsGapless,
            supportsCrossfade = playbackEngine.supportsCrossfade,
            supportsEqualizer = (playbackEngine as? EqualizerPlaybackEngine)?.supportsEqualizer == true,
            supportsSonicSimilarity = provider?.capabilities?.supportsSonicSimilarity == true,
            showMobileNetworkQuality = true,
            selectedVisualizer = selectedVisualizer,
            visualizerBandsProvider = { visualizerFrame?.bands.orEmpty() },
            query = query,
            home = shellModels.home,
            searchResults = shellModels.searchResults,
            artistMixBuilder = SharedArtistMixBuilderUi(
                query = artistMixQuery,
                selectedArtists = artistMixSelectedArtists.map { artist ->
                    artist.toSharedMediaItemUi(
                        coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                    )
                },
                suggestedArtists = artistMixSuggestions.map { artist ->
                    artist.toSharedMediaItemUi(
                        coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                    )
                },
                status = artistMixStatus,
                loading = artistMixLoading,
            ),
            albumMixBuilder = SharedAlbumMixBuilderUi(
                query = albumMixQuery,
                selectedAlbums = albumMixSelectedAlbums.map { album ->
                    album.toSharedMediaItemUi(
                        coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                    )
                },
                suggestedAlbums = albumMixSuggestions.map { album ->
                    album.toSharedMediaItemUi(
                        coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                    )
                },
                status = albumMixStatus,
                loading = albumMixLoading,
            ),
            genreMixBuilder = SharedGenreMixBuilderUi(
                query = genreMixQuery,
                selectedGenres = genreMixSelectedGenres.map { genre -> genre.toSharedGenreMixItemUi() },
                suggestedGenres = genreMixSuggestions.map { genre -> genre.toSharedGenreMixItemUi() },
                status = genreMixStatus,
                loading = genreMixLoading,
            ),
            sonicPathBuilder = sonicPathBuilder,
            sonicMixBuilder = sonicMixBuilder,
            libraryArtists = shellModels.libraryArtists,
            libraryQuery = libraryQuery,
            librarySyncStatus = shellModels.librarySyncStatus,
            downloads = shellModels.downloads,
            downloadBytes = storageStats.downloadBytes,
            maxDownloadBytes = AndroidMaxDownloadBytes,
            downloadStatus = downloadStatus,
            playlistItems = shellModels.playlistItems,
            recentPlaylistIds = recentPlaylistIds,
            playlistSortMode = playlistSortMode,
            playlistChoices = shellModels.playlistChoices,
            playlistActionStatus = playlistActionStatus,
            radioStations = homeState.radioStations,
            albumDetail = shellModels.albumDetail,
            artistDetail = shellModels.artistDetail,
            playlistDetail = shellModels.playlistDetail,
            nowPlaying = nowPlayingUi,
            nowPlayingOpen = nowPlayingOpen,
            selectedRoute = selectedRoute,
        )
    }
