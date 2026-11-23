package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
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
    val notificationChannelDescription: String = "",
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
        notificationChannelDescription = naviampString(Res.string.notification_channel_playback_description),
    )
}

/** Top-level sections a native media browser (such as Android Auto) lists at its root. */
enum class NaviampExternalBrowseSection { Playlists, RecentTracks, Radio, RecentAlbums, Queue, NowPlaying }

data class NaviampExternalBrowseSectionLabel(val title: String, val subtitle: String)

data class NaviampExternalBrowseLabels(val sections: Map<NaviampExternalBrowseSection, NaviampExternalBrowseSectionLabel>) {
    fun title(section: NaviampExternalBrowseSection): String = sections[section]?.title.orEmpty()
    fun subtitle(section: NaviampExternalBrowseSection): String = sections[section]?.subtitle.orEmpty()
}

/**
 * Resolved per browse request rather than cached with the playback labels, because the queue
 * subtitle is a plural of the current queue size.
 */
suspend fun loadNaviampExternalBrowseLabels(queueSize: Int): NaviampExternalBrowseLabels {
    val queueSubtitle = naviampPluralString(Res.plurals.tv_track_count, queueSize, queueSize)
    suspend fun label(title: StringResource, subtitle: StringResource) =
        NaviampExternalBrowseSectionLabel(naviampString(title), naviampString(subtitle))
    return NaviampExternalBrowseLabels(mapOf(
        NaviampExternalBrowseSection.Playlists to label(Res.string.playlists_title, Res.string.external_browse_playlists_subtitle),
        NaviampExternalBrowseSection.RecentTracks to label(Res.string.tv_home_recently_played, Res.string.external_browse_recent_tracks_subtitle),
        NaviampExternalBrowseSection.Radio to label(Res.string.settings_experience_related_tracks_title, Res.string.tv_home_stations),
        NaviampExternalBrowseSection.RecentAlbums to label(Res.string.home_recent_albums, Res.string.external_browse_recent_albums_subtitle),
        NaviampExternalBrowseSection.Queue to NaviampExternalBrowseSectionLabel(naviampString(Res.string.player_queue), queueSubtitle),
        NaviampExternalBrowseSection.NowPlaying to label(Res.string.tv_now_playing_uppercase, Res.string.external_browse_now_playing_subtitle),
    ))
}

/** Labels for the selected language now, and again after every language change. */
fun naviampExternalPlaybackLabelUpdates(
    load: suspend () -> NaviampExternalPlaybackLabels = ::loadNaviampExternalPlaybackLabels,
): Flow<NaviampExternalPlaybackLabels> = NaviampResourceLanguage.selected.map { load() }
