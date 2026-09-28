package app.naviamp.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.mohamedrejeb.ksoup.entities.KsoupEntities

/**
 * The single rendering boundary for raw provider descriptions, including previously cached text.
 * Decode text tokens without parsing decoded characters as markup. The caller remembers the result.
 */
internal fun String.toProviderRichText(): AnnotatedString {
    var boldDepth = 0
    var italicDepth = 0
    var cursor = 0

    return buildAnnotatedString {
        fun appendStyled(value: String) {
            if (value.isEmpty()) return
            val style = SpanStyle(
                fontWeight = FontWeight.Bold.takeIf { boldDepth > 0 },
                fontStyle = FontStyle.Italic.takeIf { italicDepth > 0 },
            )
            if (boldDepth > 0 || italicDepth > 0) {
                withStyle(style) { append(value) }
            } else {
                append(value)
            }
        }

        fun appendParagraphBreak() {
            if (length > 0) append("\n\n")
        }

        while (cursor < this@toProviderRichText.length) {
            when (this@toProviderRichText[cursor]) {
                '<' -> {
                    if (this@toProviderRichText.startsWith("<!--", cursor)) {
                        val end = this@toProviderRichText.indexOf("-->", cursor + 4)
                        cursor = if (end < 0) this@toProviderRichText.length else end + 3
                        continue
                    }
                    val closingBracket = this@toProviderRichText.providerTagEnd(cursor)
                    if (closingBracket < 0) {
                        appendStyled("<")
                        cursor++
                        continue
                    }
                    val contents = this@toProviderRichText
                        .substring(cursor + 1, closingBracket)
                        .trim()
                    val closing = contents.startsWith('/')
                    val tagName = contents
                        .removePrefix("/")
                        .trimStart()
                        .takeWhile { it.isLetterOrDigit() }
                        .lowercase()
                    val nameAndAttributes = contents.removePrefix("/").trimStart()
                    if (tagName.firstOrNull()?.isLetter() != true ||
                        nameAndAttributes.getOrNull(tagName.length)?.let { !it.isWhitespace() && it != '/' } == true
                    ) {
                        appendStyled("<")
                        cursor++
                        continue
                    }
                    if (!closing && tagName in setOf("script", "style")) {
                        val end = this@toProviderRichText.indexOf("</$tagName", closingBracket + 1, ignoreCase = true)
                        val endBracket = if (end < 0) -1 else this@toProviderRichText.providerTagEnd(end)
                        cursor = if (endBracket < 0) this@toProviderRichText.length else endBracket + 1
                        continue
                    }
                    when (tagName) {
                        "b", "strong" -> if (closing) {
                            boldDepth = (boldDepth - 1).coerceAtLeast(0)
                        } else {
                            boldDepth++
                        }
                        "i", "em" -> if (closing) {
                            italicDepth = (italicDepth - 1).coerceAtLeast(0)
                        } else {
                            italicDepth++
                        }
                        "br" -> append("\n")
                        "p" -> if (!closing) appendParagraphBreak()
                    }
                    cursor = closingBracket + 1
                }
                '&' -> {
                    var semicolon = this@toProviderRichText.providerEntityEnd(cursor + 1)
                    val entity = if (semicolon >= 0) {
                        this@toProviderRichText.substring(cursor + 1, semicolon)
                    } else {
                        null
                    }
                    var decoded = entity?.decodeHtmlEntity()
                    // Some descriptions carry an escaped numeric reference, e.g. &amp;#039;.
                    // Unwrap that one numeric token only, never recursively decode text or markup.
                    if (decoded == "&" && this@toProviderRichText.getOrNull(semicolon + 1) == '#') {
                        val numericEnd = this@toProviderRichText.providerEntityEnd(semicolon + 1)
                        if (numericEnd >= 0) {
                            this@toProviderRichText.substring(semicolon + 1, numericEnd)
                                .decodeNumericHtmlEntity()?.let {
                                    decoded = it
                                    semicolon = numericEnd
                                }
                        }
                    }
                    if (decoded != null) {
                        appendStyled(requireNotNull(decoded))
                        cursor = semicolon + 1
                    } else {
                        appendStyled("&")
                        cursor++
                    }
                }
                else -> {
                    val nextMarkup = sequenceOf(
                        this@toProviderRichText.indexOf('<', cursor).takeIf { it >= 0 },
                        this@toProviderRichText.indexOf('&', cursor).takeIf { it >= 0 },
                    ).filterNotNull().minOrNull() ?: this@toProviderRichText.length
                    appendStyled(this@toProviderRichText.substring(cursor, nextMarkup))
                    cursor = nextMarkup
                }
            }
        }
    }
}

/** Normalizes provider line endings while preserving authored line and paragraph breaks. */
internal fun String.normalizedProviderDescription(): String =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()

private fun String.decodeHtmlEntity(): String? {
    if (startsWith('#')) return decodeNumericHtmlEntity()
    if (this == "nbsp") return " " // Preserve the existing description wrapping behavior.
    val reference = "&$this;"
    val decoded = KsoupEntities.decodeHtml(reference)
    // A legacy semicolon-less prefix match leaves the rest of the token, including its
    // semicolon, in the result. Only the complete &semi; entity decodes to a semicolon.
    return decoded.takeIf { it != reference && (!it.endsWith(';') || this == "semi") }
}

private fun String.providerEntityEnd(start: Int): Int {
    for (index in start until minOf(length, start + 33)) {
        val character = this[index]
        if (character == ';') return index.takeIf { it > start } ?: -1
        if (!character.isLetterOrDigit() && character != '#') return -1
    }
    return -1
}

/** A > inside a quoted attribute does not end the tag. */
private fun String.providerTagEnd(start: Int): Int {
    var quote: Char? = null
    for (index in start + 1 until length) {
        val character = this[index]
        if (quote != null) {
            if (character == quote) quote = null
        } else when (character) {
            '\'', '"' -> quote = character
            '>' -> return index
            '<' -> return -1
        }
    }
    return -1
}

private fun String.decodeNumericHtmlEntity(): String? {
    val hex = startsWith("#x", ignoreCase = true)
    if (!startsWith('#')) return null
    val digits = drop(if (hex) 2 else 1)
    if (digits.isEmpty() || digits.any { it.digitToIntOrNull(if (hex) 16 else 10) == null }) return null
    val codePoint = digits.toIntOrNull(if (hex) 16 else 10)
        ?.takeIf { it in 1..0x10ffff && it !in 0xd800..0xdfff } ?: return null
    return if (codePoint <= 0xffff) {
        codePoint.toChar().toString()
    } else {
        val adjusted = codePoint - 0x10000
        charArrayOf(
            (0xd800 + (adjusted shr 10)).toChar(),
            (0xdc00 + (adjusted and 0x3ff)).toChar(),
        ).concatToString()
    }
}
