package app.naviamp.app

import app.naviamp.domain.Playlist
import app.naviamp.domain.provider.PendingPlaybackAction
import app.naviamp.domain.provider.PlaylistPlaybackPreparedApplication
import app.naviamp.domain.provider.PlaylistPlaybackStartPlan
import app.naviamp.domain.provider.playlistPlaybackCompletionApplication
import app.naviamp.domain.provider.playlistPlaybackErrorMessage
import app.naviamp.domain.provider.playlistPlaybackStartApplication
import app.naviamp.domain.provider.playlistPlaybackStartPlan

data class NaviampPlaylistPlaybackEffects(
    val pendingPlaybackAction: () -> PendingPlaybackAction?,
    val setPendingPlaybackAction: (PendingPlaybackAction?) -> Unit,
    val setStatus: (String?) -> Unit,
    val applyPrepared: (PlaylistPlaybackPreparedApplication) -> Unit,
)

class NaviampPlaylistPlaybackController {
    fun begin(
        playlist: Playlist,
        shuffle: Boolean,
        effects: NaviampPlaylistPlaybackEffects,
    ): PlaylistPlaybackStartPlan? {
        val plan = playlistPlaybackStartPlan(playlist, shuffle, effects.pendingPlaybackAction())
        val application = playlistPlaybackStartApplication(plan)
        effects.setStatus(application.status)
        if (!plan.shouldStart) return null
        effects.setPendingPlaybackAction(application.pendingPlaybackAction)
        return plan
    }

    suspend fun execute(
        playlist: Playlist,
        plan: PlaylistPlaybackStartPlan,
        loadPrepared: suspend () -> PlaylistPlaybackPreparedApplication,
        effects: NaviampPlaylistPlaybackEffects,
    ) {
        try {
            effects.applyPrepared(loadPrepared())
        } catch (error: Exception) {
            effects.setStatus(playlistPlaybackErrorMessage(error, playlist))
        } finally {
            effects.setPendingPlaybackAction(
                playlistPlaybackCompletionApplication(
                    pending = effects.pendingPlaybackAction(),
                    completed = plan.action,
                ).pendingPlaybackAction,
            )
        }
    }
}
