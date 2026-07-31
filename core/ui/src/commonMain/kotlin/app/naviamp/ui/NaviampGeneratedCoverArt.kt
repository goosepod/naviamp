package app.naviamp.ui

internal data class NaviampRadioTileSpec(
    val label: String,
    val fromRgb: Int,
    val toRgb: Int,
    val sidePx: Int = 512,
    val cornerRadiusPx: Float = 48f,
    val ringRadiusPx: Float = 118f,
    val ringStrokePx: Float = 22f,
    val centerRadiusPx: Float = 56f,
    val ringAlpha: Int = 54,
    val centerAlpha: Int = 42,
    val shortLabelTextSizePx: Float = 126f,
    val labelTextSizePx: Float = 104f,
) {
    val centerPx: Float get() = sidePx / 2f
    val textSizePx: Float get() = if (label.length <= 2) shortLabelTextSizePx else labelTextSizePx
}

internal fun naviampRadioTileSpec(url: String): NaviampRadioTileSpec? {
    if (!url.startsWith(NaviampRadioTileScheme)) return null
    val params = url.substringAfter("?", "")
        .split("&")
        .mapNotNull { part ->
            val key = part.substringBefore("=", "")
            val value = part.substringAfter("=", "")
            if (key.isBlank()) null else key to value
        }
        .toMap()
    return NaviampRadioTileSpec(
        label = params["label"]?.naviampUrlDecode()?.takeIf(String::isNotBlank) ?: "RAD",
        fromRgb = params["from"].naviampRgbOr(DefaultRadioTileFromRgb),
        toRgb = params["to"].naviampRgbOr(DefaultRadioTileToRgb),
    )
}

private fun String?.naviampRgbOr(fallback: Int): Int =
    this?.takeIf { it.length == 6 }
        ?.toIntOrNull(16)
        ?.takeIf { it in 0x000000..0xFFFFFF }
        ?: fallback

private fun String.naviampUrlDecode(): String =
    replace("+", " ").replace(Regex("%([0-9A-Fa-f]{2})")) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }

private const val NaviampRadioTileScheme = "naviamp-radio-tile://"
private const val DefaultRadioTileFromRgb = 0x465d7a
private const val DefaultRadioTileToRgb = 0x161f2c
