package app.naviamp.ui

import java.util.Locale

/** Compose Desktop resolves resource locales through the JVM default Locale. */
actual fun createNaviampLocaleEffect(): NaviampLocaleEffect {
    val original = Locale.getDefault()
    return object : NaviampLocaleEffect {
        override fun applyLanguage(languageTag: String?) {
            Locale.setDefault(languageTag?.let(Locale::forLanguageTag) ?: original)
        }
    }
}
