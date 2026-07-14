package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.smartplaylist.SmartPlaylistCondition
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistFields
import app.naviamp.domain.smartplaylist.SmartPlaylistOperator
import app.naviamp.domain.smartplaylist.SmartPlaylistValue
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistAuthenticationUiTest {
    @Test
    fun expiredTokenPromptsForPasswordAndRetriesPendingSave() = runComposeUiTest {
        val retriedPasswords = mutableListOf<String>()
        var dismissCount = 0
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(),
                initialDraft = SmartPlaylistDraft.fromDefinition(testDefinition()),
                onDismissRequest = { dismissCount += 1 },
                onSave = { error("Navidrome returned HTTP 401.") },
                onSaveWithPassword = { _, password -> retriedPasswords += password },
            )
        }

        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitForIdle()
        onNodeWithTag(SmartPlaylistPasswordFieldTestTag).performTextInput("secret")
        onNodeWithTag(SmartPlaylistPasswordSaveTestTag).performClick()
        waitForIdle()

        runOnIdle {
            assertEquals(listOf("secret"), retriedPasswords)
            assertEquals(1, dismissCount)
        }
    }

    @Test
    fun failedReauthenticationKeepsDraftOpenAndShowsFailure() = runComposeUiTest {
        var retryCount = 0
        var dismissCount = 0
        setContent {
            SmartPlaylistBuilderDialog(
                colors = NaviampColors(),
                initialDraft = SmartPlaylistDraft.fromDefinition(testDefinition()),
                onDismissRequest = { dismissCount += 1 },
                onSave = { error("Navidrome returned HTTP 401.") },
                onSaveWithPassword = { _, _ ->
                    retryCount += 1
                    error("Invalid Navidrome credentials.")
                },
            )
        }

        onNodeWithTag(SmartPlaylistSaveTestTag).performClick()
        waitForIdle()
        onNodeWithTag(SmartPlaylistPasswordFieldTestTag).performTextInput("wrong")
        onNodeWithTag(SmartPlaylistPasswordSaveTestTag).performClick()
        waitForIdle()

        onNodeWithText("Invalid Navidrome credentials.").assertExists()
        onNodeWithText("Work Ambient").assertExists()
        runOnIdle {
            assertEquals(1, retryCount)
            assertEquals(0, dismissCount)
        }
    }
}

private fun testDefinition() = SmartPlaylistDefinition(
    name = "Work Ambient",
    rules = listOf(
        SmartPlaylistCondition(
            operator = SmartPlaylistOperator.Is,
            field = SmartPlaylistFields.Artist,
            value = SmartPlaylistValue.Text("Ascendant"),
        ),
    ),
)
