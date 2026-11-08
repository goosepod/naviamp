package app.naviamp.domain.smartplaylist

import app.naviamp.domain.Playlist
import kotlin.test.*

class SmartPlaylistReferencesTest {
    @Test fun referencesIncludeReadOnlySmartAndRegularPlaylistsAndExcludeSelf() {
        val references = listOf(
            Playlist("a", "Mix", 0, owner = "Alice"),
            Playlist("b", "Mix", 0, isSmart = true, owner = "Bob", canEdit = false),
        ).map { it.toSmartPlaylistReference() }
        assertEquals(listOf("a", "b"), smartPlaylistReferenceMatches(references, "mix").map { it.id })
        assertEquals("b", smartPlaylistReferenceMatches(references, "BOB").single().id)
        assertEquals(listOf("a"), smartPlaylistReferenceMatches(references, "", "b").map { it.id })
    }

    @Test fun renamedAndUnavailableTargetsRetainStableIdsThroughOperatorChangeAndReopening() {
        val field = SmartPlaylistFieldCatalog.fields.single { it.valueType == SmartPlaylistValueType.PlaylistId }
        val condition = SmartPlaylistConditionDraft(field, SmartPlaylistOperator.InPlaylist, "missing-id")
        val changed = condition.selectOperator(SmartPlaylistOperator.NotInPlaylist)
        val definition = SmartPlaylistDraft(name = "References", conditions = listOf(changed)).toDefinition()
        val reopened = SmartPlaylistDraft.fromDefinition(definition).conditions.single()
        assertEquals("missing-id", reopened.value)
        assertEquals(SmartPlaylistOperator.NotInPlaylist, reopened.operator)
        assertTrue(definition.toNspJson().contains("missing-id"))
        assertTrue(definition.referencesPlaylist("missing-id"))
        assertFalse(definition.referencesPlaylist("different"))
    }

    @Test fun selfReferenceDetectionIncludesGroupedExclusionsButIgnoresOrdinaryText() {
        val reference = SmartPlaylistCondition(SmartPlaylistOperator.NotInPlaylist, "id", SmartPlaylistValue.Text("self"))
        val definition = SmartPlaylistDefinition("Test", rules = listOf(
            SmartPlaylistGroup(SmartPlaylistMatch.Any, listOf(reference)),
        ))
        assertTrue(definition.referencesPlaylist("self"))
        assertFalse(definition.copy(rules = listOf(reference.copy(field = "title", operator = SmartPlaylistOperator.Is)))
            .referencesPlaylist("self"))
    }
}
