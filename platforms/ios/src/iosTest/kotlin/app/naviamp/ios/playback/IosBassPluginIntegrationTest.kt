package app.naviamp.ios.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosBassPluginIntegrationTest {
    @Test
    fun bundledCodecPluginsRegisterWithBass() {
        val diagnostics = IosBassAudioBackend().pluginDiagnostics

        assertEquals(11, diagnostics.size)
        assertTrue(
            diagnostics.all { it.loaded },
            "Bundled iOS BASS components should load: ${diagnostics.filterNot { it.loaded }}",
        )
    }
}
