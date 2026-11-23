package app.naviamp.ui

import androidx.compose.runtime.Composable
import app.naviamp.domain.settings.InterfaceLanguage
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Process-wide owner of the selected interface language. Media services, vehicle surfaces and
 * notifications resolve text without an application composition, and hosts may restore the system
 * process locale at any time, so every lookup re-applies the selection before reading resources.
 */
object NaviampResourceLanguage {
    private val selection = MutableStateFlow(InterfaceLanguage.System)
    private val follower = MutableStateFlow<Any?>(null)
    @Volatile private var override: NaviampLocaleController? = null

    val selected: StateFlow<InterfaceLanguage> = selection.asStateFlow()

    /**
     * Follows [languages] for the lifetime of [scope]. The first value is applied before this
     * returns and each later one in the emitting thread, so no reader sees a stale language.
     * A newer follower takes over; when the current one ends, the system language returns.
     */
    fun follow(scope: CoroutineScope, languages: Flow<InterfaceLanguage>): Job {
        val token = Any()
        follower.value = token
        return scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            try {
                languages.collect { language -> if (follower.value === token) select(language) }
            } finally {
                if (follower.compareAndSet(token, null)) select(InterfaceLanguage.System)
            }
        }
    }

    private fun select(language: InterfaceLanguage) {
        if (language == InterfaceLanguage.System) {
            override?.close()
            override = null
        } else {
            // A new override period captures the current system default, which may have changed.
            val controller = override ?: NaviampLocaleController(createNaviampLocaleEffect()).also { override = it }
            controller.select(language)
            controller.reassert()
        }
        // Publish only after the native locale matches, so observers never resolve the old language.
        selection.value = language
    }

    internal fun reassert() {
        override?.reassert()
    }

    @Composable
    internal fun nativeLocaleRevision(): Any? = override?.effect?.nativeLocaleRevision()
}

/** Non-composable string lookup in the selected Naviamp language. */
suspend fun naviampString(resource: StringResource): String {
    NaviampResourceLanguage.reassert()
    return getString(resource)
}

suspend fun naviampString(resource: StringResource, vararg formatArgs: Any): String {
    NaviampResourceLanguage.reassert()
    return getString(resource, *formatArgs)
}
