package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampConnectNowPlayingActionsTest {
    @Test
    fun remoteControlsKeepLocalDisplayAndCollapseActions() {
        val calls = mutableListOf<String>()
        val local = actions(
            playback = { calls += "local-playback" },
            display = { calls += "local-display:${it.action}" },
        )
        val remote = actions(
            playback = { calls += "remote-playback" },
            display = { calls += "remote-display" },
        )

        val merged = remote.withLocalDisplayActions(local)
        merged.playback(NowPlayingPlaybackAction.PlayCurrent)
        merged.display(NowPlayingDisplayAction.Collapse)

        assertEquals(
            listOf("remote-playback", "local-display:${NowPlayingDisplayAction.Collapse}"),
            calls,
        )
    }

    private fun actions(
        playback: (NowPlayingPlaybackActionRequest) -> Unit,
        display: (NowPlayingDisplayActionRequest) -> Unit,
    ) = NaviampNowPlayingActions(
        onPlaybackAction = playback,
        onDisplayAction = display,
        onCurrentTrackAction = {},
        onQueueAction = {},
        onSleepTimerAction = {},
        onSelectionAction = {},
        onQueueItemAction = {},
    )
}
