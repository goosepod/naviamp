package app.naviamp.desktop

import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.RadioRequest
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.SeededRadioRequest
import app.naviamp.domain.radio.albumSeededRadioRequest
import app.naviamp.domain.radio.artistMixSeededRadioRequest
import app.naviamp.domain.radio.artistSeededRadioRequest
import app.naviamp.domain.radio.decadeRadioRequest
import app.naviamp.domain.radio.genreRadioRequest
import app.naviamp.domain.radio.libraryRadioRequest
import app.naviamp.domain.radio.popularTracksRadioRequest
import app.naviamp.domain.radio.radioRefillSeedTrack
import app.naviamp.domain.radio.randomAlbumSeededRadioRequest
import app.naviamp.domain.radio.shouldFinishRadioRefillForSession
import app.naviamp.domain.radio.trackRadioRequest
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
    private val provider: () -> NavidromeProvider?,
    private val sourceId: () -> String?,
    private val streamQuality: () -> StreamQuality,
    private val replayGainMode: () -> ReplayGainMode,
    private val repeatMode: () -> RepeatMode,
    private val playlistCallbacks: () -> PlaylistCallbacks,
    private val rememberRadioStream: (RecentRadioStream) -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val resetNowPlayingSidecars: () -> Unit,
    private val setConnectionStatus: (String?) -> Unit,
    private val radioSessionId: () -> Int,
    private val setRadioSessionId: (Int) -> Unit,
    private val isRadioQueueActive: () -> Boolean,
    private val setRadioQueueActive: (Boolean) -> Unit,
    private val isRadioRefilling: () -> Boolean,
    private val setRadioRefilling: (Boolean) -> Unit,
    private val lastRadioRefillSeedId: () -> TrackId?,
    private val setLastRadioRefillSeedId: (TrackId?) -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
) {
    fun stopContinuation() {
        setRadioSessionId(radioSessionId() + 1)
        setRadioQueueActive(false)
        setRadioRefilling(false)
        setLastRadioRefillSeedId(null)
    }

    fun refillIfNeeded(queue: PlaybackQueue) {
        val seedTrack = radioRefillSeedTrack(
            queue = queue,
            refillThreshold = RadioRefillThreshold,
            repeatMode = repeatMode(),
            isActive = isRadioQueueActive(),
            isRefilling = isRadioRefilling(),
            lastRefillSeedTrackId = lastRadioRefillSeedId(),
        ) ?: return
        val provider = provider() ?: return

        setRadioRefilling(true)
        setLastRadioRefillSeedId(seedTrack.id)
        val activeRadioSessionId = radioSessionId()
        scope.launch {
            try {
                val fetchedTracks = withContext(Dispatchers.IO) {
                    radioService(provider).trackRadio(seedTrack.id)
                }
                appendGeneratedRadioTracks(
                    playlistEngine = playlistEngine,
                    radioQueueActive = isRadioQueueActive(),
                    radioSession = activeRadioSessionId,
                    currentRadioSession = radioSessionId(),
                    seedTrack = seedTrack,
                    fetchedTracks = fetchedTracks,
                    maxHistory = RadioQueueHistoryLimit,
                )
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not extend radio.")
            } finally {
                if (shouldFinishRadioRefillForSession(activeRadioSessionId, radioSessionId())) {
                    setRadioRefilling(false)
                }
            }
        }
    }

    fun play(request: RadioRequest) {
        val provider = provider() ?: return
        val radioService = radioService(provider)
        setConnectionStatus("Loading ${request.label}...")
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    request.loadTracks(radioService)
                }
                if (tracks.isEmpty()) {
                    setConnectionStatus("${request.label} did not return any tracks.")
                    return@launch
                }
                rememberRadioStream(request.recentRadioStream.withRadioCoverArtIds(tracks))
                setConnectionStatus(null)
                setRadioSessionId(radioSessionId() + 1)
                setRadioQueueActive(true)
                clearShuffleSnapshot()
                setRadioRefilling(false)
                setLastRadioRefillSeedId(null)
                setOpenPlayerOnTrackStart(true)
                playlistEngine.playFrom(
                    scope = scope,
                    provider = provider,
                    tracks = tracks,
                    index = 0,
                    quality = streamQuality(),
                    replayGainMode = replayGainMode(),
                    callbacks = playlistCallbacks(),
                )
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not start ${request.label}.")
            }
        }
    }

    fun playLibrary() {
        play(libraryRadioRequest())
    }

    fun playGenre(genre: Genre) {
        play(genreRadioRequest(genre))
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
        startSeeded(activeProvider, trackRadioRequest(track))
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
                val seedTrack = withContext(Dispatchers.IO) {
                    albumRadioSeedTrack(libraryIndexRepository, providerResponseService, activeProvider, album, sourceId())
                } ?: run {
                    setConnectionStatus("${album.title} did not return any tracks.")
                    return@launch
                }
                startSeeded(activeProvider, randomAlbumSeededRadioRequest(album, seedTrack))
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not start random album radio.")
            }
        }
    }

    fun playArtist(artist: Artist) {
        val activeProvider = provider() ?: return
        setConnectionStatus("Starting ${artist.name} radio...")
        scope.launch {
            try {
                val seedTrack = withContext(Dispatchers.IO) {
                    artistRadioSeedTrack(libraryIndexRepository, providerResponseService, activeProvider, artist, sourceId())
                } ?: run {
                    setConnectionStatus("${artist.name} radio did not find a seed track.")
                    return@launch
                }
                startSeeded(activeProvider, artistSeededRadioRequest(artist, seedTrack))
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not start ${artist.name} radio.")
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
            try {
                val seedTrack = withContext(Dispatchers.IO) {
                    popularTracks.shuffled().firstOrNull()
                        ?: distinctArtists.firstNotNullOfOrNull { artist ->
                            artistRadioSeedTrack(libraryIndexRepository, providerResponseService, activeProvider, artist, sourceId())
                        }
                } ?: run {
                    setConnectionStatus("Artist mix did not find a seed track.")
                    return@launch
                }
                startSeeded(activeProvider, artistMixSeededRadioRequest(distinctArtists, seedTrack, popularTracks.shuffled()))
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not start artist mix.")
            }
        }
    }

    fun playAlbum(album: Album, loadedAlbumTracks: List<Track> = emptyList()) {
        val activeProvider = provider() ?: return
        setConnectionStatus("Starting ${album.title} radio...")
        scope.launch {
            try {
                val seedTrack = withContext(Dispatchers.IO) {
                    albumRadioSeedTrack(
                        libraryIndexRepository = libraryIndexRepository,
                        providerResponseService = providerResponseService,
                        provider = activeProvider,
                        album = album,
                        sourceId = sourceId(),
                        loadedAlbumTracks = loadedAlbumTracks,
                    )
                } ?: run {
                    setConnectionStatus("${album.title} did not return any tracks.")
                    return@launch
                }
                startSeeded(activeProvider, albumSeededRadioRequest(album, seedTrack, loadedAlbumTracks))
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not start ${album.title} radio.")
            }
        }
    }

    fun startSeeded(
        provider: NavidromeProvider,
        request: SeededRadioRequest,
    ) {
        rememberRadioStream(request.recentRadioStream.withRadioCoverArtIds(listOf(request.seedTrack)))
        setConnectionStatus(null)
        setRadioSessionId(radioSessionId() + 1)
        val activeRadioSessionId = radioSessionId()
        setRadioQueueActive(true)
        clearShuffleSnapshot()
        setRadioRefilling(true)
        setLastRadioRefillSeedId(request.seedTrack.id)
        setOpenPlayerOnTrackStart(true)
        resetNowPlayingSidecars()
        playlistEngine.playFrom(
            scope = scope,
            provider = provider,
            tracks = listOf(request.seedTrack),
            index = 0,
            quality = streamQuality(),
            replayGainMode = replayGainMode(),
            callbacks = playlistCallbacks(),
        )

        scope.launch {
            try {
                val fetchedTracks = withContext(Dispatchers.IO) {
                    request.loadRest(radioService(provider, count = InitialSimilarRadioCount))
                }
                rememberRadioStream(request.recentRadioStream.withRadioCoverArtIds(listOf(request.seedTrack) + fetchedTracks))
                appendGeneratedRadioTracks(
                    playlistEngine = playlistEngine,
                    radioQueueActive = isRadioQueueActive(),
                    radioSession = activeRadioSessionId,
                    currentRadioSession = radioSessionId(),
                    seedTrack = request.seedTrack,
                    fetchedTracks = fetchedTracks,
                    maxHistory = RadioQueueHistoryLimit,
                )
                if (activeRadioSessionId == radioSessionId()) {
                    setConnectionStatus("Building ${request.label} queue...")
                }
            } catch (exception: Exception) {
                if (activeRadioSessionId == radioSessionId()) {
                    setConnectionStatus(exception.message ?: "Could not build ${request.label}.")
                }
            }
            if (shouldFinishRadioRefillForSession(activeRadioSessionId, radioSessionId())) {
                setRadioRefilling(false)
            }

            SimilarRadioExpansionCounts.forEach { count ->
                if (!isRadioQueueActive() || activeRadioSessionId != radioSessionId()) return@launch
                val fetchedTracks = runCatching {
                    withContext(Dispatchers.IO) {
                        request.loadRest(radioService(provider, count = count))
                    }
                }.getOrElse {
                    return@forEach
                }
                appendGeneratedRadioTracks(
                    playlistEngine = playlistEngine,
                    radioQueueActive = isRadioQueueActive(),
                    radioSession = activeRadioSessionId,
                    currentRadioSession = radioSessionId(),
                    seedTrack = request.seedTrack,
                    fetchedTracks = fetchedTracks,
                    maxHistory = RadioQueueHistoryLimit,
                )
                if (activeRadioSessionId == radioSessionId()) {
                    setConnectionStatus("Building ${request.label} queue (${playlistEngine.queue.tracks.size} tracks)...")
                }
            }

            if (activeRadioSessionId == radioSessionId()) {
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

        val request = trackRadioRequest(track)
        setConnectionStatus("Building ${track.title} radio...")
        rememberRadioStream(request.recentRadioStream)
        setRadioSessionId(radioSessionId() + 1)
        val activeRadioSessionId = radioSessionId()
        setRadioQueueActive(true)
        setRadioRefilling(true)
        setLastRadioRefillSeedId(track.id)
        scope.launch {
            try {
                val fetchedTracks = withContext(Dispatchers.IO) {
                    radioService(provider, count = InitialSimilarRadioCount).trackRadio(track.id)
                }
                replaceGeneratedRadioUpcomingTracks(
                    playlistEngine = playlistEngine,
                    radioQueueActive = isRadioQueueActive(),
                    radioSession = activeRadioSessionId,
                    currentRadioSession = radioSessionId(),
                    currentTrack = track,
                    fetchedTracks = fetchedTracks,
                    maxHistory = RadioQueueHistoryLimit,
                )
                if (activeRadioSessionId == radioSessionId()) {
                    setConnectionStatus("Building ${track.title} radio queue...")
                }
            } catch (exception: Exception) {
                if (activeRadioSessionId == radioSessionId()) {
                    setConnectionStatus(exception.message ?: "Could not build ${track.title} radio.")
                }
            }
            if (shouldFinishRadioRefillForSession(activeRadioSessionId, radioSessionId())) {
                setRadioRefilling(false)
            }
            SimilarRadioExpansionCounts.forEach { count ->
                if (!isRadioQueueActive() || activeRadioSessionId != radioSessionId()) return@launch
                val fetchedTracks = runCatching {
                    withContext(Dispatchers.IO) {
                        radioService(provider, count = count).trackRadio(track.id)
                    }
                }.getOrElse {
                    return@forEach
                }
                appendGeneratedRadioUpcomingTracks(
                    playlistEngine = playlistEngine,
                    radioQueueActive = isRadioQueueActive(),
                    radioSession = activeRadioSessionId,
                    currentRadioSession = radioSessionId(),
                    currentTrack = track,
                    fetchedTracks = fetchedTracks,
                    maxHistory = RadioQueueHistoryLimit,
                )
                if (activeRadioSessionId == radioSessionId()) {
                    setConnectionStatus("Building ${track.title} radio queue (${playlistEngine.queue.tracks.size} tracks)...")
                }
            }
            if (activeRadioSessionId == radioSessionId()) {
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
            providerResponseService = providerResponseService,
        )

    private companion object {
        const val RadioServiceDefaultCount = 50
    }
}
