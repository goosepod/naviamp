package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectCommand
import app.naviamp.domain.connect.NaviampConnectMoveQueueOccurrence
import app.naviamp.domain.connect.NaviampConnectNext
import app.naviamp.domain.connect.NaviampConnectPause
import app.naviamp.domain.connect.NaviampConnectPlay
import app.naviamp.domain.connect.NaviampConnectPlaybackState
import app.naviamp.domain.connect.NaviampConnectPrevious
import app.naviamp.domain.connect.NaviampConnectRemoveQueueOccurrence
import app.naviamp.domain.connect.NaviampConnectRepeatMode
import app.naviamp.domain.connect.NaviampConnectSeek
import app.naviamp.domain.connect.NaviampConnectSelectQueueOccurrence
import app.naviamp.domain.connect.NaviampConnectSetFavorite
import app.naviamp.domain.connect.NaviampConnectSetRepeat
import app.naviamp.domain.connect.NaviampConnectSetShuffle
import app.naviamp.domain.connect.NaviampConnectStop
import app.naviamp.domain.connect.NaviampConnectTargetSnapshot
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampRepeatMode
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingItemAction
import app.naviamp.ui.NowPlayingItemTarget
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingSelectionAction
import app.naviamp.ui.NowPlayingUi

internal fun NaviampConnectTargetSnapshot.toRemoteNowPlayingUi(targetName: String): NowPlayingUi {
    val current = queue.occurrences.getOrNull(queue.currentIndex)
    fun item(index: Int) = queue.occurrences[index].let { occurrence ->
        NaviampNowPlayingItemUi(
            id = "queue:$index",
            title = occurrence.title,
            subtitle = occurrence.artistName,
            meta = occurrence.albumTitle.orEmpty(),
            favoriteActive = occurrence.favorite,
            hasAlbum = !occurrence.albumTitle.isNullOrBlank(),
            playNextPriority = index in (queue.currentIndex + 1)..(queue.currentIndex + queue.playNextCount),
        )
    }
    val capabilities = capabilities
    val state = playback.state
    return NowPlayingUi(
        id = current?.mediaId.orEmpty(),
        title = current?.title.orEmpty(),
        subtitle = current?.artistName.orEmpty(),
        stateLabel = when (state) {
            NaviampConnectPlaybackState.Playing -> "Playing on $targetName"
            NaviampConnectPlaybackState.Paused -> "Paused on $targetName"
            NaviampConnectPlaybackState.Buffering -> "Loading on $targetName"
            NaviampConnectPlaybackState.Failed -> "Playback failed on $targetName"
            NaviampConnectPlaybackState.Idle -> "Connected to $targetName"
        },
        albumLine = current?.albumTitle.orEmpty(),
        albumTitle = current?.albumTitle.orEmpty(),
        positionSeconds = playback.positionMillis / 1_000.0,
        durationSeconds = playback.durationMillis?.div(1_000.0),
        volumePercent = playback.volumePercent ?: 100,
        isPlaying = state == NaviampConnectPlaybackState.Playing,
        isPaused = state == NaviampConnectPlaybackState.Paused,
        canPlayPause = NaviampConnectCapability.TransportControls in capabilities,
        canSeek = NaviampConnectCapability.Seeking in capabilities,
        canChangeVolume = false,
        hasPrevious = queue.currentIndex > 0,
        hasNext = queue.currentIndex in 0 until queue.occurrences.lastIndex,
        shuffleEnabled = NaviampConnectCapability.Shuffle in capabilities && queue.occurrences.size > 2,
        shuffleActive = playback.shuffled,
        repeatMode = playback.repeatMode.toUiRepeatMode(),
        canRepeat = NaviampConnectCapability.Repeat in capabilities,
        favoriteActive = current?.favorite == true,
        canFavorite = NaviampConnectCapability.Favorites in capabilities && current != null,
        queueCurrentIndex = queue.currentIndex.takeIf { it >= 0 },
        queueManagementActionsOnly = true,
        backTo = queue.occurrences.indices.take(queue.currentIndex.coerceAtLeast(0)).map(::item),
        upNext = if (queue.currentIndex < 0) emptyList() else {
            ((queue.currentIndex + 1)..queue.occurrences.lastIndex).map(::item)
        },
        relatedEmptyLabel = "Related tracks stay on the TV queue.",
    )
}

internal fun createNaviampCoreConnectRemoteNowPlayingActions(
    snapshot: () -> NaviampConnectTargetSnapshot?,
    send: (NaviampConnectCommand) -> Unit,
): NaviampNowPlayingActions = NaviampNowPlayingActions(
    onPlaybackAction = { request ->
        val current = snapshot() ?: return@NaviampNowPlayingActions
        when (request.action) {
            NowPlayingPlaybackAction.Stop -> send(NaviampConnectStop)
            NowPlayingPlaybackAction.Pause -> send(NaviampConnectPause)
            NowPlayingPlaybackAction.Resume,
            NowPlayingPlaybackAction.PlayCurrent,
            -> send(NaviampConnectPlay)
            NowPlayingPlaybackAction.Seek -> request.seekSeconds
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let { send(NaviampConnectSeek((it * 1_000.0).toLong())) }
            NowPlayingPlaybackAction.Previous -> send(NaviampConnectPrevious)
            NowPlayingPlaybackAction.Next -> send(NaviampConnectNext)
            NowPlayingPlaybackAction.ToggleShuffle ->
                send(NaviampConnectSetShuffle(!current.playback.shuffled))
            NowPlayingPlaybackAction.CycleRepeatMode -> send(
                NaviampConnectSetRepeat(
                    when (current.playback.repeatMode) {
                        NaviampConnectRepeatMode.Off -> NaviampConnectRepeatMode.All
                        NaviampConnectRepeatMode.All -> NaviampConnectRepeatMode.One
                        NaviampConnectRepeatMode.One -> NaviampConnectRepeatMode.Off
                    },
                ),
            )
            NowPlayingPlaybackAction.ChangeVolume -> Unit
        }
    },
    onDisplayAction = {},
    onCurrentTrackAction = { request ->
        if (request.action == NowPlayingCurrentTrackAction.ToggleFavorite) {
            snapshot()?.let { current ->
                current.queue.occurrences.getOrNull(current.queue.currentIndex)?.let { occurrence ->
                    send(NaviampConnectSetFavorite(occurrence.mediaId, !occurrence.favorite))
                }
            }
        }
    },
    onQueueAction = { request ->
        val current = snapshot() ?: return@NaviampNowPlayingActions
        val sourceIndex = request.queueIndex
        val occurrence = sourceIndex?.let(current.queue.occurrences::getOrNull)
        when (request.action) {
            NowPlayingQueueAction.MoveToNext -> occurrence?.let {
                val before = current.queue.occurrences.getOrNull(current.queue.currentIndex + 1)
                send(NaviampConnectMoveQueueOccurrence(it.occurrenceId, before?.occurrenceId))
            }
            NowPlayingQueueAction.MoveQueueItem -> occurrence?.let {
                val destination = request.destinationQueueIndex ?: return@let
                val beforeIndex = if (sourceIndex < destination) destination + 1 else destination
                send(
                    NaviampConnectMoveQueueOccurrence(
                        occurrenceId = it.occurrenceId,
                        beforeOccurrenceId = current.queue.occurrences.getOrNull(beforeIndex)?.occurrenceId,
                    ),
                )
            }
            NowPlayingQueueAction.RemoveFromQueue -> occurrence?.let {
                send(NaviampConnectRemoveQueueOccurrence(it.occurrenceId))
            }
            NowPlayingQueueAction.SaveQueueAsPlaylist,
            NowPlayingQueueAction.EmptyQueue,
            -> Unit
        }
    },
    onSleepTimerAction = {},
    onSelectionAction = { request ->
        if (request.action == NowPlayingSelectionAction.SelectQueueItem) {
            request.item.queueIndex()?.let { index ->
                snapshot()?.queue?.occurrences?.getOrNull(index)?.let {
                    send(NaviampConnectSelectQueueOccurrence(it.occurrenceId))
                }
            }
        }
    },
    onQueueItemAction = { request ->
        val current = snapshot() ?: return@NaviampNowPlayingActions
        val index = (request.target as? NowPlayingItemTarget.QueueIndex)?.index ?: request.item.queueIndex()
        val occurrence = index?.let(current.queue.occurrences::getOrNull) ?: return@NaviampNowPlayingActions
        when (request.action) {
            NowPlayingItemAction.RemoveFromQueue -> send(NaviampConnectRemoveQueueOccurrence(occurrence.occurrenceId))
            NowPlayingItemAction.ToggleFavorite ->
                send(NaviampConnectSetFavorite(occurrence.mediaId, !occurrence.favorite))
            NowPlayingItemAction.PlayNext,
            NowPlayingItemAction.PlayNextTrack,
            -> {
                val before = current.queue.occurrences.getOrNull(current.queue.currentIndex + 1)
                send(NaviampConnectMoveQueueOccurrence(occurrence.occurrenceId, before?.occurrenceId))
            }
            else -> Unit
        }
    },
)

private fun NaviampConnectRepeatMode.toUiRepeatMode(): NaviampRepeatMode = when (this) {
    NaviampConnectRepeatMode.Off -> NaviampRepeatMode.Off
    NaviampConnectRepeatMode.All -> NaviampRepeatMode.Queue
    NaviampConnectRepeatMode.One -> NaviampRepeatMode.Track
}

private fun NaviampNowPlayingItemUi.queueIndex(): Int? = id.removePrefix("queue:")
    .takeIf { it != id }
    ?.toIntOrNull()
