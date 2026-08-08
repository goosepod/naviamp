package app.naviamp.presentation

import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedHomeCollectionPageUi
import app.naviamp.ui.SharedHomeCollectionSectionUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampCoreSystemBackPolicyTest {
    @Test
    fun detailScreensUseTheirSharedBackCommands() {
        assertEquals(
            NaviampCoreCommand.Navigation.BackFromPlaylist,
            stateWithPlaylist().systemBackCommand(),
        )
        assertEquals(
            NaviampCoreCommand.Navigation.BackFromAlbum,
            stateWithAlbum().systemBackCommand(),
        )
        assertEquals(
            NaviampCoreCommand.Navigation.BackFromArtist,
            stateWithArtist().systemBackCommand(),
        )
    }

    @Test
    fun overlaysCloseBeforeUnderlyingDetails() {
        val detail = stateWithPlaylist()

        assertEquals(
            NaviampCoreCommand.Settings.CloseStats,
            detail.copy(overlays = NaviampCoreOverlayState(statsForNerdsVisible = true)).systemBackCommand(),
        )
        assertEquals(
            NaviampCoreCommand.Navigation.CloseNowPlaying,
            detail.copy(
                shell = detail.shell.copy(
                    shellChrome = detail.shell.shellChrome.copy(nowPlayingOpen = true),
                ),
            ).systemBackCommand(),
        )
    }

    @Test
    fun rootScreenLetsTheHostHandleBack() {
        assertNull(NaviampCoreState().systemBackCommand())
    }

    @Test
    fun homeCollectionPageUsesItsCoreBackCommand() {
        val state = NaviampCoreState().let { initial ->
            initial.copy(
                shell = initial.shell.copy(
                    home = initial.shell.home.copy(
                        collectionPage = SharedHomeCollectionPageUi(
                            SharedHomeCollectionSectionUi("section", "SECTION", emptyList()),
                        ),
                    ),
                ),
            )
        }

        assertEquals(NaviampCoreCommand.Home.CloseCollection, state.systemBackCommand())
    }

    private fun stateWithPlaylist() = NaviampCoreState(
        shell = NaviampCoreState().shell.copy(
            playlistDetail = NaviampPlaylistDetailScreenUi(selectedPlaylist = item("playlist")),
        ),
    )

    private fun stateWithAlbum() = NaviampCoreState(
        shell = NaviampCoreState().shell.copy(
            albumDetail = NaviampAlbumDetailScreenUi(selectedAlbum = item("album")),
        ),
    )

    private fun stateWithArtist() = NaviampCoreState(
        shell = NaviampCoreState().shell.copy(
            artistDetail = NaviampArtistDetailScreenUi(selectedArtist = item("artist")),
        ),
    )

    private fun item(id: String) = SharedMediaItemUi(id = id, title = id, subtitle = "")
}
