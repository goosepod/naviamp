package app.naviamp.ui

import app.naviamp.domain.settings.InterfaceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NaviampLocaleControllerTest {
    @Test fun selectionRestorationAndImportedPreferencesUseTheSamePolicy() {
        val applied = mutableListOf<String?>()
        val controller = NaviampLocaleController(object : NaviampLocaleEffect {
            override fun applyLanguage(languageTag: String?) { applied += languageTag }
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
        })
        assertFailsWith<IllegalStateException> { controller.select(InterfaceLanguage.Spanish) }
        fail = false
        controller.select(InterfaceLanguage.Spanish)
        assertEquals(2, attempts)
    }
}
