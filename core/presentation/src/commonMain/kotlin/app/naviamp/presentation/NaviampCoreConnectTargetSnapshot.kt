package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectClearUpNext
import app.naviamp.domain.connect.NaviampConnectCommand
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectErrorCode
import app.naviamp.domain.connect.NaviampConnectMoveQueueOccurrence
import app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning
import app.naviamp.domain.connect.NaviampConnectHandoffQueue
import app.naviamp.domain.connect.NaviampConnectPlaybackSnapshot
import app.naviamp.domain.connect.NaviampConnectPlaybackState
import app.naviamp.domain.connect.NaviampConnectQueueGroup
import app.naviamp.domain.connect.NaviampConnectQueueOccurrence
import app.naviamp.domain.connect.NaviampConnectQueueSnapshot
import app.naviamp.domain.connect.NaviampConnectRemoveQueueOccurrence
import app.naviamp.domain.connect.NaviampConnectRepeatMode
import app.naviamp.domain.connect.NaviampConnectSourceIdentity
import app.naviamp.domain.connect.NaviampConnectStartMedia
import app.naviamp.domain.connect.NaviampConnectSelectQueueOccurrence
import app.naviamp.domain.connect.NaviampConnectSetFavorite
import app.naviamp.domain.connect.NaviampConnectTargetSnapshot
import app.naviamp.domain.connect.NaviampConnectTargetSurface
import app.naviamp.app.NaviampConnectTargetCommandExecutor
import app.naviamp.app.NaviampConnectTargetCommandResult
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.queue.normalizedGroups
import kotlin.math.roundToLong

/**
 * Projects Naviamp's canonical live playback graph into the Connect wire model.
 *
 * Queue occurrences deliberately include their queue position because Naviamp queues may contain
 * the same provider track more than once. A remote mutation is revision-bound, so an occurrence ID
 * only needs to remain stable for the lifetime of the snapshot that supplied it.
 */
class NaviampCoreConnectTargetSnapshotFactory(
    private val target: NaviampConnectDevice,
    private val capabilities: Set<NaviampConnectCapability>,
    private val sourceIdentity: () -> NaviampConnectSourceIdentity? = { null },
) {
    fun create(
        revision: Long,
        live: NaviampLivePlaybackState,
        volumePercent: Int,
        visibleSurface: NaviampConnectTargetSurface = NaviampConnectTargetSurface.NowPlaying,
    ): NaviampConnectTargetSnapshot {
        val queue = live.toNaviampConnectQueueSnapshot()
        return NaviampConnectTargetSnapshot(
            revision = revision,
            target = target,
            capabilities = capabilities,
            sourceIdentity = sourceIdentity(),
            playback = NaviampConnectPlaybackSnapshot(
                state = live.playbackState.toConnectPlaybackState(),
                currentOccurrenceId = queue.occurrences.getOrNull(queue.currentIndex)?.occurrenceId,
                positionMillis = live.progress.positionSeconds.toNonNegativeMillis(),
                durationMillis = (
                    live.progress.durationSeconds
                        ?: live.currentTrack?.durationSeconds?.toDouble()
                        ?: live.queue.current?.durationSeconds?.toDouble()
                    ).toNonNegativeMillisOrNull(),
                repeatMode = live.repeatMode.toConnectRepeatMode(),
                shuffled = live.shuffledUpNextSnapshot != null,
                volumePercent = volumePercent.coerceIn(0, 100),
                visibleSurface = visibleSurface,
            ),
            queue = queue,
        )
    }

    companion object {
        fun occurrenceId(index: Int, mediaId: String): String = "$index:$mediaId"
    }
}

/** Projects controller-local playback into a handoff command without inventing a target device. */
fun naviampCoreConnectQueueHandoff(
    live: NaviampLivePlaybackState,
    sourceIdentity: NaviampConnectSourceIdentity,
    playing: Boolean = live.playbackState == PlaybackState.Playing,
): NaviampConnectHandoffQueue = NaviampConnectHandoffQueue(
    sourceIdentity = sourceIdentity,
    queue = live.toNaviampConnectQueueSnapshot(),
    positionMillis = live.progress.positionSeconds.toNonNegativeMillis(),
    repeatMode = live.repeatMode.toConnectRepeatMode(),
    shuffled = live.shuffledUpNextSnapshot != null,
    playing = playing,
)

private fun NaviampLivePlaybackState.toNaviampConnectQueueSnapshot(): NaviampConnectQueueSnapshot {
    val playbackQueue = queue
    val occurrences = playbackQueue.tracks.mapIndexed { index, track ->
        NaviampConnectQueueOccurrence(
            occurrenceId = NaviampCoreConnectTargetSnapshotFactory.occurrenceId(index, track.id.value),
            mediaId = track.id.value,
            title = track.title,
            artistId = track.artistId?.value,
            artistName = track.artistName,
            albumId = track.albumId?.value,
            albumTitle = track.albumTitle,
            durationMillis = track.durationSeconds?.toLong()?.times(1_000L),
            artworkId = track.coverArtId,
            favorite = track.favoritedAtIso8601 != null,
        )
    }
    val currentIndex = playbackQueue.currentIndex.takeIf { it in occurrences.indices } ?: -1
    return NaviampConnectQueueSnapshot(
        occurrences = occurrences,
        currentIndex = currentIndex,
        playNextCount = playbackQueue.playNextCount.coerceIn(
            0,
            (occurrences.size - currentIndex - 1).coerceAtLeast(0),
        ),
        groups = playbackQueue.normalizedGroups().map { group ->
            NaviampConnectQueueGroup(
                groupId = group.id,
                label = group.label.takeIf(String::isNotBlank),
                startIndex = group.startIndex,
                endIndexExclusive = group.endIndexExclusive,
                targetType = group.target.type,
                targetId = group.target.id,
                playbackProfile = group.profile,
            )
        },
    )
}

val NaviampCoreSupportedConnectTargetCapabilities: Set<NaviampConnectCapability> = setOf(
    NaviampConnectCapability.TransportControls,
    NaviampConnectCapability.Seeking,
    NaviampConnectCapability.Favorites,
    NaviampConnectCapability.Repeat,
    NaviampConnectCapability.Shuffle,
    NaviampConnectCapability.QueueRead,
    NaviampConnectCapability.QueueSelect,
    NaviampConnectCapability.QueueEdit,
    NaviampConnectCapability.QueueReorder,
    NaviampConnectCapability.QueueClear,
    NaviampConnectCapability.QueueHandoff,
    NaviampConnectCapability.CatalogPlayback,
    NaviampConnectCapability.InternetRadio,
)

/** Applies remote commands to the canonical shared playback owners and re-projects their state. */
class NaviampCoreConnectTargetCommandExecutor(
    private val playback: NaviampCorePlaybackController,
    private val nowPlaying: NaviampCoreNowPlayingMediaController,
    private val catalog: NaviampCoreConnectCatalogController,
    private val stateStore: NaviampCoreStateStore,
    private val snapshots: NaviampCoreConnectTargetSnapshotFactory,
    private val offerProvisioning: (NaviampConnectOfferConnectionProvisioning) -> Boolean = { false },
) : NaviampConnectTargetCommandExecutor {
    override suspend fun execute(
        command: NaviampConnectCommand,
        currentSnapshot: NaviampConnectTargetSnapshot,
    ): NaviampConnectTargetCommandResult {
        val accepted = when (command) {
            is NaviampConnectOfferConnectionProvisioning -> offerProvisioning(command)
            is NaviampConnectStartMedia -> catalog.start(command)
            is app.naviamp.domain.connect.NaviampConnectQueueMedia -> catalog.queue(command)
            is NaviampConnectHandoffQueue -> playback.handoffConnectQueue(command)
            is NaviampConnectSetFavorite -> nowPlaying.setConnectFavorite(command.mediaId, command.favorite)
            is NaviampConnectSelectQueueOccurrence -> currentSnapshot.queue.indexOf(command.occurrenceId)
                ?.let(playback::selectConnectQueueIndex)
                ?: false
            is NaviampConnectMoveQueueOccurrence -> {
                val fromIndex = currentSnapshot.queue.indexOf(command.occurrenceId)
                val beforeIndex = command.beforeOccurrenceId?.let(currentSnapshot.queue::indexOf)
                if (fromIndex == null || (command.beforeOccurrenceId != null && beforeIndex == null)) false
                else playback.moveConnectQueueIndex(fromIndex, beforeIndex)
            }
            is NaviampConnectRemoveQueueOccurrence -> currentSnapshot.queue.indexOf(command.occurrenceId)
                ?.let(playback::removeConnectQueueIndex)
                ?: false
            NaviampConnectClearUpNext -> playback.clearConnectUpNext()
            else -> playback.executeConnectPlayback(command)
        }
        if (!accepted) {
            val supported = command.requiredCoreTargetCapability() in NaviampCoreSupportedConnectTargetCapabilities
            return NaviampConnectTargetCommandResult.Failure(
                code = if (supported) NaviampConnectErrorCode.MediaUnavailable else NaviampConnectErrorCode.UnsupportedCapability,
                message = if (supported) {
                    "The requested playback or queue item is no longer available."
                } else {
                    "This Naviamp target does not support the requested command yet."
                },
            )
        }

        val projected = snapshots.create(
            revision = currentSnapshot.revision,
            live = playback.connectLiveState(),
            volumePercent = stateStore.state.value.shell.playback.settings.volumePercent,
            visibleSurface = currentSnapshot.playback.visibleSurface,
        )
        val changed = projected != currentSnapshot
        return NaviampConnectTargetCommandResult.Success(
            snapshot = if (changed) projected.copy(revision = currentSnapshot.revision + 1) else projected,
            changed = changed,
        )
    }
}

private fun NaviampConnectQueueSnapshot.indexOf(occurrenceId: String): Int? =
    occurrences.indexOfFirst { it.occurrenceId == occurrenceId }.takeIf { it >= 0 }

private fun NaviampConnectCommand.requiredCoreTargetCapability(): NaviampConnectCapability? = when (this) {
    app.naviamp.domain.connect.NaviampConnectPlay,
    app.naviamp.domain.connect.NaviampConnectPause,
    app.naviamp.domain.connect.NaviampConnectTogglePlayPause,
    app.naviamp.domain.connect.NaviampConnectPrevious,
    app.naviamp.domain.connect.NaviampConnectNext,
    app.naviamp.domain.connect.NaviampConnectStop,
    -> NaviampConnectCapability.TransportControls
    is app.naviamp.domain.connect.NaviampConnectSeek -> NaviampConnectCapability.Seeking
    is app.naviamp.domain.connect.NaviampConnectSetRepeat -> NaviampConnectCapability.Repeat
    is app.naviamp.domain.connect.NaviampConnectSetShuffle -> NaviampConnectCapability.Shuffle
    is NaviampConnectSetFavorite -> NaviampConnectCapability.Favorites
    is NaviampConnectSelectQueueOccurrence -> NaviampConnectCapability.QueueSelect
    is NaviampConnectMoveQueueOccurrence -> NaviampConnectCapability.QueueReorder
    is NaviampConnectRemoveQueueOccurrence -> NaviampConnectCapability.QueueEdit
    NaviampConnectClearUpNext -> NaviampConnectCapability.QueueClear
    is NaviampConnectHandoffQueue -> NaviampConnectCapability.QueueHandoff
    is NaviampConnectOfferConnectionProvisioning -> NaviampConnectCapability.ConnectionProvisioning
    is NaviampConnectStartMedia -> when (mediaType) {
        app.naviamp.domain.connect.NaviampConnectMediaType.InternetRadioStation ->
            NaviampConnectCapability.InternetRadio
        else -> NaviampConnectCapability.CatalogPlayback
    }
    is app.naviamp.domain.connect.NaviampConnectQueueMedia -> NaviampConnectCapability.QueueEdit
    else -> null
}

private fun PlaybackState.toConnectPlaybackState(): NaviampConnectPlaybackState = when (this) {
    PlaybackState.Idle,
    PlaybackState.Stopped,
    PlaybackState.Finished,
    -> NaviampConnectPlaybackState.Idle
    PlaybackState.Loading -> NaviampConnectPlaybackState.Buffering
    PlaybackState.Playing -> NaviampConnectPlaybackState.Playing
    PlaybackState.Paused -> NaviampConnectPlaybackState.Paused
    is PlaybackState.Error -> NaviampConnectPlaybackState.Failed
}

private fun RepeatMode.toConnectRepeatMode(): NaviampConnectRepeatMode = when (this) {
    RepeatMode.Off -> NaviampConnectRepeatMode.Off
    RepeatMode.Queue -> NaviampConnectRepeatMode.All
    RepeatMode.Track -> NaviampConnectRepeatMode.One
}

private fun Double?.toNonNegativeMillis(): Long =
    this?.takeIf { it.isFinite() && it > 0.0 }?.times(1_000.0)?.roundToLong() ?: 0L

private fun Double?.toNonNegativeMillisOrNull(): Long? =
    this?.takeIf { it.isFinite() && it >= 0.0 }?.times(1_000.0)?.roundToLong()
