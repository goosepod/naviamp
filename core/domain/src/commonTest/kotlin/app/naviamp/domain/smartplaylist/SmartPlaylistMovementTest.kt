package app.naviamp.domain.smartplaylist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SmartPlaylistMovementTest {
    private fun condition(value: String) = SmartPlaylistConditionDraft(value = value)
    private val up = SmartPlaylistMoveDirection.Up
    private val down = SmartPlaylistMoveDirection.Down

    @Test fun movementPreservesValuesIdentityAndLogicalContainers() {
        val first = condition("first")
        val second = condition("second")
        val group = SmartPlaylistGroupDraft(conditions = listOf(condition("inner one"), condition("inner two")))
        val other = SmartPlaylistGroupDraft(match = SmartPlaylistMatch.All, conditions = listOf(condition("other")))
        val draft = SmartPlaylistDraft(conditions = listOf(first, second), groups = listOf(group, other))
        val moved = draft.moveCondition(second.id, up)
        assertEquals(listOf(second, first), moved.conditions)
        assertSame(second, moved.conditions.first())
        assertEquals(draft.groups, moved.groups)
        val inner = moved.moveCondition(group.conditions.last().id, up, group.id)
        assertEquals(group.conditions.reversed(), inner.groups.first().conditions)
        assertEquals(moved.conditions, inner.conditions)
        assertEquals(other, inner.groups.last())
        assertEquals(SmartPlaylistMatch.Any, inner.groups.first().match)
        assertEquals(inner.groups.reversed(), inner.moveGroup(other.id, up).groups)
        assertEquals(draft, draft.moveCondition(second.id, up, other.id))
    }

    @Test fun boundariesMissingIdsAndSingleRowsAreNoOps() {
        val first = condition("first")
        val second = condition("second")
        val draft = SmartPlaylistDraft(conditions = listOf(first, second))
        assertEquals(draft, draft.moveCondition(first.id, up))
        assertEquals(draft, draft.moveCondition(second.id, down))
        assertEquals(draft, draft.moveCondition(Long.MIN_VALUE, down))
        assertEquals(draft, draft.moveSort(draft.sort.single().id, down))
        assertEquals(draft, draft.moveGroup(Long.MIN_VALUE, up))
        assertEquals(draft, draft.moveCondition(first.id, down, Long.MIN_VALUE))
        val empty = draft.copy(conditions = emptyList(), sort = emptyList())
        assertEquals(empty, empty.moveCondition(first.id, down).moveSort(0, up))
    }

    @Test fun saveAndReopenPreservesMovedRuleValuesAndSortPrecedence() {
        val year = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Year }
        val title = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Title }
        val first = condition("first")
        val second = condition("second")
        val sorts = listOf(SmartPlaylistSortDraft(year, true), SmartPlaylistSortDraft(title, false))
        val draft = SmartPlaylistDraft(conditions = listOf(first, second), sort = sorts)
        val moved = draft.moveCondition(second.id, up).moveSort(sorts.last().id, up)
        assertSame(sorts.last(), moved.sort.first())
        val definition = moved.toDefinition()
        assertEquals(listOf(SmartPlaylistSort(SmartPlaylistFields.Title, false), SmartPlaylistSort(SmartPlaylistFields.Year, true)), definition.sort)
        val reopened = SmartPlaylistDraft.fromDefinition(SmartPlaylistDefinition.fromJsonObject(definition.toJsonElement())).toDefinition()
        assertEquals(definition.rules, reopened.rules)
        assertEquals(definition.sort, reopened.sort)
        assertEquals(listOf(first, second), draft.conditions)
        assertEquals(sorts, draft.sort)
    }
}
