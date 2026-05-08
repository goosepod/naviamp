package app.naviamp.provider.navidrome

import java.security.MessageDigest
import kotlin.random.Random

data class NavidromeConnection(
    val baseUrl: String,
    val username: String,
    val token: String,
    val salt: String,
) {
    val normalizedBaseUrl: String =
        baseUrl.trim().trimEnd('/')

    companion object {
        fun fromPassword(
            baseUrl: String,
            username: String,
            password: String,
            salt: String = randomSalt(),
        ): NavidromeConnection =
            NavidromeConnection(
                baseUrl = baseUrl,
                username = username,
                token = md5(password + salt),
                salt = salt,
            )

        private fun randomSalt(): String =
            Random.Default.nextBytes(16).joinToString(separator = "") { byte ->
                byte.toUByte().toString(radix = 16).padStart(2, '0')
            }

        private fun md5(value: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
            return digest.joinToString(separator = "") { byte ->
                byte.toUByte().toString(radix = 16).padStart(2, '0')
            }
        }
    }
}
