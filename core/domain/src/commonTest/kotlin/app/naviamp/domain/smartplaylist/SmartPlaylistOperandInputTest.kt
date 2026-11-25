package app.naviamp.domain.smartplaylist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartPlaylistOperandInputTest {
    private fun field(key: String) = SmartPlaylistFieldCatalog.fields.first { it.field == key }

    private fun condition(key: String, operator: SmartPlaylistOperator? = null, value: String = "", second: String = "") =
        SmartPlaylistConditionDraft(field(key), operator ?: field(key).operators.first(), value, second)

    private fun json(draft: SmartPlaylistConditionDraft): String = requireNotNull(draft.toRuleOrNull()).toJsonElement().toString()

    @Test
    fun everyCatalogFieldAndOperatorHasAnInputMatchingItsValueType() {
        SmartPlaylistFieldCatalog.fields.forEach { option ->
            option.operators.forEach { operator ->
                val input = SmartPlaylistConditionDraft(option, operator).operandInput()
                when {
                    operator == SmartPlaylistOperator.IsMissing || operator == SmartPlaylistOperator.IsPresent ->
                        assertEquals(SmartPlaylistOperandInput.None, input)
                    operator == SmartPlaylistOperator.InTheLast || operator == SmartPlaylistOperator.NotInTheLast ->
                        assertIs<SmartPlaylistOperandInput.RelativeDays>(input)
                    input is SmartPlaylistOperandInput.Number ->
                        assertEquals(option.valueType == SmartPlaylistValueType.Decimal, input.decimal, option.field)
                    option.valueType == SmartPlaylistValueType.Date -> assertEquals(SmartPlaylistOperandInput.CalendarDate, input)
                }
            }
        }
    }

    @Test
    fun numericUnitsFollowTheProviderFieldUnits() {
        fun unit(key: String) = (condition(key).operandInput() as SmartPlaylistOperandInput.Number).unit
        assertEquals(SmartPlaylistValueUnit.Seconds, unit(SmartPlaylistFields.Duration))
        assertEquals(SmartPlaylistValueUnit.Bytes, unit(SmartPlaylistFields.Size))
        assertEquals(SmartPlaylistValueUnit.Kilobits, unit(SmartPlaylistFields.Bitrate))
        assertEquals(SmartPlaylistValueUnit.Hertz, unit(SmartPlaylistFields.SampleRate))
        assertEquals(SmartPlaylistValueUnit.BeatsPerMinute, unit(SmartPlaylistFields.Bpm))
        assertEquals(SmartPlaylistValueUnit.Decibels, unit(SmartPlaylistFields.ReplayGainTrackGain))
        assertNull(unit(SmartPlaylistFields.Year))
        val average = condition(SmartPlaylistFields.AverageRating).operandInput() as SmartPlaylistOperandInput.Number
        assertEquals(true, average.decimal)
        assertEquals(0.0 to 5.0, average.minimum to average.maximum)
        assertEquals("""{"gt":{"duration":90.5}}""", json(condition(SmartPlaylistFields.Duration, SmartPlaylistOperator.GreaterThan, "90.5")))
        assertNull(condition(SmartPlaylistFields.Year, value = "1999.5").toRuleOrNull())
    }

    @Test
    fun ratingChoicesMapToNavidromeStarsIncludingUnrated() {
        val input = assertIs<SmartPlaylistOperandInput.Choice>(condition(SmartPlaylistFields.Rating).operandInput())
        assertEquals(listOf("0", "1", "2", "3", "4", "5"), input.choices.map { it.value })
        assertEquals(SmartPlaylistChoiceLabel.Unrated, input.choices.first().label)
        assertEquals(3, input.selected("3")?.count)
        assertEquals("""{"is":{"rating":0}}""", json(condition(SmartPlaylistFields.Rating, value = "0")))
        assertEquals("""{"inTheRange":{"albumrating":[3,5]}}""",
            json(condition(SmartPlaylistFields.AlbumRating, SmartPlaylistOperator.InTheRange, "3", "5")))
        assertIs<SmartPlaylistOperandInput.Choice>(condition(SmartPlaylistFields.ArtistRating).operandInput())
    }

    @Test
    fun explicitChoicesMapToProviderCodesIncludingUnknownStatus() {
        val input = assertIs<SmartPlaylistOperandInput.Choice>(condition(SmartPlaylistFields.ExplicitStatus).operandInput())
        assertEquals(
            listOf("e" to SmartPlaylistChoiceLabel.Explicit, "c" to SmartPlaylistChoiceLabel.Clean, "" to SmartPlaylistChoiceLabel.NotSpecified),
            input.choices.map { it.value to it.label },
        )
        assertEquals("""{"is":{"explicitstatus":"e"}}""", json(condition(SmartPlaylistFields.ExplicitStatus, value = "e")))
        assertEquals("""{"is":{"explicitstatus":""}}""", json(condition(SmartPlaylistFields.ExplicitStatus, value = "")))
        assertTrue(condition(SmartPlaylistFields.ExplicitStatus, value = "x").validate().isNotEmpty())
    }

    @Test
    fun relativePresetsAndCustomDayCountsSerializeIdentically() {
        val input = assertIs<SmartPlaylistOperandInput.RelativeDays>(
            condition(SmartPlaylistFields.DateAdded, SmartPlaylistOperator.InTheLast).operandInput(),
        )
        input.presets.forEach { preset ->
            assertEquals(
                json(condition(SmartPlaylistFields.DateAdded, SmartPlaylistOperator.InTheLast, preset.toString())),
                json(condition(SmartPlaylistFields.DateAdded, SmartPlaylistOperator.InTheLast, " $preset ")),
            )
        }
        assertEquals("""{"notInTheLast":{"lastplayed":45}}""",
            json(condition(SmartPlaylistFields.LastPlayed, SmartPlaylistOperator.NotInTheLast, "45")))
        assertNull(condition(SmartPlaylistFields.DateAdded, SmartPlaylistOperator.InTheLast, "0").toRuleOrNull())
    }

    @Test
    fun presenceConditionsNeedNoValueButKeepAnImportedFalseVisible() {
        val missing = condition(SmartPlaylistFields.Bpm, SmartPlaylistOperator.IsMissing)
        assertEquals(SmartPlaylistOperandInput.None, missing.operandInput())
        assertTrue(missing.validate().isEmpty())
        assertEquals("""{"isMissing":{"bpm":true}}""", json(missing))
        assertEquals("""{"isPresent":{"comment":true}}""", json(condition(SmartPlaylistFields.Comment, SmartPlaylistOperator.IsPresent)))
        val legacy = condition(SmartPlaylistFields.Bpm, SmartPlaylistOperator.IsMissing, "false")
        assertIs<SmartPlaylistOperandInput.Choice>(legacy.operandInput())
        assertEquals("""{"isMissing":{"bpm":false}}""", json(legacy))
    }

    @Test
    fun changingFieldOrOperatorNeverRetainsAnIncompatibleOperand() {
        val dated = condition(SmartPlaylistFields.DateAdded, SmartPlaylistOperator.Before, "2026-01-31")
        val relative = dated.selectOperator(SmartPlaylistOperator.InTheLast)
        assertEquals("", relative.value)
        val rated = condition(SmartPlaylistFields.Rating, SmartPlaylistOperator.InTheRange, "2", "4")
        val single = rated.selectOperator(SmartPlaylistOperator.Is)
        assertEquals("" to "", single.value to single.secondValue)
        val invalidRaw = condition(SmartPlaylistFields.Year, value = "19x9")
        assertEquals("19x9", invalidRaw.value)
        assertTrue(invalidRaw.validate().isNotEmpty())
    }

    @Test
    fun booleanAndGenreConditionsKeepTheirDedicatedInputs() {
        assertEquals(
            listOf("true", "false"),
            (condition(SmartPlaylistFields.Loved).operandInput() as SmartPlaylistOperandInput.Choice).choices.map { it.value },
        )
        assertEquals(SmartPlaylistOperandInput.Genre, condition(SmartPlaylistFields.Genre).operandInput())
        assertEquals(SmartPlaylistOperandInput.PlaylistReference, condition("id").operandInput())
        assertEquals(SmartPlaylistOperandInput.Library, condition(SmartPlaylistFields.LibraryId).operandInput())
        assertEquals(SmartPlaylistOperandInput.Text, condition(SmartPlaylistFields.Artist).operandInput())
    }
}
