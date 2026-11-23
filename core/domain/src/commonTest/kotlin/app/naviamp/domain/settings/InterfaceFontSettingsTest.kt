package app.naviamp.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterfaceFontSettingsTest {
    @Test
    fun bundledChoicesResolveDirectlyOnEveryDevice() {
        for (family in InterfaceFontFamily.entries) {
            for (installed in listOf(null, emptyList(), listOf("Fira Sans"))) {
                assertEquals(InterfaceFontSelection(family), InterfaceSettings(fontFamily = family).resolvedFontSelection(installed))
            }
        }
    }

    @Test
    fun anAvailableInstalledFamilyIsUsedWithItsInstalledSpelling() {
        val settings = InterfaceSettings(fontFamily = InterfaceFontFamily.Inter, installedFontFamily = " fira sans ")
        assertEquals(
            InterfaceFontSelection(InterfaceFontFamily.Inter, installedFamily = "Fira Sans"),
            settings.normalized().resolvedFontSelection(listOf("Arial", "Fira Sans")),
        )
    }

    @Test
    fun aMissingInstalledFamilyFallsBackToNunitoSansAndIsReported() {
        val settings = InterfaceSettings(fontFamily = InterfaceFontFamily.Inter, installedFontFamily = "Wingdings")
        for (installed in listOf(null, emptyList(), listOf("Arial"))) {
            val selection = settings.resolvedFontSelection(installed)
            assertEquals(InterfaceFontFamily.NunitoSans, selection.family)
            assertNull(selection.installedFamily)
            assertEquals("Wingdings", selection.unavailableInstalledFamily)
        }
    }

    @Test
    fun aDeviceWithoutTheSyncedFamilyRendersNunitoSansAndKeepsTheStoredChoice() {
        val synced = InterfaceSettings(fontFamily = InterfaceFontFamily.Inter, installedFontFamily = "Fira Sans")
        val phone = synced.resolvedFontSelection(availableInstalledFamilies = null)
        assertEquals(InterfaceFontFamily.NunitoSans, phone.family)
        assertNull(phone.installedFamily)
        assertEquals("Fira Sans", synced.normalized().installedFontFamily)
        val desktop = synced.resolvedFontSelection(listOf("Fira Sans"))
        assertEquals("Fira Sans", desktop.installedFamily)
    }

    @Test
    fun choosingABundledFamilyClearsTheInstalledFamily() {
        val installed = InterfaceSettings().withInstalledFontFamily("Fira Sans")
        assertEquals("Fira Sans", installed.installedFontFamily)
        val reset = installed.withBundledFontFamily(InterfaceFontFamily.NunitoSans)
        assertEquals(InterfaceSettings(), reset)
        assertNull(InterfaceSettings().withInstalledFontFamily("   ").installedFontFamily)
    }

    @Test
    fun sizesClampAndRoundToTheNearestStep() {
        for ((raw, expected) in listOf(
            Int.MIN_VALUE to 80, 79 to 80, 82 to 80, 83 to 85, 100 to 100,
            117 to 115, 118 to 120, 151 to 150, Int.MAX_VALUE to 150,
        )) {
            assertEquals(expected, InterfaceFontSize(raw).normalized().percent, "raw $raw")
        }
        assertEquals(0.9f, InterfaceFontSize.Small.scale)
        assertEquals(1.15f, InterfaceFontSize.Large.scale)
        assertEquals(1.5f, InterfaceFontSize(999).scale)
    }

    @Test
    fun aFontSizeFieldDecodesPresetsAndPercentagesEvenWithoutTheSectionTransform() {
        // Any decoder that bypasses the InterfaceSettings transform must not reject a stored preset
        // and discard the whole interface section.
        val generated = InterfaceSettings.generatedSerializer()
        for ((raw, expected) in listOf("\"Small\"" to 90, "\"Standard\"" to 100, "\"Large\"" to 115, "125" to 125, "\"Unknown\"" to 100)) {
            val decoded = SettingsSyncJson.format.decodeFromString(generated, """{"generalFontSize":$raw,"showArtistPlaylists":false}""")
            assertEquals(expected, decoded.generalFontSize.percent, raw)
            assertEquals(false, decoded.showArtistPlaylists)
        }
    }
}
