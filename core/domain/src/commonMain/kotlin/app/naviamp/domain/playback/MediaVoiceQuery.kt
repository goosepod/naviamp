package app.naviamp.domain.playback

/** Portable interpretation of a voice request before any host-specific voice API executes it. */
class MediaVoiceQuery private constructor(
    val original: String,
    val normalized: String,
) {
    val isDownloadedMusic: Boolean
        get() = normalized.contains("downloaded") ||
            normalized.contains("downloads") ||
            normalized.contains("offline")

    val isLibraryRadio: Boolean
        get() = normalized.contains("library radio") ||
            normalized.contains("my library radio")

    val isPlaylist: Boolean
        get() = normalized.contains("playlist")

    val isInternetRadioStation: Boolean
        get() = normalized.contains("internet radio") || normalized.contains("station")

    val playlistTarget: String
        get() = original.voiceIntentTarget()
            .replace(Regex("\\bplaylist\\b", RegexOption.IGNORE_CASE), " ")
            .normalizedVoiceTarget()

    val stationTarget: String
        get() = original.voiceIntentTarget()
            .replace(Regex("\\binternet radio\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\bradio station\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\bstation\\b", RegexOption.IGNORE_CASE), " ")
            .normalizedVoiceTarget()

    val radioTarget: String?
        get() {
            if (!normalized.contains("radio")) return null
            return original
                .replace(Regex("\\b(play|start|listen to|listen|some|an|a|the)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\bradio\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\bon naviamp\\b", RegexOption.IGNORE_CASE), " ")
                .normalizedVoiceTarget()
                .takeIf { it.isNotBlank() }
        }

    companion object {
        fun parse(query: String): MediaVoiceQuery = MediaVoiceQuery(
            original = query.trim(),
            normalized = query.lowercase().replace(Regex("\\s+"), " ").trim(),
        )
    }
}

fun <T> Iterable<T>.bestVoiceNameMatch(
    query: String,
    name: (T) -> String,
): T? {
    val queryKey = query.voiceSearchKey()
    if (queryKey.isBlank()) return null
    return mapNotNull { item ->
        voiceNameMatchScore(queryKey, name(item).voiceSearchKey())?.let { score -> item to score }
    }
        .sortedWith(compareBy<Pair<T, Int>> { it.second }.thenBy { name(it.first).length })
        .firstOrNull()
        ?.first
}

private fun String.voiceIntentTarget(): String =
    replace(Regex("\\b(play|start|listen to|listen|some|an|a|the|my)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\bon naviamp\\b", RegexOption.IGNORE_CASE), " ")
        .normalizedVoiceTarget()

private fun String.normalizedVoiceTarget(): String = replace(Regex("\\s+"), " ").trim()

private fun voiceNameMatchScore(queryKey: String, candidateKey: String): Int? = when {
    candidateKey == queryKey -> 0
    candidateKey.startsWith(queryKey) || queryKey.startsWith(candidateKey) -> 1
    candidateKey.contains(queryKey) || queryKey.contains(candidateKey) -> 2
    else -> null
}

private fun String.voiceSearchKey(): String = lowercase()
    .replace("&", "and")
    .replace("ph", "f")
    .replace(Regex("\\b(the|a|an)\\b"), " ")
    .filter { it.isLetterOrDigit() }
