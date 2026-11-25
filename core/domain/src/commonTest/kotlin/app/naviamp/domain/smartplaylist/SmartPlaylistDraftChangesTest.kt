package app.naviamp.domain.smartplaylist

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartPlaylistDraftChangesTest {
    private val baseline = SmartPlaylistDraft.fromDefinition(
        SmartPlaylistDefinition(
            name = "Evening",
            rules = listOf(
                SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.Artist, SmartPlaylistValue.Text("Ascendant")),
                SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.Loved, SmartPlaylistValue.Flag(true)),
            ),
        ),
    )

    @Test
    fun anUntouchedOrRestoredDraftHasNoUnsavedChanges() {
        assertFalse(baseline.hasUnsavedChanges(baseline))
        assertFalse(baseline.copy(name = "Evening mix").copy(name = "Evening").hasUnsavedChanges(baseline))
        val added = baseline.copy(conditions = baseline.conditions + SmartPlaylistConditionDraft())
        assertFalse(added.copy(conditions = added.conditions.dropLast(1)).hasUnsavedChanges(baseline))
        assertFalse(SmartPlaylistDraft().hasUnsavedChanges(SmartPlaylistDraft()))
    }

    @Test
    fun editsToDetailsRulesOrderAndOptionsAreUnsavedChanges() {
        assertTrue(baseline.copy(name = "Evening mix").hasUnsavedChanges(baseline))
        assertTrue(baseline.copy(conditions = baseline.conditions.reversed()).hasUnsavedChanges(baseline))
        val first = baseline.conditions.first()
        assertTrue(baseline.copy(conditions = listOf(first.copy(value = "Other")) + baseline.conditions.drop(1)).hasUnsavedChanges(baseline))
        assertTrue(baseline.copy(limitMode = SmartPlaylistLimitMode.Percent).hasUnsavedChanges(baseline))
        assertTrue(baseline.copy(refreshDelay = "1h").hasUnsavedChanges(baseline))
    }
}
