package app.naviamp.domain.smartplaylist

/** Normalize equality and negation for readable boolean predicates without changing the rule. */
fun SmartPlaylistCondition.summaryBooleanValue(): Boolean? {
    val operand = (value as? SmartPlaylistValue.Flag)?.value ?: return null
    return when (operator) {
        SmartPlaylistOperator.Is -> operand
        SmartPlaylistOperator.IsNot -> !operand
        else -> null
    }
}

/** A false missing/present operand reverses the predicate; it is not a displayed value. */
fun SmartPlaylistCondition.summaryOperator(): SmartPlaylistOperator = when {
    value != SmartPlaylistValue.Flag(false) -> operator
    operator == SmartPlaylistOperator.IsMissing -> SmartPlaylistOperator.IsPresent
    operator == SmartPlaylistOperator.IsPresent -> SmartPlaylistOperator.IsMissing
    else -> operator
}

data class SmartPlaylistSummaryReference(
    val name: String,
    val owner: String? = null,
    val isSmart: Boolean = false,
    val distinguishingId: String? = null,
)

/** Names are presentation only. Stable IDs remain in the definition, including unresolved IDs. */
fun smartPlaylistSummaryReference(id: String, references: List<SmartPlaylistReference>): SmartPlaylistSummaryReference? {
    val reference = references.firstOrNull { it.id == id } ?: return null
    if (reference.name.isBlank()) return null
    val duplicates = references.filter { it.name.equals(reference.name, ignoreCase = true) }
    val ambiguous = duplicates.count { it.owner == reference.owner && it.isSmart == reference.isSmart } > 1
    return SmartPlaylistSummaryReference(
        reference.name,
        reference.owner?.takeIf { duplicates.size > 1 && it.isNotBlank() },
        isSmart = duplicates.size > 1 && reference.isSmart,
        distinguishingId = reference.id.takeIf { ambiguous },
    )
}

/** Owner-specific server semantics also apply when a playlist is public. */
fun SmartPlaylistDefinition.ownerDependentFields(): Set<String> =
    rules.flatMapTo(linkedSetOf()) { it.ownerDependentFields() } +
        sort.map { it.field.lowercase() }.filter { it in OwnerDependentFields }

private fun SmartPlaylistRule.ownerDependentFields(): Set<String> = when (this) {
    is SmartPlaylistGroup -> rules.flatMapTo(linkedSetOf()) { it.ownerDependentFields() }
    is SmartPlaylistCondition -> setOf(field.lowercase()).intersect(OwnerDependentFields)
}

private val OwnerDependentFields = setOf(
    SmartPlaylistFields.Loved, SmartPlaylistFields.DateLoved, SmartPlaylistFields.Rating,
    SmartPlaylistFields.DateRated, SmartPlaylistFields.PlayCount, SmartPlaylistFields.LastPlayed,
    SmartPlaylistFields.AlbumRating, SmartPlaylistFields.AlbumLoved,
    SmartPlaylistFields.AlbumPlayCount, SmartPlaylistFields.AlbumLastPlayed, SmartPlaylistFields.AlbumDateLoved,
    SmartPlaylistFields.AlbumDateRated, SmartPlaylistFields.ArtistRating, SmartPlaylistFields.ArtistLoved,
    SmartPlaylistFields.ArtistPlayCount, SmartPlaylistFields.ArtistLastPlayed,
    SmartPlaylistFields.ArtistDateLoved, SmartPlaylistFields.ArtistDateRated,
)
