package app.naviamp.presentation

import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampExternalPlaybackAction
import app.naviamp.ui.NowPlayingCurrentTrackTarget
import app.naviamp.ui.NaviampMediaItemActionRequest
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampPlaylistDetailCommand
import app.naviamp.ui.NaviampPlaylistMediaCommand
import app.naviamp.ui.NaviampRepeatMode
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingCurrentTrackUiActionRequest
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingSelectionAction
import app.naviamp.ui.NowPlayingSelectionActionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi

enum class NaviampExternalPlaybackState {
    Idle,
    Loading,
    Playing,
    Paused,
}

/** The car catalog is independent of playback and of Home's recent-station subset. */
data class NaviampExternalBrowseCatalog(
    val playlists: List<SharedMediaItemUi> = emptyList(),
    val recentTracks: List<SharedTrackRowUi> = emptyList(),
    val recentAlbums: List<SharedMediaItemUi> = emptyList(),
    val internetRadio: List<SharedMediaItemUi> = emptyList(),
    val stations: List<SharedHomeStationUi> = emptyList(),
)

private fun NaviampAppShellUiState.externalBrowseCatalog() = NaviampExternalBrowseCatalog(
    playlists = playlists.playlists.ifEmpty { home.content.playlists },
    recentTracks = home.content.recentlyPlayedTracks,
    recentAlbums = home.content.recentAlbums,
    internetRadio = radio.stations.map { it.item },
    // Keep one immediate library-wide choice; station builders belong on the phone.
    stations = home.content.stations.filter { it.id == app.naviamp.domain.home.HomeStationLibrary },
)

data class NaviampExternalMediaItem(
    val mediaId: String,
    val title: String,
    val subtitle: String,
    val description: String = "",
    val albumTitle: String = "",
    val artworkUrl: String? = null,
    val playable: Boolean = true,
    val queueIndex: Int? = null,
    val mediaKind: app.naviamp.ui.SharedMediaItemKind = app.naviamp.ui.SharedMediaItemKind.Unknown,
    /** Private local copy of [artworkUrl] for native integrations that accept only a URI. */
    val localArtworkUri: String? = null,
) {
    /**
     * Artwork URI that may leave the process. Provider URLs can carry credentials (for example
     * Subsonic token and salt parameters), so only local files are ever exposed; synthetic in-app
     * schemes cannot be loaded by native consumers at all.
     */
    val externalArtworkUrl: String?
        get() = localArtworkUri ?: artworkUrl?.takeIf(::isLocalArtworkUri)
}

internal fun isLocalArtworkUri(url: String): Boolean = url.startsWith("file://", ignoreCase = true)

internal fun isRemoteArtworkUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

/** Runtime-only eligibility token, shared by phone, car and native interruption callbacks. */
data class NaviampExternalPlaybackResumeIdentity(
    val sourceId: String?, val sourceGeneration: Long, val accountGeneration: Long,
    val selectionGeneration: Long, val intentGeneration: Long, val mediaId: String?,
    val queueIndex: Int, val sleepTimerActive: Boolean,
)

data class NaviampExternalPlaybackSnapshot(
    val state: NaviampExternalPlaybackState = NaviampExternalPlaybackState.Idle,
    val current: NaviampExternalMediaItem? = null,
    val queue: List<NaviampExternalMediaItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val positionMillis: Long? = null,
    val seekGeneration: Long = 0L,
    val durationMillis: Long? = null,
    val canPlayPause: Boolean = false,
    val canSeek: Boolean = false,
    val canChangeVolume: Boolean = false,
    val volumePercent: Int = 100,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val favorite: Boolean = false,
    val canFavorite: Boolean = false,
    val shuffleActive: Boolean = false,
    val repeatMode: NaviampRepeatMode = NaviampRepeatMode.Off,
    val browseCatalog: NaviampExternalBrowseCatalog = NaviampExternalBrowseCatalog(),
    val canStartRadio: Boolean = false,
    val canShuffle: Boolean = false,
    val canRepeat: Boolean = false,
    val isLive: Boolean = false,
    val resumeIdentity: NaviampExternalPlaybackResumeIdentity? = null,
    val localPlayback: Boolean = true,
    val connectionPending: Boolean = false,
) {
    /** Keep custom controls together when previous/next become unavailable at queue boundaries. */
    val reserveTransportSlots: Boolean
        get() = current != null && !isLive

    /** Priority is product policy; compact car layouts may move later actions into overflow. */
    val customActions: List<NaviampExternalPlaybackAction>
        get() = buildList {
            if (canStartRadio) add(NaviampExternalPlaybackAction.StartRadio)
            if (canFavorite) add(if (favorite) NaviampExternalPlaybackAction.Unfavorite else NaviampExternalPlaybackAction.Favorite)
            if (canShuffle) add(if (shuffleActive) NaviampExternalPlaybackAction.ShuffleOn else NaviampExternalPlaybackAction.ShuffleOff)
            if (canRepeat) add(when (repeatMode) {
                NaviampRepeatMode.Off -> NaviampExternalPlaybackAction.RepeatOff
                NaviampRepeatMode.Queue -> NaviampExternalPlaybackAction.RepeatQueue
                NaviampRepeatMode.Track -> NaviampExternalPlaybackAction.RepeatTrack
            })
        }

    val transportActions: Set<NaviampExternalTransportAction>
        get() = buildSet {
            add(NaviampExternalTransportAction.PlayFromMediaId)
            add(NaviampExternalTransportAction.PlayFromSearch)
            if (canPlayPause) add(NaviampExternalTransportAction.PlayPause)
            if (canControl) add(NaviampExternalTransportAction.Stop)
            if (canSeek) add(NaviampExternalTransportAction.Seek)
            if (hasPrevious) add(NaviampExternalTransportAction.Previous)
            if (hasNext) add(NaviampExternalTransportAction.Next)
            if (queue.isNotEmpty()) add(NaviampExternalTransportAction.SelectQueueItem)
            if (canShuffle) add(NaviampExternalTransportAction.Shuffle)
            if (canRepeat) add(NaviampExternalTransportAction.Repeat)
        }
    val canControl: Boolean
        get() = canPlayPause || canSeek || canChangeVolume || hasPrevious || hasNext

    val shouldRetainPlaybackService: Boolean
        get() = current != null && state != NaviampExternalPlaybackState.Idle
}

enum class NaviampExternalTransportAction {
    PlayFromMediaId, PlayFromSearch, PlayPause, Stop, Seek, Previous, Next, SelectQueueItem, Shuffle, Repeat,
}

/**
 * Shared lifecycle policy for retaining a native playback surface.
 *
 * Core playback can briefly project an idle state while advancing between queue items. Releasing a
 * native playback service during that transition both churns the host lifecycle and can make the
 * subsequent background restart illegal on modern mobile operating systems. Retain immediately,
 * but require a stable idle state before asking a host to release its native surface.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<NaviampExternalPlaybackSnapshot>.playbackServiceRetentionDecisions(
    releaseGracePeriodMillis: Long = 1_000L,
): Flow<Boolean> =
    transformLatest { snapshot ->
        if (snapshot.shouldRetainPlaybackService) {
            emit(true)
        } else {
            delay(releaseGracePeriodMillis)
            emit(false)
        }
    }.distinctUntilChanged()

data class NaviampExternalPlaybackPublication(
    val sessionContent: Boolean,
    val playbackState: Boolean,
    val browseCatalog: Boolean,
    val notification: Boolean,
    val seekedPositionMillis: Long? = null,
)

/**
 * Shared policy for publishing Core playback state to comparatively expensive native surfaces.
 * A playing MediaSession extrapolates position from its timestamp, so hosts do not need to rebuild
 * metadata, queues, notifications, and browse trees for every Core progress tick.
 */
class NaviampExternalPlaybackPublicationPlanner(
    private val maximumNaturalPositionStepMillis: Long = 2_500L,
) {
    private var previous: NaviampExternalPlaybackSnapshot? = null

    fun reset() {
        previous = null
    }

    fun plan(next: NaviampExternalPlaybackSnapshot): NaviampExternalPlaybackPublication {
        val prior = previous
        val sourceChanged = prior != null && (prior.sourceScope() != next.sourceScope() || prior.localPlayback != next.localPlayback)
        val sessionContent = prior == null || sourceChanged ||
            prior.current != next.current ||
            prior.queue != next.queue ||
            prior.durationMillis != next.durationMillis
        val semanticPlaybackChange = prior == null || sourceChanged ||
            prior.state != next.state ||
            prior.currentQueueIndex != next.currentQueueIndex ||
            prior.canPlayPause != next.canPlayPause ||
            prior.canSeek != next.canSeek ||
            prior.canChangeVolume != next.canChangeVolume ||
            prior.volumePercent != next.volumePercent ||
            prior.hasPrevious != next.hasPrevious ||
            prior.hasNext != next.hasNext ||
            prior.favorite != next.favorite ||
            prior.canFavorite != next.canFavorite ||
            prior.canStartRadio != next.canStartRadio ||
            prior.canShuffle != next.canShuffle ||
            prior.canRepeat != next.canRepeat ||
            prior.reserveTransportSlots != next.reserveTransportSlots ||
            prior.shuffleActive != next.shuffleActive ||
            prior.repeatMode != next.repeatMode
        val positionDiscontinuity = when {
            prior == null -> false
            prior.current?.mediaId != next.current?.mediaId -> false
            prior.seekGeneration != next.seekGeneration -> true
            prior.positionMillis == null || next.positionMillis == null -> prior.positionMillis != next.positionMillis
            next.positionMillis < prior.positionMillis -> true
            prior.state != NaviampExternalPlaybackState.Playing && next.state == prior.state ->
                next.positionMillis != prior.positionMillis
            else -> next.positionMillis - prior.positionMillis > maximumNaturalPositionStepMillis
        }
        val playbackState = semanticPlaybackChange || positionDiscontinuity
        val browseCatalog = prior == null || sourceChanged ||
            prior.browseCatalog != next.browseCatalog ||
            prior.current?.mediaId != next.current?.mediaId ||
            prior.queue != next.queue
        val notification = prior == null || sourceChanged ||
            prior.current != next.current ||
            prior.state != next.state ||
            prior.favorite != next.favorite

        previous = next
        return NaviampExternalPlaybackPublication(
            sessionContent = sessionContent,
            playbackState = playbackState,
            browseCatalog = browseCatalog,
            notification = notification,
            seekedPositionMillis = next.positionMillis.takeIf { positionDiscontinuity },
        )
    }
}

/**
 * Host-neutral policy for native audio-session lifecycle events.
 *
 * Hosts translate their operating-system callbacks into these methods; Core alone decides whether
 * playback pauses or resumes. An interruption only resumes playback when it interrupted an active
 * track and the operating system explicitly permits resumption. Disconnecting an output pauses
 * active playback and cancels any pending automatic interruption resume.
 */
class NaviampExternalPlaybackLifecycleCoordinator(
    private val snapshot: () -> NaviampExternalPlaybackSnapshot,
    private val play: () -> Unit,
    private val pause: () -> Unit,
    private val stop: () -> Unit = pause,
) {
    private data class ResumeTarget(
        val mediaId: String,
        val queueIndex: Int,
        val identity: NaviampExternalPlaybackResumeIdentity?,
    )

    private val interruption = app.naviamp.domain.playback.PlaybackInterruptionPolicy()
    private var interrupted = false
    private var target: ResumeTarget? = null
    private var resumePending = false
    private var closed = false

    fun sessionBecameInactive(
        source: NaviampAudioSessionDeactivationSource,
        reason: NaviampAudioSessionInterruptionReason?,
    ) {
        when {
            source == NaviampAudioSessionDeactivationSource.Application -> cancelPendingResume()
            source == NaviampAudioSessionDeactivationSource.Unknown ||
                reason == NaviampAudioSessionInterruptionReason.OutputDisconnected -> outputDisconnected()
            else -> interruptionBegan()
        }
    }

    fun interruptionBegan() {
        if (closed) return
        val current = snapshot()
        if (interrupted || resumePending) {
            interrupted = true
            if (resumePending) {
                resumePending = false
                interruption.began(wasPlaying = true)
            }
            onSnapshot(current)
            return
        }
        interrupted = true
        val track = current.current ?: return
        if (!current.localPlayback || current.connectionPending) return
        if (current.state == NaviampExternalPlaybackState.Loading) {
            stop()
            return
        }
        if (!current.canPlayPause || !interruption.began(current.state == NaviampExternalPlaybackState.Playing)) return

        pause()
        val afterPause = snapshot()
        // Our pause dispatch records a new intent synchronously. Retain every other guard from
        // the interrupted playback so a new source or queue occurrence cannot inherit the resume.
        val identity = current.resumeIdentity?.let { before ->
            before.copy(intentGeneration = afterPause.resumeIdentity?.intentGeneration ?: before.intentGeneration)
        }
        val candidate = ResumeTarget(track.mediaId, current.currentQueueIndex, identity)
        if (afterPause.canResume(candidate)) target = candidate else cancelPendingResume()
    }

    fun interruptionEnded(shouldResume: Boolean) {
        if (closed) return
        if (!shouldResume) {
            interrupted = false
            cancelPendingResume()
            return
        }
        if (!interrupted) return
        interrupted = false
        val current = snapshot()
        val eligible = target?.let { current.canResume(it) } == true
        if (!interruption.ended(eligible)) {
            cancelPendingResume()
            return
        }
        resumePending = true
        onSnapshot(current)
    }

    internal fun onSnapshot(current: NaviampExternalPlaybackSnapshot) {
        val candidate = target ?: return
        if (!current.canResume(candidate)) {
            cancelPendingResume()
        } else if (resumePending && current.state == NaviampExternalPlaybackState.Paused) {
            val latest = snapshot()
            if (!latest.canResume(candidate)) {
                cancelPendingResume()
            } else if (latest.state == NaviampExternalPlaybackState.Paused) {
                cancelPendingResume()
                play()
            }
        }
    }

    fun outputDisconnected() {
        if (closed) return
        interrupted = false
        cancelPendingResume()
        val current = snapshot()
        if (!current.localPlayback) return
        when (current.state) {
            NaviampExternalPlaybackState.Playing, NaviampExternalPlaybackState.Paused -> pause()
            NaviampExternalPlaybackState.Loading -> stop()
            else -> Unit
        }
    }

    fun cancelPendingResume() {
        interruption.cancel()
        target = null
        resumePending = false
    }

    fun close() {
        closed = true
        interrupted = false
        cancelPendingResume()
    }

    private fun NaviampExternalPlaybackSnapshot.canResume(candidate: ResumeTarget): Boolean =
        localPlayback && !connectionPending && canPlayPause &&
            (state == NaviampExternalPlaybackState.Playing || state == NaviampExternalPlaybackState.Paused) &&
            current?.mediaId == candidate.mediaId && currentQueueIndex == candidate.queueIndex &&
            resumeIdentity == candidate.identity
}

/**
 * Host-neutral projection used by lock screens, media sessions, cars, and other external controls.
 * Native hosts publish this model and return commands; they never reconstruct playback policy.
 */
interface NaviampExternalPlaybackControl {
    fun snapshot(): NaviampExternalPlaybackSnapshot
    fun play()
    fun pause()
    fun playPause()
    fun stop()
    fun previous()
    fun next()
    fun seekTo(positionMillis: Long)
    fun seekBy(deltaMillis: Long, advancePastEnd: Boolean = false)
    fun setShuffleActive(active: Boolean)
    fun setRepeatMode(mode: NaviampRepeatMode)
    fun setVolumePercent(percent: Int)
}

class NaviampCoreExternalPlaybackBridge internal constructor(
    private val state: StateFlow<NaviampCoreState>,
    private val dispatch: (NaviampCoreCommand) -> Unit,
    private val progress: StateFlow<PlaybackProgress>? = null,
) : NaviampExternalPlaybackControl {
    val snapshots: Flow<NaviampExternalPlaybackSnapshot> = (progress?.let { progressFlow ->
        state.combine(progressFlow) { currentState, currentProgress ->
            currentState.toExternalPlaybackSnapshot(currentProgress)
        }
    } ?: state.map(NaviampCoreState::toExternalPlaybackSnapshot))
        .distinctUntilChanged()

    override fun snapshot(): NaviampExternalPlaybackSnapshot = state.value.toExternalPlaybackSnapshot(progress?.value)

    override fun play() {
        val current = snapshot()
        if (!current.canPlayPause || current.state == NaviampExternalPlaybackState.Playing) return
        playback(
            if (current.state == NaviampExternalPlaybackState.Paused) {
                NowPlayingPlaybackAction.Resume
            } else {
                NowPlayingPlaybackAction.PlayCurrent
            },
        )
    }

    override fun pause() {
        if (snapshot().canPlayPause && snapshot().state in setOf(NaviampExternalPlaybackState.Playing, NaviampExternalPlaybackState.Paused)) {
            playback(NowPlayingPlaybackAction.Pause)
        }
    }
    override fun playPause() = if (snapshot().state == NaviampExternalPlaybackState.Playing) pause() else play()
    override fun stop() {
        val current = snapshot()
        if (current.canControl || (current.localPlayback && current.current != null && current.state == NaviampExternalPlaybackState.Loading))
            playback(NowPlayingPlaybackAction.Stop)
    }
    override fun previous() { if (snapshot().hasPrevious) playback(NowPlayingPlaybackAction.Previous) }
    override fun next() { if (snapshot().hasNext) playback(NowPlayingPlaybackAction.Next) }
    fun rewind() = seekBy(-ExternalPlaybackSeekStepMillis)
    fun fastForward() = seekBy(ExternalPlaybackSeekStepMillis)
    fun toggleShuffle() { if (snapshot().canShuffle) playback(NowPlayingPlaybackAction.ToggleShuffle) }
    fun cycleRepeatMode() { if (snapshot().canRepeat) playback(NowPlayingPlaybackAction.CycleRepeatMode) }

    override fun setVolumePercent(percent: Int) {
        if (!snapshot().canChangeVolume) return
        dispatch(
            NaviampCoreCommand.NowPlaying.Playback(
                NowPlayingPlaybackActionRequest(
                    action = NowPlayingPlaybackAction.ChangeVolume,
                    volumePercent = percent.coerceIn(0, 100),
                ),
            ),
        )
    }

    override fun setShuffleActive(active: Boolean) {
        if (snapshot().canShuffle && snapshot().shuffleActive != active) toggleShuffle()
    }

    override fun setRepeatMode(mode: NaviampRepeatMode) {
        if (!snapshot().canRepeat) return
        val modes = NaviampRepeatMode.entries
        val currentIndex = modes.indexOf(snapshot().repeatMode)
        val targetIndex = modes.indexOf(mode)
        repeat((targetIndex - currentIndex + modes.size) % modes.size) { cycleRepeatMode() }
    }

    override fun seekTo(positionMillis: Long) {
        val current = snapshot()
        if (!current.canSeek || positionMillis < 0 ||
            current.durationMillis?.let { positionMillis > it } == true) return
        dispatch(
            NaviampCoreCommand.NowPlaying.Playback(
                NowPlayingPlaybackActionRequest(
                    action = NowPlayingPlaybackAction.Seek,
                    seekSeconds = positionMillis.coerceAtLeast(0L) / 1_000.0,
                ),
            ),
        )
    }

    override fun seekBy(deltaMillis: Long, advancePastEnd: Boolean) {
        val snapshot = snapshot()
        if (!snapshot.canSeek) return
        val position = (snapshot.positionMillis ?: 0L).coerceAtLeast(0L)
        val requested = if (deltaMillis > 0 && position > Long.MAX_VALUE - deltaMillis) {
            Long.MAX_VALUE
        } else {
            position + deltaMillis
        }
        if (advancePastEnd && snapshot.durationMillis?.let { requested > it } == true) next()
        else seekTo((snapshot.durationMillis?.let(requested::coerceAtMost) ?: requested).coerceAtLeast(0L))
    }

    fun selectQueueItem(index: Int) {
        if (snapshot().queue.getOrNull(index) == null) return
        if (index == snapshot().currentQueueIndex) {
            play()
            return
        }
        queuePlaybackCommand(index)?.let(dispatch)
    }

    fun toggleFavorite() {
        customActionCommand(if (snapshot().favorite) NaviampExternalPlaybackAction.Unfavorite else NaviampExternalPlaybackAction.Favorite)?.let(dispatch)
    }

    fun startRadio() { customActionCommand(NaviampExternalPlaybackAction.StartRadio)?.let(dispatch) }

    /** Validates the current capability/state before binding a vehicle or external-control tap. */
    internal fun customActionCommand(action: NaviampExternalPlaybackAction): NaviampCoreCommand? {
        if (action !in snapshot().customActions) return null
        val nowPlaying = state.value.shell.nowPlaying ?: return null
        return when (action) {
            NaviampExternalPlaybackAction.StartRadio,
            NaviampExternalPlaybackAction.Favorite,
            NaviampExternalPlaybackAction.Unfavorite -> NaviampCoreCommand.NowPlaying.CurrentTrack(
                NowPlayingCurrentTrackUiActionRequest(
                    action = if (action == NaviampExternalPlaybackAction.StartRadio) NowPlayingCurrentTrackAction.StartRadio else NowPlayingCurrentTrackAction.ToggleFavorite,
                    // Remote favorite routing binds its own Connect occurrence; radio is local only.
                    target = if (state.value.shell.connect.remotePlaybackAuthorityActive) null else NowPlayingCurrentTrackTarget(
                        trackId = nowPlaying.id,
                        sourceId = state.value.shell.connectionSettings.currentSourceId,
                        queue = if (action == NaviampExternalPlaybackAction.StartRadio) nowPlaying.queueSnapshot else null,
                    ),
                ))
            else -> NaviampCoreCommand.NowPlaying.Playback(NowPlayingPlaybackActionRequest(
                if (action == NaviampExternalPlaybackAction.ShuffleOn || action == NaviampExternalPlaybackAction.ShuffleOff)
                    NowPlayingPlaybackAction.ToggleShuffle else NowPlayingPlaybackAction.CycleRepeatMode,
            ))
        }
    }

    fun browseChildren(parentId: String): List<NaviampExternalMediaItem> {
        val playback = snapshot()
        val catalog = playback.browseCatalog
        return when (parentId) {
            NaviampExternalMediaRootId -> buildList {
                if (catalog.playlists.isNotEmpty()) {
                    add(section(NaviampExternalPlaylistsId, "Playlists", "Saved playlists"))
                }
                if (catalog.recentTracks.isNotEmpty()) {
                    add(section(NaviampExternalRecentTracksId, "Recently Played", "Recent tracks"))
                }
                if (catalog.internetRadio.isNotEmpty() || catalog.stations.isNotEmpty()) {
                    add(section(NaviampExternalRadioId, "Radio", "Stations"))
                }
                if (catalog.recentAlbums.isNotEmpty()) {
                    add(section(NaviampExternalRecentAlbumsId, "Recent Albums", "Recently played albums"))
                }
                if (playback.queue.isNotEmpty()) {
                    add(section(NaviampExternalQueueId, "Queue", "${playback.queue.size} tracks"))
                }
                if (playback.current != null) {
                    add(section(NaviampExternalNowPlayingId, "Now Playing", "Current playback", playable = true))
                }
            }
            NaviampExternalQueueId -> playback.automotiveQueue()
            NaviampExternalRecentTracksId -> catalog.recentTracks.map(::externalTrack)
            NaviampExternalRecentAlbumsId -> catalog.recentAlbums.map(::externalAlbum)
            NaviampExternalPlaylistsId -> catalog.playlists.map(::externalPlaylist)
            NaviampExternalRadioId -> catalog.internetRadio.map(::externalInternetRadio) + catalog.stations.map(::externalStation)
            else -> emptyList()
        }
    }

    fun search(query: String): List<NaviampExternalMediaItem> {
        val normalized = query.trim()
        val categories = listOf(
            NaviampExternalQueueId,
            NaviampExternalRecentTracksId,
            NaviampExternalRecentAlbumsId,
            NaviampExternalPlaylistsId,
            NaviampExternalRadioId,
        )
        return categories.flatMap(::browseChildren)
            .filter { item -> item.playable }
            .filter { item ->
                normalized.isEmpty() || listOf(item.title, item.subtitle, item.description)
                    .any { value -> value.contains(normalized, ignoreCase = true) }
            }
            .distinctBy(NaviampExternalMediaItem::mediaId)
    }

    fun playSearch(query: String): Boolean = search(query)
        .firstOrNull()
        ?.let { playMediaId(it.mediaId) }
        ?: false

    fun playMediaId(mediaId: String): Boolean {
        when (mediaId) {
            NaviampExternalNowPlayingId -> {
                play()
                return true
            }
        }
        snapshot().queue.indexOfFirst { item -> item.mediaId == mediaId }
            .takeIf { it >= 0 }
            ?.let {
                selectQueueItem(it)
                return true
            }
        val command = browsePlaybackCommand(mediaId) ?: return false
        dispatch(command)
        return true
    }

    /** Current playback context is independent of the Home recents catalog. */
    internal fun currentAlbumCollection(): NaviampVehicleCollectionReference? {
        val shell = state.value.shell
        val connection = shell.connectionSettings
        val playing = shell.nowPlaying ?: return null
        if (playing.id.isBlank() || playing.isLive || shell.connect.remotePlaybackAuthorityActive ||
            connection.currentSourceId.isNullOrBlank() || connection.connection.isConnecting ||
            connection.connection.restoringConnection) return null
        val albumId = playing.albumId?.takeIf { it.isNotBlank() } ?: return null
        val title = playing.albumTitle.takeIf { it.isNotBlank() } ?: return null
        return NaviampVehicleCollectionReference(NaviampVehicleCollectionKind.Album, albumId, title)
    }

    internal fun browseCollection(mediaId: String): NaviampVehicleCollectionReference? {
        val catalog = snapshot().browseCatalog
        return when {
            mediaId.startsWith(AlbumMediaIdPrefix) -> catalog.recentAlbums
                .findByMediaId(mediaId, AlbumMediaIdPrefix, SharedMediaItemUi::id)
                ?.let { NaviampVehicleCollectionReference(NaviampVehicleCollectionKind.Album, it.id, it.title) }
            mediaId.startsWith(PlaylistMediaIdPrefix) -> catalog.playlists
                .findByMediaId(mediaId, PlaylistMediaIdPrefix, SharedMediaItemUi::id)
                ?.let { NaviampVehicleCollectionReference(NaviampVehicleCollectionKind.Playlist, it.id, it.title) }
            else -> null
        }
    }

    /** Preserve the shared queue-selection policy and the occurrence IDs supplied by Now Playing. */
    internal fun queuePlaybackCommand(mediaId: String): NaviampCoreCommand? =
        snapshot().queue.indexOfFirst { it.mediaId == mediaId }.takeIf { it >= 0 }?.let(::queuePlaybackCommand)

    private fun queuePlaybackCommand(index: Int): NaviampCoreCommand? {
        val current = snapshot()
        if (index !in current.queue.indices) return null
        if (index == current.currentQueueIndex) {
            if (!current.canPlayPause) return null
            return NaviampCoreCommand.NowPlaying.Playback(NowPlayingPlaybackActionRequest(
                if (current.state == NaviampExternalPlaybackState.Paused) NowPlayingPlaybackAction.Resume
                else NowPlayingPlaybackAction.PlayCurrent))
        }
        val nowPlaying = state.value.shell.nowPlaying ?: return null
        val item = (nowPlaying.backTo + NaviampNowPlayingItemUi(
            id = nowPlaying.id, title = nowPlaying.title, subtitle = nowPlaying.subtitle,
        ) + nowPlaying.upNext).getOrNull(index) ?: return null
        return NaviampCoreCommand.NowPlaying.Selection(
            NowPlayingSelectionActionRequest(item, NowPlayingSelectionAction.SelectQueueItem))
    }

    /** Resolve against the current catalog, then let a shared vehicle session await execution. */
    internal fun browsePlaybackCommand(mediaId: String): NaviampCoreCommand? {
        val shell = state.value.shell
        val home = shell.home.content
        val catalog = shell.externalBrowseCatalog()
        return when {
            mediaId.startsWith(RecentTrackMediaIdPrefix) -> home.recentlyPlayedTracks
                .findByMediaId(mediaId, RecentTrackMediaIdPrefix, SharedTrackRowUi::id)
                ?.let { track ->
                    NaviampCoreCommand.Home.RecentTrackAction(
                        SharedTrackRowActionRequest(track, SharedTrackRowAction.Select),
                    )
                }
            mediaId.startsWith(AlbumMediaIdPrefix) -> home.recentAlbums
                .findByMediaId(mediaId, AlbumMediaIdPrefix, SharedMediaItemUi::id)
                ?.let { album ->
                    NaviampCoreCommand.Media.ItemAction(
                        NaviampMediaItemActionRequest(album, NaviampMediaItemCommand.PlayAlbum),
                    )
                }
            mediaId.startsWith(PlaylistMediaIdPrefix) -> shell.playlists.playlists.ifEmpty { home.playlists }
                .findByMediaId(mediaId, PlaylistMediaIdPrefix, SharedMediaItemUi::id)
                ?.let { playlist ->
                    NaviampCoreCommand.Media.ItemAction(
                        NaviampMediaItemActionRequest(
                            playlist,
                            NaviampMediaItemCommand.Playlist(
                                NaviampPlaylistMediaCommand.Detail(
                                    NaviampPlaylistDetailCommand.Play(shuffle = false),
                                ),
                            ),
                        ),
                    )
                }
            mediaId.startsWith(InternetRadioMediaIdPrefix) -> catalog.internetRadio
                .findByMediaId(mediaId, InternetRadioMediaIdPrefix, SharedMediaItemUi::id)
                ?.let { station ->
                    NaviampCoreCommand.Home.SelectInternetRadio(station)
                }
            mediaId.startsWith(StationMediaIdPrefix) -> catalog.stations
                .findByMediaId(mediaId, StationMediaIdPrefix, SharedHomeStationUi::id)
                ?.let { station ->
                    NaviampCoreCommand.Home.SelectStation(station)
                }
            else -> null
        }
    }

    private fun playback(action: NowPlayingPlaybackAction) {
        dispatch(
            NaviampCoreCommand.NowPlaying.Playback(
                NowPlayingPlaybackActionRequest(action),
            ),
        )
    }
}

private const val ExternalPlaybackSeekStepMillis = 10_000L

/** Keeps the active item visible when a vehicle opens Queue without discarding playback history. */
fun NaviampExternalPlaybackSnapshot.automotiveQueue(): List<NaviampExternalMediaItem> {
    if (currentQueueIndex !in queue.indices) return queue
    return queue.drop(currentQueueIndex) + queue.take(currentQueueIndex)
}

private fun section(
    id: String,
    title: String,
    subtitle: String,
    playable: Boolean = false,
) = NaviampExternalMediaItem(id, title, subtitle, playable = playable)

private fun externalTrack(track: SharedTrackRowUi) = NaviampExternalMediaItem(
    mediaId = "$RecentTrackMediaIdPrefix${track.id}",
    title = track.title,
    subtitle = track.subtitle,
    description = track.meta,
    artworkUrl = track.coverArtUrl,
    mediaKind = app.naviamp.ui.SharedMediaItemKind.Track,
)

private fun externalAlbum(album: SharedMediaItemUi) = NaviampExternalMediaItem(
    mediaId = "$AlbumMediaIdPrefix${album.id}",
    title = album.title,
    subtitle = album.subtitle,
    description = album.meta,
    artworkUrl = album.coverArtUrl,
    mediaKind = app.naviamp.ui.SharedMediaItemKind.Album,
)

private fun externalPlaylist(playlist: SharedMediaItemUi) = NaviampExternalMediaItem(
    mediaId = "$PlaylistMediaIdPrefix${playlist.id}",
    title = playlist.title,
    subtitle = playlist.subtitle,
    description = playlist.meta,
    artworkUrl = playlist.coverArtUrl,
    mediaKind = app.naviamp.ui.SharedMediaItemKind.Playlist,
)

private fun externalInternetRadio(station: SharedMediaItemUi) = NaviampExternalMediaItem(
    mediaId = "$InternetRadioMediaIdPrefix${station.id}",
    title = station.title,
    subtitle = station.subtitle,
    description = station.meta,
    artworkUrl = station.coverArtUrl,
    mediaKind = app.naviamp.ui.SharedMediaItemKind.RadioStation,
)

private fun externalStation(station: SharedHomeStationUi) = NaviampExternalMediaItem(
    mediaId = "$StationMediaIdPrefix${station.id}",
    title = station.title,
    subtitle = station.subtitle,
)

private fun <T> List<T>.findByMediaId(
    mediaId: String,
    prefix: String,
    id: (T) -> String,
): T? = firstOrNull { item -> id(item) == mediaId.removePrefix(prefix) }

fun NaviampCore.externalPlaybackBridge(): NaviampCoreExternalPlaybackBridge =
    NaviampCoreExternalPlaybackBridge(state, ::dispatch, playbackProgress)

/**
 * External playback for native integrations that accept only an artwork URI. Artwork is fetched
 * through Core's provider-aware cache and published as a private local file.
 */
fun NaviampCore.localArtworkExternalPlayback(
    scope: kotlinx.coroutines.CoroutineScope,
    files: NaviampExternalArtworkFileWriter,
): NaviampLocalArtworkExternalPlayback {
    val bridge = externalPlaybackBridge()
    val artwork = vehicleArtwork
    return NaviampLocalArtworkExternalPlayback(scope, bridge, bridge.snapshots, { url ->
        artwork?.load(NaviampVehicleArtworkReference(app.naviamp.ui.SharedMediaItemKind.Album, url = url))
    }, files)
}

fun NaviampCoreExternalPlaybackBridge.lifecycleCoordinator(): NaviampExternalPlaybackLifecycleCoordinator =
    NaviampExternalPlaybackLifecycleCoordinator(::snapshot, ::play, ::pause, ::stop)

internal fun NaviampCoreState.toExternalPlaybackSnapshot(
    progress: PlaybackProgress? = null,
): NaviampExternalPlaybackSnapshot {
    val catalog = shell.externalBrowseCatalog()
    // The shared presenter also publishes a no-playback placeholder with no media identity.
    // It must not create an external queue row, Now Playing destination or retained service.
    val nowPlaying = shell.nowPlaying ?: return NaviampExternalPlaybackSnapshot(browseCatalog = catalog)
    if (nowPlaying.id.isBlank()) return NaviampExternalPlaybackSnapshot(
        browseCatalog = catalog,
        canChangeVolume = nowPlaying.canChangeVolume,
        volumePercent = nowPlaying.volumePercent.coerceIn(0, 100),
    )
    val queueRows = nowPlaying.backTo + NaviampNowPlayingItemUi(
        id = nowPlaying.id,
        title = nowPlaying.title,
        subtitle = nowPlaying.subtitle,
        meta = nowPlaying.albumLine,
        coverArtUrl = nowPlaying.coverArtUrl,
        favoriteActive = nowPlaying.favoriteActive,
    ) + nowPlaying.upNext
    val queue = queueRows.mapIndexed { index, item ->
        NaviampExternalMediaItem(
            mediaId = "$QueueMediaIdPrefix${item.id}",
            title = item.title,
            subtitle = item.subtitle,
            description = item.meta,
            albumTitle = if (index == nowPlaying.backTo.size) nowPlaying.albumTitle else item.meta,
            artworkUrl = item.coverArtUrl,
            queueIndex = index,
            mediaKind = app.naviamp.ui.SharedMediaItemKind.Track,
        )
    }
    val currentIndex = nowPlaying.backTo.size.takeIf { it in queue.indices } ?: -1
    val state = when {
        nowPlaying.isPlaying -> NaviampExternalPlaybackState.Playing
        nowPlaying.isPaused -> NaviampExternalPlaybackState.Paused
        nowPlaying.stateLabel.equals("Loading", ignoreCase = true) -> NaviampExternalPlaybackState.Loading
        else -> NaviampExternalPlaybackState.Idle
    }
    return NaviampExternalPlaybackSnapshot(
        browseCatalog = catalog,
        state = state,
        localPlayback = !shell.connect.remotePlaybackAuthorityActive && !nowPlaying.castSelected,
        connectionPending = shell.connectionSettings.connection.let { it.isConnecting || it.restoringConnection },
        resumeIdentity = NaviampExternalPlaybackResumeIdentity(shell.connectionSettings.currentSourceId,
            sourceGeneration, shell.connectionSettings.accountSelectionGeneration, playbackSelectionGeneration,
            playbackIntentGeneration, nowPlaying.id, currentIndex, nowPlaying.sleepTimer.active),
        current = queue.getOrNull(currentIndex),
        queue = queue,
        currentQueueIndex = currentIndex,
        seekGeneration = progress?.seekGeneration ?: 0L,
        positionMillis = (progress?.positionSeconds ?: nowPlaying.positionSeconds)?.times(1_000.0)?.toLong(),
        durationMillis = if (nowPlaying.isLive) null else (progress?.durationSeconds ?: nowPlaying.durationSeconds)?.times(1_000.0)?.toLong(),
        canPlayPause = nowPlaying.canPlayPause,
        canSeek = nowPlaying.canSeek && !nowPlaying.isLive && nowPlaying.durationSeconds != null,
        canChangeVolume = nowPlaying.canChangeVolume,
        volumePercent = nowPlaying.volumePercent.coerceIn(0, 100),
        hasPrevious = nowPlaying.hasPrevious && !nowPlaying.isLive,
        hasNext = nowPlaying.hasNext && !nowPlaying.isLive,
        favorite = nowPlaying.favoriteActive,
        canFavorite = nowPlaying.canFavorite && !nowPlaying.isLive,
        shuffleActive = nowPlaying.shuffleActive,
        repeatMode = nowPlaying.repeatMode,
        canStartRadio = nowPlaying.canStartRadio && !nowPlaying.isLive && !shell.connect.remotePlaybackAuthorityActive,
        canShuffle = nowPlaying.shuffleEnabled && !nowPlaying.isLive,
        canRepeat = nowPlaying.canRepeat && !nowPlaying.isLive,
        isLive = nowPlaying.isLive,
    )
}

const val NaviampExternalMediaRootId = "naviamp:root"
const val NaviampExternalQueueId = "naviamp:queue"
const val NaviampExternalNowPlayingId = "naviamp:now-playing"
const val NaviampExternalRecentTracksId = "naviamp:recent-tracks"
const val NaviampExternalRecentAlbumsId = "naviamp:recent-albums"
const val NaviampExternalPlaylistsId = "naviamp:playlists"
const val NaviampExternalRadioId = "naviamp:radio"
const val QueueMediaIdPrefix = "naviamp:queue-track:"
private const val RecentTrackMediaIdPrefix = "naviamp:recent-track:"
private const val AlbumMediaIdPrefix = "naviamp:album:"
private const val PlaylistMediaIdPrefix = "naviamp:playlist:"
private const val InternetRadioMediaIdPrefix = "naviamp:internet-radio:"
private const val StationMediaIdPrefix = "naviamp:station:"

internal fun NaviampExternalPlaybackSnapshot.sourceScope() = resumeIdentity?.let {
    Triple(it.sourceId, it.sourceGeneration, it.accountGeneration)
}
