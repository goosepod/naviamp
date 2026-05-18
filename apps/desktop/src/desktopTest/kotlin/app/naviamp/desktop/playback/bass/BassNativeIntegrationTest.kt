package app.naviamp.desktop.playback.bass

import kotlin.test.Test
import kotlin.test.assertTrue

class BassNativeIntegrationTest {
    @Test
    fun loadsBundledBassWhenAvailable() {
        val native = BassNative.load().getOrNull() ?: return

        assertTrue(native.version > 0)
    }
}
