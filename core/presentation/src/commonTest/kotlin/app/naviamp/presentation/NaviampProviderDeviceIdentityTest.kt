package app.naviamp.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NaviampProviderDeviceIdentityTest {
    @Test
    fun createsOneIdentifierPerInstallationAndKeepsItAcrossLaunches() {
        val values = MemoryValues()
        var generated = 0
        val first = naviampProviderDeviceId(values, "naviamp-android") { "install-${++generated}" }
        val relaunch = naviampProviderDeviceId(values, "naviamp-android") { "install-${++generated}" }

        assertEquals("naviamp-android-install-1", first)
        assertEquals(first, relaunch)
        assertEquals(1, generated)
        assertEquals(first, values.entries[KeyProviderDeviceId])
    }

    @Test
    fun twoInstallationsOfTheSamePlatformNeverShareTheLegacyConstant() {
        val phone = naviampProviderDeviceId(MemoryValues(), "naviamp-android")
        val television = naviampProviderDeviceId(MemoryValues(), "naviamp-android")

        assertNotEquals(phone, television)
        listOf(phone, television).forEach { id ->
            assertTrue(id.startsWith("naviamp-android-") && id.length > "naviamp-android-".length, id)
            assertNotEquals("naviamp-android", id)
        }
    }

    @Test
    fun replacesMissingForeignOrUnsafeStoredValues() {
        listOf("", "naviamp-android", "naviamp-desktop-abc", "naviamp-android-a\"b", "naviamp-android-a\r\nb").forEach { stored ->
            val values = MemoryValues(mutableMapOf(KeyProviderDeviceId to stored))
            assertEquals("naviamp-android-fresh", naviampProviderDeviceId(values, "naviamp-android") { "fresh" })
            assertEquals("naviamp-android-fresh", values.entries[KeyProviderDeviceId])
        }
    }

    @Test
    fun rejectsUnsafeGeneratedValuesInsteadOfSendingThemInHeaders() {
        assertFailsWith<IllegalArgumentException> { naviampProviderDeviceId(MemoryValues(), "naviamp-android") { "a\"b" } }
        assertFailsWith<IllegalArgumentException> { naviampProviderDeviceId(MemoryValues(), "naviamp android") }
    }

    private class MemoryValues(val entries: MutableMap<String, String> = mutableMapOf()) : NaviampCoreSettingsValueStore {
        override fun read(key: String): String? = entries[key]
        override fun write(key: String, value: String) {
            entries[key] = value
        }
    }
}
