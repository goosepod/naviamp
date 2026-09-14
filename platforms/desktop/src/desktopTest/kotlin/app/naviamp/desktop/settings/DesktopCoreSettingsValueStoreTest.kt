package app.naviamp.desktop.settings

import java.nio.file.Files
import java.nio.file.Path
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

    @Test
    fun developmentProfileIsolatesMacDataAndSettings() {
        val home = Path.of("/Users/tester")

        assertEquals(
            home.resolve("Library/Application Support/Naviamp"),
            desktopDataDirectory(home, "mac os x", null, development = false),
        )
        assertEquals(
            home.resolve("Library/Application Support/Naviamp Development"),
            desktopDataDirectory(home, "mac os x", null, development = true),
        )
        assertEquals(
            home.resolve("Library/Application Support/Naviamp Development"),
            desktopSettingsDirectory(home, "darwin", null, null, development = true),
        )
    }

    @Test
    fun developmentProfileIsolatesWindowsDataAndSettings() {
        val home = Path.of("C:/Users/tester")
        val appData = "C:/Users/tester/AppData/Roaming"

        assertEquals(
            Path.of(appData).resolve("Naviamp"),
            desktopDataDirectory(home, "windows 11", appData, development = false),
        )
        assertEquals(
            Path.of(appData).resolve("Naviamp Development"),
            desktopDataDirectory(home, "windows 11", appData, development = true),
        )
        assertEquals(
            Path.of(appData).resolve("Naviamp Development"),
            desktopSettingsDirectory(home, "windows 11", appData, null, development = true),
        )
    }

    @Test
    fun developmentProfileIsolatesLinuxDataAndSettings() {
        val home = Path.of("/home/tester")

        assertEquals(
            home.resolve(".local/share/naviamp-development"),
            desktopDataDirectory(home, "linux", null, development = true),
        )
        assertEquals(
            Path.of("/tmp/config/naviamp-development"),
            desktopSettingsDirectory(home, "linux", null, "/tmp/config", development = true),
        )
    }
}
