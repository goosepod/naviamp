package app.naviamp.presentation

/** Non-secret lifecycle evidence; never accepts provider payloads, credentials, names, or exceptions. */
enum class NaviampCoreConnectTraceKind {
    TargetSessionStarted, ControllerSessionStarted, InitialSetupAuthorized,
    SetupOfferSent, SetupOfferReceived, SetupValidationStarted, SetupValidationSucceeded,
    SetupValidationFailed, SetupRejected, SetupResultReceived, SetupResultTimedOut,
    SetupHeartbeatReceived, SetupHeartbeatConfirmed,
    HeartbeatOrWriteTimedOut, TargetReceiveEnded, ControllerReceiveEnded, SessionClosed,
}

data class NaviampCoreConnectTraceEvent(
    val epochMillis: Long,
    val kind: NaviampCoreConnectTraceKind,
    val sessionId: String?,
    val setupId: String? = null,
) {
    /** Session/setup IDs are public protocol correlation nonces, never pairing codes or trust keys. */
    fun logLine(): String = "NaviampConnect time=$epochMillis event=$kind" +
        " session=${sessionId.logToken()} setup=${setupId.logToken()}"
}

private fun String?.logToken(): String = this?.take(120)
    ?.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
    ?.joinToString("") ?: "none"
