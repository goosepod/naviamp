package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Aurral's native API complements the shared Subsonic engine; tokens never enter saved settings. */
internal class AurralDiscoveryClient(
    private val connection: NavidromeConnection,
    private val http: NavidromeHttpClient,
    private val nowMillis: () -> Long = ::navidromeCurrentTimeMillis,
) : ProviderDiscovery {
    private val mutex = Mutex()
    private var session: Session? = null
    private data class Session(val token: String, val expiresAt: Long)
    private val base = connection.normalizedBaseUrl
    private val headers = connection.customHeaders.mapNotNull { it.normalized() }
        .mapNotNull { header -> header.value?.let { header.name to it } }.toMap()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): ProviderDiscoveryFeed = mutex.withLock {
        try {
            val bootstrap = get("/api/health/bootstrap", authenticated = false)
            val api = bootstrap["api"] as? JsonObject
            if (api?.get("version")?.jsonPrimitive?.intOrNull != 1 ||
                (api["features"] as? JsonArray)?.any { it.jsonPrimitive.contentOrNull == "discover" } != true
            ) return@withLock ProviderDiscoveryFeed(ProviderDiscoveryStatus.Unsupported)

            if (connection.password.isNullOrBlank()) {
                return@withLock ProviderDiscoveryFeed(ProviderDiscoveryStatus.PasswordRequired)
            }
            if (session?.expiresAt?.let { it > nowMillis() + 30_000 } != true) signIn()
            val feed = get("/api/discover?limit=20&offset=0")
            val status = get("/api/discover/status")
            ProviderDiscoveryFeed(
                status = when {
                    status.text("error") != null -> ProviderDiscoveryStatus.Unavailable
                    status["isUpdating"]?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryStatus.Building
                    else -> ProviderDiscoveryStatus.Ready
                },
                recommendations = feed.artists("recommendations"),
                trending = feed.artists("globalTop"),
                lastUpdated = status.text("lastUpdated") ?: feed.text("lastUpdated"),
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val status = when ((error as? NavidromeHttpException)?.statusCode) {
                401 -> { session = null; ProviderDiscoveryStatus.SignInRequired }
                403 -> ProviderDiscoveryStatus.Forbidden
                404 -> ProviderDiscoveryStatus.Unsupported
                else -> ProviderDiscoveryStatus.Unavailable
            }
            ProviderDiscoveryFeed(status)
        }
    }

    private suspend fun signIn() {
        session = null
        val response = http.postJson("$base/api/auth/login", buildJsonObject {
            put("username", connection.username)
            put("password", connection.password)
        }.toString(), headers = headers).parseObject()
        val token = response.text("token")?.takeIf { it.isNotBlank() } ?: error("Missing session token")
        val expiresAt = response["expiresAt"]?.jsonPrimitive?.longOrNull ?: error("Missing session expiry")
        session = Session(token, expiresAt)
    }

    private suspend fun get(path: String, authenticated: Boolean = true): JsonObject =
        http.get("$base$path", headers = if (authenticated) {
            headers.filterKeys { !it.equals("Authorization", ignoreCase = true) } +
                ("Authorization" to "Bearer ${checkNotNull(session).token}")
        } else headers).parseObject()

    private fun String.parseObject(): JsonObject = json.parseToJsonElement(this).jsonObject

    private fun JsonObject.artists(field: String): List<ProviderDiscoveryArtist> =
        (this[field] as? JsonArray).orEmpty().mapIndexedNotNull { index, entry ->
            val artist = entry as? JsonObject ?: return@mapIndexedNotNull null
            val name = artist.text("name")?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val id = artist.text("id")?.takeIf { it.isNotBlank() }
            ProviderDiscoveryArtist(
                id = id ?: "unresolved:$field:$index",
                name = name,
                imageUrl = artist.text("image")?.let(::imageUrl),
                genres = (artist["tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                matchPercent = (artist["matchPercent"] as? JsonPrimitive)?.intOrNull?.coerceIn(0, 100),
                externalUrl = if (id != null && MusicBrainzId.matches(id)) "$base/artist/$id" else null,
            )
        }.distinctBy { it.id }.take(20)

    private fun imageUrl(value: String): String? = when {
        value.startsWith("https://") || value.startsWith("http://") -> value
        value.startsWith('/') && !value.startsWith("//") -> "$base$value"
        else -> null
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        val MusicBrainzId = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
    }
}
