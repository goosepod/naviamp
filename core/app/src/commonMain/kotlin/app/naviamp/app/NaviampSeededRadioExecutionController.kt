package app.naviamp.app

import app.naviamp.domain.TrackId
import app.naviamp.domain.radio.SeededRadioBuildEffectApplier
import app.naviamp.domain.radio.SeededRadioBuildResult
import app.naviamp.domain.radio.SeededRadioExpansionResult
import app.naviamp.domain.radio.applySeededRadioBuildResult
import app.naviamp.domain.radio.applySeededRadioExpansionResult
import app.naviamp.domain.settings.RecentRadioStream

data class NaviampSeededRadioExecutionEffects(
    val requestIsCurrent: () -> Boolean = { true },
    val expansionIsCurrent: () -> Boolean = requestIsCurrent,
    val rememberRecentRadioStream: (RecentRadioStream) -> Unit = {},
    val appendFetchedTracks: (List<app.naviamp.domain.Track>) -> Unit,
    val appendExpansionTracks: (List<app.naviamp.domain.Track>) -> Unit = appendFetchedTracks,
    val queueSize: () -> Int,
    val setStatus: (String?) -> Unit = {},
)

class NaviampSeededRadioExecutionController(
    private val continuation: NaviampRadioContinuationController,
) {
    fun begin(seedTrackId: TrackId): Int =
        continuation.start(seedTrackId, refilling = true)

    suspend fun execute(
        sessionId: Int,
        label: String,
        loadInitial: suspend () -> SeededRadioBuildResult,
        loadExpansions: List<suspend () -> SeededRadioExpansionResult>,
        effects: NaviampSeededRadioExecutionEffects,
        completedStatus: String? = null,
        failureStatus: String = "Could not build $label.",
        expandAfterFailedInitial: Boolean = true,
    ) {
        fun isCurrent(): Boolean = continuation.isCurrent(sessionId) && effects.requestIsCurrent()
        fun expansionIsCurrent(): Boolean = continuation.isCurrent(sessionId) && effects.expansionIsCurrent()

        var initialApplied = false
        try {
            initialApplied = applySeededRadioBuildResult(
                result = loadInitial(),
                requestIsCurrent = isCurrent(),
                buildingStatus = "Building $label queue...",
                failureStatus = failureStatus,
                applier = SeededRadioBuildEffectApplier(
                    rememberRecentRadioStream = effects.rememberRecentRadioStream,
                    appendFetchedTracks = effects.appendFetchedTracks,
                    setStatus = effects.setStatus,
                ),
            )
        } finally {
            continuation.finishRefill(sessionId)
        }
        if (!initialApplied && !expandAfterFailedInitial) return

        loadExpansions.forEach { loadExpansion ->
            if (!expansionIsCurrent()) return
            val applied = applySeededRadioExpansionResult(
                result = loadExpansion(),
                requestIsCurrent = expansionIsCurrent(),
                appendFetchedTracks = effects.appendExpansionTracks,
            )
            if (applied) {
                effects.setStatus("Building $label queue (${effects.queueSize()} tracks)...")
            }
        }

        if (expansionIsCurrent()) effects.setStatus(completedStatus)
    }
}
