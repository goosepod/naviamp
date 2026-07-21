package app.naviamp.presentation

import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.ui.DownloadedTrackActionRequest
import app.naviamp.ui.NaviampAlbumDetailActionRequest
import app.naviamp.ui.NaviampArtistAlbumActionRequest
import app.naviamp.ui.NaviampArtistDetailActionRequest
import app.naviamp.ui.NaviampInternetRadioStationEditUi
import app.naviamp.ui.NaviampMediaItemActionRequest
import app.naviamp.ui.NaviampPlaylistDetailActionRequest
import app.naviamp.ui.NaviampSavedConnectionUi
import app.naviamp.ui.NaviampStorageLocationUi
import app.naviamp.ui.NowPlayingCurrentTrackUiActionRequest
import app.naviamp.ui.NowPlayingDisplayActionRequest
import app.naviamp.ui.NowPlayingItemActionRequest
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSelectionActionRequest
import app.naviamp.ui.NowPlayingSleepTimerActionRequest
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedSonicMixBiasUi
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.StationRowActionRequest
import app.naviamp.ui.SharedRoute

/**
 * Every product intent emitted by the shared Naviamp UI.
 *
 * This is deliberately a Core contract. Platform hosts expose operating-system services; they do
 * not reinterpret clicks, menu selections, settings changes, or playback controls.
 */
sealed interface NaviampCoreCommand {
    sealed interface Navigation : NaviampCoreCommand {
        data class SelectRoute(val route: SharedRoute) : Navigation
        data object OpenNowPlaying : Navigation
        data object CloseNowPlaying : Navigation
        data object BackFromAlbum : Navigation
        data object BackFromArtist : Navigation
        data object BackFromPlaylist : Navigation
    }

    sealed interface Connection : NaviampCoreCommand {
        data class ChangeForm(val form: ConnectionFormState) : Connection
        data object Connect : Connection
        data object EditCurrent : Connection
        data object New : Connection
        data class Edit(val connection: NaviampSavedConnectionUi) : Connection
        data class Delete(val connection: NaviampSavedConnectionUi) : Connection
        data class ConnectSaved(val connection: NaviampSavedConnectionUi) : Connection
        data object CancelForm : Connection
    }

    sealed interface Settings : NaviampCoreCommand {
        data class ChangeInterface(val settings: InterfaceSettings) : Settings
        data class ChangePlayback(val settings: PlaybackSettings, val redownload: Boolean) : Settings
        data class ChangeCache(val settings: CacheSettings) : Settings
        data class ChangeDownloadLocation(val location: NaviampStorageLocationUi) : Settings
        data class ChangeAudioCacheLocation(val location: NaviampStorageLocationUi) : Settings
        data object OpenStats : Settings
        data object CloseStats : Settings
        data object ClearCache : Settings
        data object ClearLibrary : Settings
        data object RefreshLibrary : Settings
        data object ResetDatabase : Settings
    }

    sealed interface SettingsSync : NaviampCoreCommand {
        data class ChangeDirectory(val path: String?) : SettingsSync
        data class SelectImportDirectory(val path: String) : SettingsSync
        data class ChangeAutoExport(val enabled: Boolean) : SettingsSync
        data object Export : SettingsSync
        data object Import : SettingsSync
        data object ImportFile : SettingsSync
        data object ChooseFolder : SettingsSync
        data object ImportFolder : SettingsSync
        data object ExportFolder : SettingsSync
    }

    sealed interface Search : NaviampCoreCommand {
        data class ChangeQuery(val query: String) : Search
        data object Submit : Search
        data object Clear : Search
    }

    sealed interface Library : NaviampCoreCommand {
        data class ChangeQuery(val query: String) : Library
        data object Refresh : Library
        data object LoadMore : Library
        data class JumpToLetter(val letter: Char) : Library
    }

    sealed interface Downloads : NaviampCoreCommand {
        data class TrackAction(val request: DownloadedTrackActionRequest) : Downloads
        data class CancelJob(val id: String) : Downloads
        data class RetryJob(val id: String) : Downloads
        data object Refresh : Downloads
        data object ToggleKeepFavorites : Downloads
        data object DeleteAll : Downloads
    }

    sealed interface Playlists : NaviampCoreCommand {
        data object Refresh : Playlists
        data class ChangeSort(val sortMode: SharedPlaylistSortMode) : Playlists
        data class Detail(val request: NaviampPlaylistDetailActionRequest) : Playlists
        data class UpdateTracks(val playlist: SharedMediaItemUi, val tracks: List<SharedTrackRowUi>) : Playlists
    }

    sealed interface SmartPlaylist : NaviampCoreCommand {
        data class Save(val definition: SmartPlaylistDefinition, val password: String? = null) : SmartPlaylist
        data class Update(
            val playlist: SharedMediaItemUi,
            val definition: SmartPlaylistDefinition,
            val password: String? = null,
        ) : SmartPlaylist
        data class Load(val playlist: SharedMediaItemUi, val password: String? = null) : SmartPlaylist
    }

    sealed interface Radio : NaviampCoreCommand {
        data object Refresh : Radio
        data class StationAction(val request: StationRowActionRequest) : Radio
        data class SaveStation(val station: NaviampInternetRadioStationEditUi) : Radio
    }

    sealed interface Detail : NaviampCoreCommand {
        data class Album(val request: NaviampAlbumDetailActionRequest) : Detail
        data class Artist(val request: NaviampArtistDetailActionRequest) : Detail
        data class ArtistAlbum(val request: NaviampArtistAlbumActionRequest) : Detail
        data class AlbumTrack(val request: SharedTrackRowActionRequest) : Detail
        data class ArtistPopularTrack(val request: SharedTrackRowActionRequest) : Detail
        data class PlaylistTrack(val request: SharedTrackRowActionRequest) : Detail
    }

    sealed interface Media : NaviampCoreCommand {
        data class SelectTrack(val track: SharedTrackRowUi) : Media
        data class SelectAlbum(val album: SharedMediaItemUi, val fromMix: Boolean = false) : Media
        data class ToggleAlbumFavorite(val album: SharedMediaItemUi) : Media
        data class TrackAction(val request: SharedTrackRowActionRequest) : Media
        data class SelectArtist(val artist: SharedMediaItemUi) : Media
        data class ToggleArtistFavorite(val artist: SharedMediaItemUi) : Media
        data class SelectPlaylist(val playlist: SharedMediaItemUi) : Media
        data class ItemAction(val request: NaviampMediaItemActionRequest) : Media
    }

    sealed interface Home : NaviampCoreCommand {
        data object Refresh : Home
        data class SelectRecentRadio(val item: SharedMediaItemUi) : Home
        data class SelectInternetRadio(val item: SharedMediaItemUi) : Home
        data class SelectMixBuilder(val item: SharedMixBuilderUi) : Home
        data class SelectStation(val station: SharedHomeStationUi) : Home
        data class SonicTrackAction(val request: SharedHomeDiscoveryTrackActionRequest) : Home
        data class RecentTrackAction(val request: SharedTrackRowActionRequest) : Home
    }

    sealed interface MixBuilder : NaviampCoreCommand {
        data class Artist(val action: ArtistAction) : MixBuilder
        data class Album(val action: AlbumAction) : MixBuilder
        data class Genre(val action: GenreAction) : MixBuilder
        data class SonicPath(val action: SonicPathAction) : MixBuilder
        data class SonicMix(val action: SonicMixAction) : MixBuilder
    }

    sealed interface ArtistAction {
        data class ChangeQuery(val query: String) : ArtistAction
        data object Search : ArtistAction
        data class Select(val artist: SharedMediaItemUi) : ArtistAction
        data class Remove(val artist: SharedMediaItemUi) : ArtistAction
        data object Reset : ArtistAction
        data object Play : ArtistAction
    }

    sealed interface AlbumAction {
        data class ChangeQuery(val query: String) : AlbumAction
        data object Search : AlbumAction
        data class Select(val album: SharedMediaItemUi) : AlbumAction
        data class Remove(val album: SharedMediaItemUi) : AlbumAction
        data object Reset : AlbumAction
        data object Play : AlbumAction
    }

    sealed interface GenreAction {
        data class ChangeQuery(val query: String) : GenreAction
        data object Search : GenreAction
        data class Select(val genre: SharedGenreMixItemUi) : GenreAction
        data class Remove(val genre: SharedGenreMixItemUi) : GenreAction
        data object Reset : GenreAction
        data object Play : GenreAction
    }

    sealed interface SonicPathAction {
        data class ChangeStartQuery(val query: String) : SonicPathAction
        data class ChangeEndQuery(val query: String) : SonicPathAction
        data object SearchStart : SonicPathAction
        data object SearchEnd : SonicPathAction
        data class SelectStart(val track: SharedTrackRowUi) : SonicPathAction
        data class SelectEnd(val track: SharedTrackRowUi) : SonicPathAction
        data object ClearStart : SonicPathAction
        data object ClearEnd : SonicPathAction
        data class ChangeCount(val count: Int) : SonicPathAction
        data object Build : SonicPathAction
        data object Reset : SonicPathAction
        data object Play : SonicPathAction
        data object AddToQueue : SonicPathAction
        data class SaveAsPlaylist(val name: String) : SonicPathAction
    }

    sealed interface SonicMixAction {
        data class ChangeQuery(val query: String) : SonicMixAction
        data object Search : SonicMixAction
        data class Select(val track: SharedTrackRowUi) : SonicMixAction
        data class Remove(val track: SharedTrackRowUi) : SonicMixAction
        data class ChangeLength(val length: Int) : SonicMixAction
        data class ChangeBias(val bias: SharedSonicMixBiasUi) : SonicMixAction
        data object Build : SonicMixAction
        data object Reset : SonicMixAction
        data object Play : SonicMixAction
        data object AddToQueue : SonicMixAction
        data class SaveAsPlaylist(val name: String) : SonicMixAction
    }

    sealed interface NowPlaying : NaviampCoreCommand {
        data class Playback(val request: NowPlayingPlaybackActionRequest) : NowPlaying
        data class Display(val request: NowPlayingDisplayActionRequest) : NowPlaying
        data class CurrentTrack(val request: NowPlayingCurrentTrackUiActionRequest) : NowPlaying
        data class Queue(val request: NowPlayingQueueActionRequest) : NowPlaying
        data class SleepTimer(val request: NowPlayingSleepTimerActionRequest) : NowPlaying
        data class Selection(val request: NowPlayingSelectionActionRequest) : NowPlaying
        data class QueueItem(val request: NowPlayingItemActionRequest) : NowPlaying
    }
}

sealed interface NaviampCoreCommandResult {
    data object Completed : NaviampCoreCommandResult
    data class SmartPlaylistLoaded(val definition: SmartPlaylistDefinition) : NaviampCoreCommandResult
}

interface NaviampCoreCommandHandler {
    fun dispatch(command: NaviampCoreCommand)
    suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult
}
