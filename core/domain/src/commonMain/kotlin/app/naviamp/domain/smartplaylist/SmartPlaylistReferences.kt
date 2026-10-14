package app.naviamp.domain.smartplaylist

import app.naviamp.domain.Playlist

/** Accessible server playlists, independent of whether their tracks can be edited. */
data class SmartPlaylistReference(
    val id: String,
    val name: String,
    val owner: String? = null,
    val isSmart: Boolean = false,
)

fun Playlist.toSmartPlaylistReference() = SmartPlaylistReference(id, name, owner, isSmart)

fun SmartPlaylistConditionDraft.selectOperator(next: SmartPlaylistOperator): SmartPlaylistConditionDraft {
    require(next in field.operators)
    return copy(operator = next,
        value = if (field.valueType == SmartPlaylistValueType.PlaylistId) value else "",
        secondValue = "", genreSelection = null)
}

fun smartPlaylistReferenceMatches(
    references: List<SmartPlaylistReference>,
    query: String,
    currentPlaylistId: String? = null,
): List<SmartPlaylistReference> {
    val search = query.trim()
    return references.filter {
        it.id != currentPlaylistId && (search.isEmpty() || it.name.contains(search, true) ||
            it.owner.orEmpty().contains(search, true) || it.id.contains(search, true))
    }.sortedWith(compareBy<SmartPlaylistReference> { it.name.lowercase() }.thenBy { it.id })
}

fun SmartPlaylistDefinition.referencesPlaylist(playlistId: String): Boolean =
    rules.any { it.referencesPlaylist(playlistId) }

private fun SmartPlaylistRule.referencesPlaylist(playlistId: String): Boolean = when (this) {
    is SmartPlaylistGroup -> rules.any { it.referencesPlaylist(playlistId) }
    is SmartPlaylistCondition ->
        operator in listOf(SmartPlaylistOperator.InPlaylist, SmartPlaylistOperator.NotInPlaylist) &&
            (value as? SmartPlaylistValue.Text)?.value == playlistId
}
