package app.naviamp.android

import android.util.Log
import app.naviamp.android.playback.AndroidAutoMediaIdParser
import app.naviamp.android.playback.AndroidAutoPlaybackControls
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.app.NaviampPlaybackCommandController
import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.CatalogPlaybackIntent
import kotlinx.coroutines.CoroutineScope

internal class AndroidAutoAppController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
    private val playbackEngine: AndroidPlaybackEngine,
    private val restorePlaybackSession: (String) -> Boolean,
    private val playTrack: (Track, List<Track>?, Boolean, Double?) -> Unit,
    private val playInternetRadioStation: (InternetRadioStation) -> Unit,
    private val playAdjacentTrack: (Int) -> Unit,
    private val performSeek: (Double) -> Unit,
    private val toggleCurrentFavorite: () -> Unit,
    private val startCurrentTrackRadio: () -> Unit,
    private val savePlaybackSessionThrottled: (Boolean) -> Unit,
) : NaviampPlaybackExecution {
    private val playbackCommands = NaviampPlaybackCommandController(
        execution = this,
        playback = state.sharedLivePlaybackController,
    )

    fun handlePlayPauseCommand(): Boolean =
        playbackCommands.playPause(
            hasPlaybackTarget = state.nowPlaying != null ||
                state.nowPlayingStation != null ||
                state.activeSourceId != null,
        )

    override fun pause() {
        playbackEngine.pause()
    }

    override fun resume() {
        playbackEngine.resume()
    }

    override fun startOrRestore(): Boolean {
        if (state.nowPlaying == null && state.nowPlayingStation == null) {
            state.activeSourceId?.let(restorePlaybackSession)
        }
        state.nowPlayingStation?.let { station ->
            playInternetRadioStation(station)
            return true
        }
        val currentTrack = state.nowPlaying ?: return false
        playTrack(
            currentTrack,
            state.playbackQueue.tracks.takeIf { it.isNotEmpty() },
            false,
            state.restoredStartPositionSeconds,
        )
        state.restoredStartPositionSeconds = null
        return true
    }

    override fun seek(positionSeconds: Double) {
        performSeek(positionSeconds)
    }

    override fun replayCurrent(positionSeconds: Double) {
        startOrRestore()
        performSeek(positionSeconds)
    }

    override fun setVolume(percent: Int) {
        playbackEngine.setVolume(percent)
    }

    override fun stop() {
        savePlaybackSessionThrottled(true)
        playbackEngine.stop()
    }

    fun handleCommand(command: String): Boolean =
        when (command) {
            AndroidAutoPlaybackControls.CommandPlayPause -> handlePlayPauseCommand()
            AndroidAutoPlaybackControls.CommandPrevious -> {
                playAdjacentTrack(-1)
                state.nowPlaying != null
            }
            AndroidAutoPlaybackControls.CommandNext -> {
                playAdjacentTrack(1)
                state.nowPlaying != null
            }
            else -> false
        }.also { handled ->
            Log.i(
                "NaviampAutoCommand",
                "Handled Auto command=$command handled=$handled state=${state.playbackState} nowPlaying=${state.nowPlaying?.title}",
            )
        }

    fun installNotificationControls() {
        AndroidPlaybackNotificationControls.onPlayPause = {
            handlePlayPauseCommand()
        }
        AndroidPlaybackNotificationControls.onPrevious = { playAdjacentTrack(-1) }
        AndroidPlaybackNotificationControls.onNext = { playAdjacentTrack(1) }
        AndroidPlaybackNotificationControls.onToggleFavorite = { toggleCurrentFavorite() }
        AndroidPlaybackNotificationControls.onStartTrackRadio = { startCurrentTrackRadio() }
        AndroidPlaybackNotificationControls.onStop = {
            playbackCommands.stop()
        }
        AndroidPlaybackNotificationControls.onSeekTo = seekHandler@{ positionMillis ->
            val normalizedPositionMillis = normalizeAndroidAutoSeekPositionMillis(
                rawPositionMillis = positionMillis,
                durationSeconds = state.playbackProgress.durationSeconds ?: state.nowPlaying?.durationSeconds?.toDouble(),
            )
            Log.i(
                "NaviampAutoSeek",
                "Auto seek raw=$positionMillis normalized=$normalizedPositionMillis duration=${state.playbackProgress.durationSeconds ?: state.nowPlaying?.durationSeconds?.toDouble()}",
            )
            if (
                normalizedPositionMillis == 0L &&
                (state.playbackProgress.positionSeconds ?: 0.0) > AndroidAutoIgnoreZeroSeekAfterSeconds
            ) {
                Log.i(
                    "NaviampAutoSeek",
                    "Ignoring zero seek while currentPosition=${state.playbackProgress.positionSeconds}",
                )
                return@seekHandler
            }
            performSeek(normalizedPositionMillis / 1_000.0)
        }
    }

    fun playMediaId(mediaId: String): Boolean {
        val sourceId = state.activeSourceId
        val handled = when (val selection = AndroidAutoMediaIdParser.parse(mediaId)) {
            CatalogPlaybackIntent.Resume -> handlePlayPauseCommand()
            CatalogPlaybackIntent.LibraryRadio -> {
                startAndroidRadioTracks(
                    scope = scope,
                    state = state,
                    statusLabel = "Library Radio",
                    playTrack = { track, queue -> playTrack(track, queue, true, null) },
                ) { radioService ->
                    radioService.libraryRadio()
                }
                true
            }
            is CatalogPlaybackIntent.Track -> if (sourceId != null) {
                storage.libraryTrack(sourceId, TrackId(selection.id))?.let { track ->
                    val queue = track.albumId?.let { storage.libraryTracksForAlbum(sourceId, it, 200) }
                        ?.takeIf { tracks -> tracks.any { it.id == track.id } }
                        ?: track.artistId?.let { storage.libraryTracksForArtist(sourceId, it, 200) }
                            ?.takeIf { tracks -> tracks.any { it.id == track.id } }
                    playTrack(track, queue, false, null)
                    true
                } ?: run {
                    state.status = "Track is not available in the local library index."
                    false
                }
            } else false
            is CatalogPlaybackIntent.Download -> if (sourceId != null) {
                val download = storage.downloadedTracks(sourceId).firstOrNull { it.track.id.value == selection.trackId }
                if (download != null) {
                    val queue = storage.downloadedTracks(sourceId).map { it.track }
                    playTrack(download.track, queue, false, null)
                    true
                } else {
                    state.status = "Downloaded track is not available."
                    false
                }
            } else false
            else -> {
                state.status = "Open Naviamp on your phone before starting Android Auto playback."
                false
            }
        }
        Log.i(
            "NaviampAutoCommand",
            "Handled Auto mediaId=$mediaId handled=$handled state=${state.playbackState} nowPlaying=${state.nowPlaying?.title}",
        )
        return handled
    }

    fun consumePendingMediaId(onConsumed: () -> Unit) {
        val mediaId = state.pendingAutoPlayMediaId ?: return
        if (state.activeSourceId == null) return
        if (playMediaId(mediaId)) {
            state.pendingAutoPlayMediaId = null
            onConsumed()
        }
    }

    fun consumePendingCommand(onConsumed: () -> Unit) {
        val command = state.pendingAutoCommand ?: return
        if (state.provider == null || state.activeSourceId == null) return
        if (handleCommand(command)) {
            state.pendingAutoCommand = null
            onConsumed()
        }
    }
}
