package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.naviamp.domain.settings.InterfaceLanguage

/** Native locale override with a captured, restorable system default. No preference is persisted here. */
interface NaviampLocaleEffect {
    fun applyLanguage(languageTag: String?)
}

internal class NaviampLocaleController(private val effect: NaviampLocaleEffect) {
    private var applied: InterfaceLanguage? = null

    fun select(language: InterfaceLanguage) {
        if (applied == language) return
        effect.applyLanguage(language.languageTag)
        applied = language
    }

    fun close() {
        effect.applyLanguage(null)
        applied = null
    }
}

// A static local invalidates the complete resource subtree without replacing its composition keys.
// Unlike key(language), this retains open dialogs, scroll positions, and remote focus.
private val LocalNaviampResourceLanguage = staticCompositionLocalOf { InterfaceLanguage.System }

@Composable
fun NaviampLocaleEnvironment(
    language: InterfaceLanguage,
    effect: NaviampLocaleEffect,
    content: @Composable () -> Unit,
) {
    val controller = remember(effect) { NaviampLocaleController(effect) }
    // Resources read the native locale during composition, so apply before composing descendants.
    controller.select(language)
    DisposableEffect(controller) { onDispose(controller::close) }
    CompositionLocalProvider(LocalNaviampResourceLanguage provides language, content = content)
}

/** Creates the narrow native override for one application composition's lifetime. */
expect fun createNaviampLocaleEffect(): NaviampLocaleEffect
