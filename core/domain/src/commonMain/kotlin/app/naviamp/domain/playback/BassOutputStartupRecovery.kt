package app.naviamp.domain.playback

import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.BassPlaybackSnapshot

/** One bounded repair for an output client that never consumes its already-decoded audio. */
internal data class BassOutputStartupRecoveryState(
    val initialAudibleSeconds: Double? = null,
    val waitingSinceMillis: Long? = null,
    val complete: Boolean = false,
)

internal data class BassOutputStartupRecoveryUpdate(
    val state: BassOutputStartupRecoveryState,
    val reinitializeOutput: Boolean = false,
)

internal fun planBassOutputStartupRecovery(
    previous: BassOutputStartupRecoveryState,
    snapshot: BassPlaybackSnapshot,
    nowMillis: Long,
): BassOutputStartupRecoveryUpdate {
    if (previous.complete) return BassOutputStartupRecoveryUpdate(previous)
    val audible = snapshot.progress.positionSeconds?.takeIf { it.isFinite() && it >= 0 }
    val decoded = snapshot.progress.decodedPositionSeconds?.takeIf { it.isFinite() && it >= 0 }
    val buffered = snapshot.bufferedOutputSeconds?.takeIf { it.isFinite() && it >= 0 }
    val initial = previous.initialAudibleSeconds ?: audible
    if (audible == null || (decoded == null && buffered == null)) {
        return BassOutputStartupRecoveryUpdate(previous.copy(complete = true))
    }
    if (snapshot.activeState == BassActiveState.Playing && initial != null && audible > initial + 0.001) {
        return BassOutputStartupRecoveryUpdate(previous.copy(complete = true))
    }
    // A paused channel or an input-starved decoder is not a failed output startup.
    if (snapshot.activeState != BassActiveState.Playing ||
        (decoded?.minus(audible) ?: 0.0) < 0.2 && (buffered ?: 0.0) < 0.2
    ) {
        return BassOutputStartupRecoveryUpdate(previous.copy(initialAudibleSeconds = initial, waitingSinceMillis = null))
    }
    val since = previous.waitingSinceMillis ?: nowMillis
    val stalled = nowMillis - since >= BassOutputStartupRecoveryDelayMillis
    return BassOutputStartupRecoveryUpdate(
        state = BassOutputStartupRecoveryState(initial, since, complete = stalled),
        reinitializeOutput = stalled,
    )
}

internal const val BassOutputStartupRecoveryDelayMillis: Long = 2_500
