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

    override suspend fun recommendations(offset: Int, limit: Int): ProviderRecommendationPage {
        require(limit in 1..200)
        require(offset in 0..(Int.MAX_VALUE - limit))
        return nativeCall({ ProviderRecommendationPage(status = it, offset = offset) }) {
            val feed = get("/api/discover?limit=$limit&offset=$offset")
            val rows = feed.array("recommendations") ?: error("Missing recommendation rows")
            check(rows.size <= limit)
            val count = feed.nonNegativeInt("recommendationCount")
            ProviderRecommendationPage(
                status = ProviderDiscoveryStatus.Ready,
                artists = feed.artists("recommendations", limit, offset), offset = offset,
                nextOffset = nextPageOffset(offset, rows.size, limit, count), totalCount = count,
                seeds = feed.array("basedOn").orEmpty().mapNotNull { row ->
                    val seed = row as? JsonObject ?: return@mapNotNull null
                    val name = seed.text("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    ProviderDiscoverySeed(name, seed.text("id")?.takeIf(MusicBrainzId::matches)?.let(::DiscoveryArtistMbid))
                },
                genres = feed.strings("topGenres"), tags = feed.strings("topTags"),
                lastUpdated = feed.text("lastUpdated"), runId = feed.text("discoveryRunId"),
            )
        }
    }

    override suspend fun searchCatalog(query: String, scope: ProviderCatalogScope, offset: Int, limit: Int): ProviderCatalogPage {
        require(limit in 1..100)
        require(offset in 0..(Int.MAX_VALUE - limit))
        if (query.isBlank()) return ProviderCatalogPage(status = ProviderDiscoveryStatus.Ready, offset = offset, totalCount = 0)
        return nativeCall({ ProviderCatalogPage(status = it, offset = offset) }) {
            val scopeName = if (scope == ProviderCatalogScope.Artist) "artist" else "album"
            val page = get("/api/search?q=${query.trim().urlEncode()}&scope=$scopeName&limit=$limit&offset=$offset")
            check(page.text("scope") == scopeName && page.nonNegativeInt("offset") == offset)
            val rows = page.array("items") ?: error("Missing catalog rows")
            check(rows.size <= limit)
            val count = page.nonNegativeInt("count")
            ProviderCatalogPage(status = ProviderDiscoveryStatus.Ready, offset = offset, totalCount = count,
                nextOffset = nextPageOffset(offset, rows.size, limit, count),
                artists = if (scope == ProviderCatalogScope.Artist) page.artists("items", limit, offset) else emptyList(),
                albums = if (scope == ProviderCatalogScope.Album) rows.mapIndexedNotNull { index, row ->
                    val album = row as? JsonObject ?: return@mapIndexedNotNull null
                    val id = album.text("id")?.takeIf { it.isNotBlank() } ?: "unresolved:catalog-album:${offset + index}"
                    val title = album.text("title")?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                    val artistName = album.text("artistName")?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                    ProviderCatalogAlbum(ProviderDiscoveryRelease(id, title, album.text("releaseDate"), album.text("primaryType"),
                        album.strings("secondaryTypes"), album.text("coverUrl")?.let(::imageUrl)), artistName,
                        album.text("artistMbid")?.takeIf(MusicBrainzId::matches)?.let(::DiscoveryArtistMbid),
                        (album["inLibrary"] as? JsonPrimitive)?.booleanOrNull, album.text("status"))
                }.distinctBy { it.release.id } else emptyList())
        }
    }

    override suspend fun releaseTracks(release: DiscoveryReleaseGroupMbid): ProviderDiscoveryReleaseTracks =
        nativeCall({ ProviderDiscoveryReleaseTracks(it) }, notFoundStatus = ProviderDiscoveryStatus.Unavailable) {
            val rows = json.parseToJsonElement(http.get("$base/api/artists/release-group/${release.value}/tracks",
                headers = authorizedHeaders())) as? JsonArray ?: error("Missing release tracks")
            ProviderDiscoveryReleaseTracks(ProviderDiscoveryStatus.Ready, rows.mapNotNull { row ->
                val track = row as? JsonObject ?: return@mapNotNull null
                val title = track.text("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val preview = track.text("previewTrackId")?.takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }
                    ?.takeIf { track.text("previewProvider") == "deezer" && !track.text("preview_url").isNullOrBlank() }
                ProviderDiscoveryReleaseTrack(
                    track.text("mbid")?.takeIf(MusicBrainzId::matches)?.let(::DiscoveryRecordingMbid), title,
                    track.nonNegativeInt("discNumber")?.takeIf { it > 0 },
                    track.nonNegativeInt("trackNumber")?.takeIf { it > 0 },
                    (track["length"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 },
                    preview?.let(::DiscoveryDeezerPreviewId))
            })
        }

    override suspend fun releaseDetails(release: DiscoveryReleaseGroupMbid): ProviderDiscoveryReleaseDetails =
        nativeCall({ ProviderDiscoveryReleaseDetails(it) }, notFoundStatus = ProviderDiscoveryStatus.Unavailable) {
            val detail = get("/api/artists/release-group/${release.value}")
            check(detail.text("id").equals(release.value, ignoreCase = true))
            val title = detail.text("title")?.takeIf { it.isNotBlank() } ?: error("Missing release title")
            ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Ready,
                ProviderDiscoveryRelease(release.value, title, detail.text("first-release-date"), detail.text("primary-type"),
                    detail.strings("secondary-types"), detail.text("coverUrl")?.let(::imageUrl)),
                detail.array("artist-credit").orEmpty().mapNotNull { row ->
                    val credit = row as? JsonObject ?: return@mapNotNull null
                    val artist = credit["artist"] as? JsonObject
                    val name = credit.text("name") ?: artist?.text("name") ?: return@mapNotNull null
                    ProviderDiscoveryArtistCredit(name, artist?.text("id")?.takeIf(MusicBrainzId::matches)?.let(::DiscoveryArtistMbid))
                }, detail.text("overview"), detail.strings("genres"))
        }

    override suspend fun requestActivity(): ProviderDiscoveryActivities = nativeCall({ ProviderDiscoveryActivities(it) }) {
        if ("activity" !in features) throw NavidromeHttpException(404)
        val rows = json.parseToJsonElement(http.get("$base/api/requests", headers = authorizedHeaders())) as? JsonArray
            ?: error("Missing activity rows")
        ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready, rows.mapNotNull { row ->
            val item = row as? JsonObject ?: return@mapNotNull null
            val id = item.text("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ProviderDiscoveryActivity(id, item.text("status"), item.requestIdentity(),
                item.text("albumMbid")?.takeIf(MusicBrainzId::matches)?.let(::DiscoveryReleaseGroupMbid))
        })
    }

    override suspend fun similarArtists(artist: DiscoveryArtistMbid): ProviderDiscoverySimilarArtists =
        nativeCall({ ProviderDiscoverySimilarArtists(it) }, notFoundStatus = ProviderDiscoveryStatus.Unavailable) {
            val result = get("/api/artists/${artist.value}/similar?limit=20")
            check(result.array("artists") != null)
            ProviderDiscoverySimilarArtists(ProviderDiscoveryStatus.Ready, result.artists("artists"))
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
        }) }, block = block)
    private fun JsonObject.operation(): ProviderDiscoveryOperation {
        check(text("error") == null)
        return ProviderDiscoveryOperation(when {
            text("status") == "download_source_missing" -> ProviderDiscoveryOperationStatus.MissingSource
            text("status") == "available" || get("alreadyOwned")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.AlreadyOwned
            get("alreadyQueued")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.AlreadyQueued
            text("status") in listOf("queued", "searching") || get("queued")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.Queued
            get("success")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.Saved
            else -> error("Unconfirmed request")
        }, requestIdentity())
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

    private suspend fun <T> nativeCall(
        fallback: (ProviderDiscoveryStatus) -> T,
        notFoundStatus: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported,
        block: suspend () -> T,
    ): T = mutex.withLock {
        var routeStarted = false
        try {
            val bootstrap = get("/api/health/bootstrap", authenticated = false)
            val api = bootstrap["api"] as? JsonObject
            if (api?.get("version")?.jsonPrimitive?.intOrNull != 1 ||
                api.array("features")?.any { (it as? JsonPrimitive)?.contentOrNull == "discover" } != true
            ) return@withLock fallback(ProviderDiscoveryStatus.Unsupported)
            features = api.array("features").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
            if (connection.password.isNullOrBlank()) return@withLock fallback(ProviderDiscoveryStatus.PasswordRequired)
            if (session?.expiresAt?.let { it > nowMillis() + 30_000 } != true) signIn()
            routeStarted = true
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            fallback(when ((error as? NavidromeHttpException)?.statusCode) {
                401 -> { session = null; ProviderDiscoveryStatus.SignInRequired }
                403 -> ProviderDiscoveryStatus.Forbidden
                // Released metadata routes also return 404 during upstream rate-limit cooldowns.
                404 -> if (routeStarted) notFoundStatus else ProviderDiscoveryStatus.Unsupported
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

    private fun JsonObject.artists(field: String, limit: Int = 20, offset: Int = 0): List<ProviderDiscoveryArtist> =
        array(field).orEmpty().take(limit).mapIndexedNotNull { index, entry ->
            val artist = entry as? JsonObject ?: return@mapIndexedNotNull null
            val name = artist.text("name")?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val id = artist.text("id")?.takeIf { it.isNotBlank() }
            val validId = id != null && MusicBrainzId.matches(id)
            ProviderDiscoveryArtist(
                id = id ?: "unresolved:$field:${offset + index}", name = name,
                imageUrl = (artist.text("image") ?: artist.text("imageUrl"))?.let(::imageUrl)
                    ?: if (validId) "$base/api/artists/$id/cover?artistName=${name.urlEncode()}" else null,
                genres = artist.array("tags").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                matchPercent = (artist["matchPercent"] as? JsonPrimitive)?.intOrNull?.coerceIn(0, 100),
                externalUrl = if (validId) "$base/artist/$id" else null,
                detailsAvailable = validId,
                disambiguation = artist.text("disambiguation"), artistType = artist.text("artistType"),
                recommendation = if (field == "recommendations") ProviderRecommendationContext(
                    rank = offset + index + 1,
                    matchedTags = artist.strings("matchedTags"), sourceArtists = artist.strings("sourceArtists")
                        .ifEmpty { listOfNotNull(artist.text("sourceArtist")) },
                    supportingSeeds = artist.array("supportingSeeds").orEmpty().mapNotNull { (it as? JsonObject)?.text("artistName") },
                    sourceType = artist.text("sourceType"), tier = artist.text("discoveryTier"),
                    depth = artist.nonNegativeInt("discoveryDepth")?.takeIf { it > 0 },
                    firstDiscoveredAt = artist.text("firstDiscoveredAt")?.takeIf { it.isNotBlank() },
                ) else null,
            )
        }.distinctBy { it.id }

    private fun JsonObject.nonNegativeInt(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 }
    private fun JsonObject.strings(key: String): List<String> = array(key).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { value -> value.isNotBlank() } }

    private fun JsonObject.requestIdentity(): ProviderDiscoveryRequestIdentity? {
        val album = this["album"] as? JsonObject
        val manager = when (text("managedBy")) {
            "aurral" -> DiscoveryLibraryManager.Aurral
            "lidarr" -> DiscoveryLibraryManager.Lidarr
            else -> null
        }
        fun String?.positiveId() = this?.takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }
        val identity = ProviderDiscoveryRequestIdentity(
            requestGroupId = (text("requestGroupId") ?: album?.text("requestGroupId"))
                ?.takeIf { it.isNotBlank() }?.let(::DiscoveryRequestGroupId),
            jobIds = (strings("jobIds") + listOfNotNull(text("jobId")?.takeIf { it.isNotBlank() })).distinct().map(::DiscoveryJobId),
            canonicalAlbumId = (text("canonicalAlbumId") ?: album?.text("canonicalId")).positiveId()?.let(::DiscoveryCanonicalAlbumId),
            managedAlbumId = album?.text("id").positiveId()?.let { id -> manager?.let { DiscoveryManagedAlbumId(it, id) } },
        )
        return identity.takeUnless { it == ProviderDiscoveryRequestIdentity() }
    }

    private fun nextPageOffset(offset: Int, rows: Int, limit: Int, count: Int?): Int? {
        val next = offset.toLong() + rows
        return next.takeIf { rows > 0 && it <= Int.MAX_VALUE && (count?.let { total -> it < total } ?: (rows == limit)) }?.toInt()
    }

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
