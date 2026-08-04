package app.naviamp.desktop.playback.bass

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopBassPlaybackEngineRuntimeTest {
    @Test
    fun resolvesOnlyLocalFileUris() {
        val runtime = DesktopBassPlaybackEngineRuntime()
        val file = Files.createTempFile("naviamp-bass-runtime", ".audio")

        try {
            assertEquals(file.toFile().absolutePath, runtime.localFilePath(file.toUri().toString()))
            assertNull(runtime.localFilePath("https://example.test/audio.flac"))
            assertNull(runtime.localFilePath("not a uri"))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun providesClockAndPreparedPlaybackSynchronization() {
        val runtime = DesktopBassPlaybackEngineRuntime()
        var entered = false

        val result = runtime.withPreparedPlaybackLock {
            entered = true
            "prepared"
        }

        assertTrue(entered)
        assertEquals("prepared", result)
        assertTrue(runtime.nowEpochMillis() > 0L)
    }
}
