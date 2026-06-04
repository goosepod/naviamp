package app.naviamp.desktop.playback.bass

import kotlin.test.Test
import kotlin.test.assertTrue

class BassNativeIntegrationTest {
    @Test
    fun loadsBundledBassWhenAvailable() {
        val native = DesktopBassNative.load().getOrNull() ?: return

        assertTrue(native.version > 0)
    }

    @Test
    fun loadsBundledBassmixWhenAvailable() {
        val native = DesktopBassNative.load().getOrNull() ?: return

        assertTrue(native.supportsMixer, native.mixerError ?: "BASSmix was not loaded")
        assertTrue((native.mixerVersion ?: 0) > 0)
    }
}
