package app.naviamp.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.settings_language_title
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalTestApi::class)
class NaviampResourceLanguageTest {
    @Test fun theProcessLanguageOutlivesTheApplicationCompositionAndSurvivesAHostLocaleReset() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.GERMAN)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val languages = MutableStateFlow(InterfaceLanguage.English)
            NaviampResourceLanguage.follow(scope, languages)
            runComposeUiTest {
                var composed by mutableStateOf(true)
                setContent {
                    if (composed) NaviampLocaleEnvironment { Text(stringResource(Res.string.settings_language_title)) }
                }
                onNodeWithText("Language").assertExists()
                runOnIdle { languages.value = InterfaceLanguage.Spanish }
                onNodeWithText("Idioma").assertExists()
                runOnIdle { composed = false }
                runOnIdle { assertEquals("es", Locale.getDefault().language) }
            }
            // The host restores the system locale while only native media services are running.
            Locale.setDefault(Locale.GERMAN)
            assertEquals("Idioma", runBlocking { naviampString(Res.string.settings_language_title) })

            languages.value = InterfaceLanguage.System
            assertEquals("Sprache", runBlocking { naviampString(Res.string.settings_language_title) })
            languages.value = InterfaceLanguage.English
            scope.cancel()
            assertEquals(InterfaceLanguage.System, NaviampResourceLanguage.selected.value)
            assertEquals("Sprache", runBlocking { naviampString(Res.string.settings_language_title) })
        } finally {
            scope.cancel()
            Locale.setDefault(original)
        }
    }
}
