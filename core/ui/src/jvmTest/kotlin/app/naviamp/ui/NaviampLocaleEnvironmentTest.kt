package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.settings_language_title
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalTestApi::class)
class NaviampLocaleEnvironmentTest {
    @Test fun switchingRestoresSystemWithoutRecreatingRememberedUiOrLosingFocus() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.FRENCH) // Unsupported system language falls back to English resources.
        try {
            runComposeUiTest {
                var language by mutableStateOf(InterfaceLanguage.Spanish)
                var creations = 0
                val effect = createNaviampLocaleEffect()
                setContent {
                    NaviampLocaleEnvironment(language, effect) {
                        remember { creations++ }
                        Column {
                            Text(stringResource(Res.string.settings_language_title))
                            Button(onClick = {}) { Text("focus anchor") }
                        }
                    }
                }
                onNodeWithText("Idioma").assertExists()
                onNodeWithText("focus anchor").performSemanticsAction(SemanticsActions.RequestFocus)
                runOnIdle { language = InterfaceLanguage.English }
                onNodeWithText("Language").assertExists()
                onNodeWithText("focus anchor").assertIsFocused()
                runOnIdle { language = InterfaceLanguage.System }
                onNodeWithText("Language").assertExists()
                runOnIdle {
                    assertEquals(Locale.FRENCH, Locale.getDefault())
                    assertEquals(1, creations)
                }
                runOnIdle { language = InterfaceLanguage.Spanish }
                onNodeWithText("Idioma").assertExists()
            }
            assertEquals(Locale.FRENCH, Locale.getDefault())
        } finally { Locale.setDefault(original) }
    }
}
