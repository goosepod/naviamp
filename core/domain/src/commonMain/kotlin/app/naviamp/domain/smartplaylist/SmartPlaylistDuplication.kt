package app.naviamp.domain.smartplaylist

/**
 * Picks the first localized copy name that no existing playlist already uses. The ordinal starts
 * at 1 for the plain copy name and increases only on collisions.
 */
suspend fun duplicateSmartPlaylistName(
    sourceName: String,
    existingNames: Collection<String>,
    format: suspend (base: String, ordinal: Int) -> String,
): String {
    val base = sourceName.trim()
    val taken = existingNames.mapTo(HashSet()) { it.trim().lowercase() }
    var ordinal = 1
    while (true) {
        val candidate = format(base, ordinal)
        if (candidate.trim().lowercase() !in taken || ordinal > taken.size) return candidate
        ordinal++
    }
}

/** The copy keeps every dynamic setting so it stays a smart playlist, not a track snapshot. */
fun SmartPlaylistDefinition.duplicateDraft(name: String): SmartPlaylistDraft =
    SmartPlaylistDraft.fromDefinition(copy(name = name))
