package app.naviamp.domain.smartplaylist

fun SmartPlaylistConditionDraft.valueLabel(): String =
    when (operator) {
        SmartPlaylistOperator.InTheLast,
        SmartPlaylistOperator.NotInTheLast -> "Days"
        SmartPlaylistOperator.InPlaylist,
        SmartPlaylistOperator.NotInPlaylist -> "Playlist ID"
        SmartPlaylistOperator.IsMissing,
        SmartPlaylistOperator.IsPresent -> "Missing"
        else -> "Value"
    }

fun SmartPlaylistOperator.displayLabel(): String =
    when (this) {
        SmartPlaylistOperator.Is -> "is"
        SmartPlaylistOperator.IsNot -> "is not"
        SmartPlaylistOperator.GreaterThan -> "greater than"
        SmartPlaylistOperator.LessThan -> "less than"
        SmartPlaylistOperator.Contains -> "contains"
        SmartPlaylistOperator.NotContains -> "does not contain"
        SmartPlaylistOperator.StartsWith -> "starts with"
        SmartPlaylistOperator.EndsWith -> "ends with"
        SmartPlaylistOperator.InTheRange -> "in range"
        SmartPlaylistOperator.Before -> "before"
        SmartPlaylistOperator.After -> "after"
        SmartPlaylistOperator.InTheLast -> "in the last"
        SmartPlaylistOperator.NotInTheLast -> "not in the last"
        SmartPlaylistOperator.InPlaylist -> "in playlist"
        SmartPlaylistOperator.NotInPlaylist -> "not in playlist"
        SmartPlaylistOperator.IsMissing -> "is missing"
        SmartPlaylistOperator.IsPresent -> "is present"
    }

fun <T> List<T>.updated(index: Int, value: T): List<T> =
    mapIndexed { currentIndex, currentValue -> if (currentIndex == index) value else currentValue }

data class SmartPlaylistGenreOption(
    val canonicalName: String,
    val aliases: List<String> = emptyList(),
    val libraryGenreNames: List<String> = emptyList(),
    val trackCount: Int? = null,
) {
    val inLibrary: Boolean
        get() = libraryGenreNames.isNotEmpty()
}

data class SmartPlaylistGenreSelection(
    val canonicalName: String,
    val libraryGenreNames: List<String> = emptyList(),
)

/** Full-ontology suggestions for genre rules. Blank input intentionally produces no choices. */
fun smartPlaylistGenreSuggestions(
    query: String,
    genres: List<SmartPlaylistGenreOption>,
    limit: Int = 20,
): List<SmartPlaylistGenreOption> {
    val needle = query.trim()
    if (needle.isEmpty() || limit <= 0) return emptyList()
    return genres
        .asSequence()
        .filter { it.canonicalName.isNotBlank() }
        .distinctBy { it.canonicalName.lowercase() }
        .filter { genre ->
            genre.canonicalName.contains(needle, ignoreCase = true) ||
                genre.aliases.any { it.contains(needle, ignoreCase = true) } ||
                genre.libraryGenreNames.any { it.contains(needle, ignoreCase = true) }
        }
        .sortedWith(
            compareBy<SmartPlaylistGenreOption> { genre ->
                !genre.canonicalName.startsWith(needle, ignoreCase = true)
            }.thenBy { genre ->
                !genre.aliases.any { it.startsWith(needle, ignoreCase = true) }
            }.thenBy { genre ->
                !genre.inLibrary
            }.thenBy { it.canonicalName.length }
                .thenBy { it.canonicalName.lowercase() },
        )
        .take(limit)
        .toList()
}

/** Compatibility helper for callers that only have ontology names. */
fun smartPlaylistGenreSuggestions(
    query: String,
    ontologyGenreNames: Collection<String>,
    limit: Int = 20,
): List<String> = smartPlaylistGenreSuggestions(
    query = query,
    genres = ontologyGenreNames.map(::SmartPlaylistGenreOption),
    limit = limit,
).map(SmartPlaylistGenreOption::canonicalName)
