package app.naviamp.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** Renders the small HTML subset returned by provider artist and album information endpoints. */
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
                    val closingBracket = this@toProviderRichText.indexOf('>', cursor + 1)
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
                    val semicolon = this@toProviderRichText.indexOf(';', cursor + 1)
                    val entity = if (semicolon in (cursor + 2)..(cursor + 12)) {
                        this@toProviderRichText.substring(cursor + 1, semicolon)
                    } else {
                        null
                    }
                    val decoded = entity?.decodeHtmlEntity()
                    if (decoded != null) {
                        appendStyled(decoded)
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

private fun String.decodeHtmlEntity(): String? = when (lowercase()) {
    "amp" -> "&"
    "lt" -> "<"
    "gt" -> ">"
    "quot" -> "\""
    "apos", "#39" -> "'"
    "nbsp" -> " "
    else -> decodeNumericHtmlEntity()
}

private fun String.decodeNumericHtmlEntity(): String? {
    val codePoint = when {
        startsWith("#x", ignoreCase = true) -> drop(2).toIntOrNull(16)
        startsWith('#') -> drop(1).toIntOrNull()
        else -> null
    }?.takeIf { it in 0..0x10ffff && it !in 0xd800..0xdfff } ?: return null
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
