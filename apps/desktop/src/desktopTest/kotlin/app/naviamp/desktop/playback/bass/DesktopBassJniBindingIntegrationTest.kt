package app.naviamp.desktop.playback.bass

import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopBassJniBindingIntegrationTest {
    @Test
    fun loadsBundledJniBindingWhenAvailable() {
        val libraryDirectory = DesktopBassLibraryResolver().resolve() ?: return
        val binding = DesktopBassJniBinding.loadFrom(libraryDirectory).getOrNull() ?: return

        assertTrue(binding.version > 0)
    }
}
