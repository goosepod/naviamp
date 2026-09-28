package app.naviamp.domain.playback

enum class NamedMediaKind { ArtistRadio, Artist, Album, Playlist }

data class NamedMediaRequest(
    val kind: NamedMediaKind,
    val name: String,
    val spokenQuery: String = name,
)

enum class NamedMediaMatchStatus { Matched, Missing, Ambiguous }

data class NamedMediaMatch<T>(
    val status: NamedMediaMatchStatus,
    val item: T? = null,
)

/** Explicit media types take precedence over the native assistant's broad search hint. */
fun namedMediaRequest(query: String, hintedKind: NamedMediaKind? = null, hintedName: String? = null): NamedMediaRequest? {
    val spoken = query.trim()
    val words = spoken
        .replace(Regex("(?i)^\\s*(hey google|hey siri)[, ]*"), "")
        .replace(Regex("(?i)\\s+on naviamp\\s*$"), "")
        .replace(Regex("(?i)^\\s*(play|start|listen to)\\s+"), "")
        .replace(Regex("(?i)^\\s*(my|the|some)\\s+"), "")
        .trim()
    val explicit = when {
        Regex("(?i)\\bradio\\b").containsMatchIn(words) &&
            !Regex("(?i)\\b(station|internet radio)\\b").containsMatchIn(words) -> NamedMediaKind.ArtistRadio
        Regex("(?i)\\bplaylist\\b").containsMatchIn(words) -> NamedMediaKind.Playlist
        Regex("(?i)\\balbum\\b").containsMatchIn(words) -> NamedMediaKind.Album
        Regex("(?i)\\bartist\\b").containsMatchIn(words) -> NamedMediaKind.Artist
        else -> null
    }
    val kind = explicit ?: hintedKind ?: return null
    val name = if (explicit == null && !hintedName.isNullOrBlank()) hintedName.trim() else words
        .replace(Regex("(?i)\\b(artist|album|playlist|radio)\\b"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return name.takeIf(String::isNotBlank)?.let { NamedMediaRequest(kind, it, spoken) }
}

fun <T> namedMediaMatch(query: String, candidates: List<T>, name: (T) -> String): NamedMediaMatch<T> {
    val key = query.namedMediaKey()
    if (key.isBlank()) return NamedMediaMatch(NamedMediaMatchStatus.Missing)
    val scored = candidates.mapNotNull { item ->
        val candidate = name(item).namedMediaKey()
        val score = when {
            candidate == key -> 0
            candidate.startsWith(key) -> 1
            candidate.contains(key) -> 2
            else -> null
        }
        score?.let { item to it }
    }
    val bestScore = scored.minOfOrNull { it.second } ?: return NamedMediaMatch(NamedMediaMatchStatus.Missing)
    val best = scored.filter { it.second == bestScore }
    return if (best.size == 1) NamedMediaMatch(NamedMediaMatchStatus.Matched, best.single().first)
    else NamedMediaMatch(NamedMediaMatchStatus.Ambiguous)
}

private fun String.namedMediaKey(): String = lowercase()
    .replace("&", "and")
    .replace(Regex("^(the|a|an)\\s+"), "")
    .filter(Char::isLetterOrDigit)
