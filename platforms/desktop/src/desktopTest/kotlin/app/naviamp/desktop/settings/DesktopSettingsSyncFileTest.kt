package app.naviamp.desktop.settings

import app.naviamp.domain.settings.SettingsSyncDocument
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSettingsSyncFileTest {
    @Test
    fun documentStoreRoundTripsThroughTheSelectedDirectory() {
        val directory = Files.createTempDirectory("naviamp-settings-sync-test")
        try {
            val store = DesktopSettingsSyncDocumentStore(directory)
            val document = SettingsSyncDocument(updatedAtEpochMillis = 42L)

            assertNull(store.read())
            store.write(document)

            assertEquals(document, store.read())
        } finally {
            DesktopSettingsSyncFile.syncFile(directory).deleteIfExists()
            directory.deleteIfExists()
        }
    }
}
