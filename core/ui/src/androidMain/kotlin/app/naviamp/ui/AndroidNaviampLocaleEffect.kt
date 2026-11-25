package app.naviamp.ui

import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** Compose's Android locale delegate reads the native default LocaleList. */
actual fun createNaviampLocaleEffect(): NaviampLocaleEffect {
    val original = LocaleList.getDefault()
    return object : NaviampLocaleEffect {
        override fun applyLanguage(languageTag: String?) {
            LocaleList.setDefault(languageTag?.let { LocaleList(Locale.forLanguageTag(it)) } ?: original)
        }

        // ActivityThread restores the system LocaleList on every process configuration change.
        @Composable
        override fun nativeLocaleRevision(): Any? = LocalConfiguration.current
    }
}
