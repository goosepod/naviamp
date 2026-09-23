package app.naviamp.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NaviampRemoteOutputKind { Connect, Cast }

data class NaviampRemoteOutputTarget(
    val kind: NaviampRemoteOutputKind,
    val id: String,
    val displayName: String,
) {
    init {
        require(id.isNotBlank()) { "A playback target ID is required." }
        require(displayName.isNotBlank()) { "A playback target name is required." }
    }
}

enum class NaviampRemoteOutputPhase {
    Armed,
    Connecting,
    Connected,
    Reconnecting,
    Unavailable,
    Incompatible,
}

sealed interface NaviampPlaybackOutputSelection {
    data object Local : NaviampPlaybackOutputSelection

    data class Remote(
        val target: NaviampRemoteOutputTarget,
        val selectionId: Long,
        val phase: NaviampRemoteOutputPhase,
        val playbackAuthorityActive: Boolean = false,
    ) : NaviampPlaybackOutputSelection
}

/**
 * Shared ownership of the one active playback output. The selection ID invalidates callbacks from
 * a displaced session, including when the same device is selected again after a disconnect.
 */
class NaviampPlaybackOutputSelectionController {
    private val mutableState = MutableStateFlow<NaviampPlaybackOutputSelection>(NaviampPlaybackOutputSelection.Local)
    private var nextSelectionId = 0L

    val state: StateFlow<NaviampPlaybackOutputSelection> = mutableState.asStateFlow()

    fun select(target: NaviampRemoteOutputTarget): Long {
        val selectionId = ++nextSelectionId
        mutableState.value = NaviampPlaybackOutputSelection.Remote(
            target = target,
            selectionId = selectionId,
            phase = NaviampRemoteOutputPhase.Armed,
        )
        return selectionId
    }

    fun connecting(selectionId: Long): Boolean = update(selectionId) {
        it.copy(phase = NaviampRemoteOutputPhase.Connecting, playbackAuthorityActive = false)
    }

    fun connected(selectionId: Long, displayName: String? = null): Boolean = update(selectionId) {
        it.copy(
            target = displayName?.takeIf(String::isNotBlank)?.let { name ->
                it.target.copy(displayName = name)
            } ?: it.target,
            phase = NaviampRemoteOutputPhase.Connected,
        )
    }

    fun activatePlaybackAuthority(selectionId: Long): Boolean {
        val remote = current(selectionId) ?: return false
        if (remote.phase != NaviampRemoteOutputPhase.Connected) return false
        mutableState.value = remote.copy(playbackAuthorityActive = true)
        return true
    }

    fun reconnecting(selectionId: Long): Boolean = update(selectionId) {
        it.copy(phase = NaviampRemoteOutputPhase.Reconnecting, playbackAuthorityActive = false)
    }

    fun unavailable(selectionId: Long): Boolean = update(selectionId) {
        it.copy(phase = NaviampRemoteOutputPhase.Unavailable, playbackAuthorityActive = false)
    }

    fun incompatible(selectionId: Long): Boolean = update(selectionId) {
        it.copy(phase = NaviampRemoteOutputPhase.Incompatible, playbackAuthorityActive = false)
    }

    fun selectLocal() {
        ++nextSelectionId
        mutableState.value = NaviampPlaybackOutputSelection.Local
    }

    fun hasRemotePlaybackAuthority(): Boolean =
        (state.value as? NaviampPlaybackOutputSelection.Remote)?.let {
            it.phase == NaviampRemoteOutputPhase.Connected && it.playbackAuthorityActive
        } == true

    private fun current(selectionId: Long): NaviampPlaybackOutputSelection.Remote? =
        (mutableState.value as? NaviampPlaybackOutputSelection.Remote)
            ?.takeIf { it.selectionId == selectionId }

    private fun update(
        selectionId: Long,
        transform: (NaviampPlaybackOutputSelection.Remote) -> NaviampPlaybackOutputSelection.Remote,
    ): Boolean {
        val remote = current(selectionId) ?: return false
        mutableState.value = transform(remote)
        return true
    }
}
