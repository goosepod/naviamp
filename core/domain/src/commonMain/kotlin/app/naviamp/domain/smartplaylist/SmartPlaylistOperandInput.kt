package app.naviamp.domain.smartplaylist

enum class SmartPlaylistValueUnit { Seconds, Bytes, Kilobits, Hertz, Bits, BeatsPerMinute, Decibels, Stars }

enum class SmartPlaylistChoiceLabel { True, False, Unrated, Stars, Explicit, Clean, NotSpecified }

/** [value] is the exact provider operand the choice stores; [count] parameterizes star labels. */
data class SmartPlaylistValueChoice(val value: String, val label: SmartPlaylistChoiceLabel, val count: Int = 0)

/** The input a rule operand needs, derived only from the shared catalog, operator, and stored value. */
sealed interface SmartPlaylistOperandInput {
    data object None : SmartPlaylistOperandInput
    data object Text : SmartPlaylistOperandInput
    data object Genre : SmartPlaylistOperandInput
    data object PlaylistReference : SmartPlaylistOperandInput
    data object Library : SmartPlaylistOperandInput
    data object CalendarDate : SmartPlaylistOperandInput
    data class RelativeDays(val presets: List<Int> = SmartPlaylistRelativeDayPresets) : SmartPlaylistOperandInput
    data class Number(
        val decimal: Boolean,
        val unit: SmartPlaylistValueUnit? = null,
        val minimum: Double? = null,
        val maximum: Double? = null,
    ) : SmartPlaylistOperandInput

    data class Choice(val choices: List<SmartPlaylistValueChoice>) : SmartPlaylistOperandInput {
        fun selected(raw: String): SmartPlaylistValueChoice? = choices.firstOrNull { it.value == raw.trim().lowercase() }
    }
}

val SmartPlaylistRelativeDayPresets = listOf(7, 30, 90, 365)

private val BooleanChoices = SmartPlaylistOperandInput.Choice(listOf(
    SmartPlaylistValueChoice("true", SmartPlaylistChoiceLabel.True),
    SmartPlaylistValueChoice("false", SmartPlaylistChoiceLabel.False),
))

// Navidrome stores 0 for unrated tracks and whole stars from 1 to 5.
private val RatingChoices = SmartPlaylistOperandInput.Choice(
    listOf(SmartPlaylistValueChoice("0", SmartPlaylistChoiceLabel.Unrated)) +
        (1..5).map { SmartPlaylistValueChoice(it.toString(), SmartPlaylistChoiceLabel.Stars, it) },
)

private val ExplicitChoices = SmartPlaylistOperandInput.Choice(listOf(
    SmartPlaylistValueChoice("e", SmartPlaylistChoiceLabel.Explicit),
    SmartPlaylistValueChoice("c", SmartPlaylistChoiceLabel.Clean),
    SmartPlaylistValueChoice("", SmartPlaylistChoiceLabel.NotSpecified),
))

private val RatingFields = setOf(SmartPlaylistFields.Rating, SmartPlaylistFields.AlbumRating, SmartPlaylistFields.ArtistRating)

fun SmartPlaylistConditionDraft.operandInput(): SmartPlaylistOperandInput = when {
    field.valueType == SmartPlaylistValueType.PlaylistId -> SmartPlaylistOperandInput.PlaylistReference
    // An imported explicit false keeps a visible choice so its inverted meaning is never hidden.
    operator == SmartPlaylistOperator.IsMissing || operator == SmartPlaylistOperator.IsPresent ->
        if (value.trim().lowercase() == "false") BooleanChoices else SmartPlaylistOperandInput.None
    operator == SmartPlaylistOperator.InTheLast || operator == SmartPlaylistOperator.NotInTheLast ->
        SmartPlaylistOperandInput.RelativeDays()
    field.valueType == SmartPlaylistValueType.Boolean -> BooleanChoices
    field.field == SmartPlaylistFields.LibraryId -> SmartPlaylistOperandInput.Library
    field.field == SmartPlaylistFields.Genre -> SmartPlaylistOperandInput.Genre
    field.field == SmartPlaylistFields.ExplicitStatus -> ExplicitChoices
    field.field in RatingFields -> RatingChoices
    field.valueType == SmartPlaylistValueType.Date -> SmartPlaylistOperandInput.CalendarDate
    field.valueType == SmartPlaylistValueType.Integer || field.valueType == SmartPlaylistValueType.Decimal ->
        SmartPlaylistOperandInput.Number(
            decimal = field.valueType == SmartPlaylistValueType.Decimal,
            unit = smartPlaylistValueUnit(field.field),
            minimum = field.constraints.minimum,
            maximum = field.constraints.maximum,
        )
    else -> SmartPlaylistOperandInput.Text
}

private fun smartPlaylistValueUnit(field: String): SmartPlaylistValueUnit? = when (field) {
    SmartPlaylistFields.Duration, SmartPlaylistFields.AlbumDuration -> SmartPlaylistValueUnit.Seconds
    SmartPlaylistFields.Size, SmartPlaylistFields.AlbumSize -> SmartPlaylistValueUnit.Bytes
    SmartPlaylistFields.Bitrate -> SmartPlaylistValueUnit.Kilobits
    SmartPlaylistFields.SampleRate -> SmartPlaylistValueUnit.Hertz
    SmartPlaylistFields.BitDepth -> SmartPlaylistValueUnit.Bits
    SmartPlaylistFields.Bpm -> SmartPlaylistValueUnit.BeatsPerMinute
    SmartPlaylistFields.ReplayGainAlbumGain, SmartPlaylistFields.ReplayGainTrackGain -> SmartPlaylistValueUnit.Decibels
    SmartPlaylistFields.AverageRating -> SmartPlaylistValueUnit.Stars
    else -> null
}
