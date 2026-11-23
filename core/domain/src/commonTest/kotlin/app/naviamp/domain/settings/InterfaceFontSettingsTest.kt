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
}
