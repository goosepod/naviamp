package app.naviamp.domain.bass

/** Converts native BASS tag rows into the provider-neutral stream metadata consumed by Core. */
fun bassStreamProperties(tags: Iterable<String>): Map<String, String> =
    buildMap {
        tags.forEach { tag ->
            val equalsIndex = tag.indexOf('=').takeIf { it > 0 }
            val colonIndex = tag.indexOf(':').takeIf { it > 0 }
            val separator = listOfNotNull(equalsIndex, colonIndex).minOrNull() ?: return@forEach
            val key = tag.take(separator).trim().trim('\'', '"')
            val rawValue = tag.drop(separator + 1).trim()
            // JNI wraps the complete ICY block in StreamTitle=; other adapters may supply
            // the block directly or wrap it in icy-title:. Preserve every field in Core.
            val payload = rawValue.takeIf { icyFields.matchAt(it, 0) != null } ?: tag
            val fields = icyFields.findAll(payload).toList()
            if (fields.any { it.groupValues[1].equals("StreamTitle", ignoreCase = true) ||
                    it.groupValues[1].equals("StreamUrl", ignoreCase = true) }) {
                fields.forEach { field ->
                    put(field.groupValues[1], field.groupValues[3].trim())
                }
                if (key.equals("icy-title", ignoreCase = true)) {
                    fields.firstOrNull { it.groupValues[1].equals("StreamTitle", ignoreCase = true) }
                        ?.let { put(key, it.groupValues[3].trim()) }
                }
            } else {
                val value = rawValue.trim('\'', '"')
                if (key.isNotBlank() && value.isNotBlank()) put(key, value)
            }
        }
    }

// A quote ends a field only at a field boundary, so apostrophes/semicolons in titles
// and query strings do not truncate it. No platform adapter interprets ICY values.
private val icyFields = Regex("""(?:^|;)\s*([\w-]+)\s*=\s*(['"])(.*?)\2\s*(?=;|$)""")
