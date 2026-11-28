@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package app.naviamp.domain.connect

import kotlin.io.encoding.Base64

/**
 * The single-use offer a QR code carries (#715). The [secret] replaces the six digits as the J-PAKE
 * password for one attempt, and [identityFingerprint] must match the device's signed identity.
 */
class NaviampConnectLinkOffer(
    val displayName: String,
    val hosts: List<String>,
    val port: Int,
    val identityFingerprint: String,
    /** The offer's pairing session ID; a QR from an earlier offer is recognised before any attempt. */
    val offerId: String,
    val secret: String,
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= MaxNameCharacters) { "A link needs a device name." }
        require(hosts.size <= MaxHosts && hosts.all(::validLinkHost)) { "Link hosts must be addresses or host names." }
        require(port in 1..65_535) { "A link port must be valid." }
        require(identityFingerprint.length == FingerprintCharacters && identityFingerprint.all(::isLowerHex)) {
            "A link needs the device's identity fingerprint."
        }
        require(offerId.isNotBlank() && offerId.length <= MaxOfferIdCharacters && offerId.all(::isOfferIdCharacter)) {
            "A link needs its offer ID."
        }
        require(decodedSecretSize(secret)?.let { it >= MinSecretBytes } == true) { "A link secret must carry at least 128 bits." }
    }

    /** `naviamp://connect?v=1#…`; every value lives in the fragment, which is never sent to a server. */
    fun toUri(): String = buildString {
        append(NaviampConnectLinkPrefix)
        append(NaviampConnectLinkVersion)
        append('#')
        append(listOf(
            "n" to displayName,
            "h" to hosts.joinToString(","),
            "p" to port.toString(),
            "f" to identityFingerprint,
            "o" to offerId,
            "s" to secret,
        ).joinToString("&") { (key, value) -> "$key=${percentEncode(value)}" })
    }

    override fun toString(): String =
        "NaviampConnectLinkOffer(displayName=$displayName, hosts=$hosts, port=$port, offerId=$offerId, secret=<redacted>)"

    companion object {
        const val MinSecretBytes = 16
        internal const val MaxNameCharacters = 64
        internal const val MaxHosts = 6
        internal const val FingerprintCharacters = 64
        internal const val MaxOfferIdCharacters = 120
    }
}

sealed interface NaviampConnectLinkParse {
    data class Offer(val offer: NaviampConnectLinkOffer) : NaviampConnectLinkParse
    /** A Naviamp link from a newer release. */
    data object UnsupportedVersion : NaviampConnectLinkParse
    /** Not a Naviamp Connect link, or one with a missing or altered field. */
    data object Invalid : NaviampConnectLinkParse
}

const val NaviampConnectLinkVersion = 1
private const val NaviampConnectLinkPrefix = "naviamp://connect?v="

/** Base64url (no padding) form of the random bytes used as a link secret. */
fun naviampConnectLinkSecret(randomBytes: ByteArray): String {
    require(randomBytes.size >= NaviampConnectLinkOffer.MinSecretBytes) { "A link secret needs at least 128 bits." }
    return UrlSafeNoPadding.encode(randomBytes)
}

fun parseNaviampConnectLink(text: String): NaviampConnectLinkParse {
    val value = text.trim()
    if (value.length > MaxLinkCharacters || !value.startsWith("naviamp://connect", ignoreCase = true)) {
        return NaviampConnectLinkParse.Invalid
    }
    val fragmentStart = value.indexOf('#')
    if (fragmentStart < 0) return NaviampConnectLinkParse.Invalid
    val query = value.substring(0, fragmentStart).substringAfter('?', "")
    val version = query.split('&').firstNotNullOfOrNull { part ->
        part.takeIf { it.startsWith("v=") }?.removePrefix("v=")?.toIntOrNull()
    } ?: return NaviampConnectLinkParse.Invalid
    if (version != NaviampConnectLinkVersion) {
        return if (version > NaviampConnectLinkVersion) NaviampConnectLinkParse.UnsupportedVersion
        else NaviampConnectLinkParse.Invalid
    }
    val fields = mutableMapOf<String, String>()
    for (part in value.substring(fragmentStart + 1).split('&')) {
        val separator = part.indexOf('=')
        if (separator <= 0) return NaviampConnectLinkParse.Invalid
        val key = part.substring(0, separator)
        // A repeated field is ambiguous, so the whole link is refused rather than picking one.
        if (key in fields) return NaviampConnectLinkParse.Invalid
        fields[key] = percentDecode(part.substring(separator + 1)) ?: return NaviampConnectLinkParse.Invalid
    }
    return try {
        NaviampConnectLinkParse.Offer(NaviampConnectLinkOffer(
            displayName = fields.getValue("n"),
            hosts = fields.getValue("h").split(',').filter(String::isNotEmpty),
            port = fields.getValue("p").takeIf { it.length <= 5 && it.all(Char::isDigit) }?.toInt() ?: 0,
            identityFingerprint = fields.getValue("f"),
            offerId = fields.getValue("o"),
            secret = fields.getValue("s"),
        ))
    } catch (_: NoSuchElementException) {
        NaviampConnectLinkParse.Invalid
    } catch (_: IllegalArgumentException) {
        NaviampConnectLinkParse.Invalid
    }
}

private const val MaxLinkCharacters = 2_048

private val UrlSafeNoPadding = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

private fun decodedSecretSize(secret: String): Int? =
    if (secret.length > 128) null else try { UrlSafeNoPadding.decode(secret).also { it.fill(0) }.size } catch (_: IllegalArgumentException) { null }

private fun validLinkHost(host: String): Boolean =
    parseNaviampConnectManualEndpoint(if (':' in host) "[$host]:1" else "$host:1")?.host == host.lowercase()

private fun isLowerHex(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f'

private fun isOfferIdCharacter(char: Char): Boolean =
    char.isLetterOrDigit() && char.code < 128 || char == '-' || char == '_' || char == '.'

private fun isUnreserved(char: Char): Boolean =
    char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~:,"

private fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val char = (byte.toInt() and 0xff).toChar()
        if (char.code < 128 && isUnreserved(char)) append(char)
        else append('%').append(HexDigits[(byte.toInt() shr 4) and 0xf]).append(HexDigits[byte.toInt() and 0xf])
    }
}

private fun percentDecode(value: String): String? {
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '%') {
            if (index + 2 >= value.length) return null
            val high = value[index + 1].digitToIntOrNull(16) ?: return null
            val low = value[index + 2].digitToIntOrNull(16) ?: return null
            bytes += ((high shl 4) or low).toByte()
            index += 3
        } else {
            if (char.code >= 128 || char == '+') return null
            bytes += char.code.toByte()
            index += 1
        }
    }
    return try {
        bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        null
    }
}

private const val HexDigits = "0123456789ABCDEF"
