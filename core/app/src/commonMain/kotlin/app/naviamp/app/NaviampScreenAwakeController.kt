package app.naviamp.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A native display-inhibition resource. Release must be idempotent and affect only this lease. */
fun interface NaviampScreenAwakeLease {
    fun release()
}

/** Native acquisition only; Core decides when display inhibition is appropriate. */
fun interface NaviampScreenAwakeEffect {
    fun acquire(reason: String): NaviampScreenAwakeLease?
}

enum class NaviampScreenAwakeStatus { Unavailable, Inactive, Active, Failed }

/** One visible app surface owns one lease, independently of audio playback and CPU wake locks. */
class NaviampScreenAwakeController(private val effect: NaviampScreenAwakeEffect?) {
    private val mutableStatus = MutableStateFlow(
        if (effect == null) NaviampScreenAwakeStatus.Unavailable else NaviampScreenAwakeStatus.Inactive)
    val status = mutableStatus.asStateFlow()
    private var lease: NaviampScreenAwakeLease? = null
    private var wanted = false
    private var closed = false

    fun update(enabled: Boolean, visible: Boolean, reason: String) {
        if (closed) return
        val next = enabled && visible && effect != null
        if (next == wanted) return
        wanted = next
        if (!next) {
            release()
        } else if (lease != null) {
            mutableStatus.value = NaviampScreenAwakeStatus.Active
        } else {
            lease = runCatching { effect?.acquire(reason) }.getOrNull()
            mutableStatus.value = if (lease == null) NaviampScreenAwakeStatus.Failed else NaviampScreenAwakeStatus.Active
        }
    }

    fun close() {
        closed = true
        wanted = false
        release()
    }

    private fun release() {
        val current = lease
        if (current != null && runCatching { current.release() }.isFailure) {
            mutableStatus.value = NaviampScreenAwakeStatus.Failed
            return // Retain ownership so cleanup can be retried; never report a failed release as inactive.
        }
        lease = null
        mutableStatus.value = if (effect == null) NaviampScreenAwakeStatus.Unavailable else NaviampScreenAwakeStatus.Inactive
    }
}
