package app.naviamp.app

/** Ephemeral consent for an explicitly displayed pairing code; never persisted with device trust. */
class NaviampConnectInitialSetupAuthorization {
    private data class Offer(val id: String, val expiresAt: Long, val emptyTarget: Boolean)
    private data class Grant(val sessionId: String, val expiresAt: Long)
    private var offer: Offer? = null
    private var grant: Grant? = null

    fun showCode(offerId: String, expiresAtEpochMillis: Long, targetIsEmpty: Boolean) {
        offer = Offer(offerId, expiresAtEpochMillis, targetIsEmpty)
    }

    fun acceptsCodePairing(offerId: String, nowEpochMillis: Long): Boolean =
        offer?.let { it.id == offerId && nowEpochMillis < it.expiresAt } == true

    fun paired(offerId: String, sessionId: String, targetIsEmpty: Boolean, nowEpochMillis: Long) {
        val consent = offer?.takeIf { it.id == offerId } ?: return
        offer = null
        if (nowEpochMillis < consent.expiresAt && consent.emptyTarget && targetIsEmpty) {
            grant = Grant(sessionId, consent.expiresAt)
        }
    }

    fun permitsSetup(sessionId: String, targetIsEmpty: Boolean, nowEpochMillis: Long): Boolean =
        grant?.let { it.sessionId == sessionId && targetIsEmpty && nowEpochMillis < it.expiresAt } == true

    fun stopShowingCode() { offer = null }
    fun setupCompleted(sessionId: String) { if (grant?.sessionId == sessionId) grant = null }
    fun sessionClosed(sessionId: String) { setupCompleted(sessionId) }
}
