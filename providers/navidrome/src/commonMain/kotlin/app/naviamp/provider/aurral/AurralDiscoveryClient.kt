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
    private data class Session(val token: String, val expiresAt: Long, val permissions: JsonObject?)
    private var features = emptySet<String>()
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
            val monitoring = optional { get("/api/library/artists/${artist.id}/monitoring") }
            val permissions = session?.permissions
            ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Ready, ProviderDiscoveryArtistDetails(
                artist = artist.copy(name = metadata.text("name") ?: artist.name,
                    genres = metadata.array("genres")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                        .ifEmpty { artist.genres }),
                biography = metadata.text("bio"),
                releases = metadata.array("release-groups").orEmpty().mapNotNull { entry ->
                    val release = entry as? JsonObject ?: return@mapNotNull null
                    val id = release.text("id") ?: return@mapNotNull null
                    val title = release.text("title") ?: return@mapNotNull null
                    ProviderDiscoveryRelease(id, title, release.text("first-release-date"), release.text("primary-type"),
                        secondaryTypes = release.array("secondary-types").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                        imageUrl = release.text("coverUrl")?.let(::imageUrl) ?: if (MusicBrainzId.matches(id))
                            "$base/api/artists/release-group/$id/cover?artistName=${artist.name.urlEncode()}&albumTitle=${title.urlEncode()}" else null)
                }.distinctBy { it.id },
                libraryArtist = owned,
                canRequestTracks = permissions?.get("addAlbum")?.jsonPrimitive?.booleanOrNull == true && "trackDownloads" in features,
                canRequestAlbums = permissions?.get("addAlbum")?.jsonPrimitive?.booleanOrNull == true,
                canChangeMonitoring = monitoring != null && monitoring.text("error") == null &&
                    permissions?.get("changeMonitoring")?.jsonPrimitive?.booleanOrNull == true &&
                    (monitoring["added"]?.jsonPrimitive?.booleanOrNull == true || permissions["addArtist"]?.jsonPrimitive?.booleanOrNull == true),
                monitored = monitoring?.takeIf { it.text("error") == null }?.let {
                    it["monitored"]?.jsonPrimitive?.booleanOrNull ?: (it.text("monitorOption")?.let { mode -> mode != "none" })
                },
                feedbackAvailable = feedback != null,
                feedback = feedback.orEmpty().selectedFeedback(artist.id, artist.name),
            ))
        }

    override suspend fun samples(artist: ProviderDiscoveryArtist): ProviderDiscoverySamples = nativeCall({ ProviderDiscoverySamples(it) }) {
        require(MusicBrainzId.matches(artist.id))
        val tracks = get("/api/artists/${artist.id}/preview?artistName=${artist.name.urlEncode()}").array("tracks").orEmpty()
            .mapNotNull { it as? JsonObject }.mapNotNull { track ->
                val id = track.text("id")?.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true } ?: return@mapNotNull null
                if (track.text("preview_url").isNullOrBlank()) return@mapNotNull null
                val title = track.text("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val album = track["album"] as? JsonObject
                ProviderDiscoverySample(id, title, album?.text("title") ?: (track["album"] as? JsonPrimitive)?.contentOrNull,
                    (track.text("artworkUrl") ?: album?.text("cover_medium"))?.let(::imageUrl))
            }.distinctBy { it.id }.take(20)
        ProviderDiscoverySamples(ProviderDiscoveryStatus.Ready, tracks)
    }

    override suspend fun requestTrack(artist: ProviderDiscoveryArtist, sample: ProviderDiscoverySample) = operation {
        checkPermission("addAlbum")
        if ("trackDownloads" !in features) throw NavidromeHttpException(404)
        require(MusicBrainzId.matches(artist.id))
        post("/api/library/downloads/track", buildJsonObject {
            put("artistName", artist.name); put("artistMbid", artist.id); put("trackName", sample.title)
            sample.albumTitle?.let { put("albumName", it) }
        }).operation()
    }

    override suspend fun requestAlbum(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease) = operation {
        checkPermission("addAlbum")
        require(MusicBrainzId.matches(artist.id) && MusicBrainzId.matches(release.id))
        post("/api/library/albums/request", buildJsonObject {
            put("artistName", artist.name); put("artistMbid", artist.id)
            put("albumName", release.title); put("albumMbid", release.id); put("triggerSearch", true)
        }).operation()
    }

    override suspend fun setMonitoring(artist: ProviderDiscoveryArtist, enabled: Boolean) = operation {
        checkPermission("changeMonitoring")
        require(MusicBrainzId.matches(artist.id))
        val current = get("/api/library/artists/${artist.id}/monitoring")
        check(current.text("error") == null)
        if (enabled && current["added"]?.jsonPrimitive?.booleanOrNull != true) checkPermission("addArtist")
        val body = buildJsonObject {
            put("artistName", artist.name)
            if (current.text("manager") == "aurral") put("monitoring", buildJsonObject {
                put("monitored", enabled); put("useDefault", true)
            }) else if (current.text("manager") == "lidarr") put("monitorOption", if (enabled) "all" else "none")
            else error("Unknown monitoring manager")
        }
        val response = http.putJson("$base/api/library/artists/${artist.id}", body.toString(), authorizedHeaders()).parseObject()
        check(response.text("error") == null)
        val verified = get("/api/library/artists/${artist.id}/monitoring")
        val actual = verified["monitored"]?.jsonPrimitive?.booleanOrNull
            ?: verified.text("monitorOption")?.let { it != "none" }
        check(actual == enabled && verified.text("error") == null)
        ProviderDiscoveryOperation(if (enabled) ProviderDiscoveryOperationStatus.MonitoringEnabled else ProviderDiscoveryOperationStatus.MonitoringDisabled)
    }

    private fun checkPermission(name: String) {
        if (session?.permissions?.get(name)?.jsonPrimitive?.booleanOrNull != true) throw NavidromeHttpException(403)
    }
    private suspend fun post(path: String, body: JsonObject) = http.postJson("$base$path", body.toString(), authorizedHeaders()).parseObject()
    private suspend fun operation(block: suspend () -> ProviderDiscoveryOperation): ProviderDiscoveryOperation =
        nativeCall({ status -> ProviderDiscoveryOperation(when (status) {
            ProviderDiscoveryStatus.Forbidden -> ProviderDiscoveryOperationStatus.Forbidden
            ProviderDiscoveryStatus.Unsupported -> ProviderDiscoveryOperationStatus.Unsupported
            else -> ProviderDiscoveryOperationStatus.Uncertain
        }) }, block)
    private fun JsonObject.operation(): ProviderDiscoveryOperation {
        check(text("error") == null)
        return ProviderDiscoveryOperation(when {
            text("status") == "download_source_missing" -> ProviderDiscoveryOperationStatus.MissingSource
            text("status") == "available" || get("alreadyOwned")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.AlreadyOwned
            get("alreadyQueued")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.AlreadyQueued
            text("status") in listOf("queued", "searching") || get("queued")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.Queued
            get("success")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.Saved
            else -> error("Unconfirmed request")
        })
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
            features = api.array("features").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
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
        session = Session(token, expiresAt, (response["user"] as? JsonObject)?.get("permissions") as? JsonObject)
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
    fun ownsDiscoveryCoverUrl(url: String): Boolean {
        val prefix = "$base/api/artists/"
        if (!url.startsWith(prefix)) return false
        val path = url.removePrefix(prefix).removePrefix("release-group/")
        return MusicBrainzId.matches(path.substringBefore('/')) &&
            path.substringAfter('/', "").startsWith("cover?artistName=")
    }

    suspend fun discoveryCoverBytes(url: String): ByteArray? {
        require(ownsDiscoveryCoverUrl(url))
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
