package app.naviamp.desktop

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopExternalUriPortTest {
    @Test
    fun opensAValidatedUriThroughTheDesktopEffect() {
        var opened: URI? = null

        DesktopExternalUriPort { opened = it }.open("https://example.com/artist")

        assertEquals(URI("https://example.com/artist"), opened)
    }

    @Test
    fun rejectsMalformedUrisBeforeInvokingTheDesktopEffect() {
        val port = DesktopExternalUriPort { error("must not browse") }

        assertFailsWith<IllegalArgumentException> { port.open("https://bad uri") }
    }
}
