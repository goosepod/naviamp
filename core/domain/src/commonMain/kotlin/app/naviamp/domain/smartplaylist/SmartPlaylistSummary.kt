package app.naviamp.domain.smartplaylist

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
