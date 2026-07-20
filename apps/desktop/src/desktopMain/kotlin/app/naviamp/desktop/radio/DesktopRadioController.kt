package app.naviamp.desktop

import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampRadioContinuationController
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.RadioRequest
import app.naviamp.domain.radio.RadioRequestStartResult
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.RadioSeedResult
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.domain.radio.SeededRadioRequest
import app.naviamp.domain.radio.SeededRadioBuildResult
import app.naviamp.domain.radio.SeededRadioExpansionResult
import app.naviamp.domain.radio.albumMixSeededRadioRequest
import app.naviamp.domain.radio.albumSeededRadioRequest
import app.naviamp.domain.radio.artistMixSeededRadioRequest
import app.naviamp.domain.radio.artistSeededRadioRequest
import app.naviamp.domain.radio.decadeRadioRequest
import app.naviamp.domain.radio.genreRadioRequest
import app.naviamp.domain.radio.genreMixRadioRequest
import app.naviamp.domain.radio.libraryRadioRequest
import app.naviamp.domain.radio.popularTracksRadioRequest
import app.naviamp.domain.radio.radioRefillSeedTrack
import app.naviamp.domain.radio.radioRequestStartResult
import app.naviamp.domain.radio.radioSeedResult
import app.naviamp.domain.radio.randomAlbumSeededRadioRequest
import app.naviamp.domain.radio.seededRadioBuildResult
import app.naviamp.domain.radio.seededRadioExpansionResult
import app.naviamp.domain.radio.trackRadioRequest
import app.naviamp.domain.radio.TrackRadioLoadResult
import app.naviamp.domain.radio.trackRadioLoadResult
import app.naviamp.domain.radio.trackRadioLoadingStatus
import app.naviamp.domain.radio.trackRadioLoadStatus
import app.naviamp.domain.radio.withRadioCoverArtIds
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopRadioController(
    private val scope: CoroutineScope,
    private val libraryIndexRepository: LocalLibraryIndexRepository,
    private val providerResponseService: ProviderResponseService,
    private val playlistEngine: DesktopPlaylistEngine,
    private val queueCoordinator: NaviampPlaybackQueueCoordinator,
    private val provider: () -> NavidromeProvider?,
    private val sourceId: () -> String?,
    private val streamQuality: () -> StreamQuality,
    private val replayGainMode: () -> ReplayGainMode,
    private val replayGainPreampDb: () -> Float,
    private val preferSonicSimilarity: () -> Boolean,
    private val radioTuning: () -> RadioTuningSettings,
    private val repeatMode: () -> RepeatMode,
    private val playlistCallbacks: () -> PlaylistCallbacks,
    private val rememberRadioStream: (RecentRadioStream) -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val resetNowPlayingSidecars: () -> Unit,
    private val setConnectionStatus: (String?) -> Unit,
    private val continuation: NaviampRadioContinuationController,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
) {
    fun stopContinuation() {
        continuation.stop()
    }

    fun refillIfNeeded(queue: PlaybackQueue) {
        val seedTrack = radioRefillSeedTrack(
            queue = queue,
            refillThreshold = RadioRefillThreshold,
            repeatMode = repeatMode(),
            isActive = continuation.state.active,
            isRefilling = continuation.state.refilling,
            lastRefillSeedTrackId = continuation.state.lastRefillSeedId,
        ) ?: return
        val provider = provider() ?: return

        val activeRadioSessionId = continuation.beginRefill(seedTrack.id)
        scope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    seededRadioExpansionResult(
                        radioService = radioService(provider),
                    ) { radioService ->
                        radioService.trackRadio(seedTrack, preferSonicSimilarity())
                    }
                }
            ) {
                is SeededRadioExpansionResult.Ready -> {
                    playlistEngine.applyQueueUpdate(
                        queueCoordinator.appendGeneratedRadioTracks(
                        seedTrack = seedTrack,
                        fetchedTracks = result.fetchedTracks,
                        requestIsCurrent = continuation.isCurrent(activeRadioSessionId),
                        maxHistory = RadioQueueHistoryLimit,
                        ),
                    )
                }
                is SeededRadioExpansionResult.Failed -> {
                    setConnectionStatus(result.error.message ?: "Could not extend radio.")
                }
            }
            continuation.finishRefill(activeRadioSessionId)
        }
    }

    fun play(request: RadioRequest) {
        val provider = provider() ?: return
        val radioService = radioService(provider)
        setConnectionStatus("Loading ${request.label}...")
        scope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    radioRequestStartResult(request, radioService)
                }
            ) {
                RadioRequestStartResult.Empty -> {
                    setConnectionStatus("${request.label} did not return any tracks.")
                    return@launch
                }
                is RadioRequestStartResult.Failed -> {
                    setConnectionStatus(result.error.message ?: "Could not start ${request.label}.")
                    return@launch
                }
                is RadioRequestStartResult.Ready -> {
                    result.recentRadioStream?.let(rememberRadioStream)
                    val tracks = result.queue
                    setConnectionStatus(null)
                    continuation.start(refilling = false)
                    clearShuffleSnapshot()
                    setOpenPlayerOnTrackStart(true)
                    playlistEngine.playFrom(
                        scope = scope,
                        provider = provider,
                        tracks = tracks,
                        index = 0,
                        quality = streamQuality(),
                        replayGainMode = replayGainMode(),
                        replayGainPreampDb = replayGainPreampDb(),
                        callbacks = playlistCallbacks(),
                    )
                }
            }
        }
    }

    fun playLibrary() {
        play(libraryRadioRequest())
    }

    fun playGenre(genre: Genre) {
        play(genreRadioRequest(genre))
    }

    fun playGenreMix(genres: List<Genre>) {
        val distinctGenres = genres.distinctBy { it.name.lowercase() }
        if (distinctGenres.isEmpty()) return
        play(genreMixRadioRequest(distinctGenres))
    }

    fun playDecade(fromYear: Int, toYear: Int) {
        play(decadeRadioRequest(fromYear, toYear))
    }

    fun playPopularTracks(tracks: List<Track>) {
        val activeProvider = provider() ?: return
        startSeeded(activeProvider, popularTracksRadioRequest(tracks, PopularRadioSeedLimit) ?: return)
    }

    fun playTrack(track: Track) {
        val activeProvider = provider() ?: return
        startSeeded(activeProvider, trackRadioRequest(track, preferSonicSimilarity()))
    }

    fun playTrackRadioNext(track: Track) {
        enqueueTrackRadio(track, insertNext = true)
    }

    fun addTrackRadioToQueue(track: Track) {
        enqueueTrackRadio(track, insertNext = false)
    }

    private fun enqueueTrackRadio(
        track: Track,
        insertNext: Boolean,
    ) {
        val activeProvider = provider() ?: return
        val label = "track radio"
        setConnectionStatus(trackRadioLoadingStatus())
        scope.launch {
            when (val result = withContext(Dispatchers.IO) {
                trackRadioLoadResult(
                    seedTrack = track,
                    radioService = radioService(activeProvider, count = InitialSimilarRadioCount),
                    preferSonicSimilarity = preferSonicSimilarity(),
                )
            }) {
                is TrackRadioLoadResult.Ready -> {
                    val update = if (insertNext) {
                    queueCoordinator.playNextTracks(
                        tracksToAdd = result.tracks,
                        label = label,
                        existingTracks = playlistEngine.queue.tracks,
                        deduplicateExisting = true,
                        maxHistory = RadioQueueHistoryLimit,
                    )
                } else {
                    queueCoordinator.appendTracks(
                        tracksToAdd = result.tracks,
                        label = label,
                        existingTracks = playlistEngine.queue.tracks,
                        deduplicateExisting = true,
                        maxHistory = RadioQueueHistoryLimit,
                    )
                }
                setConnectionStatus(update.status)
                playlistEngine.applyQueueUpdate(update)
                }
                else -> setConnectionStatus(trackRadioLoadStatus(result))
            }
        }
    }

    fun playRandomAlbum() {
        val activeProvider = provider() ?: return
        setConnectionStatus("Starting random album radio...")
        scope.launch {
            try {
                val album = withContext(Dispatchers.IO) {
                    providerResponseService.albumList(activeProvider, AlbumListType.Random, limit = 1).firstOrNull()
                } ?: run {
                    setConnectionStatus("Random album radio did not find an album.")
                    return@launch
                }
                when (
                    val result = withContext(Dispatchers.IO) {
                        radioSeedResult {
                            albumRadioSeedTrack(libraryIndexRepository, providerResponseService, activeProvider, album, sourceId())
                        }
                    }
                ) {
                    RadioSeedResult.Missing -> {
                        setConnectionStatus("${album.title} did not return any tracks.")
                        return@launch
                    }
                    is RadioSeedResult.Ready -> {
                        startSeeded(activeProvider, randomAlbumSeededRadioRequest(album, result.seedTrack))
                    }
                    is RadioSeedResult.Failed -> {
                        setConnectionStatus(result.error.message ?: "Could not start random album radio.")
                    }
                }
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not start random album radio.")
            }
        }
    }

    fun playArtist(artist: Artist) {
        val activeProvider = provider() ?: return
        setConnectionStatus("Starting ${artist.name} radio...")
        scope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    radioSeedResult {
                        artistRadioSeedTrack(libraryIndexRepository, providerResponseService, activeProvider, artist, sourceId())
                    }
                }
            ) {
                RadioSeedResult.Missing -> {
                    setConnectionStatus("${artist.name} radio did not find a seed track.")
                    return@launch
                }
                is RadioSeedResult.Ready -> {
                    startSeeded(activeProvider, artistSeededRadioRequest(artist, result.seedTrack))
                }
                is RadioSeedResult.Failed -> {
                    setConnectionStatus(result.error.message ?: "Could not start ${artist.name} radio.")
                }
            }
        }
    }

    fun playArtistMix(
        artists: List<Artist>,
        popularTracks: List<Track>,
    ) {
        val activeProvider = provider() ?: return
        val distinctArtists = artists.distinctBy { it.id }
        if (distinctArtists.isEmpty()) return
        setConnectionStatus("Starting artist mix...")
        scope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    radioSeedResult {
                        popularTracks.shuffled().firstOrNull()
                            ?: distinctArtists.firstNotNullOfOrNull { artist ->
                                artistRadioSeedTrack(libraryIndexRepository, providerResponseService, activeProvider, artist, sourceId())
                            }
                    }
                }
            ) {
                RadioSeedResult.Missing -> {
                    setConnectionStatus("Artist mix did not find a seed track.")
                    return@launch
                }
                is RadioSeedResult.Ready -> {
                    startSeeded(activeProvider, artistMixSeededRadioRequest(distinctArtists, result.seedTrack, popularTracks.shuffled()))
                }
                is RadioSeedResult.Failed -> {
                    setConnectionStatus(result.error.message ?: "Could not start artist mix.")
                }
            }
        }
    }

    fun playAlbum(album: Album, loadedAlbumTracks: List<Track> = emptyList()) {
        val activeProvider = provider() ?: return
        setConnectionStatus("Starting ${album.title} radio...")
        scope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    radioSeedResult {
                        albumRadioSeedTrack(
                            libraryIndexRepository = libraryIndexRepository,
                            providerResponseService = providerResponseService,
                            provider = activeProvider,
                            album = album,
                            sourceId = sourceId(),
                            loadedAlbumTracks = loadedAlbumTracks,
                        )
                    }
                }
            ) {
                RadioSeedResult.Missing -> {
                    setConnectionStatus("${album.title} did not return any tracks.")
                    return@launch
                }
                is RadioSeedResult.Ready -> {
                    startSeeded(activeProvider, albumSeededRadioRequest(album, result.seedTrack, loadedAlbumTracks))
                }
                is RadioSeedResult.Failed -> {
                    setConnectionStatus(result.error.message ?: "Could not start ${album.title} radio.")
                }
            }
        }
    }

    fun playAlbumMix(
        albums: List<Album>,
        selectedTracks: List<Track>,
    ) {
        val activeProvider = provider() ?: return
        val distinctAlbums = albums.distinctBy { it.id }
        if (distinctAlbums.isEmpty()) return
        setConnectionStatus("Starting album mix...")
        scope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    radioSeedResult {
                        selectedTracks.shuffled().firstOrNull()
                            ?: distinctAlbums.firstNotNullOfOrNull { album ->
                                albumRadioSeedTrack(
                                    libraryIndexRepository = libraryIndexRepository,
                                    providerResponseService = providerResponseService,
                                    provider = activeProvider,
                                    album = album,
                                    sourceId = sourceId(),
                                )
                            }
                    }
                }
            ) {
                RadioSeedResult.Missing -> {
                    setConnectionStatus("Album mix did not find a seed track.")
                    return@launch
                }
                is RadioSeedResult.Ready -> {
                    startSeeded(activeProvider, albumMixSeededRadioRequest(distinctAlbums, result.seedTrack, selectedTracks.shuffled()))
                }
                is RadioSeedResult.Failed -> {
                    setConnectionStatus(result.error.message ?: "Could not start album mix.")
                }
            }
        }
    }

    fun startSeeded(
        provider: NavidromeProvider,
        request: SeededRadioRequest,
    ) {
        rememberRadioStream(request.recentRadioStream.withRadioCoverArtIds(listOf(request.seedTrack)))
        setConnectionStatus(null)
        val activeRadioSessionId = continuation.start(request.seedTrack.id, refilling = true)
        clearShuffleSnapshot()
        setOpenPlayerOnTrackStart(true)
        resetNowPlayingSidecars()
        playlistEngine.playFrom(
            scope = scope,
            provider = provider,
            tracks = listOf(request.seedTrack),
            index = 0,
            quality = streamQuality(),
            replayGainMode = replayGainMode(),
            replayGainPreampDb = replayGainPreampDb(),
            callbacks = playlistCallbacks(),
        )

        scope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    seededRadioBuildResult(request, radioService(provider, count = InitialSimilarRadioCount))
                }
            ) {
                is SeededRadioBuildResult.Ready -> {
                    result.recentRadioStream?.let(rememberRadioStream)
                    playlistEngine.applyQueueUpdate(
                        queueCoordinator.appendGeneratedRadioTracks(
                        seedTrack = request.seedTrack,
                        fetchedTracks = result.queue.drop(1),
                        requestIsCurrent = continuation.isCurrent(activeRadioSessionId),
                        maxHistory = RadioQueueHistoryLimit,
                        ),
                    )
                    if (continuation.isCurrent(activeRadioSessionId)) {
                        setConnectionStatus("Building ${request.label} queue...")
                    }
                }
                is SeededRadioBuildResult.Failed -> {
                    if (continuation.isCurrent(activeRadioSessionId)) {
                        setConnectionStatus(result.error.message ?: "Could not build ${request.label}.")
                    }
                }
            }
            continuation.finishRefill(activeRadioSessionId)

            SimilarRadioExpansionCounts.forEach { count ->
                if (!continuation.isCurrent(activeRadioSessionId)) return@launch
                val result = withContext(Dispatchers.IO) {
                    seededRadioExpansionResult(request, radioService(provider, count = count))
                }
                if (result !is SeededRadioExpansionResult.Ready) return@forEach
                playlistEngine.applyQueueUpdate(
                    queueCoordinator.appendGeneratedRadioTracks(
                    seedTrack = request.seedTrack,
                    fetchedTracks = result.fetchedTracks,
                    requestIsCurrent = continuation.isCurrent(activeRadioSessionId),
                    maxHistory = RadioQueueHistoryLimit,
                    ),
                )
                if (continuation.isCurrent(activeRadioSessionId)) {
                    setConnectionStatus("Building ${request.label} queue (${playlistEngine.queue.tracks.size} tracks)...")
                }
            }

            if (continuation.isCurrent(activeRadioSessionId)) {
                setConnectionStatus(null)
            }
        }
    }

    fun convertCurrentTrackToRadio(
        track: Track,
        playTrackRadio: (Track) -> Unit,
    ) {
        val provider = provider() ?: return
        val currentTrack = playlistEngine.queue.tracks.getOrNull(playlistEngine.queue.currentIndex)
        if (currentTrack?.id != track.id) {
            playTrackRadio(track)
            return
        }

        val request = trackRadioRequest(track, preferSonicSimilarity())
        setConnectionStatus("Building ${track.title} radio...")
        rememberRadioStream(request.recentRadioStream)
        val activeRadioSessionId = continuation.start(track.id, refilling = true)
        scope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    seededRadioBuildResult(request, radioService(provider, count = InitialSimilarRadioCount))
                }
            ) {
                is SeededRadioBuildResult.Ready -> {
                    result.recentRadioStream?.let(rememberRadioStream)
                    playlistEngine.applyQueueMutation(
                        queueCoordinator.replaceGeneratedRadioUpcomingTracks(
                        currentTrack = track,
                        fetchedTracks = result.queue.drop(1),
                        requestIsCurrent = continuation.isCurrent(activeRadioSessionId),
                        maxHistory = RadioQueueHistoryLimit,
                        ),
                    )
                    if (continuation.isCurrent(activeRadioSessionId)) {
                        setConnectionStatus("Building ${track.title} radio queue...")
                    }
                }
                is SeededRadioBuildResult.Failed -> {
                    if (continuation.isCurrent(activeRadioSessionId)) {
                        setConnectionStatus(result.error.message ?: "Could not build ${track.title} radio.")
                    }
                }
            }
            continuation.finishRefill(activeRadioSessionId)
            SimilarRadioExpansionCounts.forEach { count ->
                if (!continuation.isCurrent(activeRadioSessionId)) return@launch
                val result = withContext(Dispatchers.IO) {
                    seededRadioExpansionResult(request, radioService(provider, count = count))
                }
                if (result !is SeededRadioExpansionResult.Ready) return@forEach
                playlistEngine.applyQueueUpdate(
                    queueCoordinator.appendGeneratedRadioUpcomingTracks(
                    currentTrack = track,
                    fetchedTracks = result.fetchedTracks,
                    requestIsCurrent = continuation.isCurrent(activeRadioSessionId),
                    maxHistory = RadioQueueHistoryLimit,
                    ),
                )
                if (continuation.isCurrent(activeRadioSessionId)) {
                    setConnectionStatus("Building ${track.title} radio queue (${playlistEngine.queue.tracks.size} tracks)...")
                }
            }
            if (continuation.isCurrent(activeRadioSessionId)) {
                setConnectionStatus(null)
            }
        }
    }

    private fun radioService(
        provider: NavidromeProvider,
        count: Int = RadioServiceDefaultCount,
    ): RadioService =
        RadioService(
            provider = provider,
            count = count,
            tuning = radioTuning(),
            providerResponseService = providerResponseService,
        )

    private companion object {
        const val RadioServiceDefaultCount = 50
    }
}
