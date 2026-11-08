package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import app.naviamp.domain.smartplaylist.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistMovementUiTest {
    @Test fun boundaryControlsRetainMovedRowFocusAndEditedValueAtNarrowWidth() = runComposeUiTest {
        val first = SmartPlaylistConditionDraft(value = "first")
        val second = SmartPlaylistConditionDraft(value = "second")
        var draft by mutableStateOf(SmartPlaylistDraft(conditions = listOf(first, second)))
        setContent { MovementFixture(draft) { draft = it } }
        onNodeWithText("first").performTextReplacement("edited first")
        onNodeWithTag("smart-playlist-rule-${first.id}:up").assertIsNotEnabled()
        onNodeWithTag("smart-playlist-rule-${second.id}:down").assertIsNotEnabled()
        onNodeWithTag("smart-playlist-rule-${first.id}:down").performClick()
        runOnIdle {
            assertEquals(listOf(second.id, first.id), draft.conditions.map { it.id })
            assertEquals("edited first", draft.conditions.last().value)
        }
        System.getenv("NAVIAMP_MOVEMENT_CAPTURE")?.let { path ->
            val file = java.io.File(path).also { it.parentFile.mkdirs() }
            javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        }
        val up = onNodeWithTag("smart-playlist-rule-${first.id}:up")
        up.assertIsFocused().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Position 2 of 2"))
        up.performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("smart-playlist-rule-${first.id}:down").assertIsFocused()
        runOnIdle { assertEquals(listOf(first.id, second.id), draft.conditions.map { it.id }) }
    }

    @Test fun groupedRulesMoveOnlyInsideTheirGroupAndGroupsRetainTheirIdentity() = runComposeUiTest {
        val group = SmartPlaylistGroupDraft(conditions = listOf(
            SmartPlaylistConditionDraft(value = "inner first"), SmartPlaylistConditionDraft(value = "inner second")))
        val other = SmartPlaylistGroupDraft(match = SmartPlaylistMatch.All,
            conditions = listOf(SmartPlaylistConditionDraft(value = "other group")))
        val top = SmartPlaylistConditionDraft(value = "top")
        var draft by mutableStateOf(SmartPlaylistDraft(conditions = listOf(top), groups = listOf(group, other)))
        setContent { MovementFixture(draft) { draft = it } }
        val moved = group.conditions.last()
        onNodeWithTag("smart-playlist-rule-${moved.id}:up").performScrollTo().performClick()
        runOnIdle {
            assertEquals(group.conditions.reversed(), draft.groups.first().conditions)
            assertEquals(listOf(top), draft.conditions)
            assertEquals(other, draft.groups.last())
        }
        onNodeWithTag("smart-playlist-rule-${moved.id}:down").assertIsFocused()
        onNodeWithTag("smart-playlist-group-${other.id}:up").performScrollTo().performClick()
        onNodeWithTag("smart-playlist-group-${other.id}:down").assertIsFocused()
        runOnIdle { assertEquals(other.id, draft.groups.first().id); assertEquals(SmartPlaylistMatch.All, draft.groups.first().match) }
    }

    @Test fun movingSortPriorityChangesSavedOrderAndSupportsRemoteActivation() = runComposeUiTest {
        val title = SmartPlaylistFieldCatalog.sortableFields.first { it.field == SmartPlaylistFields.Title }
        val year = SmartPlaylistFieldCatalog.sortableFields.first { it.field == SmartPlaylistFields.Year }
        val sorts = listOf(SmartPlaylistSortDraft(title, false), SmartPlaylistSortDraft(year, true))
        var draft by mutableStateOf(SmartPlaylistDraft(
            conditions = listOf(SmartPlaylistConditionDraft(value = "fixture")), sort = sorts))
        setContent { MovementFixture(draft) { draft = it } }
        val up = onNodeWithTag("smart-playlist-sort-${sorts.last().id}:up")
        up.performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        up.performKeyInput { pressKey(Key.DirectionCenter) }
        waitForIdle()
        onNodeWithTag("smart-playlist-sort-${sorts.last().id}:down").assertIsFocused()
        runOnIdle {
            val saved = draft.toDefinition()
            assertEquals(listOf(SmartPlaylistSort(SmartPlaylistFields.Year, true), SmartPlaylistSort(SmartPlaylistFields.Title, false)), saved.sort)
            assertEquals(saved.sort, SmartPlaylistDraft.fromDefinition(saved).toDefinition().sort)
        }
    }
}

@Composable
private fun MovementFixture(draft: SmartPlaylistDraft, onChange: (SmartPlaylistDraft) -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
    Column(Modifier.background(NaviampColors.Dark.background).width(340.dp).height(700.dp).verticalScroll(rememberScrollState())) {
        SmartPlaylistCustomControls(NaviampColors.Dark, draft, emptyList(), emptyList(), emptyList(),
            false, false, null, {}, onChange)
    }
    }
}
