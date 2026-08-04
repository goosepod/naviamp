package app.naviamp.domain.radio

import app.naviamp.domain.Track
import app.naviamp.domain.settings.RecentRadioStream

data class SeededRadioBuildEffectApplier(
    val rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
    val appendFetchedTracks: (List<Track>) -> Unit = {},
    val setStatus: (String) -> Unit = {},
)

data class RadioRequestStartEffectApplier(
    val rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
    val startQueue: (Track, List<Track>) -> Unit,
    val setStatus: (String?) -> Unit = {},
)

fun applyRadioRequestStartResult(
    result: RadioRequestStartResult,
    emptyStatus: String,
    failureStatus: String,
    applier: RadioRequestStartEffectApplier,
): Boolean =
    when (result) {
        is RadioRequestStartResult.Ready -> {
            result.recentRadioStream?.let(applier.rememberRecentRadioStream)
            applier.setStatus(null)
            applier.startQueue(result.firstTrack, result.queue)
            true
        }
        RadioRequestStartResult.Empty -> {
            applier.setStatus(emptyStatus)
            false
        }
        is RadioRequestStartResult.Failed -> {
            applier.setStatus(result.error.message ?: failureStatus)
            false
        }
    }

fun applyTrackRadioLoadResult(
    result: TrackRadioLoadResult,
    applyTracks: (List<Track>) -> Unit,
    setStatus: (String) -> Unit,
): Boolean =
    when (result) {
        is TrackRadioLoadResult.Ready -> {
            applyTracks(result.tracks)
            true
        }
        else -> {
            setStatus(trackRadioLoadStatus(result) ?: "Could not load track radio.")
            false
        }
    }

fun applySeededRadioBuildResult(
    result: SeededRadioBuildResult,
    requestIsCurrent: Boolean,
    buildingStatus: String,
    failureStatus: String,
    applier: SeededRadioBuildEffectApplier,
): Boolean {
    when (result) {
        is SeededRadioBuildResult.Ready -> {
            result.recentRadioStream?.let(applier.rememberRecentRadioStream)
            if (!requestIsCurrent) return false
            applier.appendFetchedTracks(result.queue.drop(1))
            applier.setStatus(buildingStatus)
            return true
        }
        is SeededRadioBuildResult.Failed -> {
            if (requestIsCurrent) applier.setStatus(result.error.message ?: failureStatus)
            return false
        }
    }
}

fun applySeededRadioExpansionResult(
    result: SeededRadioExpansionResult,
    requestIsCurrent: Boolean,
    failureStatus: String? = null,
    appendFetchedTracks: (List<Track>) -> Unit,
    setStatus: (String) -> Unit = {},
): Boolean {
    when (result) {
        is SeededRadioExpansionResult.Ready -> {
            if (!requestIsCurrent) return false
            appendFetchedTracks(result.fetchedTracks)
            return true
        }
        is SeededRadioExpansionResult.Failed -> {
            if (requestIsCurrent && failureStatus != null) {
                setStatus(result.error.message ?: failureStatus)
            }
            return false
        }
    }
}
