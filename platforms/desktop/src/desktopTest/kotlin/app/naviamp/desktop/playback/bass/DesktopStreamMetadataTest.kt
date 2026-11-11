package app.naviamp.desktop.playback.bass

import app.naviamp.testkit.naviampStreamMetadataFixture
import app.naviamp.testkit.verifyNaviampStreamTagReading
import java.nio.file.Files
import kotlin.test.Test

/** Real BASS/JNI and temporary filesystem effects; the metadata assertions are shared. */
class DesktopStreamMetadataTest {
    @Test fun embeddedTagsReachSharedStreamDetails() {
        val file = Files.createTempFile("naviamp-stream-metadata-", ".ogg")
        val backend = DesktopBassAudioBackend(DesktopBassJniBinding.load().getOrThrow())
        try {
            Files.write(file, naviampStreamMetadataFixture())
            backend.init("0").getOrThrow()
            verifyNaviampStreamTagReading(backend, file.toString())
        } finally {
            backend.free()
            Files.deleteIfExists(file)
        }
    }
}
