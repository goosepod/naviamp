package app.naviamp.ui

import androidx.compose.runtime.Composable
import app.naviamp.domain.settings.InterfaceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NaviampLocaleControllerTest {
    @Test fun selectionRestorationAndImportedPreferencesUseTheSamePolicy() {
        val applied = mutableListOf<String?>()
        val controller = NaviampLocaleController(object : NaviampLocaleEffect {
            override fun applyLanguage(languageTag: String?) { applied += languageTag }
            @Composable override fun nativeLocaleRevision(): Any? = null
        })
        controller.select(InterfaceLanguage.Spanish)
        controller.select(InterfaceLanguage.Spanish)
        controller.select(InterfaceLanguage.English)
        controller.select(InterfaceLanguage.System)
        controller.close()
        assertEquals(listOf("es", "en", null, null), applied)
    }

    @Test fun failedNativeApplicationCanBeRetried() {
        var fail = true
        var attempts = 0
        val controller = NaviampLocaleController(object : NaviampLocaleEffect {
            override fun applyLanguage(languageTag: String?) {
                attempts++
                if (fail) error("Native locale unavailable")
            }
            @Composable override fun nativeLocaleRevision(): Any? = null
        })
        assertFailsWith<IllegalStateException> { controller.select(InterfaceLanguage.Spanish) }
        fail = false
        controller.select(InterfaceLanguage.Spanish)
        assertEquals(2, attempts)
    }

    @Test fun explicitLanguageIsReassertedAfterTheHostResetsTheNativeLocale() {
        val applied = mutableListOf<String?>()
        val controller = NaviampLocaleController(object : NaviampLocaleEffect {
            override fun applyLanguage(languageTag: String?) { applied += languageTag }
            @Composable override fun nativeLocaleRevision(): Any? = null
        })
        controller.select(InterfaceLanguage.English, nativeRevision = 1)
        controller.select(InterfaceLanguage.English, nativeRevision = 1)
        controller.select(InterfaceLanguage.English, nativeRevision = 2)
        controller.select(InterfaceLanguage.System, nativeRevision = 2)
        // The host's own reset already restores the system language.
        controller.select(InterfaceLanguage.System, nativeRevision = 3)
        assertEquals(listOf("en", "en", null), applied)
    }
}
