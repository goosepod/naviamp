package app.naviamp.presentation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.ui.NaviampApplicationSurface
import app.naviamp.ui.SharedRoute
import java.util.Locale
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionLanguageTest {
    @Test fun remoteLanguageSelectionKeepsThePageAndBackRestoresFocusAt720p() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            runDesktopComposeUiTest(1280, 720) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
                val provider = FakeCoreMediaProvider()
                val core = NaviampCore.create(scope, fakeCoreServices(provider), initialState = NaviampCoreInitialState(
                    connection = NaviampConnectionRuntimeState(
                        phase = NaviampConnectionPhase.Connected, sourceId = provider.id.value,
                    ),
                ))
                try {
                    setContent { NaviampCoreApp(core, applicationSurface = NaviampApplicationSurface.Television) }
                    runOnIdle { core.actions.shell.navigationActions.onRouteSelected(SharedRoute.Settings) }
                    onNodeWithText("Display").performSemanticsAction(SemanticsActions.RequestFocus)
                        .performKeyInput { pressKey(Key.Enter) }
                    onNodeWithText("Language").performSemanticsAction(SemanticsActions.RequestFocus)
                        .performKeyInput { pressKey(Key.Enter) }
                    onNodeWithText("Spanish").performSemanticsAction(SemanticsActions.RequestFocus)
                        .performKeyInput { pressKey(Key.Enter) }
                    onNodeWithText("Español").assertIsFocused()
                    onNodeWithText("Predeterminado del sistema").assertIsDisplayed()
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    onNodeWithText("Predeterminado del sistema", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(1, layouts.size)
                    val layout = layouts.single()
                    // Fractional glyph widths can exceed the rounded pixel size without truncation.
                    assertFalse(layout.multiParagraph.didExceedMaxLines)
                    assertFalse((0 until layout.lineCount).any(layout::isLineEllipsized))
                    assertEquals("Predeterminado del sistema".length,
                        layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
                    runOnIdle { assertEquals(InterfaceLanguage.Spanish, core.state.value.shell.general.interfaceSettings.language) }
                    onNodeWithText("Predeterminado del sistema")
                        .performSemanticsAction(SemanticsActions.RequestFocus)
                        .performKeyInput { pressKey(Key.Enter) }
                    onNodeWithText("System Default").assertIsFocused()
                    runOnIdle { assertEquals(InterfaceLanguage.System, core.state.value.shell.general.interfaceSettings.language) }
                    onNodeWithText("Spanish").performSemanticsAction(SemanticsActions.RequestFocus)
                        .performKeyInput { pressKey(Key.Enter) }
                    onNodeWithText("Español").performKeyInput { pressKey(Key.Escape) }
                    onNodeWithText("Idioma").assertIsFocused()
                    onNodeWithText("Idioma").performKeyInput { pressKey(Key.Back) }
                    onNodeWithText("Pantalla").assertIsFocused()
                        .performKeyInput { pressKey(Key.Escape) }
                    onNodeWithContentDescription("Ajustes").assertIsFocused()
                } finally { scope.cancel() }
            }
        } finally { Locale.setDefault(original) }
    }
}
