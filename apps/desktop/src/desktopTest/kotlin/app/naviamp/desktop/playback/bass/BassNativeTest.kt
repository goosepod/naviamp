package app.naviamp.desktop.playback.bass

import kotlin.test.Test
import kotlin.test.assertEquals

class BassNativeTest {
    @Test
    fun errorMessagesNameCommonFailures() {
        assertEquals("invalid position", bassErrorMessage(7))
        assertEquals("codec unavailable", bassErrorMessage(44))
        assertEquals("SSL unavailable", bassErrorMessage(50))
        assertEquals("BASS error 999", bassErrorMessage(999))
    }
}
