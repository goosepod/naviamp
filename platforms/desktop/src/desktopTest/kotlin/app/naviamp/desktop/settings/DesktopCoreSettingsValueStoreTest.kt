package app.naviamp.desktop.settings

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopCoreSettingsValueStoreTest {
    @Test
    fun storesOpaqueStringsWithoutInterpretingOrDeletingOtherFields() {
        val path = Files.createTempDirectory("naviamp-core-settings").resolve("settings.json")
        path.writeText("""{"connection":{"baseUrl":"https://music"},"legacy":"kept"}""")
        val store = DesktopCoreSettingsValueStore(path)

        store.write("naviamp.interface", """{"showDesktopTooltips":false}""")

        assertEquals("""{"showDesktopTooltips":false}""", store.read("naviamp.interface"))
        assertTrue(path.readText().contains("\"legacy\": \"kept\""))
        assertTrue(path.readText().contains("\"connection\""))
    }

    @Test
    fun exposesLegacyJsonValuesAndSupportsMigrationCleanup() {
        val path = Files.createTempDirectory("naviamp-core-settings-session").resolve("settings.json")
        path.writeText("""{"session":{"currentIndex":2},"legacyText":"value"}""")
        val store = DesktopCoreSettingsValueStore(path)

        assertEquals("""{"currentIndex":2}""", store.read("session"))
        assertEquals("value", store.read("legacyText"))
        assertTrue(store.contains("session"))

        store.remove("session")

        assertFalse(store.contains("session"))
        assertEquals("value", store.read("legacyText"))
    }
}
