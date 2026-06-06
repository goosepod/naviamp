package app.naviamp.android

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.home.HomeStationLibrary
import app.naviamp.domain.home.HomeStationRandomAlbum
import app.naviamp.domain.home.parseHomeDecadeStationId
import app.naviamp.domain.home.parseHomeGenreStationId
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.artistMixSeededRadioRequest
import app.naviamp.domain.radio.albumRecentRadioStream
import app.naviamp.domain.radio.artistRecentRadioStream
import app.naviamp.domain.radio.decadeRecentRadioStream
import app.naviamp.domain.radio.generatedRadioTracksToAppend
import app.naviamp.domain.radio.generatedRadioQueue
import app.naviamp.domain.radio.genreRecentRadioStream
import app.naviamp.domain.radio.libraryRecentRadioStream
import app.naviamp.domain.radio.popularTracksRecentRadioStream
import app.naviamp.domain.radio.radioRefillSeedTrack
import app.naviamp.domain.radio.trackRecentRadioStream
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
            val fetchedTracks = withContext(Dispatchers.IO) {
                RadioService(activeProvider, count = AndroidRadioRefillCount).trackRadio(seedTrack.id)
            }
            val newTracks = generatedRadioTracksToAppend(
                seedTrack = seedTrack,
                fetchedTracks = fetchedTracks,
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
        } catch (error: Exception) {
            state.status = error.message ?: "Could not extend radio."
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
            runCatching {
                loadRest(
                    RadioService(
                        provider = activeProvider,
                        count = AndroidInitialSimilarRadioCount,
                        providerResponseService = providerResponseService,
                    ),
                )
            }
                .onSuccess { fetchedTracks ->
                    val queue = generatedRadioQueue(seedTrack, fetchedTracks)
                        .ifEmpty { seedQueue }
                    recentRadioStream?.withRadioCoverArtIds(queue)?.let(rememberRecentRadioStream)
                    if (nowPlaying?.id == seedTrack.id) {
                        playbackQueue = PlaybackQueue(tracks = queue, currentIndex = 0)
                    }
                    status = "Building $statusLabel queue..."
                }
                .onFailure { error ->
                    if (nowPlaying?.id == seedTrack.id) {
                        status = error.message ?: "Could not build $statusLabel."
                    }
                }

            radioRefilling = false
            AndroidSimilarRadioExpansionCounts.forEach { count ->
                if (nowPlaying?.id != seedTrack.id) return@launch
                val fetchedTracks = runCatching {
                    loadRest(
                        RadioService(
                            provider = activeProvider,
                            count = count,
                            providerResponseService = providerResponseService,
                        ),
                    )
                }.getOrElse {
                    return@forEach
                }
                appendAndroidGeneratedRadioTracks(state, queueController, seedTrack, fetchedTracks)
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
        runCatching { loadTracks(service) }
            .onSuccess { radioTracks ->
                val queue = radioTracks.distinctBy { it.id }
                val firstTrack = queue.firstOrNull()
                if (firstTrack == null) {
                    state.status = "No tracks found for $statusLabel."
                } else {
                    recentRadioStream?.withRadioCoverArtIds(queue)?.let(rememberRecentRadioStream)
                    playTrack(firstTrack, queue)
                }
            }
            .onFailure { error -> state.status = error.message ?: "Could not start $statusLabel." }
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
    when {
        stationId == HomeStationLibrary -> {
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
        stationId == HomeStationRandomAlbum -> {
            startAndroidRadioTracks(scope, state, "Random Album Radio", playTrack, providerResponseCacheRepository) { radioService ->
                val album = state.homeState.randomAlbums.firstOrNull()
                    ?: state.provider?.let { provider ->
                        providerResponseService?.albumList(provider, AlbumListType.Random, limit = 1)
                            ?: provider.albumList(AlbumListType.Random, limit = 1)
                    }?.firstOrNull()
                album?.let { radioService.albumRadio(it.id) }.orEmpty()
            }
        }
        parseHomeGenreStationId(stationId) != null -> {
            val genre = parseHomeGenreStationId(stationId).orEmpty()
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
        parseHomeDecadeStationId(stationId) != null -> {
            val decade = parseHomeDecadeStationId(stationId)
            if (decade != null) {
                startAndroidRadioTracks(
                    scope,
                    state,
                    stationTitle,
                    playTrack,
                    providerResponseCacheRepository,
                    recentRadioStream = decadeRecentRadioStream(decade.fromYear, decade.toYear),
                    rememberRecentRadioStream = rememberRecentRadioStream,
                ) { radioService ->
                    radioService.decadeRadio(decade.fromYear, decade.toYear)
                }
            }
        }
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
            runCatching {
                RadioService(
                    provider = activeProvider,
                    count = AndroidInitialSimilarRadioCount,
                    providerResponseService = providerResponseService,
                )
                    .artistSeed(artist, artistDetail?.albums.orEmpty())
            }.onSuccess { seedTrack ->
                if (seedTrack == null) {
                    status = "$artistTitle radio did not find a seed track."
                } else {
                    startAndroidSeededRadio(
                        scope = scope,
                        state = state,
                        queueController = queueController,
                        statusLabel = "$artistTitle radio",
                        seedTrack = seedTrack,
                        playTrack = playTrack,
                        providerResponseCacheRepository = providerResponseCacheRepository,
                        recentRadioStream = artistRecentRadioStream(artist),
                        rememberRecentRadioStream = rememberRecentRadioStream,
                    ) { radioService ->
                        radioService.artistRadio(artistId)
                    }
                }
            }.onFailure { error ->
                status = error.message ?: "Could not start artist radio."
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
            runCatching {
                popularTracks.shuffled().firstOrNull()
                    ?: RadioService(
                        provider = activeProvider,
                        count = AndroidInitialSimilarRadioCount,
                        providerResponseService = providerResponseService,
                    ).artistSeed(distinctArtists.first(), artistDetail?.albums.orEmpty())
            }.onSuccess { seedTrack ->
                if (seedTrack == null) {
                    status = "Artist mix did not find a seed track."
                } else {
                    val request = artistMixSeededRadioRequest(distinctArtists, seedTrack, popularTracks.shuffled())
                    startAndroidSeededRadio(
                        scope = scope,
                        state = state,
                        queueController = queueController,
                        statusLabel = request.label,
                        seedTrack = seedTrack,
                        playTrack = playTrack,
                        providerResponseCacheRepository = providerResponseCacheRepository,
                        recentRadioStream = request.recentRadioStream,
                        rememberRecentRadioStream = rememberRecentRadioStream,
                        loadRest = request.loadRest,
                    )
                }
            }.onFailure { error ->
                status = error.message ?: "Could not start artist mix."
            }
        }
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
            runCatching { RadioService(activeProvider, count = AndroidInitialSimilarRadioCount).trackRadio(track.id) }
                .onSuccess { radioTracks ->
                    val queue = generatedRadioQueue(track, radioTracks)
                    rememberRecentRadioStream(trackRecentRadioStream(track).withRadioCoverArtIds(queue))
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
                        val fetchedTracks = runCatching {
                            RadioService(activeProvider, count = count).trackRadio(track.id)
                        }.getOrElse {
                            return@forEach
                        }
                        appendAndroidGeneratedRadioTracks(state, queueController, track, fetchedTracks)
                        status = "Building ${track.title} radio queue (${playbackQueue.tracks.size} tracks)..."
                    }
                    if (nowPlaying?.id == track.id) {
                        status = "Track radio loaded."
                    }
                }
                .onFailure { error ->
                    radioRefilling = false
                    status = error.message ?: "Could not start track radio."
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
            val seedTrack = service.albumSeed(album, loadedAlbumTracks)
            if (seedTrack == null) {
                status = "${album.title} did not return any tracks."
            } else {
                startAndroidSeededRadio(
                    scope = scope,
                    state = state,
                    queueController = queueController,
                    statusLabel = "${album.title} radio",
                    seedTrack = seedTrack,
                    playTrack = playTrack,
                    providerResponseCacheRepository = providerResponseCacheRepository,
                    recentRadioStream = albumRecentRadioStream(album),
                    rememberRecentRadioStream = rememberRecentRadioStream,
                ) { radioService ->
                    radioService.albumRadio(album.id, loadedAlbumTracks)
                }
            }
        }
    }
}
