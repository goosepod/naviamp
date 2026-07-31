package app.naviamp.provider.navidrome

/** Navidrome's canonical 128-bit, zero-padded base62 identifier codec. */
object NavidromeCanonicalId {
    fun migrate(value: String): String = migrateComposite(value) ?: migrateDirect(value)

    private fun migrateComposite(value: String): String? {
        val separator = value.indexOf('_')
        if (separator <= 3) return null
        val prefix = value.substring(0, 3)
        if (prefix !in CompositePrefixes) return null
        val oldEntityId = value.substring(3, separator)
        val migratedEntityId = migrateDirect(oldEntityId)
        return if (migratedEntityId == oldEntityId) value else prefix + migratedEntityId + value.substring(separator)
    }

    private fun migrateDirect(value: String): String = when (value.length) {
        CanonicalLength -> migrateBase62(value)
        LegacyHexLength -> decodeHex(value)?.let(::encode) ?: value
        LegacyUuidLength -> decodeUuid(value)?.let(::encode) ?: value
        else -> value
    }

    private fun migrateBase62(value: String): String {
        val decoded = decodeBase62(value) ?: return value
        if (decoded.size <= CanonicalByteCount) return value
        return encode(decodeHex(requireNotNull(md5Hex(value.encodeToByteArray())))!!)
    }

    private fun decodeBase62(value: String): ByteArray? {
        var magnitude = byteArrayOf(0)
        value.forEach { character ->
            val digit = Base62Alphabet.indexOf(character)
            if (digit < 0) return null
            var carry = digit
            for (index in magnitude.indices.reversed()) {
                val next = (magnitude[index].toInt() and 0xff) * Base + carry
                magnitude[index] = next.toByte()
                carry = next ushr 8
            }
            while (carry > 0) {
                magnitude = byteArrayOf(carry.toByte()) + magnitude
                carry = carry ushr 8
            }
            magnitude = magnitude.dropLeadingZeroes()
        }
        return magnitude
    }

    private fun encode(bytes: ByteArray): String {
        require(bytes.size == CanonicalByteCount)
        var magnitude = bytes.dropLeadingZeroes()
        val encoded = StringBuilder(CanonicalLength)
        while (magnitude.any { it.toInt() != 0 }) {
            var remainder = 0
            val quotient = ByteArray(magnitude.size)
            magnitude.indices.forEach { index ->
                val dividend = (remainder shl 8) + (magnitude[index].toInt() and 0xff)
                quotient[index] = (dividend / Base).toByte()
                remainder = dividend % Base
            }
            encoded.append(Base62Alphabet[remainder])
            magnitude = quotient.dropLeadingZeroes()
        }
        while (encoded.length < CanonicalLength) encoded.append('0')
        return encoded.reverse().toString()
    }

    private fun decodeUuid(value: String): ByteArray? {
        if (value[8] != '-' || value[13] != '-' || value[18] != '-' || value[23] != '-') return null
        return decodeHex(value.filterNot { it == '-' })
    }

    private fun decodeHex(value: String): ByteArray? {
        if (value.length != LegacyHexLength) return null
        return ByteArray(CanonicalByteCount) { index ->
            val high = value[index * 2].digitToIntOrNull(16) ?: return null
            val low = value[index * 2 + 1].digitToIntOrNull(16) ?: return null
            ((high shl 4) or low).toByte()
        }
    }

    private fun ByteArray.dropLeadingZeroes(): ByteArray {
        val first = indexOfFirst { it.toInt() != 0 }
        return when {
            first < 0 -> byteArrayOf(0)
            first == 0 -> this
            else -> copyOfRange(first, size)
        }
    }

    private const val Base = 62
    private const val CanonicalByteCount = 16
    private const val CanonicalLength = 22
    private const val LegacyHexLength = 32
    private const val LegacyUuidLength = 36
    private const val Base62Alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val CompositePrefixes = setOf("mf-", "al-", "ar-")
}

internal const val NavidromeCanonicalIdentityVersion = 1L

/** Provider-common result for the bounded canonical-ID capability proof. */
enum class NavidromeCanonicalIdProbeResult {
    Confirmed,
    Unsupported,
    NoCandidates,
    Inconclusive,
}

internal data class NavidromeCanonicalIdResolution(
    val resolvedId: String? = null,
    val definitelyMissing: Boolean = false,
)

internal suspend fun probeNavidromeCanonicalIds(
    ownedIds: List<String>,
    resolveCanonicalId: suspend (String) -> NavidromeCanonicalIdResolution,
): NavidromeCanonicalIdProbeResult {
    val candidates = ownedIds.asSequence()
        .map { oldId -> oldId to NavidromeCanonicalId.migrate(oldId) }
        .filter { (oldId, canonicalId) -> oldId != canonicalId }
        .take(5)
        .toList()
    if (candidates.isEmpty()) return NavidromeCanonicalIdProbeResult.NoCandidates
    candidates.forEach { (_, canonicalId) ->
        val resolution = resolveCanonicalId(canonicalId)
        when {
            resolution.resolvedId == canonicalId -> return NavidromeCanonicalIdProbeResult.Confirmed
            !resolution.definitelyMissing -> return NavidromeCanonicalIdProbeResult.Inconclusive
        }
    }
    return NavidromeCanonicalIdProbeResult.Unsupported
}
