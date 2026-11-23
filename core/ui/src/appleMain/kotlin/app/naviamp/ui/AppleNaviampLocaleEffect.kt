package app.naviamp.ui

import androidx.compose.runtime.Composable
import platform.Foundation.NSArgumentDomain
import platform.Foundation.NSUserDefaults

/** NSLocale.preferredLanguages reads AppleLanguages from the native defaults search list on Apple hosts. */
actual fun createNaviampLocaleEffect(): NaviampLocaleEffect {
    val defaults = NSUserDefaults.standardUserDefaults
    val original = defaults.volatileDomainForName(NSArgumentDomain)
    return object : NaviampLocaleEffect {
        override fun applyLanguage(languageTag: String?) {
            // A volatile override never persists a second language preference or replaces the OS default.
            defaults.setVolatileDomain(
                languageTag?.let { original + ("AppleLanguages" to listOf(it)) } ?: original,
                NSArgumentDomain,
            )
        }

        @Composable
        override fun nativeLocaleRevision(): Any? = null
    }
}
