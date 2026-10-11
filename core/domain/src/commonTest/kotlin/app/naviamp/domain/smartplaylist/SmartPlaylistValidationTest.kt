package app.naviamp.domain.smartplaylist

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartPlaylistValidationTest {
    private fun condition(field: String, value: String, operator: SmartPlaylistOperator = SmartPlaylistOperator.Is) =
        SmartPlaylistConditionDraft(SmartPlaylistFieldCatalog.fields.first { it.field == field }, operator, value)

    @Test
    fun datesRequireRealCalendarDays() {
        for (date in listOf("2026-02-30", "1900-02-29", "2026-13-01", "0000-01-01", "2026-2-01")) {
            assertEquals(SmartPlaylistIssueCode.InvalidDate,
                condition(SmartPlaylistFields.DateAdded, date, SmartPlaylistOperator.Before).validate().single().code)
        }
        for (date in listOf("2000-02-29", "2024-02-29", "2026-10-10")) {
            assertTrue(condition(SmartPlaylistFields.DateAdded, date, SmartPlaylistOperator.Before).validate().isEmpty())
        }
    }

    @Test
    fun validatesNumbersRangesAndKnownDomains() {
        val range = condition(SmartPlaylistFields.Year, "2000", SmartPlaylistOperator.InTheRange).copy(secondValue = "1990")
        assertEquals(SmartPlaylistIssueCode.ReversedRange, range.validate().single().code)
        assertTrue(range.copy(secondValue = "2010").validate().isEmpty())
        for (value in listOf("NaN", "Infinity", "-Infinity", "1e400")) {
            assertEquals(SmartPlaylistIssueCode.InvalidNumber,
                condition(SmartPlaylistFields.ReplayGainTrackGain, value).validate().single().code)
        }
        assertTrue(condition(SmartPlaylistFields.ReplayGainTrackGain, "-9.5").validate().isEmpty())
        assertEquals(SmartPlaylistIssueCode.OutOfBounds, condition(SmartPlaylistFields.Rating, "6").validate().single().code)
        assertEquals(SmartPlaylistIssueCode.OutOfBounds, condition(SmartPlaylistFields.Size, "-1").validate().single().code)
        assertTrue(SmartPlaylistDraft.fromDefinition(SmartPlaylistTemplates.randomLibrarySongs()).validate().isEmpty())
        assertEquals(SmartPlaylistIssueCode.InvalidChoice, condition(SmartPlaylistFields.ExplicitStatus, "explicit").validate().single().code)
        assertTrue(condition(SmartPlaylistFields.ExplicitStatus, "").validate().isEmpty())
        assertEquals(SmartPlaylistValue.Text(""), condition(SmartPlaylistFields.ExplicitStatus, "").toConditionOrNull()?.value)
    }

    @Test
    fun validatesDaysAndOperatorCompatibility() {
        for (value in listOf("0", "-1", "1.5", "tomorrow")) {
            assertEquals(SmartPlaylistIssueCode.InvalidDays,
                condition(SmartPlaylistFields.LastPlayed, value, SmartPlaylistOperator.InTheLast).validate().single().code)
        }
        assertEquals(SmartPlaylistIssueCode.UnsupportedOperator,
            condition(SmartPlaylistFields.Artist, "1", SmartPlaylistOperator.GreaterThan).validate().single().code)
    }

    @Test
    fun onlyUntouchedImplicitStarterCanBeIgnored() {
        val valid = SmartPlaylistConditionDraft(value = "Miles Davis")
        val starter = SmartPlaylistDraft().conditions.single()
        assertTrue(SmartPlaylistDraft(conditions = listOf(starter, valid)).validate().isEmpty())
        assertEquals(1, SmartPlaylistDraft(conditions = listOf(starter, valid)).toDefinition().rules.size)
        for (invalid in listOf(SmartPlaylistConditionDraft(), starter.copy(isStarter = false), starter.copy(value = " "))) {
            assertFailsWith<SmartPlaylistValidationException> {
                SmartPlaylistDraft(conditions = listOf(invalid, valid)).toDefinition()
            }
        }
        assertEquals(SmartPlaylistIssueCode.NoRules, SmartPlaylistDraft().validate().single().code)
    }

    @Test
    fun findingsKeepIdentityAndDisappearWhenCorrectedOrRemoved() {
        val invalid = condition(SmartPlaylistFields.Rating, "four")
        val group = SmartPlaylistGroupDraft(conditions = listOf(invalid))
        val draft = SmartPlaylistDraft(groups = listOf(group))
        assertEquals(SmartPlaylistIssueLocation(invalid.id, group.id), draft.validate().single().location)
        val corrected = invalid.copy(value = "4")
        assertEquals(invalid.id, corrected.id)
        assertTrue(draft.copy(groups = listOf(group.copy(conditions = listOf(corrected)))).validate().isEmpty())
        assertTrue(draft.copy(conditions = listOf(SmartPlaylistConditionDraft(value = "Miles Davis")), groups = emptyList()).validate().isEmpty())
        assertEquals(SmartPlaylistIssueCode.EmptyGroup, draft.copy(groups = listOf(group.copy(conditions = emptyList()))).validate().single().code)
    }
    @Test
    fun mixedValidAndInvalidConditionsCannotSave() {
        val valid = SmartPlaylistConditionDraft(value = "Miles Davis")
        val invalid = SmartPlaylistConditionDraft(
            field = SmartPlaylistFieldCatalog.fields.first { it.field == SmartPlaylistFields.Rating },
            operator = SmartPlaylistOperator.Is,
            value = "four",
        )
        assertFailsWith<IllegalArgumentException> {
            SmartPlaylistDraft(conditions = listOf(valid, invalid)).toDefinition()
        }
    }

    @Test
    fun partlyCompletedGroupCannotLoseConditions() {
        assertFailsWith<IllegalArgumentException> {
            SmartPlaylistDraft(
                conditions = emptyList(),
                groups = listOf(SmartPlaylistGroupDraft(conditions = listOf(
                    SmartPlaylistConditionDraft(value = "Miles Davis"),
                    SmartPlaylistConditionDraft(),
                ))),
            ).toDefinition()
        }
    }
}
