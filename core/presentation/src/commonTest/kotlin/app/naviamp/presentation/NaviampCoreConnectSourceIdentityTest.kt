package app.naviamp.presentation

import app.naviamp.ui.NaviampSavedConnectionUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampCoreConnectSourceIdentityTest {
    private val provider = FakeCoreMediaProvider()

    @Test
    fun buildsNormalizedIdentityOnlyForTheConnectedCurrentSource() {
        val store = NaviampCoreStateStore().also { stateStore ->
            stateStore.updateShell { shell ->
                shell.copy(
                    connectionSettings = shell.connectionSettings.copy(
                        connection = shell.connectionSettings.connection.copy(
                            connected = true,
                            savedConnections = listOf(
                                NaviampSavedConnectionUi(
                                    id = "source",
                                    displayName = "Home",
                                    serverUrl = " HTTPS://MUSIC.EXAMPLE.TEST/ ",
                                    username = " Listener ",
                                    providerId = " NAVIDROME ",
                                    current = true,
                                ),
                            ),
                        ),
                    ),
                )
            }
        }

        val identity = naviampCoreConnectSourceIdentity(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
        )

        assertEquals("navidrome", identity?.providerId)
        assertEquals("https://music.example.test", identity?.canonicalServerOrigin)
        assertEquals("listener", identity?.accountIdentity)
        assertEquals(emptyList(), identity?.libraryIds)
    }

    @Test
    fun doesNotPublishAnIdentityWhileDisconnected() {
        assertNull(
            naviampCoreConnectSourceIdentity(
                stateStore = NaviampCoreStateStore(),
                providerSource = NaviampCoreMediaProviderSource { provider },
            ),
        )
    }
}
