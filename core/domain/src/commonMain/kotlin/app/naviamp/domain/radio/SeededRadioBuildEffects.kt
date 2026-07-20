package app.naviamp.domain.radio

import app.naviamp.domain.Track
import app.naviamp.domain.settings.RecentRadioStream

data class SeededRadioBuildEffectApplier(
    val rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
    val appendFetchedTracks: (List<Track>) -> Unit = {},
    val setStatus: (String) -> Unit = {},
)

fun applySeededRadioBuildResult(
    result: SeededRadioBuildResult,
    requestIsCurrent: Boolean,
    buildingStatus: String,
    failureStatus: String,
    applier: SeededRadioBuildEffectApplier,
) {
    when (result) {
        is SeededRadioBuildResult.Ready -> {
            result.recentRadioStream?.let(applier.rememberRecentRadioStream)
            if (!requestIsCurrent) return
            applier.appendFetchedTracks(result.queue.drop(1))
            applier.setStatus(buildingStatus)
        }
        is SeededRadioBuildResult.Failed -> {
            if (requestIsCurrent) applier.setStatus(result.error.message ?: failureStatus)
        }
    }
}
