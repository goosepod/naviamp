package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import app.naviamp.domain.settings.LyricsDisplayPreference
import app.naviamp.domain.settings.LyricsTimingPreference
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionLocalizationTest {
    @Test fun englishCountsAndChoicesRemainReadable() = labels("en", listOf(
        "1 track", "2 tracks", "1 release", "6 releases", "Off", "1 second", "5 seconds",
        "320 steps", "First available", "Word synced", "Match preferred lyrics", "Repeat all",
    ))

    @Test fun spanishCountsAndChoicesUseTranslatedPlurals() = labels("es", listOf(
        "1 pista", "2 pistas", "1 lanzamiento", "6 lanzamientos", "Desactivado", "1 segundo", "5 segundos",
        "320 niveles", "Primera disponible", "Sincronizadas por palabra", "Usar letras preferidas", "Repetir todo",
    ))

    private fun labels(language: String, expected: List<String>) = withLocale(language) {
        runComposeUiTest {
            setContent {
                Column {
                    listOf(televisionTrackCountLabel(1), televisionTrackCountLabel(2),
                        televisionReleaseCountLabel(1), televisionReleaseCountLabel(6),
                        televisionCrossfadeLabel(0), televisionCrossfadeLabel(1), televisionCrossfadeLabel(5),
                        televisionWaveformDensityLabel(320), televisionLyricsTimingLabel(LyricsTimingPreference.FirstAvailable),
                        televisionLyricsTimingLabel(LyricsTimingPreference.WordSynced),
                        televisionLyricsDisplayLabel(LyricsDisplayPreference.MatchDownload),
                        televisionRepeatModeDescription(NaviampRepeatMode.Queue),
                    ).forEach { Text(it) }
                }
            }
            expected.forEach { onNodeWithText(it).assertExists() }
        }
    }

    @Test fun spanishSettingsRemainOperableWithTheRemoteAt720p() = withLocale("es") {
        runDesktopComposeUiTest(1280, 720) {
            var selected: TelevisionSettingsCategory? = null
            setContent {
                Box(Modifier.fillMaxSize().background(NaviampColors.Dark.background)) {
                    Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.43f)
                        .widthIn(min = 430.dp).padding(horizontal = 22.dp, vertical = 20.dp)) {
                    TelevisionSettingsRoot(
                        uiState = NaviampAppShellUiState(), colors = NaviampColors.Dark,
                        firstFocusRequester = remember { FocusRequester() },
                        returnFocusRequester = remember { FocusRequester() }, returnCategory = null,
                        onCategorySelected = { selected = it },
                    )
                    }
                }
            }
            onNodeWithText("Fuentes").assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.RequestFocus)
                .performKeyInput { pressKey(Key.Enter) }
            assertEquals(TelevisionSettingsCategory.Sources, selected)
            onNodeWithText("Pantalla").assertIsDisplayed()
            onNodeWithText("Reproducción").assertIsDisplayed()
            val pixels = onRoot().captureToImage().toPixelMap()
            val capture = BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until 720) for (x in 0 until 1280) capture.setRGB(x, y, pixels[x, y].toArgb())
            val file = File("build/reports/television-localization/settings-es-1280x720.png")
            file.parentFile.mkdirs()
            ImageIO.write(capture, "png", file)
        }
    }

    @Test fun spanishConnectStatusFormatsDeviceNamesWithoutParsingThem() = withLocale("es") {
        runComposeUiTest {
            setContent {
                val state = NaviampConnectSettingsUi(
                    status = "Diagnostic fallback",
                    statusMessage = NaviampConnectStatusMessage(
                        NaviampConnectStatusText.ReconnectedToDevice, listOf("Sala % café")),
                )
                Text(state.displayStatus().orEmpty())
            }
            onNodeWithText("Se ha vuelto a conectar con Sala % café.").assertExists()
            onNodeWithText("Diagnostic fallback").assertDoesNotExist()
        }
    }

    @Test fun spanishConnectionFormKeepsImeNavigationAndLocalizedActions() = withLocale("es") {
        runComposeUiTest {
            setContent {
                NaviampConnectionForm(
                    form = ConnectionFormState(), colors = NaviampColors.Dark, isReconnect = false,
                    onFormChanged = {}, onConnect = {}, onCancel = null,
                    allowLocalFileInputs = false, allowFallbackUrls = false,
                )
            }
            onNodeWithText("URL del servidor").assertExists()
            onAllNodesWithText("Contraseña").assertCountEquals(2)
            onNodeWithText("Conectar").assertExists()
            onNodeWithTag(ConnectionNameFieldTestTag).performClick().performImeAction()
            onNodeWithTag(ConnectionServerUrlFieldTestTag).assertIsFocused()
            onNodeWithTag(ConnectionAdvancedActionTestTag).performClick()
            onNodeWithText("Cabeceras").assertExists()
        }
    }

    private fun withLocale(language: String, block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(language))
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
