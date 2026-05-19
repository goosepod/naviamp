package app.naviamp.domain.popular

import java.net.URLEncoder

actual fun String.urlEncoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8)
