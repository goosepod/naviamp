package app.naviamp.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionFormValidationTest {
    @Test
    fun formErrorRequiresServerUsernameAndFirstPassword() {
        assertEquals(
            "Enter a server URL and username.",
            connectionFormError("", "demo", "secret", hasSavedConnectionForLogin = false),
        )
        assertEquals(
            "Enter a password for first-time setup.",
            connectionFormError("https://music.example.test", "demo", "", hasSavedConnectionForLogin = false),
        )
        assertNull(
            connectionFormError("https://music.example.test", "demo", "", hasSavedConnectionForLogin = true),
        )
    }
}
