package app.naviamp.app

import app.naviamp.domain.TrackId

data class NaviampRadioContinuationState(
    val sessionId: Int = 0,
    val active: Boolean = false,
    val refilling: Boolean = false,
    val lastRefillSeedId: TrackId? = null,
)

class NaviampRadioContinuationController(
    initialState: NaviampRadioContinuationState = NaviampRadioContinuationState(),
) {
    var state: NaviampRadioContinuationState = initialState
        private set

    fun stop() {
        state = state.copy(
            sessionId = state.sessionId + 1,
            active = false,
            refilling = false,
            lastRefillSeedId = null,
        )
    }

    fun start(
        seedId: TrackId? = null,
        refilling: Boolean,
    ): Int {
        state = NaviampRadioContinuationState(
            sessionId = state.sessionId + 1,
            active = true,
            refilling = refilling,
            lastRefillSeedId = seedId,
        )
        return state.sessionId
    }

    fun beginRefill(seedId: TrackId): Int {
        state = state.copy(refilling = true, lastRefillSeedId = seedId)
        return state.sessionId
    }

    fun isCurrent(sessionId: Int): Boolean = state.active && state.sessionId == sessionId

    fun finishRefill(sessionId: Int) {
        if (state.sessionId == sessionId) state = state.copy(refilling = false)
    }
}
