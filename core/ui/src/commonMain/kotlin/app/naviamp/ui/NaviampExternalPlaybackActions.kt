package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Common vocabulary for native media surfaces; hosts only map native IDs and icons. */
enum class NaviampExternalPlaybackAction {
    StartRadio, Favorite, Unfavorite, ShuffleOn, ShuffleOff, RepeatOff, RepeatQueue, RepeatTrack,
}

/** Standard transport buttons that a native surface labels itself, such as notification actions. */
enum class NaviampExternalTransportLabel { Play, Pause, Previous, Next }

data class NaviampExternalPlaybackLabels(
    val queueTitle: String,
    val actions: Map<NaviampExternalPlaybackAction, String>,
    val transport: Map<NaviampExternalTransportLabel, String> = emptyMap(),
    val playback: String = "",
)

/** Reuses the maintained shared translations rather than introducing host-owned copy. */
suspend fun loadNaviampExternalPlaybackLabels(): NaviampExternalPlaybackLabels {
    val shuffle = naviampString(Res.string.transport_shuffle)
    return NaviampExternalPlaybackLabels(
        queueTitle = naviampString(Res.string.player_queue),
        actions = mapOf(
            NaviampExternalPlaybackAction.StartRadio to naviampString(Res.string.tv_start_radio),
            NaviampExternalPlaybackAction.Favorite to naviampString(Res.string.tv_favorite),
            NaviampExternalPlaybackAction.Unfavorite to naviampString(Res.string.playlist_item_unfavorite),
            NaviampExternalPlaybackAction.ShuffleOn to naviampString(Res.string.radio_status, shuffle, naviampString(Res.string.common_enabled)),
            NaviampExternalPlaybackAction.ShuffleOff to naviampString(Res.string.radio_status, shuffle, naviampString(Res.string.common_off)),
            NaviampExternalPlaybackAction.RepeatOff to naviampString(Res.string.tv_repeat_off),
            NaviampExternalPlaybackAction.RepeatQueue to naviampString(Res.string.tv_repeat_all),
            NaviampExternalPlaybackAction.RepeatTrack to naviampString(Res.string.tv_repeat_one),
        ),
        transport = mapOf(
            NaviampExternalTransportLabel.Play to naviampString(Res.string.transport_play),
            NaviampExternalTransportLabel.Pause to naviampString(Res.string.transport_pause),
            NaviampExternalTransportLabel.Previous to naviampString(Res.string.transport_previous),
            NaviampExternalTransportLabel.Next to naviampString(Res.string.transport_next),
        ),
        playback = naviampString(Res.string.settings_category_playback_title),
    )
}

/** Labels for the selected language now, and again after every language change. */
fun naviampExternalPlaybackLabelUpdates(
    load: suspend () -> NaviampExternalPlaybackLabels = ::loadNaviampExternalPlaybackLabels,
): Flow<NaviampExternalPlaybackLabels> = NaviampResourceLanguage.selected.map { load() }
