package app.naviamp.ui

import androidx.compose.runtime.Composable
import app.naviamp.domain.settings.*
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun televisionBackgroundLabel(value: AppBackgroundStyle): String = when (value) {
    AppBackgroundStyle.Aurora -> stringResource(Res.string.tv_aurora)
    AppBackgroundStyle.AlbumBlur -> stringResource(Res.string.tv_album_blur)
    AppBackgroundStyle.SingleColor -> stringResource(Res.string.tv_single_color_uppercase)
}

@Composable
internal fun televisionAuroraToneLabel(value: AuroraTone): String = when (value) {
    AuroraTone.Light -> stringResource(Res.string.aurora_tone_light)
    AuroraTone.Dark -> stringResource(Res.string.mix_bias_balanced)
    AuroraTone.DeepDark -> stringResource(Res.string.aurora_tone_dark)
}

@Composable
internal fun televisionReplayGainLabel(value: ReplayGainMode): String = when (value) {
    ReplayGainMode.Off -> stringResource(Res.string.common_off)
    ReplayGainMode.Track -> stringResource(Res.string.tv_track)
    ReplayGainMode.Album -> stringResource(Res.string.tv_album)
}

@Composable
internal fun televisionSampleRateLabel(value: SampleRateMatching): String = when (value) {
    SampleRateMatching.Disabled -> stringResource(Res.string.tv_disabled)
    SampleRateMatching.Smart -> stringResource(Res.string.tv_smart)
    SampleRateMatching.Strict -> stringResource(Res.string.tv_strict)
}

@Composable
internal fun televisionDestinationLabel(value: NaviampTelevisionDestination): String = when (value) {
    NaviampTelevisionDestination.Home -> stringResource(Res.string.home_music_title)
    NaviampTelevisionDestination.Library -> stringResource(Res.string.library_title)
    NaviampTelevisionDestination.Playlists -> stringResource(Res.string.playlists_title)
    NaviampTelevisionDestination.Search -> stringResource(Res.string.search_title)
    NaviampTelevisionDestination.NowPlaying -> stringResource(Res.string.tv_now_playing_uppercase)
    NaviampTelevisionDestination.Settings -> stringResource(Res.string.nav_settings)
}

@Composable
internal fun televisionPlaylistSortLabel(value: SharedPlaylistSortMode): String = when (value) {
    SharedPlaylistSortMode.Alphabetical -> stringResource(Res.string.tv_alphabetical_sort)
    SharedPlaylistSortMode.RecentlyPlayed -> stringResource(Res.string.mix_bias_recent)
}

@Composable
internal fun televisionSampleRateSubtitle(value: SampleRateMatching): String = stringResource(when (value) {
    SampleRateMatching.Disabled -> Res.string.tv_sample_rates_are_fixed
    SampleRateMatching.Smart -> Res.string.tv_sample_rates_are_adjusted_when_playback_is_idle
    SampleRateMatching.Strict -> Res.string.tv_sample_rates_are_adjusted_for_each_track_as_needed
})
