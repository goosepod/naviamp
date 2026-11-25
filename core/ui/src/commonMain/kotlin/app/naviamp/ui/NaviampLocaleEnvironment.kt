package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.naviamp.domain.settings.InterfaceLanguage

/** Native locale override with a captured, restorable system default. No preference is persisted here. */
interface NaviampLocaleEffect {
    fun applyLanguage(languageTag: String?)

    /** Changes whenever the host may have replaced the process locale with the system default. */
    @Composable
    fun nativeLocaleRevision(): Any?
}

internal class NaviampLocaleController(private val effect: NaviampLocaleEffect) {
    private var applied: InterfaceLanguage? = null
    private var appliedRevision: Any? = null

    fun select(language: InterfaceLanguage, nativeRevision: Any? = null) {
        if (applied == language && (language == InterfaceLanguage.System || appliedRevision == nativeRevision)) return
        effect.applyLanguage(language.languageTag)
        applied = language
        appliedRevision = nativeRevision
    }

    fun close() {
        effect.applyLanguage(null)
        applied = null
        appliedRevision = null
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
    // Reading the revision here makes a host reset recompose this scope ahead of its descendants.
    controller.select(language, effect.nativeLocaleRevision())
    DisposableEffect(controller) { onDispose(controller::close) }
    val direction = when (language) {
        InterfaceLanguage.System -> LocalLayoutDirection.current
        InterfaceLanguage.Arabic -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
    CompositionLocalProvider(
        LocalNaviampResourceLanguage provides language,
        LocalLayoutDirection provides direction,
        content = content,
    )
}

/** Creates the narrow native override for one application composition's lifetime. */
expect fun createNaviampLocaleEffect(): NaviampLocaleEffect
