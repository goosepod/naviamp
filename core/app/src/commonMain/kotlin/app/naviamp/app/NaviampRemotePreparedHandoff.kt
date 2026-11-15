package app.naviamp.app

/** Identity of a play occurrence, not a track ID. Repeats of the same song have distinct IDs. */
data class NaviampRemoteQueueOccurrence(val queueRevision: Long, val occurrenceId: Long)

/** Issue a distinct scoped URL for each occurrence, including consecutive copies of one song. */
data class NaviampRemoteQueuedMedia(val occurrence: NaviampRemoteQueueOccurrence, val mediaUrl: String)

class NaviampRemotePreparation internal constructor(
    internal val owner: Any,
    internal val selectionId: Long,
    internal val revision: Long,
    internal val current: NaviampRemoteQueueOccurrence,
)

sealed interface NaviampRemoteHandoffObservation {
    data object Ignored : NaviampRemoteHandoffObservation
    data object Current : NaviampRemoteHandoffObservation
    data object UnexpectedResource : NaviampRemoteHandoffObservation
    data class Promoted(val media: NaviampRemoteQueuedMedia) : NaviampRemoteHandoffObservation
}

/**
 * Small shared current/next reconciliation ledger, not a second playback queue. The existing queue
 * owner supplies occurrence identities and consumes Promoted exactly once without another Load/Play.
 * Calls are serialized by the common owner. It clears/replaces the renderer slot before revoking
 * old leases; this ledger owns neither leases, transport commands nor queue navigation policy.
 */
class NaviampRemotePreparedHandoff {
    private val identity = Any()
    private var selectionId: Long? = null
    private var revision = 0L
    private var lastObservation = -1L
    private val retiredUrls = ArrayDeque<String>()
    var current: NaviampRemoteQueuedMedia? = null
        private set
    var next: NaviampRemoteQueuedMedia? = null
        private set

    /** Also used after a queue edit/skip; pending preparations from the old queue are invalid. */
    fun reset(selectionId: Long, current: NaviampRemoteQueuedMedia) {
        require(selectionId >= 0 && selectionId >= (this.selectionId ?: 0))
        require(current.mediaUrl.isNotBlank())
        if (this.selectionId != selectionId) {
            lastObservation = -1
            retiredUrls.clear()
        } else if (this.current?.mediaUrl != current.mediaUrl) {
            this.current?.mediaUrl?.let(::retire)
        }
        this.selectionId = selectionId
        this.current = current
        next = null
        revision++
    }

    fun prepareNext(selectionId: Long): NaviampRemotePreparation? {
        if (this.selectionId != selectionId) return null
        val active = current ?: return null
        next = null
        return NaviampRemotePreparation(identity, selectionId, ++revision, active.occurrence)
    }

    /** Call only after the renderer accepted SetNext; command acceptance is not promotion. */
    fun acceptPreparedNext(ticket: NaviampRemotePreparation, media: NaviampRemoteQueuedMedia): Boolean {
        val active = current ?: return false
        if (ticket.owner !== identity || selectionId != ticket.selectionId || revision != ticket.revision || active.occurrence != ticket.current) return false
        if (media.occurrence.queueRevision != active.occurrence.queueRevision || media.occurrence == active.occurrence) return false
        if (media.mediaUrl.isBlank() || media.mediaUrl == active.mediaUrl || media.mediaUrl in retiredUrls) return false
        next = media
        revision++
        return true
    }

    /**
     * observationRevision is assigned by the common protocol reconciler after ordering events/polls;
     * it is NOT a raw wrapping GENA SEQ. Only observed Playing on the prepared URI promotes it.
     */
    fun observe(
        selectionId: Long,
        observationRevision: Long,
        mediaUrl: String,
        playing: Boolean,
    ): NaviampRemoteHandoffObservation {
        if (this.selectionId != selectionId || current == null || observationRevision <= lastObservation) {
            return NaviampRemoteHandoffObservation.Ignored
        }
        lastObservation = observationRevision
        if (mediaUrl == current?.mediaUrl) return NaviampRemoteHandoffObservation.Current
        if (mediaUrl in retiredUrls) return NaviampRemoteHandoffObservation.Ignored
        val prepared = next
        if (prepared == null || mediaUrl != prepared.mediaUrl) return NaviampRemoteHandoffObservation.UnexpectedResource
        if (!playing) return NaviampRemoteHandoffObservation.Ignored
        current?.mediaUrl?.let(::retire)
        current = prepared
        next = null
        revision++
        return NaviampRemoteHandoffObservation.Promoted(prepared)
    }

    fun clear(selectionId: Long) {
        if (this.selectionId != selectionId) return
        current = null
        next = null
        revision++
    }

    private fun retire(url: String) {
        retiredUrls.addLast(url)
        // Late echoes across the immediate transition are common. Older history belongs to the
        // protocol/session reconciler, not an unbounded list of credential-bearing media URLs.
        while (retiredUrls.size > 4) retiredUrls.removeFirst()
    }
}
