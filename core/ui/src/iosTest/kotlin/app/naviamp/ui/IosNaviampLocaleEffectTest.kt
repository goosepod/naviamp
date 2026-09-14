package app.naviamp.ui

import platform.Foundation.NSArgumentDomain
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import kotlin.test.Test
import kotlin.test.assertEquals

class IosNaviampLocaleEffectTest {
    @Test fun nativeLanguageOverrideIsVolatileAndRestoresTheOriginalPreferenceList() {
        val defaults = NSUserDefaults.standardUserDefaults
        val originalDomain = defaults.volatileDomainForName(NSArgumentDomain)
        val originalLanguages = NSLocale.preferredLanguages
        val effect = createNaviampLocaleEffect()
        try {
            effect.applyLanguage("es")
            assertEquals("es", NSLocale.preferredLanguages.first())
            effect.applyLanguage("en")
            assertEquals("en", NSLocale.preferredLanguages.first())
            effect.applyLanguage(null)
            assertEquals(originalLanguages, NSLocale.preferredLanguages)
            assertEquals(originalDomain, defaults.volatileDomainForName(NSArgumentDomain))
        } finally { effect.applyLanguage(null) }
    }
}
