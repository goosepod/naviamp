package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.getString

/** Common vocabulary for native media surfaces; hosts only map native IDs and icons. */
enum class NaviampExternalPlaybackAction {
    StartRadio, Favorite, Unfavorite, ShuffleOn, ShuffleOff, RepeatOff, RepeatQueue, RepeatTrack,
}

data class NaviampExternalPlaybackLabels(
    val queueTitle: String,
    val actions: Map<NaviampExternalPlaybackAction, String>,
)

/** Reuses the maintained shared translations rather than introducing host-owned copy. */
suspend fun loadNaviampExternalPlaybackLabels(): NaviampExternalPlaybackLabels {
    val shuffle = getString(Res.string.transport_shuffle)
    return NaviampExternalPlaybackLabels(
        queueTitle = getString(Res.string.player_queue),
        actions = mapOf(
            NaviampExternalPlaybackAction.StartRadio to getString(Res.string.tv_start_radio),
            NaviampExternalPlaybackAction.Favorite to getString(Res.string.tv_favorite),
            NaviampExternalPlaybackAction.Unfavorite to getString(Res.string.playlist_item_unfavorite),
            NaviampExternalPlaybackAction.ShuffleOn to getString(Res.string.radio_status, shuffle, getString(Res.string.common_enabled)),
            NaviampExternalPlaybackAction.ShuffleOff to getString(Res.string.radio_status, shuffle, getString(Res.string.common_off)),
            NaviampExternalPlaybackAction.RepeatOff to getString(Res.string.tv_repeat_off),
            NaviampExternalPlaybackAction.RepeatQueue to getString(Res.string.tv_repeat_all),
            NaviampExternalPlaybackAction.RepeatTrack to getString(Res.string.tv_repeat_one),
        ),
    )
}
