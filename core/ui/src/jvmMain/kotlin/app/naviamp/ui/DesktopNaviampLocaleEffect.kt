package app.naviamp.ui

import androidx.compose.runtime.Composable
import java.util.Locale

/** Compose Desktop resolves resource locales through the JVM default Locale, which the JVM never resets. */
actual fun createNaviampLocaleEffect(): NaviampLocaleEffect {
    val original = Locale.getDefault()
    return object : NaviampLocaleEffect {
        override fun applyLanguage(languageTag: String?) {
            Locale.setDefault(languageTag?.let(Locale::forLanguageTag) ?: original)
        }

        @Composable
        override fun nativeLocaleRevision(): Any? = null
    }
}
