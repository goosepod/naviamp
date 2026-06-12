package app.naviamp.android

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.radio.RecentRadioAction
import app.naviamp.domain.radio.homeStationRadioAction
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.InternetRadioStationManager
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.RadioRequestStartResult
import app.naviamp.domain.radio.SeededRadioBuildResult
import app.naviamp.domain.radio.SeededRadioExpansionResult
import app.naviamp.domain.radio.RadioSeedResult
import app.naviamp.domain.radio.albumMixSeededRadioRequest
import app.naviamp.domain.radio.artistMixSeededRadioRequest
import app.naviamp.domain.radio.albumRecentRadioStream
import app.naviamp.domain.radio.artistRecentRadioStream
import app.naviamp.domain.radio.decadeRecentRadioStream
import app.naviamp.domain.radio.generatedRadioTracksToAppend
import app.naviamp.domain.radio.genreRecentRadioStream
import app.naviamp.domain.radio.genreMixRadioRequest
import app.naviamp.domain.radio.internetRadioDeleteErrorStatus
import app.naviamp.domain.radio.internetRadioDeleteLoadingStatus
import app.naviamp.domain.radio.internetRadioSaveErrorStatus
import app.naviamp.domain.radio.internetRadioSaveLoadingStatus
import app.naviamp.domain.radio.libraryRecentRadioStream
import app.naviamp.domain.radio.popularTracksRecentRadioStream
import app.naviamp.domain.radio.radioRefillSeedTrack
import app.naviamp.domain.radio.radioRequestStartResult
import app.naviamp.domain.radio.radioSeedResult
import app.naviamp.domain.radio.selectAlbumRadioSeedTrack
import app.naviamp.domain.radio.seededRadioBuildResult
import app.naviamp.domain.radio.seededRadioExpansionResult
import app.naviamp.domain.radio.trackRecentRadioStream
import app.naviamp.domain.radio.trackRadioRequest
import app.naviamp.domain.radio.withRadioCoverArtIds
import app.naviamp.domain.settings.RecentRadioStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun appendAndroidGeneratedRadioTracks(
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    seedTrack: Track,
    fetchedTracks: List<Track>,
) {
    with(state) {
        if (nowPlaying?.id != seedTrack.id) return
        val previousQueue = playbackQueue
        val newTracks = generatedRadioTracksToAppend(seedTrack, fetchedTracks, playbackQueue.tracks)
        if (newTracks.isNotEmpty()) {
            playbackQueue = playbackQueue.copy(tracks = playbackQueue.tracks + newTracks)
        }
        if (playbackQueue != previousQueue) {
            queueController.replaceQueue(playbackQueue, clearPreparedNext = false)
        }
    }
}

fun refillAndroidRadioIfNeeded(
    scope: CoroutineScope,
    state: AndroidAppState,
    queue: PlaybackQueue,
    queueController: PlaybackQueueController,
) {
    val seedTrack = radioRefillSeedTrack(
        queue = queue,
        refillThreshold = AndroidRadioRefillThreshold,
        repeatMode = state.repeatMode,
        isActive = state.radioQueueActive,
        isRefilling = state.radioRefilling,
        lastRefillSeedTrackId = state.lastRadioRefillSeedId,
    ) ?: return
    val activeProvider = state.provider ?: return

    state.radioRefilling = true
    state.lastRadioRefillSeedId = seedTrack.id
    scope.launch {
        try {
            when (
                val result = withContext(Dispatchers.IO) {
                    seededRadioExpansionResult(
                        radioService = RadioService(activeProvider, count = AndroidRadioRefillCount),
                    ) { radioService ->
                        radioService.trackRadio(seedTrack.id)
                    }
                }
            ) {
                is SeededRadioExpansionResult.Ready -> {
                    val newTracks = generatedRadioTracksToAppend(
                        seedTrack = seedTrack,
                        fetchedTracks = result.fetchedTracks,
                        queuedTracks = state.playbackQueue.tracks,
                    )
                    if (state.radioQueueActive && newTracks.isNotEmpty()) {
                        queueController.replaceQueue(state.playbackQueue, clearPreparedNext = false)
                        queueController.appendTracks(
                            tracks = newTracks,
                            maxHistory = AndroidRadioQueueHistoryLimit,
                        )?.let { updatedQueue ->
                            state.playbackQueue = updatedQueue
                        }
                        state.status = "Extending radio queue (${state.playbackQueue.tracks.size} tracks)..."
                    }
                }
                is SeededRadioExpansionResult.Failed -> {
                    state.status = result.error.message ?: "Could not extend radio."
                }
            }
        } finally {
            if (state.lastRadioRefillSeedId == seedTrack.id) {
                state.radioRefilling = false
            }
        }
    }
}

fun startAndroidSeededRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    statusLabel: String,
    seedTrack: Track,
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    recentRadioStream: RecentRadioStream? = null,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
    loadRest: suspend (RadioService) -> List<Track>,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    val seedQueue = listOf(seedTrack)
    state.radioQueueActive = true
    state.radioRefilling = true
    state.lastRadioRefillSeedId = seedTrack.id
    recentRadioStream?.withRadioCoverArtIds(seedQueue)?.let(rememberRecentRadioStream)
    playTrack(seedTrack, seedQueue)
    scope.launch {
        with(state) {
            when (
                val result = seededRadioBuildResult(
                    seedTrack = seedTrack,
                    recentRadioStream = recentRadioStream,
                    radioService = RadioService(
                        provider = activeProvider,
                        count = AndroidInitialSimilarRadioCount,
                        providerResponseService = providerResponseService,
                    ),
                    loadRest = loadRest,
                )
            ) {
                is SeededRadioBuildResult.Ready -> {
                    val queue = result.queue
                    result.recentRadioStream?.let(rememberRecentRadioStream)
                    if (nowPlaying?.id == seedTrack.id) {
                        playbackQueue = PlaybackQueue(tracks = queue, currentIndex = 0)
                    }
                    status = "Building $statusLabel queue..."
                }
                is SeededRadioBuildResult.Failed -> {
                    if (nowPlaying?.id == seedTrack.id) {
                        status = result.error.message ?: "Could not build $statusLabel."
                    }
                }
            }

            radioRefilling = false
            AndroidSimilarRadioExpansionCounts.forEach { count ->
                if (nowPlaying?.id != seedTrack.id) return@launch
                val result = seededRadioExpansionResult(
                    radioService = RadioService(
                        provider = activeProvider,
                        count = count,
                        providerResponseService = providerResponseService,
                    ),
                    loadRest = loadRest,
                )
                if (result !is SeededRadioExpansionResult.Ready) return@forEach
                appendAndroidGeneratedRadioTracks(state, queueController, seedTrack, result.fetchedTracks)
                status = "Building $statusLabel queue (${playbackQueue.tracks.size} tracks)..."
            }
            if (nowPlaying?.id == seedTrack.id) {
                status = "Playing $statusLabel."
            }
        }
    }
}

fun startAndroidTrackRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    track: Track,
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
) {
    startAndroidSeededRadio(
        scope = scope,
        state = state,
        queueController = queueController,
        statusLabel = "${track.title} radio",
        seedTrack = track,
        playTrack = playTrack,
        providerResponseCacheRepository = providerResponseCacheRepository,
        recentRadioStream = trackRecentRadioStream(track),
        rememberRecentRadioStream = rememberRecentRadioStream,
    ) { radioService ->
        radioService.trackRadio(track.id)
    }
}

fun loadAndroidRelatedTracks(
    scope: CoroutineScope,
    state: AndroidAppState,
    track: Track,
) {
    val activeProvider = state.provider ?: return
    if (!activeProvider.capabilities.supportsTrackRadio) {
        state.relatedTracks = emptyList()
        return
    }
    scope.launch {
        runCatching {
            if (
                state.playbackSettings.sonicSimilarityEnabled &&
                activeProvider.capabilities.supportsSonicSimilarity
            ) {
                activeProvider.sonicSimilarTracks(track.id, count = 20)
                    .ifEmpty { RadioService(activeProvider, count = 20).trackRadio(track.id) }
            } else {
                RadioService(activeProvider, count = 20).trackRadio(track.id)
            }
        }
            .onSuccess { tracks -> state.relatedTracks = tracks }
            .onFailure { state.relatedTracks = emptyList() }
    }
}

fun saveAndroidInternetRadioStation(
    scope: CoroutineScope,
    state: AndroidAppState,
    stationManager: InternetRadioStationManager,
    station: InternetRadioStation,
) {
    val activeProvider = state.provider
    if (activeProvider == null) {
        state.status = "Not connected."
        return
    }
    state.status = internetRadioSaveLoadingStatus(station)
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                stationManager.saveStation(activeProvider, station)
            }
        }.onSuccess { stations ->
            state.homeState = state.homeState.copy(radioStations = stations)
            state.status = ""
        }.onFailure { error ->
            state.status = error.message ?: internetRadioSaveErrorStatus()
        }
    }
}

fun deleteAndroidInternetRadioStation(
    scope: CoroutineScope,
    state: AndroidAppState,
    stationManager: InternetRadioStationManager,
    station: InternetRadioStation,
) {
    val activeProvider = state.provider
    if (activeProvider == null) {
        state.status = "Not connected."
        return
    }
    state.status = internetRadioDeleteLoadingStatus(station)
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                stationManager.deleteStation(activeProvider, station)
            }
        }.onSuccess { stations ->
            state.homeState = state.homeState.copy(radioStations = stations)
            state.status = ""
        }.onFailure { error ->
            state.status = error.message ?: internetRadioDeleteErrorStatus()
        }
    }
}

fun startAndroidRadioTracks(
    scope: CoroutineScope,
    state: AndroidAppState,
    statusLabel: String,
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    recentRadioStream: RecentRadioStream? = null,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
    loadTracks: suspend (RadioService) -> List<Track>,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    val service = RadioService(activeProvider, providerResponseService = providerResponseService)
    scope.launch {
        state.status = "Starting $statusLabel..."
        when (
            val result = radioRequestStartResult(
                radioService = service,
                recentRadioStream = recentRadioStream,
                deduplicateTracks = true,
                loadTracks = loadTracks,
            )
        ) {
            RadioRequestStartResult.Empty -> {
                state.status = "No tracks found for $statusLabel."
            }
            is RadioRequestStartResult.Failed -> {
                state.status = result.error.message ?: "Could not start $statusLabel."
            }
            is RadioRequestStartResult.Ready -> {
                result.recentRadioStream?.let(rememberRecentRadioStream)
                playTrack(result.firstTrack, result.queue)
            }
        }
    }
}

fun startAndroidHomeStationRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    stationId: String,
    stationTitle: String,
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
) {
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    when (val action = homeStationRadioAction(stationId) ?: return) {
        RecentRadioAction.PlayLibrary -> {
            startAndroidRadioTracks(
                scope,
                state,
                "Library Radio",
                playTrack,
                providerResponseCacheRepository,
                recentRadioStream = libraryRecentRadioStream(),
                rememberRecentRadioStream = rememberRecentRadioStream,
            ) { radioService ->
                radioService.libraryRadio()
            }
        }
        RecentRadioAction.PlayRandomAlbum -> {
            startAndroidRadioTracks(scope, state, "Random Album Radio", playTrack, providerResponseCacheRepository) { radioService ->
                val album = state.homeState.randomAlbums.firstOrNull()
                    ?: state.provider?.let { provider ->
                        providerResponseService?.albumList(provider, AlbumListType.Random, limit = 1)
                            ?: provider.albumList(AlbumListType.Random, limit = 1)
                    }?.firstOrNull()
                album?.let { radioService.albumRadio(it.id) }.orEmpty()
            }
        }
        is RecentRadioAction.PlayGenre -> {
            val genre = action.genre.name
            startAndroidRadioTracks(
                scope,
                state,
                "${genre} Radio",
                playTrack,
                providerResponseCacheRepository,
                recentRadioStream = genreRecentRadioStream(app.naviamp.domain.Genre(genre)),
                rememberRecentRadioStream = rememberRecentRadioStream,
            ) { radioService ->
                radioService.genreRadio(genre)
            }
        }
        is RecentRadioAction.PlayDecade -> {
            startAndroidRadioTracks(
                scope,
                state,
                stationTitle,
                playTrack,
                providerResponseCacheRepository,
                recentRadioStream = decadeRecentRadioStream(action.fromYear, action.toYear),
                rememberRecentRadioStream = rememberRecentRadioStream,
            ) { radioService ->
                radioService.decadeRadio(action.fromYear, action.toYear)
            }
        }
        is RecentRadioAction.PlayArtist,
        is RecentRadioAction.PlayAlbum,
        is RecentRadioAction.PlayTrack,
        -> Unit
    }
}

fun startAndroidArtistRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    artistId: ArtistId,
    artistTitle: String,
    artist: Artist,
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        with(state) {
            status = "Starting $artistTitle radio..."
            when (
                val result = radioSeedResult {
                    RadioService(
                        provider = activeProvider,
                        count = AndroidInitialSimilarRadioCount,
                        providerResponseService = providerResponseService,
                    )
                        .artistSeed(artist, artistDetail?.albums.orEmpty())
                }
            ) {
                RadioSeedResult.Missing -> {
                    status = "$artistTitle radio did not find a seed track."
                }
                is RadioSeedResult.Ready -> {
                    startAndroidSeededRadio(
                        scope = scope,
                        state = state,
                        queueController = queueController,
                        statusLabel = "$artistTitle radio",
                        seedTrack = result.seedTrack,
                        playTrack = playTrack,
                        providerResponseCacheRepository = providerResponseCacheRepository,
                        recentRadioStream = artistRecentRadioStream(artist),
                        rememberRecentRadioStream = rememberRecentRadioStream,
                    ) { radioService ->
                        radioService.artistRadio(artistId)
                    }
                }
                is RadioSeedResult.Failed -> {
                    status = result.error.message ?: "Could not start artist radio."
                }
            }
        }
    }
}

fun startAndroidArtistMixRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    artists: List<Artist>,
    popularTracks: List<Track>,
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
) {
    val distinctArtists = artists.distinctBy { it.id }
    if (distinctArtists.isEmpty()) return
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        with(state) {
            status = "Starting artist mix..."
            when (
                val result = radioSeedResult {
                    popularTracks.shuffled().firstOrNull()
                        ?: RadioService(
                            provider = activeProvider,
                            count = AndroidInitialSimilarRadioCount,
                            providerResponseService = providerResponseService,
                        ).artistSeed(distinctArtists.first(), artistDetail?.albums.orEmpty())
                }
            ) {
                RadioSeedResult.Missing -> {
                    status = "Artist mix did not find a seed track."
                }
                is RadioSeedResult.Ready -> {
                    val request = artistMixSeededRadioRequest(distinctArtists, result.seedTrack, popularTracks.shuffled())
                    startAndroidSeededRadio(
                        scope = scope,
                        state = state,
                        queueController = queueController,
                        statusLabel = request.label,
                        seedTrack = result.seedTrack,
                        playTrack = playTrack,
                        providerResponseCacheRepository = providerResponseCacheRepository,
                        recentRadioStream = request.recentRadioStream,
                        rememberRecentRadioStream = rememberRecentRadioStream,
                        loadRest = request.loadRest,
                    )
                }
                is RadioSeedResult.Failed -> {
                    status = result.error.message ?: "Could not start artist mix."
                }
            }
        }
    }
}

fun startAndroidAlbumMixRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    albums: List<Album>,
    selectedTracks: List<Track>,
    playTrack: (Track, List<Track>) -> Unit,
    libraryIndexRepository: LocalLibraryIndexRepository? = null,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
) {
    val distinctAlbums = albums.distinctBy { it.id }
    if (distinctAlbums.isEmpty()) return
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        with(state) {
            status = "Starting album mix..."
            when (
                val result = radioSeedResult {
                    selectedTracks.shuffled().firstOrNull()
                        ?: distinctAlbums.firstNotNullOfOrNull { album ->
                            selectAlbumRadioSeedTrack(
                                album = album,
                                sourceId = activeSourceId,
                                randomLibraryTrackForAlbum = { sourceId, albumId ->
                                    libraryIndexRepository?.randomLibraryTrackForAlbum(sourceId, albumId)
                                },
                                albumDetails = {
                                    providerResponseService?.album(activeProvider, album.id)
                                        ?: activeProvider.album(album.id)
                                },
                            )
                        }
                }
            ) {
                RadioSeedResult.Missing -> {
                    status = "Album mix did not find a seed track."
                }
                is RadioSeedResult.Ready -> {
                    val request = albumMixSeededRadioRequest(distinctAlbums, result.seedTrack, selectedTracks.shuffled())
                    startAndroidSeededRadio(
                        scope = scope,
                        state = state,
                        queueController = queueController,
                        statusLabel = request.label,
                        seedTrack = result.seedTrack,
                        playTrack = playTrack,
                        providerResponseCacheRepository = providerResponseCacheRepository,
                        recentRadioStream = request.recentRadioStream,
                        rememberRecentRadioStream = rememberRecentRadioStream,
                        loadRest = request.loadRest,
                    )
                }
                is RadioSeedResult.Failed -> {
                    status = result.error.message ?: "Could not start album mix."
                }
            }
        }
    }
}

fun startAndroidGenreMixRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    genres: List<Genre>,
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
) {
    val distinctGenres = genres.distinctBy { it.name.lowercase() }
    if (distinctGenres.isEmpty()) return
    val request = genreMixRadioRequest(distinctGenres)
    startAndroidRadioTracks(
        scope = scope,
        state = state,
        statusLabel = request.label,
        playTrack = playTrack,
        providerResponseCacheRepository = providerResponseCacheRepository,
        recentRadioStream = request.recentRadioStream,
        rememberRecentRadioStream = rememberRecentRadioStream,
    ) { radioService ->
        request.loadTracks(radioService)
    }
}

fun startAndroidPopularTracksRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    artistTitle: String,
    popularTracks: List<Track>,
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
) {
    val seedTrack = popularTracks.shuffled().firstOrNull()
    if (seedTrack == null) {
        state.status = "No popular tracks matched your library."
        return
    }
    startAndroidSeededRadio(
        scope = scope,
        state = state,
        queueController = queueController,
        statusLabel = "$artistTitle popular tracks radio",
        seedTrack = seedTrack,
        playTrack = playTrack,
        providerResponseCacheRepository = providerResponseCacheRepository,
        recentRadioStream = popularTracksRecentRadioStream(seedTrack),
        rememberRecentRadioStream = rememberRecentRadioStream,
    ) { radioService ->
        coroutineScope {
            popularTracks.take(AndroidPopularRadioSeedLimit)
                .map { track -> async(Dispatchers.IO) { radioService.trackRadio(track.id) } }
                .awaitAll()
                .flatten()
        }
    }
}

fun startAndroidTrackRadioQueue(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    track: Track,
    playSeed: Boolean,
    playTrack: (Track, List<Track>) -> Unit,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
) {
    val activeProvider = state.provider ?: return
    if (state.provider?.capabilities?.supportsTrackRadio != true) return
    state.radioQueueActive = true
    state.radioRefilling = true
    state.lastRadioRefillSeedId = track.id
    scope.launch {
        with(state) {
            status = "Starting ${track.title} radio..."
            val request = trackRadioRequest(track)
            when (
                val result = seededRadioBuildResult(
                    request = request,
                    radioService = RadioService(activeProvider, count = AndroidInitialSimilarRadioCount),
                )
            ) {
                is SeededRadioBuildResult.Ready -> {
                    val queue = result.queue
                    result.recentRadioStream?.let(rememberRecentRadioStream)
                    if (playSeed) {
                        playTrack(track, queue)
                    } else {
                        queueController.replaceQueue(PlaybackQueue(tracks = queue, currentIndex = 0))
                        playbackQueue = queueController.queue
                        relatedTracks = queue.drop(1)
                        shuffledUpNextSnapshot = null
                    }
                    status = "Building ${track.title} radio queue..."
                    radioRefilling = false
                    AndroidSimilarRadioExpansionCounts.forEach { count ->
                        if (nowPlaying?.id != track.id) return@launch
                        val expansionResult = seededRadioExpansionResult(
                            request = request,
                            radioService = RadioService(activeProvider, count = count),
                        )
                        if (expansionResult !is SeededRadioExpansionResult.Ready) return@forEach
                        appendAndroidGeneratedRadioTracks(state, queueController, track, expansionResult.fetchedTracks)
                        status = "Building ${track.title} radio queue (${playbackQueue.tracks.size} tracks)..."
                    }
                    if (nowPlaying?.id == track.id) {
                        status = "Track radio loaded."
                    }
                }
                is SeededRadioBuildResult.Failed -> {
                    radioRefilling = false
                    status = result.error.message ?: "Could not start track radio."
                }
            }
        }
    }
}

fun startAndroidAlbumRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    album: Album,
    loadedAlbumTracks: List<Track> = emptyList(),
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
) {
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    val service = state.provider?.let { RadioService(it, providerResponseService = providerResponseService) } ?: return
    scope.launch {
        with(state) {
            status = "Starting ${album.title} radio..."
            when (val result = radioSeedResult { service.albumSeed(album, loadedAlbumTracks) }) {
                RadioSeedResult.Missing -> {
                    status = "${album.title} did not return any tracks."
                }
                is RadioSeedResult.Ready -> {
                    startAndroidSeededRadio(
                        scope = scope,
                        state = state,
                        queueController = queueController,
                        statusLabel = "${album.title} radio",
                        seedTrack = result.seedTrack,
                        playTrack = playTrack,
                        providerResponseCacheRepository = providerResponseCacheRepository,
                        recentRadioStream = albumRecentRadioStream(album),
                        rememberRecentRadioStream = rememberRecentRadioStream,
                    ) { radioService ->
                        radioService.albumRadio(album.id, loadedAlbumTracks)
                    }
                }
                is RadioSeedResult.Failed -> {
                    status = result.error.message ?: "Could not start ${album.title} radio."
                }
            }
        }
    }
}
