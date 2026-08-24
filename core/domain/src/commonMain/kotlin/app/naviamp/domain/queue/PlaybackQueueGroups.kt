package app.naviamp.domain.queue

import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileTarget
import kotlinx.serialization.Serializable

@Serializable
data class PlaybackQueueGroup(
    val id: String,
    val target: PlaybackProfileTarget,
    val label: String,
    val startIndex: Int,
    val endIndexExclusive: Int,
    val profile: PlaybackProfile = PlaybackProfile(),
) {
    fun normalized(trackCount: Int): PlaybackQueueGroup? {
        val normalizedId = id.trim().takeIf(String::isNotEmpty) ?: return null
        val normalizedTarget = target.normalized() ?: return null
        val start = startIndex.coerceIn(0, trackCount)
        val end = endIndexExclusive.coerceIn(start, trackCount)
        if (start == end) return null
        return copy(
            id = normalizedId,
            target = normalizedTarget,
            label = label.trim(),
            startIndex = start,
            endIndexExclusive = end,
            profile = profile.normalized(),
        )
    }

    operator fun contains(index: Int): Boolean = index in startIndex until endIndexExclusive
}

fun PlaybackQueue.normalizedGroups(): List<PlaybackQueueGroup> =
    groups.mapNotNull { it.normalized(tracks.size) }
        .sortedBy(PlaybackQueueGroup::startIndex)
        .fold(emptyList()) { accepted, group ->
            if (accepted.lastOrNull()?.endIndexExclusive?.let { it > group.startIndex } == true) accepted
            else accepted + group
        }

fun PlaybackQueue.groupAt(index: Int = currentIndex): PlaybackQueueGroup? =
    normalizedGroups().firstOrNull { index in it }

fun PlaybackQueue.groupForTransition(
    fromIndex: Int = currentIndex,
    toIndex: Int,
): PlaybackQueueGroup? {
    val currentGroup = groupAt(fromIndex) ?: return null
    return currentGroup.takeIf { toIndex in it }
}

fun PlaybackQueue.withSingleGroup(group: PlaybackQueueGroup): PlaybackQueue =
    copy(groups = listOfNotNull(group.normalized(tracks.size)))

internal fun List<PlaybackQueueGroup>.afterDroppingPrefix(
    droppedTrackCount: Int,
    remainingTrackCount: Int,
): List<PlaybackQueueGroup> =
    mapNotNull { group ->
        group.copy(
            startIndex = group.startIndex - droppedTrackCount,
            endIndexExclusive = group.endIndexExclusive - droppedTrackCount,
        ).normalized(remainingTrackCount)
    }

internal fun List<PlaybackQueueGroup>.afterInserting(
    insertionIndex: Int,
    insertedTrackCount: Int,
    resultingTrackCount: Int,
): List<PlaybackQueueGroup> = flatMap { group ->
    when {
        group.endIndexExclusive <= insertionIndex -> listOf(group)
        group.startIndex >= insertionIndex -> listOf(
            group.copy(
                startIndex = group.startIndex + insertedTrackCount,
                endIndexExclusive = group.endIndexExclusive + insertedTrackCount,
            ),
        )
        else -> listOf(
            group.copy(endIndexExclusive = insertionIndex),
            group.copy(
                id = "${group.id}:after:$insertionIndex",
                startIndex = insertionIndex + insertedTrackCount,
                endIndexExclusive = group.endIndexExclusive + insertedTrackCount,
            ),
        )
    }
}.mapNotNull { it.normalized(resultingTrackCount) }

internal fun List<PlaybackQueueGroup>.afterReordering(
    originalIndexes: List<Int>,
    resultingTrackCount: Int,
): List<PlaybackQueueGroup> = flatMap { group ->
    val reorderedPositions = originalIndexes.mapIndexedNotNull { position, originalIndex ->
        position.takeIf { originalIndex in group }
    }
    if (reorderedPositions.isEmpty()) return@flatMap emptyList()

    buildList {
        var runStart = reorderedPositions.first()
        var previous = runStart
        var runIndex = 0

        fun addRun(endExclusive: Int) {
            add(
                group.copy(
                    id = if (runIndex == 0) group.id else "${group.id}:reordered:$runStart",
                    startIndex = runStart,
                    endIndexExclusive = endExclusive,
                ),
            )
            runIndex += 1
        }

        reorderedPositions.drop(1).forEach { position ->
            if (position != previous + 1) {
                addRun(previous + 1)
                runStart = position
            }
            previous = position
        }
        addRun(previous + 1)
    }
}.normalized(resultingTrackCount)

internal fun List<PlaybackQueueGroup>.normalized(trackCount: Int): List<PlaybackQueueGroup> =
    mapNotNull { it.normalized(trackCount) }
        .sortedBy(PlaybackQueueGroup::startIndex)

internal fun String.isGeneratedContinuationOf(groupId: String): Boolean =
    startsWith("$groupId:after:") || startsWith("$groupId:reordered:")
