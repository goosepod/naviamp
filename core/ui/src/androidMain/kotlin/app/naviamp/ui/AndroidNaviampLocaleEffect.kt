package app.naviamp.ui

import android.os.LocaleList
import java.util.Locale

/** Compose's Android locale delegate reads the native default LocaleList. */
actual fun createNaviampLocaleEffect(): NaviampLocaleEffect {
    val original = LocaleList.getDefault()
    return object : NaviampLocaleEffect {
        override fun applyLanguage(languageTag: String?) {
            LocaleList.setDefault(languageTag?.let { LocaleList(Locale.forLanguageTag(it)) } ?: original)
        }
    }
}
