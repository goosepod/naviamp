package app.naviamp.presentation

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What started a connection attempt; each connect path names its own. */
enum class NaviampConnectionAttemptTrigger {
    EditorSave,
    SavedConnectionButton,
    SavedSourceSelection,
    AccountSwitch,
    StartupRestore,
    OfflineRecovery,
    SettingsImport,
    ConnectProvisioning,
    ProvisioningCredential,
    LibrarySources,
}

enum class NaviampConnectionAttemptOutcome {
    /** Still running. */
    InProgress,
    Connected,
    /** Refused because another attempt was already connecting. */
    Refused,
    /** The form failed local validation before any network request. */
    Invalid,
    Failed,
    Cancelled,
    /** Failed or cancelled, and the session it would have replaced was kept. */
    KeptPrevious,
    /** Succeeded, but its caller no longer wanted it, so the previous state was restored. */
    Discarded,
    Offline,
}

/** One connection attempt. Holds no credentials, URLs or form contents. */
data class NaviampConnectionAttemptRecord(
    val sequence: Long,
    val startedAtEpochMillis: Long,
    val trigger: NaviampConnectionAttemptTrigger,
    /** "saved:<source id>" or "form" (with "/edit:<source id>" when saving a saved connection). */
    val request: String,
    /** The route shown when the attempt started. */
    val route: String,
    /** True when a connection editor was open as the attempt started; the attempt may close it. */
    val editorOpen: Boolean,
    val restoring: Boolean? = null,
    val clearsPlayback: Boolean? = null,
    val outcome: NaviampConnectionAttemptOutcome = NaviampConnectionAttemptOutcome.InProgress,
    /** A sanitized status for failures, as shown to the user. */
    val detail: String? = null,
    val durationMillis: Long? = null,
)

/**
 * A bounded, in-memory history of connection attempts, newest first, for diagnosing
 * unexpected reconnects. It never persists and holds no secrets.
 */
class NaviampConnectionAttemptLog(
    private val capacity: Int = 25,
    private val nowEpochMillis: () -> Long = ::naviampNowEpochMillis,
) {
    private var nextSequence = 1L
    private var records = emptyList<NaviampConnectionAttemptRecord>()

    fun records(): List<NaviampConnectionAttemptRecord> = records

    fun started(
        trigger: NaviampConnectionAttemptTrigger,
        request: NaviampCoreConnectionRequest,
        route: String,
        editorOpen: Boolean,
    ): Long {
        val sequence = nextSequence++
        val requestLabel = when (request) {
            is NaviampCoreConnectionRequest.Saved -> "saved:${request.id}"
            is NaviampCoreConnectionRequest.Form -> request.savedConnectionId?.let { "form/edit:$it" } ?: "form"
        }
        records = (listOf(NaviampConnectionAttemptRecord(sequence, nowEpochMillis(), trigger, requestLabel, route, editorOpen)) +
            records).take(capacity)
        return sequence
    }

    fun planned(sequence: Long, restoring: Boolean, clearsPlayback: Boolean) =
        update(sequence) { it.copy(restoring = restoring, clearsPlayback = clearsPlayback) }

    fun finished(sequence: Long, outcome: NaviampConnectionAttemptOutcome, detail: String? = null) =
        update(sequence) {
            it.copy(outcome = outcome, detail = detail, durationMillis = (nowEpochMillis() - it.startedAtEpochMillis).coerceAtLeast(0L))
        }

    private fun update(sequence: Long, transform: (NaviampConnectionAttemptRecord) -> NaviampConnectionAttemptRecord) {
        records = records.map { if (it.sequence == sequence) transform(it) else it }
    }

    /** Diagnostics rows, newest first. */
    @OptIn(ExperimentalTime::class)
    fun diagnosticRows(): List<Pair<String, String>> = records.map { record ->
        val time = Instant.fromEpochMilliseconds(record.startedAtEpochMillis).toString()
        val facts = buildList {
            add(record.request)
            add("route=${record.route}")
            if (record.editorOpen) add("editor open")
            record.restoring?.let { add("restoring=$it") }
            record.clearsPlayback?.let { add("clears playback=$it") }
            add(record.outcome.name)
            record.detail?.let { add(it) }
            record.durationMillis?.let { add("$it ms") }
        }
        "#${record.sequence} $time ${record.trigger.name}" to facts.joinToString(" · ")
    }
}
