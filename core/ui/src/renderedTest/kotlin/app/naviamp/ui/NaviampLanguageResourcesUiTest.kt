package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.LayoutDirection
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.settings_language_use_subtitle
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalTestApi::class)
class NaviampLanguageResourcesUiTest {
    // Check actual localized output, including Android's legacy Indonesian locale code.
    private val expected = mapOf(
        "en" to "Use this language for Naviamp.",
        "ar" to "استخدم هذه اللغة في Naviamp.",
        "de" to "Diese Sprache für Naviamp verwenden.",
        "es" to "Usar este idioma en Naviamp.",
        "fr" to "Utiliser cette langue pour Naviamp.",
        "hi" to "Naviamp के लिए इस भाषा का उपयोग करें।",
        "id" to "Gunakan bahasa ini untuk Naviamp.",
        "it" to "Usa questa lingua per Naviamp.",
        "ja" to "Naviamp でこの言語を使用します。",
        "ko" to "Naviamp에 이 언어를 사용합니다.",
        "nl" to "Gebruik deze taal voor Naviamp.",
        "pl" to "Używaj tego języka w Naviamp.",
        "pt-BR" to "Usar este idioma no Naviamp.",
        "ru" to "Использовать этот язык в Naviamp.",
        "tr" to "Naviamp için bu dili kullan.",
        "uk" to "Використовувати цю мову в Naviamp.",
        "zh-CN" to "在 Naviamp 中使用此语言。",
    )

    @Test fun everyLanguageResolvesItsCatalogWithoutRecreatingUiOrLosingFocus() = runComposeUiTest {
        var language by mutableStateOf(InterfaceLanguage.English)
        var creations = 0
        var direction: LayoutDirection? = null
        val effect = createNaviampLocaleEffect()
        setContent {
            NaviampLocaleEnvironment(language, effect) {
                remember { creations++ }
                direction = LocalLayoutDirection.current
                Column {
                    Text(stringResource(Res.string.settings_language_use_subtitle), Modifier.testTag("localized"))
                    Button(onClick = {}) { Text("focus anchor") }
                }
            }
        }
        onNodeWithText("focus anchor").performSemanticsAction(SemanticsActions.RequestFocus)
        InterfaceLanguage.entries.filter { it.languageTag != null }.forEach { next ->
            runOnIdle { language = next }
            onNodeWithTag("localized").assertTextEquals(expected.getValue(next.languageTag!!))
            onNodeWithText("focus anchor").assertIsFocused()
            runOnIdle {
                assertEquals(1, creations)
                assertEquals(if (next == InterfaceLanguage.Arabic) LayoutDirection.Rtl else LayoutDirection.Ltr, direction)
            }
        }
    }
}
