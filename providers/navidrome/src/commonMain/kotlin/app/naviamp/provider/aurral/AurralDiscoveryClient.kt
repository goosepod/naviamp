package app.naviamp.provider.aurral

import app.naviamp.domain.Artist
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
    private val resolveLibraryArtist: suspend (identityKey: String, mbid: String) -> Artist? = { _, _ -> null },
) : ProviderDiscovery {
    private val mutex = Mutex()
    private var session: Session? = null
    private data class Session(val token: String, val expiresAt: Long)
    private val base = connection.normalizedBaseUrl
    private val headers = connection.customHeaders.mapNotNull { it.normalized() }
        .mapNotNull { header -> header.value?.let { header.name to it } }.toMap()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): ProviderDiscoveryFeed = nativeCall(::ProviderDiscoveryFeed) {
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
    }

    override suspend fun artistDetails(artist: ProviderDiscoveryArtist): ProviderDiscoveryArtistResult =
        nativeCall({ ProviderDiscoveryArtistResult(it) }) {
            require(MusicBrainzId.matches(artist.id))
            val metadata = get("/api/artists/${artist.id}?mode=core")
            check(metadata.text("id").equals(artist.id, ignoreCase = true))
            val feedback = optional { get("/api/discover/feedback").array("feedback") }
            val owned = optional { lookupLibraryArtist(artist.id) }
            ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Ready, ProviderDiscoveryArtistDetails(
                artist = artist.copy(name = metadata.text("name") ?: artist.name,
                    genres = metadata.array("genres")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                        .ifEmpty { artist.genres }),
                biography = metadata.text("bio"),
                releases = metadata.array("release-groups").orEmpty().mapNotNull { entry ->
                    val release = entry as? JsonObject ?: return@mapNotNull null
                    val id = release.text("id") ?: return@mapNotNull null
                    val title = release.text("title") ?: return@mapNotNull null
                    ProviderDiscoveryRelease(id, title, release.text("first-release-date"), release.text("primary-type"))
                }.distinctBy { it.id },
                libraryArtist = owned,
                feedbackAvailable = feedback != null,
                feedback = feedback.orEmpty().selectedFeedback(artist.id, artist.name),
            ))
        }

    /** Mirrors the web client's toggle semantics; refresh on a partial failure rather than claiming success. */
    override suspend fun toggleFeedback(artist: ProviderDiscoveryArtist, action: ProviderDiscoveryFeedbackAction): ProviderDiscoveryFeedbackResult =
        nativeCall({ ProviderDiscoveryFeedbackResult(it) }) {
            require(MusicBrainzId.matches(artist.id))
            val existing = get("/api/discover/feedback").array("feedback") ?: error("Missing feedback")
            val selected = existing.selectedFeedback(artist.id, artist.name)
            val removing = if (action in selected) action else when (action) {
                ProviderDiscoveryFeedbackAction.MoreLikeThis -> ProviderDiscoveryFeedbackAction.LessLikeThis
                ProviderDiscoveryFeedbackAction.LessLikeThis -> ProviderDiscoveryFeedbackAction.MoreLikeThis
                ProviderDiscoveryFeedbackAction.BlockArtist -> null
            }
            existing.mapNotNull { it as? JsonObject }.filter {
                it.matchesFeedbackArtist(artist.id, artist.name) && it.text("action") == removing?.apiValue()
            }.forEach { entry ->
                val id = entry.text("id") ?: error("Missing feedback identity")
                val response = http.deleteJson("$base/api/discover/feedback/${id.urlEncode()}", authorizedHeaders()).parseObject()
                check(response["success"]?.jsonPrimitive?.booleanOrNull == true)
            }
            if (action !in selected) {
                val response = http.postJson("$base/api/discover/feedback", buildJsonObject {
                    put("artistId", artist.id)
                    put("artistName", artist.name)
                    put("action", action.apiValue())
                    put("sourceContext", "Naviamp")
                }.toString(), authorizedHeaders()).parseObject()
                check(response["success"]?.jsonPrimitive?.booleanOrNull == true)
            }
            val current = get("/api/discover/feedback").array("feedback") ?: error("Missing feedback")
            ProviderDiscoveryFeedbackResult(ProviderDiscoveryStatus.Ready, current.selectedFeedback(artist.id, artist.name))
        }

    /** Never turn a native numeric ID or a display-name match into a playable identity. */
    private suspend fun lookupLibraryArtist(mbid: String): Artist? {
        val lookup = get("/api/library/lookup/$mbid")
        val canonicalId = lookup.text("libraryArtistId")?.takeIf { it.toLongOrNull() != null } ?: return null
        val page = get("/api/library/canonical?kind=artists&pageSize=1&availableOnly=true&artistId=${canonicalId.urlEncode()}")
        val artist = page.array("items")?.singleOrNull() as? JsonObject ?: return null
        if (!artist.text("mbid").equals(mbid, ignoreCase = true) || artist.text("id") != canonicalId) return null
        val identityKey = artist.text("identityKey")?.takeIf { it.isNotBlank() } ?: return null
        // The Subsonic adapter must validate this candidate against its actual artist response too.
        return resolveLibraryArtist(identityKey, mbid)
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        null
    }

    private suspend fun <T> nativeCall(fallback: (ProviderDiscoveryStatus) -> T, block: suspend () -> T): T = mutex.withLock {
        try {
            val bootstrap = get("/api/health/bootstrap", authenticated = false)
            val api = bootstrap["api"] as? JsonObject
            if (api?.get("version")?.jsonPrimitive?.intOrNull != 1 ||
                api.array("features")?.any { (it as? JsonPrimitive)?.contentOrNull == "discover" } != true
            ) return@withLock fallback(ProviderDiscoveryStatus.Unsupported)
            if (connection.password.isNullOrBlank()) return@withLock fallback(ProviderDiscoveryStatus.PasswordRequired)
            if (session?.expiresAt?.let { it > nowMillis() + 30_000 } != true) signIn()
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            fallback(when ((error as? NavidromeHttpException)?.statusCode) {
                401 -> { session = null; ProviderDiscoveryStatus.SignInRequired }
                403 -> ProviderDiscoveryStatus.Forbidden
                404 -> ProviderDiscoveryStatus.Unsupported
                else -> ProviderDiscoveryStatus.Unavailable
            })
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

    private fun authorizedHeaders(): Map<String, String> =
        headers.filterKeys { !it.equals("Authorization", ignoreCase = true) } +
            ("Authorization" to "Bearer ${checkNotNull(session).token}")

    private suspend fun get(path: String, authenticated: Boolean = true): JsonObject =
        http.get("$base$path", headers = if (authenticated) authorizedHeaders() else headers).parseObject()

    private fun String.parseObject(): JsonObject = json.parseToJsonElement(this).jsonObject

    private fun JsonObject.artists(field: String): List<ProviderDiscoveryArtist> =
        array(field).orEmpty().mapIndexedNotNull { index, entry ->
            val artist = entry as? JsonObject ?: return@mapIndexedNotNull null
            val name = artist.text("name")?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val id = artist.text("id")?.takeIf { it.isNotBlank() }
            val validId = id != null && MusicBrainzId.matches(id)
            ProviderDiscoveryArtist(
                id = id ?: "unresolved:$field:$index", name = name,
                imageUrl = (artist.text("image") ?: artist.text("imageUrl"))?.let(::imageUrl)
                    ?: if (validId) "$base/api/artists/$id/cover?artistName=${name.urlEncode()}" else null,
                genres = artist.array("tags").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                matchPercent = (artist["matchPercent"] as? JsonPrimitive)?.intOrNull?.coerceIn(0, 100),
                externalUrl = if (validId) "$base/artist/$id" else null,
                detailsAvailable = validId,
            )
        }.distinctBy { it.id }.take(20)

    /** A cover endpoint returns JSON; resolve it lazily through the provider artwork boundary. */
    fun ownsArtistCoverUrl(url: String): Boolean {
        val path = url.removePrefix("$base/api/artists/")
        return url.startsWith("$base/api/artists/") &&
            MusicBrainzId.matches(path.substringBefore('/')) &&
            path.substringAfter('/', "").startsWith("cover?artistName=")
    }

    suspend fun artistCoverBytes(url: String): ByteArray? {
        require(ownsArtistCoverUrl(url))
        val resolved = nativeCall<String?>({ null }) {
            val images = get(url.removePrefix(base)).array("images").orEmpty()
                .mapNotNull { it as? JsonObject }
            val preferred = images.firstOrNull { it["front"]?.jsonPrimitive?.booleanOrNull == true }
            (listOfNotNull(preferred) + images).firstNotNullOfOrNull { it.text("image")?.let(::imageUrl) }
        } ?: return null
        // Never send a user session or custom proxy credentials to an external artwork service.
        return http.getBytes(resolved, headers = if (resolved.startsWith("$base/"))
            authorizedHeaders() else emptyMap())
    }

    private fun imageUrl(value: String): String? = when {
        value.startsWith("https://") || value.startsWith("http://") -> value
        value.startsWith('/') && !value.startsWith("//") -> "$base$value"
        else -> null
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject?.array(key: String): JsonArray? = this?.get(key) as? JsonArray
    private fun JsonObject.matchesFeedbackArtist(artistId: String, artistName: String): Boolean =
        text("artistId")?.takeIf { it.isNotBlank() }?.equals(artistId, ignoreCase = true)
            ?: text("artistName").equals(artistName, ignoreCase = true)

    private fun List<JsonElement>.selectedFeedback(artistId: String, artistName: String): Set<ProviderDiscoveryFeedbackAction> =
        mapNotNull { it as? JsonObject }.filter { it.matchesFeedbackArtist(artistId, artistName) }
            .mapNotNull { entry -> ProviderDiscoveryFeedbackAction.entries.firstOrNull { it.apiValue() == entry.text("action") } }.toSet()

    private fun ProviderDiscoveryFeedbackAction.apiValue(): String = when (this) {
        ProviderDiscoveryFeedbackAction.MoreLikeThis -> "more_like_this"
        ProviderDiscoveryFeedbackAction.LessLikeThis -> "less_like_this"
        ProviderDiscoveryFeedbackAction.BlockArtist -> "block_artist"
    }

    private companion object {
        val MusicBrainzId = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
    }
}
