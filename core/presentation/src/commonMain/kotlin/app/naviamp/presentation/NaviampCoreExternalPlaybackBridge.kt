package app.naviamp.presentation

import app.naviamp.ui.NaviampNowPlayingItemUi
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

data class NaviampExternalMediaItem(
    val mediaId: String,
    val title: String,
    val subtitle: String,
    val description: String = "",
    val artworkUrl: String? = null,
    val playable: Boolean = true,
    val queueIndex: Int? = null,
)

data class NaviampExternalPlaybackSnapshot(
    val state: NaviampExternalPlaybackState = NaviampExternalPlaybackState.Idle,
    val current: NaviampExternalMediaItem? = null,
    val queue: List<NaviampExternalMediaItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val positionMillis: Long? = null,
    val durationMillis: Long? = null,
    val canPlayPause: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val favorite: Boolean = false,
    val canFavorite: Boolean = false,
    val shuffleActive: Boolean = false,
    val repeatMode: NaviampRepeatMode = NaviampRepeatMode.Off,
) {
    val shouldRetainPlaybackService: Boolean
        get() = current != null && state != NaviampExternalPlaybackState.Idle
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
        val sessionContent = prior == null ||
            prior.current != next.current ||
            prior.queue != next.queue ||
            prior.durationMillis != next.durationMillis
        val semanticPlaybackChange = prior == null ||
            prior.state != next.state ||
            prior.currentQueueIndex != next.currentQueueIndex ||
            prior.canPlayPause != next.canPlayPause ||
            prior.hasPrevious != next.hasPrevious ||
            prior.hasNext != next.hasNext ||
            prior.favorite != next.favorite ||
            prior.canFavorite != next.canFavorite ||
            prior.shuffleActive != next.shuffleActive ||
            prior.repeatMode != next.repeatMode
        val positionDiscontinuity = when {
            prior == null -> false
            prior.current?.mediaId != next.current?.mediaId -> false
            prior.positionMillis == null || next.positionMillis == null -> prior.positionMillis != next.positionMillis
            else -> kotlin.math.abs(next.positionMillis - prior.positionMillis) > maximumNaturalPositionStepMillis
        }
        val playbackState = semanticPlaybackChange || positionDiscontinuity
        val browseCatalog = prior == null ||
            prior.current?.mediaId != next.current?.mediaId ||
            prior.queue != next.queue
        val notification = prior == null ||
            prior.current != next.current ||
            prior.state != next.state ||
            prior.favorite != next.favorite

        previous = next
        return NaviampExternalPlaybackPublication(
            sessionContent = sessionContent,
            playbackState = playbackState,
            browseCatalog = browseCatalog,
            notification = notification,
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
) {
    private var resumeAfterInterruption = false

    fun interruptionBegan() {
        resumeAfterInterruption = snapshot().state == NaviampExternalPlaybackState.Playing
        if (resumeAfterInterruption) pause()
    }

    fun interruptionEnded(shouldResume: Boolean) {
        val resume = resumeAfterInterruption && shouldResume
        resumeAfterInterruption = false
        if (resume) play()
    }

    fun outputDisconnected() {
        resumeAfterInterruption = false
        if (snapshot().state == NaviampExternalPlaybackState.Playing) pause()
    }
}

/**
 * Host-neutral projection used by lock screens, media sessions, cars, and other external controls.
 * Native hosts publish this model and return commands; they never reconstruct playback policy.
 */
class NaviampCoreExternalPlaybackBridge internal constructor(
    private val state: StateFlow<NaviampCoreState>,
    private val dispatch: (NaviampCoreCommand) -> Unit,
) {
    val snapshots: Flow<NaviampExternalPlaybackSnapshot> = state
        .map(NaviampCoreState::toExternalPlaybackSnapshot)
        .distinctUntilChanged()

    fun snapshot(): NaviampExternalPlaybackSnapshot = state.value.toExternalPlaybackSnapshot()

    fun play() = playback(
        if (snapshot().state == NaviampExternalPlaybackState.Paused) {
            NowPlayingPlaybackAction.Resume
        } else {
            NowPlayingPlaybackAction.PlayCurrent
        },
    )

    fun pause() = playback(NowPlayingPlaybackAction.Pause)
    fun stop() = playback(NowPlayingPlaybackAction.Stop)
    fun previous() = playback(NowPlayingPlaybackAction.Previous)
    fun next() = playback(NowPlayingPlaybackAction.Next)
    fun toggleShuffle() = playback(NowPlayingPlaybackAction.ToggleShuffle)
    fun cycleRepeatMode() = playback(NowPlayingPlaybackAction.CycleRepeatMode)

    fun setShuffleActive(active: Boolean) {
        if (snapshot().shuffleActive != active) toggleShuffle()
    }

    fun setRepeatMode(mode: NaviampRepeatMode) {
        val modes = NaviampRepeatMode.entries
        val currentIndex = modes.indexOf(snapshot().repeatMode)
        val targetIndex = modes.indexOf(mode)
        repeat((targetIndex - currentIndex + modes.size) % modes.size) { cycleRepeatMode() }
    }

    fun seekTo(positionMillis: Long) {
        dispatch(
            NaviampCoreCommand.NowPlaying.Playback(
                NowPlayingPlaybackActionRequest(
                    action = NowPlayingPlaybackAction.Seek,
                    seekSeconds = positionMillis.coerceAtLeast(0L) / 1_000.0,
                ),
            ),
        )
    }

    fun selectQueueItem(index: Int) {
        val item = snapshot().queue.getOrNull(index) ?: return
        if (index == snapshot().currentQueueIndex) {
            play()
            return
        }
        val nowPlaying = state.value.shell.nowPlaying ?: return
        val sourceItem = (nowPlaying.backTo + NaviampNowPlayingItemUi(
            id = nowPlaying.id,
            title = nowPlaying.title,
            subtitle = nowPlaying.subtitle,
            meta = nowPlaying.albumLine,
            coverArtUrl = nowPlaying.coverArtUrl,
        ) + nowPlaying.upNext).getOrNull(index) ?: return
        dispatch(
            NaviampCoreCommand.NowPlaying.Selection(
                NowPlayingSelectionActionRequest(
                    item = sourceItem,
                    action = NowPlayingSelectionAction.SelectQueueItem,
                ),
            ),
        )
    }

    fun toggleFavorite() {
        if (!snapshot().canFavorite) return
        dispatch(
            NaviampCoreCommand.NowPlaying.CurrentTrack(
                NowPlayingCurrentTrackUiActionRequest(NowPlayingCurrentTrackAction.ToggleFavorite),
            ),
        )
    }

    fun browseChildren(parentId: String): List<NaviampExternalMediaItem> {
        val shell = state.value.shell
        val home = shell.home.content
        return when (parentId) {
            NaviampExternalMediaRootId -> buildList {
                if (snapshot().current != null) {
                    add(section(NaviampExternalNowPlayingId, "Now Playing", "Current playback", playable = true))
                }
                if (snapshot().queue.isNotEmpty()) {
                    add(section(NaviampExternalQueueId, "Queue", "${snapshot().queue.size} tracks"))
                }
                if (home.recentlyPlayedTracks.isNotEmpty()) {
                    add(section(NaviampExternalRecentTracksId, "Recently Played", "Recent tracks"))
                }
                if (home.recentAlbums.isNotEmpty()) {
                    add(section(NaviampExternalRecentAlbumsId, "Recent Albums", "Recently played albums"))
                }
                val playlists = shell.playlists.playlists.ifEmpty { home.playlists }
                if (playlists.isNotEmpty()) {
                    add(section(NaviampExternalPlaylistsId, "Playlists", "Saved playlists"))
                }
                if (home.radioStations.isNotEmpty() || home.stations.isNotEmpty()) {
                    add(section(NaviampExternalRadioId, "Radio", "Stations"))
                }
            }
            NaviampExternalQueueId -> snapshot().automotiveQueue()
            NaviampExternalRecentTracksId -> home.recentlyPlayedTracks.map(::externalTrack)
            NaviampExternalRecentAlbumsId -> home.recentAlbums.map(::externalAlbum)
            NaviampExternalPlaylistsId -> shell.playlists.playlists.ifEmpty { home.playlists }.map(::externalPlaylist)
            NaviampExternalRadioId -> home.radioStations.map(::externalInternetRadio) + home.stations.map(::externalStation)
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
        val shell = state.value.shell
        val home = shell.home.content
        return when {
            mediaId.startsWith(RecentTrackMediaIdPrefix) -> home.recentlyPlayedTracks
                .findByMediaId(mediaId, RecentTrackMediaIdPrefix, SharedTrackRowUi::id)
                ?.let { track ->
                    dispatch(
                        NaviampCoreCommand.Media.TrackAction(
                            SharedTrackRowActionRequest(track, SharedTrackRowAction.Select),
                        ),
                    )
                    true
                } ?: false
            mediaId.startsWith(AlbumMediaIdPrefix) -> home.recentAlbums
                .findByMediaId(mediaId, AlbumMediaIdPrefix, SharedMediaItemUi::id)
                ?.let { album ->
                    dispatch(
                        NaviampCoreCommand.Media.ItemAction(
                            NaviampMediaItemActionRequest(album, NaviampMediaItemCommand.PlayAlbum),
                        ),
                    )
                    true
                } ?: false
            mediaId.startsWith(PlaylistMediaIdPrefix) -> shell.playlists.playlists.ifEmpty { home.playlists }
                .findByMediaId(mediaId, PlaylistMediaIdPrefix, SharedMediaItemUi::id)
                ?.let { playlist ->
                    dispatch(
                        NaviampCoreCommand.Media.ItemAction(
                            NaviampMediaItemActionRequest(
                                playlist,
                                NaviampMediaItemCommand.Playlist(
                                    NaviampPlaylistMediaCommand.Detail(
                                        NaviampPlaylistDetailCommand.Play(shuffle = false),
                                    ),
                                ),
                            ),
                        ),
                    )
                    true
                } ?: false
            mediaId.startsWith(InternetRadioMediaIdPrefix) -> home.radioStations
                .findByMediaId(mediaId, InternetRadioMediaIdPrefix, SharedMediaItemUi::id)
                ?.let { station ->
                    dispatch(NaviampCoreCommand.Home.SelectInternetRadio(station))
                    true
                } ?: false
            mediaId.startsWith(StationMediaIdPrefix) -> home.stations
                .findByMediaId(mediaId, StationMediaIdPrefix, SharedHomeStationUi::id)
                ?.let { station ->
                    dispatch(NaviampCoreCommand.Home.SelectStation(station))
                    true
                } ?: false
            else -> false
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
)

private fun externalAlbum(album: SharedMediaItemUi) = NaviampExternalMediaItem(
    mediaId = "$AlbumMediaIdPrefix${album.id}",
    title = album.title,
    subtitle = album.subtitle,
    description = album.meta,
    artworkUrl = album.coverArtUrl,
)

private fun externalPlaylist(playlist: SharedMediaItemUi) = NaviampExternalMediaItem(
    mediaId = "$PlaylistMediaIdPrefix${playlist.id}",
    title = playlist.title,
    subtitle = playlist.subtitle,
    description = playlist.meta,
    artworkUrl = playlist.coverArtUrl,
)

private fun externalInternetRadio(station: SharedMediaItemUi) = NaviampExternalMediaItem(
    mediaId = "$InternetRadioMediaIdPrefix${station.id}",
    title = station.title,
    subtitle = station.subtitle,
    description = station.meta,
    artworkUrl = station.coverArtUrl,
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
    NaviampCoreExternalPlaybackBridge(state, ::dispatch)

fun NaviampCoreExternalPlaybackBridge.lifecycleCoordinator(): NaviampExternalPlaybackLifecycleCoordinator =
    NaviampExternalPlaybackLifecycleCoordinator(::snapshot, ::play, ::pause)

internal fun NaviampCoreState.toExternalPlaybackSnapshot(): NaviampExternalPlaybackSnapshot {
    val nowPlaying = shell.nowPlaying ?: return NaviampExternalPlaybackSnapshot()
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
            artworkUrl = item.coverArtUrl,
            queueIndex = index,
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
        state = state,
        current = queue.getOrNull(currentIndex),
        queue = queue,
        currentQueueIndex = currentIndex,
        positionMillis = nowPlaying.positionSeconds?.times(1_000.0)?.toLong(),
        durationMillis = nowPlaying.durationSeconds?.times(1_000.0)?.toLong(),
        canPlayPause = nowPlaying.canPlayPause,
        hasPrevious = nowPlaying.hasPrevious,
        hasNext = nowPlaying.hasNext,
        favorite = nowPlaying.favoriteActive,
        canFavorite = nowPlaying.canFavorite,
        shuffleActive = nowPlaying.shuffleActive,
        repeatMode = nowPlaying.repeatMode,
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
