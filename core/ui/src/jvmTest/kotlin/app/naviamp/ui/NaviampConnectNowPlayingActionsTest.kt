package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampConnectNowPlayingActionsTest {
    @Test
    fun duplicateDeviceNamesAreStableAndDoNotExposeIdentity() {
        assertEquals(
            listOf("Living room (1)", "Office", "living room (2)"),
            disambiguateNaviampConnectDeviceNames(listOf("Living room", "Office", "living room")),
        )
    }

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
            remoteOutput = { calls += "remote-stop" },
        )

        val merged = remote.withLocalDisplayActions(local)
        merged.playback(NowPlayingPlaybackAction.PlayCurrent)
        merged.display(NowPlayingDisplayAction.Collapse)
        merged.onRemoteOutputAction()

        assertEquals(
            listOf("remote-playback", "local-display:${NowPlayingDisplayAction.Collapse}", "remote-stop"),
            calls,
        )
    }

    @Test
    fun rememberedTargetsBecomeSelectableNowPlayingOutputs() {
        val connect = NaviampConnectSettingsUi(
            available = true,
            localDeviceName = "My phone",
            selectedPlaybackDeviceId = "living-room-trust",
            selectedPlaybackDeviceName = "Living room",
            playbackDestinationStatus = NaviampConnectPlaybackDestinationUiStatus.Connected,
            trustedDevices = listOf(
                NaviampConnectTrustedDeviceUi(
                    deviceId = "living-room-trust",
                    displayName = "Living room",
                    detail = "Paired television",
                    reconnectAvailable = true,
                    playbackTarget = true,
                ),
                NaviampConnectTrustedDeviceUi(
                    deviceId = "controller-only",
                    displayName = "Remote",
                    detail = "Paired controller",
                ),
            ),
        )

        val decorated = NowPlayingUi(title = "Track", subtitle = "Artist", stateLabel = "Playing")
            .withSelectedRemoteOutput(connect)

        assertEquals("Living room", decorated.remoteOutputDeviceName)
        assertEquals(
            listOf(
                NaviampPlaybackOutputUi(null, "My phone", selected = false),
                NaviampPlaybackOutputUi("living-room-trust", "Living room", selected = true),
            ),
            decorated.playbackOutputs,
        )
    }

    private fun actions(
        playback: (NowPlayingPlaybackActionRequest) -> Unit,
        display: (NowPlayingDisplayActionRequest) -> Unit,
        remoteOutput: () -> Unit = {},
    ) = NaviampNowPlayingActions(
        onPlaybackAction = playback,
        onDisplayAction = display,
        onCurrentTrackAction = {},
        onQueueAction = {},
        onSleepTimerAction = {},
        onSelectionAction = {},
        onQueueItemAction = {},
        onRemoteOutputAction = remoteOutput,
    )
}
