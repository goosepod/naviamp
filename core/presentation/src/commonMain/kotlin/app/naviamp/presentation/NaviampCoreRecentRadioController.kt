package app.naviamp.presentation

import app.naviamp.app.NaviampRecentRadioStreamController
import app.naviamp.domain.radio.RecentRadioAction
import app.naviamp.domain.radio.recentRadioAction

/** Owns replay of portable generated-radio history selected from Home. */
class NaviampCoreRecentRadioController(
    private val recents: NaviampRecentRadioStreamController,
    private val media: NaviampCoreMediaTransactions,
) : NaviampCoreCommandController {
    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult =
        if (command is NaviampCoreCommand.Home.SelectRecentRadio) {
            NaviampCoreImmediateCommandResult.Deferred
        } else {
            NaviampCoreImmediateCommandResult.Unhandled
        }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        val selected = command as? NaviampCoreCommand.Home.SelectRecentRadio ?: return null
        val stream = recents.current().firstOrNull { it.id == selected.item.id }
        if (stream == null) {
            media.publish("Recent radio ${selected.item.title} is no longer available.")
            return NaviampCoreCommandResult.Completed
        }
        val action = recentRadioAction(stream)
        if (action == null) {
            media.publish("Recent radio ${stream.label} is incomplete.")
            return NaviampCoreCommandResult.Completed
        }
        when (action) {
            RecentRadioAction.PlayLibrary -> media.startLibraryRadio()
            RecentRadioAction.PlayRandomAlbum -> media.startRandomAlbumRadio()
            is RecentRadioAction.PlayGenre -> media.startGenreRadio(action.genre.name)
            is RecentRadioAction.PlayDecade -> media.startDecadeRadio(action.fromYear, action.toYear)
            is RecentRadioAction.PlayArtist -> media.startArtistRadio(action.artist)
            is RecentRadioAction.PlayAlbum -> media.startAlbumRadio(action.album)
            is RecentRadioAction.PlayTrack -> media.startTrackRadio(action.track)
        }
        return NaviampCoreCommandResult.Completed
    }
}
