package app.naviamp.domain.network

actual fun String.urlEncodedParameter(): String {
    val hex = "0123456789ABCDEF"
    return buildString {
        for (byte in encodeToByteArray()) {
            val value = byte.toInt() and 0xff
            when {
                value.isFormUrlSafe() -> append(value.toChar())
                value == ' '.code -> append('+')
                else -> {
                    append('%')
                    append(hex[value shr 4])
                    append(hex[value and 0x0f])
                }
            }
        }
    }
}

private fun Int.isFormUrlSafe(): Boolean =
    this in 'A'.code..'Z'.code ||
        this in 'a'.code..'z'.code ||
        this in '0'.code..'9'.code ||
        this == '-'.code ||
        this == '_'.code ||
        this == '.'.code ||
        this == '*'.code
