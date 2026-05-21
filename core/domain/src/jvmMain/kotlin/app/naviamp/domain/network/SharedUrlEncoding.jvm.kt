package app.naviamp.domain.network

import java.net.URLEncoder

actual fun String.urlEncodedParameter(): String =
    URLEncoder.encode(this, Charsets.UTF_8)
