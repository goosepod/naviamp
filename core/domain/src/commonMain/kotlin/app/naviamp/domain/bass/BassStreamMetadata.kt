package app.naviamp.domain.bass

/** Converts native BASS tag rows into the provider-neutral stream metadata consumed by Core. */
fun bassStreamProperties(tags: Iterable<String>): Map<String, String> =
    buildMap {
        tags.forEach { tag ->
            val equalsIndex = tag.indexOf('=').takeIf { it > 0 }
            val colonIndex = tag.indexOf(':').takeIf { it > 0 }
            val separator = listOfNotNull(equalsIndex, colonIndex).minOrNull() ?: return@forEach
            val key = tag.take(separator).trim().trim('\'', '"')
            val value = tag.drop(separator + 1).trim().trim('\'', '"').icyStreamTitleValue()
            if (key.isNotBlank() && value.isNotBlank()) put(key, value)
        }
    }

private fun String.icyStreamTitleValue(): String {
    val marker = "StreamTitle='"
    val start = indexOf(marker)
    if (start < 0) return this
    val titleStart = start + marker.length
    val titleEnd = indexOf("';", titleStart).takeIf { it >= 0 } ?: indexOf("'", titleStart)
    return if (titleEnd > titleStart) substring(titleStart, titleEnd).trim() else this
}
