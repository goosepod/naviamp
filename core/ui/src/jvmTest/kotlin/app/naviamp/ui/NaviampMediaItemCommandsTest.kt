package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NaviampMediaItemCommandsTest {
    private val item = SharedMediaItemUi("item", "Item", "")

    @Test
    fun convertsOnlyCommandsSupportedByTheMediaKind() {
        val album = request(SharedMediaItemKind.Album, SharedMediaItemAction.StartRadio)
            .toNaviampMediaItemCommand()
        val artist = request(SharedMediaItemKind.Artist, SharedMediaItemAction.FindSimilar)
            .toNaviampMediaItemCommand()
        val playlist = request(SharedMediaItemKind.Playlist, SharedMediaItemAction.Shuffle)
            .toNaviampMediaItemCommand()

        assertEquals(
            NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.StartRadio),
            assertIs<NaviampMediaItemCommandConversion.Converted>(album).request.command,
        )
        assertEquals(
            NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.FindSimilar),
            assertIs<NaviampMediaItemCommandConversion.Converted>(artist).request.command,
        )
        assertEquals(
            NaviampMediaItemCommand.Playlist(
                NaviampPlaylistMediaCommand.Detail(NaviampPlaylistDetailCommand.Play(shuffle = true)),
            ),
            assertIs<NaviampMediaItemCommandConversion.Converted>(playlist).request.command,
        )
        assertEquals(
            NaviampMediaItemCommandConversion.Unsupported,
            request(SharedMediaItemKind.Artist, SharedMediaItemAction.Download).toNaviampMediaItemCommand(),
        )
    }

    @Test
    fun rejectsMissingOrBlankCommandValues() {
        assertEquals(
            NaviampMediaItemCommandConversion.InvalidValue,
            request(SharedMediaItemKind.Album, SharedMediaItemAction.AddToPlaylist).toNaviampMediaItemCommand(),
        )
        assertEquals(
            NaviampMediaItemCommandConversion.InvalidValue,
            request(
                SharedMediaItemKind.Playlist,
                SharedMediaItemAction.Rename,
                textValue = " ",
            ).toNaviampMediaItemCommand(),
        )
    }

    private fun request(
        kind: SharedMediaItemKind,
        action: SharedMediaItemAction,
        textValue: String? = null,
    ) = SharedMediaItemActionRequest(
        item = item,
        action = action,
        kind = kind,
        textValue = textValue,
    )
}
