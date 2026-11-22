package app.naviamp.provider.aurral

import app.naviamp.domain.Artist
import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Aurral's native API complements the shared Subsonic engine; tokens never enter saved settings. */
internal class AurralDiscoveryClient(
    private val connection: NavidromeConnection,
    private val http: NavidromeHttpClient,
    private val nowMillis: () -> Long = ::navidromeCurrentTimeMillis,
    private val resolveLibraryArtist: suspend (identityKey: String, mbid: String) -> Artist? = { _, _ -> null },
    private val resolveLibraryAlbum: suspend (identityKey: String, mbid: String) -> app.naviamp.domain.Album? = { _, _ -> null },
) : ProviderDiscovery {
    private val mutex = Mutex()
    private val mutationMutex = Mutex()
    private var session: Session? = null
    private data class Session(val token: String, val expiresAt: Long, val permissions: JsonObject?)
    private class NativeContext(val session: Session) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<NativeContext>
    }
    private suspend fun currentSession(): Session? = currentCoroutineContext()[NativeContext]?.session ?: session
    private val base = connection.normalizedBaseUrl
    override val requestsUrl: String get() = "$base/requests"
    override val catalogUrl: String get() = "$base/search"
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
            chartsAvailable = true,
            genreBlockingAvailable = optional { get("/api/discover/blocked-tags").array("tags") } != null,
        )
    }

    override suspend fun blockedGenres(): ProviderDiscoveryBlockedGenres = nativeCall({ ProviderDiscoveryBlockedGenres(it) }) {
        val payload = get("/api/discover/blocked-tags")
        check(payload.array("tags") != null)
        ProviderDiscoveryBlockedGenres(ProviderDiscoveryStatus.Ready, payload.strings("tags").distinct())
    }
    override suspend fun setGenreBlocked(tag: String, blocked: Boolean): ProviderDiscoveryGenreMutation {
        val value = tag.trim()
        if (value.isEmpty() || value.length > 100) return ProviderDiscoveryGenreMutation(ProviderDiscoveryOperationStatus.Rejected)
        return mutationMutex.withLock { nativeCall({ ProviderDiscoveryGenreMutation(when (it) {
            ProviderDiscoveryStatus.Unsupported -> ProviderDiscoveryOperationStatus.Unsupported
            ProviderDiscoveryStatus.Forbidden -> ProviderDiscoveryOperationStatus.Forbidden
            else -> ProviderDiscoveryOperationStatus.Uncertain
        }) }) {
            // Safe GET support probe; never mutate to detect support on an older server.
            val existing = get("/api/discover/blocked-tags")
            check(existing.array("tags") != null)
            val tags = existing.strings("tags")
            val key = discoveryGenreKey(value)
            if (tags.any { discoveryGenreKey(it) == key } == blocked)
                return@nativeCall ProviderDiscoveryGenreMutation(ProviderDiscoveryOperationStatus.Saved, tags)
            if (blocked) {
                val genres = get("/api/discover?limit=1&offset=0").strings("topGenres")
                if (genres.none { discoveryGenreKey(it) == key }) return@nativeCall ProviderDiscoveryGenreMutation(ProviderDiscoveryOperationStatus.Rejected)
            }
            val result = try {
                if (blocked) post("/api/discover/blocked-tags", buildJsonObject { put("tag", value) })
                else http.deleteJson("$base/api/discover/blocked-tags?tag=${value.urlEncode()}", authorizedHeaders()).parseObject()
            } catch (error: NavidromeHttpException) {
                if (error.statusCode == 400) return@nativeCall ProviderDiscoveryGenreMutation(ProviderDiscoveryOperationStatus.Rejected)
                throw error
            }
            check(result.array("tags") != null)
            val confirmed = result.strings("tags")
            check(confirmed.any { discoveryGenreKey(it) == key } == blocked)
            ProviderDiscoveryGenreMutation(ProviderDiscoveryOperationStatus.Saved, confirmed)
        } }
    }

    override suspend fun charts(): ProviderDiscoveryCharts = nativeCall({ ProviderDiscoveryCharts(status = it) }) {
        val payload = get("/api/discover/trending")
        val songs = payload["songs"] as? JsonObject
        ProviderDiscoveryCharts(ProviderDiscoveryStatus.Ready, payload.text("region"), payload.chartUpdatedAt(),
            songs?.array("items")?.chartSongs(),
            payload.array("albums")?.mapNotNull { row ->
                val album = row as? JsonObject ?: return@mapNotNull null
                DiscoveryChartAlbum(album.text("key")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                    album.text("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                    album.text("artistName")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                    album.text("artworkUrl")?.let(::imageUrl), album.text("releaseDate"), album.nonNegativeInt("position"))
            }?.distinctBy { it.key },
            payload.array("artists")?.mapNotNull { row ->
                val artist = row as? JsonObject ?: return@mapNotNull null
                val key = artist.text("key")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val mbid = artist.text("mbid")?.takeIf(MusicBrainzId::matches)
                DiscoveryChartArtist(key, ProviderDiscoveryArtist(mbid ?: "chart:$key",
                    artist.text("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                    artist.text("imageUrl")?.let(::imageUrl), detailsAvailable = mbid != null,
                    externalUrl = mbid?.let { "$base/artist/$it" }), artist.nonNegativeInt("position"))
            }?.distinctBy { it.key },
            payload.array("genres").orEmpty().mapNotNull { row ->
                val genre = row as? JsonObject ?: return@mapNotNull null
                val id = genre.text("id")?.takeIf { it.matches(Regex("[0-9]{1,6}")) } ?: return@mapNotNull null
                DiscoveryChartGenre(id, genre.text("name") ?: return@mapNotNull null, genre.strings("covers").mapNotNull(::imageUrl))
            }.distinctBy { it.id }, songs?.text("playlistId")?.takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true },
            songs?.text("libraryPlaylistId"),
            hasPermission("accessPlaylists"),
            (songs?.get("spotlight") as? JsonObject)?.let { spotlight ->
                DiscoveryChartSpotlight(spotlight.array("ranks").orEmpty().take(30).map { (it as? JsonPrimitive)?.intOrNull?.takeIf { n -> n > 0 } }, spotlight.nonNegativeInt("daysAtTop"))
            })
    }
    override suspend fun genreChart(id: String): ProviderDiscoveryGenreChart {
        require(id.matches(Regex("[0-9]{1,6}")))
        return nativeCall({ ProviderDiscoveryGenreChart(status = it) }, notFoundStatus = ProviderDiscoveryStatus.Unavailable) {
            val payload = get("/api/discover/trending/genres/$id")
            check(payload.text("id") == id)
            ProviderDiscoveryGenreChart(ProviderDiscoveryStatus.Ready, id, payload.text("name"), payload.text("region"), payload.chartUpdatedAt(),
                (payload.array("items") ?: error("Missing genre chart")).chartSongs())
        }
    }
    override suspend fun saveChartPlaylist(id: String): ProviderDiscoveryChartSave {
        require(id.toLongOrNull()?.let { it > 0 } == true)
        return mutationMutex.withLock { nativeCall({ ProviderDiscoveryChartSave(status = when (it) {
            ProviderDiscoveryStatus.Forbidden -> ProviderDiscoveryOperationStatus.Forbidden
            ProviderDiscoveryStatus.Unsupported -> ProviderDiscoveryOperationStatus.Unsupported
            else -> ProviderDiscoveryOperationStatus.Uncertain
        }) }) {
            if (!hasPermission("accessPlaylists"))
                return@nativeCall ProviderDiscoveryChartSave(ProviderDiscoveryOperationStatus.Forbidden)
            // Reconcile before the deliberate POST; this endpoint deduplicates by account and external ID.
            val songs = get("/api/discover/trending")["songs"] as? JsonObject ?: error("Missing songs chart")
            check(songs.text("playlistId") == id)
            songs.text("libraryPlaylistId")?.takeIf(String::isNotBlank)?.let {
                return@nativeCall ProviderDiscoveryChartSave(ProviderDiscoveryOperationStatus.Saved, it)
            }
            val result = post("/api/discover/editorial/$id/library", buildJsonObject {})
            val playlist = result.text("playlistId")?.takeIf(String::isNotBlank) ?: error("Missing saved playlist")
            ProviderDiscoveryChartSave(ProviderDiscoveryOperationStatus.Saved, playlist)
        } }
    }
    private fun JsonObject.chartUpdatedAt(): String? {
        val value = this["updatedAt"] as? JsonPrimitive ?: return null
        return value.longOrNull?.let { runCatching { kotlin.time.Instant.fromEpochMilliseconds(it).toString() }.getOrNull() }
            ?: value.takeIf { it.isString }?.contentOrNull?.takeIf { it.length <= 64 }
    }
    private fun JsonArray.chartSongs(): List<DiscoveryChartSong> = take(200).mapNotNull { row ->
        val song = row as? JsonObject ?: return@mapNotNull null
        val key = song.text("key")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val path = "/api/deezer/preview/$key.mp3"
        val preview = song.text("preview_url")?.takeIf { it == path || it == "$base$path" }
            ?.let { key.takeIf { value -> value.toLongOrNull()?.let { it > 0 } == true }?.let(::DiscoveryDeezerPreviewId) }
        DiscoveryChartSong(key, song.text("trackName")?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
            song.text("artistName")?.takeIf(String::isNotBlank) ?: return@mapNotNull null, song.text("albumName"),
            song.text("artworkUrl")?.let(::imageUrl), song.nonNegativeInt("position"),
            (song["movement"] as? JsonPrimitive)?.intOrNull, song.text("movement") == "new", preview)
    }.distinctBy { it.key }

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
                        album.strings("secondaryTypes"), releaseCoverUrl(id, album.text("coverUrl"), artistName, title)), artistName,
                        album.text("artistMbid")?.takeIf(MusicBrainzId::matches)?.let(::DiscoveryArtistMbid),
                        (album["inLibrary"] as? JsonPrimitive)?.booleanOrNull, album.text("status"))
                }.distinctBy { it.release.id } else emptyList())
        }
    }

    override suspend fun releaseTracks(release: DiscoveryReleaseGroupMbid): ProviderDiscoveryReleaseTracks = loadReleaseTracks(release, null)

    override suspend fun releaseTracks(release: DiscoveryReleaseGroupMbid, context: ProviderDiscoveryReleaseContext): ProviderDiscoveryReleaseTracks =
        loadReleaseTracks(release, context)

    private suspend fun loadReleaseTracks(release: DiscoveryReleaseGroupMbid, context: ProviderDiscoveryReleaseContext?): ProviderDiscoveryReleaseTracks =
        nativeCall({ ProviderDiscoveryReleaseTracks(it) }, notFoundStatus = ProviderDiscoveryStatus.Unavailable) {
            require(context == null || context.release.releaseGroupMbid == release && context.artist.artistMbid != null)
            val query = context?.let {
                buildList {
                    add("artistMbid=${it.artist.id.urlEncode()}")
                    add("artistName=${it.artist.name.urlEncode()}")
                    add("albumTitle=${it.release.title.urlEncode()}")
                    it.release.type?.let { value -> add("releaseType=${value.urlEncode()}") }
                    it.release.releaseDate?.let { value -> add("releaseDate=${value.urlEncode()}") }
                }.joinToString("&", prefix = "?")
            }.orEmpty()
            val rows = json.parseToJsonElement(http.get("$base/api/artists/release-group/${release.value}/tracks$query",
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
                    preview?.let(::DiscoveryDeezerPreviewId),
                    track.text("releaseMbid")?.takeIf(MusicBrainzId::matches)?.let(::DiscoveryReleaseMbid))
            })
        }

    override suspend fun releaseDetails(release: DiscoveryReleaseGroupMbid): ProviderDiscoveryReleaseDetails =
        nativeCall({ ProviderDiscoveryReleaseDetails(it) }, notFoundStatus = ProviderDiscoveryStatus.Unavailable) {
            val detail = get("/api/artists/release-group/${release.value}")
            check(detail.text("id").equals(release.value, ignoreCase = true))
            val title = detail.text("title")?.takeIf { it.isNotBlank() } ?: error("Missing release title")
            val creditedArtist = detail.array("artist-credit")?.firstNotNullOfOrNull { (it as? JsonObject)?.text("name") }
            ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Ready,
                ProviderDiscoveryRelease(release.value, title, detail.text("first-release-date"), detail.text("primary-type"),
                    detail.strings("secondary-types"), releaseCoverUrl(release.value, detail.text("coverUrl"), creditedArtist, title)),
                detail.array("artist-credit").orEmpty().mapNotNull { row ->
                    val credit = row as? JsonObject ?: return@mapNotNull null
                    val artist = credit["artist"] as? JsonObject
                    val name = credit.text("name") ?: artist?.text("name") ?: return@mapNotNull null
                    ProviderDiscoveryArtistCredit(name, artist?.text("id")?.takeIf(MusicBrainzId::matches)?.let(::DiscoveryArtistMbid))
                }, detail.text("overview"), detail.strings("genres"),
                detail.array("releases").orEmpty().mapNotNull { row ->
                    val edition = row as? JsonObject ?: return@mapNotNull null
                    val id = edition.text("id")?.takeIf(MusicBrainzId::matches) ?: return@mapNotNull null
                    ProviderDiscoveryEdition(DiscoveryReleaseMbid(id), edition.text("title"), edition.text("date"), edition.text("status"))
                }.distinctBy { it.mbid })
        }

    override suspend fun requestActivity(): ProviderDiscoveryActivities = nativeCall({ ProviderDiscoveryActivities(it) }) {
        val rows = json.parseToJsonElement(http.get("$base/api/requests", headers = authorizedHeaders())) as? JsonArray
            ?: error("Missing activity rows")
        ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready, rows.take(200).mapNotNull { row ->
            val item = row as? JsonObject ?: return@mapNotNull null
            val id = item.text("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ProviderDiscoveryActivity(id, item.text("status"), item.requestIdentity(),
                item.text("albumMbid")?.takeIf(MusicBrainzId::matches)?.let(::DiscoveryReleaseGroupMbid),
                item.text("title") ?: item.text("albumName"), item.text("artistName"), item.text("kind"))
        })
    }

    override suspend fun acquisitionStatus(identity: ProviderDiscoveryRequestIdentity): ProviderDiscoveryAcquisitionStatus =
        nativeCall({ ProviderDiscoveryAcquisitionStatus(it) }) {
            // Only Aurral-managed albums have a download status. Identities saved from the Aurral 2
            // contract may name Lidarr, which Aurral 3 no longer reports; they stay unknown.
            if (identity.managedAlbumId?.manager != DiscoveryLibraryManager.Aurral) return@nativeCall ProviderDiscoveryAcquisitionStatus()
            val album = identity.canonicalAlbumId ?: return@nativeCall ProviderDiscoveryAcquisitionStatus()
            val response = get("/api/library/albums/${album.value.urlEncode()}/status")
            check(response.text("error") == null && response.text("libraryId") == album.value)
            val raw = response.text("status")
            val phase = acquisitionPhase(raw)
            ProviderDiscoveryAcquisitionStatus(ProviderDiscoveryStatus.Ready, phase, raw,
                if (phase == DiscoveryAcquisitionPhase.WaitingForRelease)
                    ProviderDiscoveryWaitingForRelease(DiscoveryLibraryManager.Aurral, response.displayReleaseDate()) else null)
        }

    override suspend fun releaseOwnership(release: DiscoveryReleaseGroupMbid): ProviderDiscoveryReleaseOwnership =
        nativeCall({ ProviderDiscoveryReleaseOwnership(it) }) {
            // The released batch endpoint is a read; it accepts POST solely to carry the ID list.
            val lookup = post("/api/library/albums/lookup/batch", buildJsonObject {
                put("mbids", buildJsonArray { add(release.value) })
            })
            check(lookup.text("error") == null)
            val entry = lookup[release.value] as? JsonObject
            if (entry == null || entry["inLibrary"]?.jsonPrimitive?.booleanOrNull != true)
                return@nativeCall ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.Ready, ProviderDiscoveryReleaseAvailability.NotOwned)
            val libraryAlbum = entry.text("libraryAlbumId").positiveId()
            val canonical = libraryAlbum?.let(::DiscoveryCanonicalAlbumId)
            val managed = libraryAlbum?.takeIf { entry.flag("managed") == true }
                ?.let { DiscoveryManagedAlbumId(DiscoveryLibraryManager.Aurral, it) }
            val files = entry.nonNegativeInt("trackFileCount")
            val count = entry.nonNegativeInt("trackCount")
            val availability = when {
                files == null -> null
                files == 0 -> ProviderDiscoveryReleaseAvailability.Catalogued
                entry.text("status") == "partial" || entry["trackListComplete"]?.jsonPrimitive?.booleanOrNull == false ||
                    count?.let { files < it } == true -> ProviderDiscoveryReleaseAvailability.Partial
                entry.text("status") == "available" -> ProviderDiscoveryReleaseAvailability.Available
                else -> ProviderDiscoveryReleaseAvailability.Partial
            }
            val album = if (canonical != null && files != null && files > 0) optional {
                val page = get("/api/library?kind=albums&pageSize=1&availableOnly=true&albumId=${canonical.value.urlEncode()}")
                val row = page.array("items")?.singleOrNull() as? JsonObject
                if (row == null || row.text("id") != canonical.value || !row.text("mbid").equals(release.value, ignoreCase = true)) null
                else row.text("identityKey")?.takeIf(String::isNotBlank)?.let { resolveLibraryAlbum(it, release.value) }
            } else null
            ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.Ready, availability, canonical, managed,
                entry.strings("ownedTrackMbids").filter(MusicBrainzId::matches).map(::DiscoveryRecordingMbid).toSet(), album)
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
            val permissions = currentSession()?.permissions
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
                        imageUrl = releaseCoverUrl(id, release.text("coverUrl"), artist.name, title)
                            ?: if (MusicBrainzId.matches(id)) lazyReleaseCoverUrl(id, artist.name, title) else null)
                }.distinctBy { it.id },
                libraryArtist = owned,
                canRequestTracks = permissions?.get("addAlbum")?.jsonPrimitive?.booleanOrNull == true,
                canRequestAlbums = permissions?.get("addAlbum")?.jsonPrimitive?.booleanOrNull == true,
                canChangeMonitoring = monitoring != null && monitoring.text("error") == null &&
                    permissions?.get("changeMonitoring")?.jsonPrimitive?.booleanOrNull == true &&
                    (monitoring["added"]?.jsonPrimitive?.booleanOrNull == true || permissions["addArtist"]?.jsonPrimitive?.booleanOrNull == true),
                monitored = monitoring?.takeIf { it.text("error") == null }?.flag("monitored"),
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
        require(MusicBrainzId.matches(artist.id))
        postOperation("/api/library/downloads/track", buildJsonObject {
            put("artistName", artist.name); put("artistMbid", artist.id); put("trackName", sample.title)
            sample.albumTitle?.let { put("albumName", it) }
        })
    }

    override suspend fun requestAlbum(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease) = operation {
        checkPermission("addAlbum")
        require(MusicBrainzId.matches(artist.id) && MusicBrainzId.matches(release.id))
        postOperation("/api/library/albums/request", buildJsonObject {
            put("artistName", artist.name); put("artistMbid", artist.id)
            put("albumName", release.title); put("albumMbid", release.id)
        })
    }

    override suspend fun requestReleaseTrack(context: ProviderDiscoveryReleaseContext, track: ProviderDiscoveryReleaseTrack): ProviderDiscoveryOperation {
        val recording = track.recordingMbid
        if (context.artist.artistMbid == null || context.release.releaseGroupMbid == null || recording == null ||
            context.artist.name.isBlank() || context.release.title.isBlank() || track.title.isBlank())
            return ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Unavailable)
        return operation {
            checkPermission("addAlbum")
            postOperation("/api/library/downloads/track", buildJsonObject {
                put("artistName", context.artist.name); put("artistMbid", context.artist.id)
                put("albumName", context.release.title); put("albumMbid", context.release.id)
                put("trackName", track.title); put("trackMbid", recording.value)
                track.releaseMbid?.let { put("releaseMbid", it.value) }
                track.discNumber?.let { put("discNumber", it) }
                track.trackNumber?.let { put("trackNumber", it) }
                track.durationMillis?.let { put("durationMs", it) }
            })
        }
    }

    override suspend fun setMonitoring(artist: ProviderDiscoveryArtist, enabled: Boolean) = operation {
        checkPermission("changeMonitoring")
        require(MusicBrainzId.matches(artist.id))
        val current = get("/api/library/artists/${artist.id}/monitoring")
        check(current.text("error") == null)
        if (enabled && current.flag("added") != true) checkPermission("addArtist")
        // Enabling keeps the server's default monitoring rules; disabling sends only the flag.
        val body = buildJsonObject {
            put("artistName", artist.name)
            put("monitoring", buildJsonObject { put("monitored", enabled); if (enabled) put("useDefault", true) })
        }
        val response = http.putJson("$base/api/library/artists/${artist.id}", body.toString(), authorizedHeaders()).parseObject()
        check(response.text("error") == null)
        val verified = get("/api/library/artists/${artist.id}/monitoring")
        check(verified.flag("monitored") == enabled && verified.text("error") == null)
        ProviderDiscoveryOperation(if (enabled) ProviderDiscoveryOperationStatus.MonitoringEnabled else ProviderDiscoveryOperationStatus.MonitoringDisabled)
    }

    private suspend fun hasPermission(name: String): Boolean =
        currentSession()?.permissions?.get(name)?.jsonPrimitive?.booleanOrNull == true
    private suspend fun checkPermission(name: String) {
        if (!hasPermission(name)) throw NavidromeHttpException(403)
    }
    private suspend fun post(path: String, body: JsonObject) = http.postJson("$base$path", body.toString(), authorizedHeaders()).parseObject()
    private suspend fun postOperation(path: String, body: JsonObject): ProviderDiscoveryOperation = try {
        val response = http.postJsonResponse("$base$path", body.toString(), authorizedHeaders())
        response.body.parseObject().operation(response.statusCode, albumRequest = path == "/api/library/albums/request")
    } catch (error: NavidromeHttpException) {
        // Only released route/status envelopes establish a definite rejection. Error identities
        // cannot establish ownership, and unknown or partial submissions must never be replayed.
        val payload = error.responseBody?.let { runCatching { it.parseObject() }.getOrNull() }
        val status = when {
            error.statusCode == 400 && payload?.text("error") == "Invalid request" -> ProviderDiscoveryOperationStatus.Rejected
            path == "/api/library/albums/request" && error.statusCode == 409 &&
                payload?.text("code") == "album_owner_conflict" -> ProviderDiscoveryOperationStatus.Conflict
            path == "/api/library/albums/request" && error.statusCode == 422 &&
                payload?.text("code") == "ambiguous_identity" -> ProviderDiscoveryOperationStatus.Rejected
            else -> throw error
        }
        ProviderDiscoveryOperation(status, httpStatusCode = error.statusCode)
    }
    private suspend fun operation(block: suspend () -> ProviderDiscoveryOperation): ProviderDiscoveryOperation =
        mutationMutex.withLock { nativeCall({ status -> ProviderDiscoveryOperation(when (status) {
            ProviderDiscoveryStatus.Forbidden -> ProviderDiscoveryOperationStatus.Forbidden
            ProviderDiscoveryStatus.Unsupported -> ProviderDiscoveryOperationStatus.Unsupported
            else -> ProviderDiscoveryOperationStatus.Uncertain
        }) }, block = block) }
    private fun JsonObject.operation(httpStatusCode: Int? = null, albumRequest: Boolean = false): ProviderDiscoveryOperation {
        check(text("error") == null)
        val identity = requestIdentity()
        val summary = this["albumStatus"] as? JsonObject
        val album = this["album"] as? JsonObject
        val waiting = albumRequest && get("success")?.jsonPrimitive?.booleanOrNull == true &&
            summary?.text("status") == "unreleased" && album?.flag("managed") == true &&
            text("status") in listOf(null, "inLibrary", "blocked", "unreleased") &&
            get("alreadyOwned")?.jsonPrimitive?.booleanOrNull != true &&
            get("alreadyQueued")?.jsonPrimitive?.booleanOrNull != true &&
            (album["monitored"] as? JsonPrimitive)?.booleanOrNull != false &&
            get("queued")?.jsonPrimitive?.booleanOrNull != true && identity?.jobIds.isNullOrEmpty()
        // This is display metadata, including partial dates. Never schedule or infer availability from it.
        val waitingContext = if (waiting) ProviderDiscoveryWaitingForRelease(DiscoveryLibraryManager.Aurral,
            summary.displayReleaseDate() ?: album.displayReleaseDate()) else null
        return ProviderDiscoveryOperation(when {
            waiting -> ProviderDiscoveryOperationStatus.WaitingForRelease
            text("status") == "download_source_missing" -> ProviderDiscoveryOperationStatus.MissingSource
            text("status") == "available" || get("alreadyOwned")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.AlreadyOwned
            get("alreadyQueued")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.AlreadyQueued
            text("status") in listOf("queued", "searching") || get("queued")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.Queued
            get("success")?.jsonPrimitive?.booleanOrNull == true -> ProviderDiscoveryOperationStatus.Saved
            else -> error("Unconfirmed request")
        }, identity, httpStatusCode, waitingContext)
    }

    /** Mirrors the web client's toggle semantics; refresh on a partial failure rather than claiming success. */
    override suspend fun toggleFeedback(artist: ProviderDiscoveryArtist, action: ProviderDiscoveryFeedbackAction): ProviderDiscoveryFeedbackResult =
        mutationMutex.withLock { nativeCall({ ProviderDiscoveryFeedbackResult(it) }) {
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
        } }

    /** Never turn a native numeric ID or a display-name match into a playable identity. */
    private suspend fun lookupLibraryArtist(mbid: String): Artist? {
        val lookup = get("/api/library/lookup/$mbid")
        val libraryId = lookup.text("libraryArtistId")?.takeIf { it.toLongOrNull() != null } ?: return null
        val page = get("/api/library?kind=artists&pageSize=1&availableOnly=true&artistId=${libraryId.urlEncode()}")
        val artist = page.array("items")?.singleOrNull() as? JsonObject ?: return null
        if (!artist.text("mbid").equals(mbid, ignoreCase = true) || artist.text("id") != libraryId) return null
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
    ): T {
        var routeStarted = false
        var usedSession: Session? = null
        return try {
            val bootstrap = get("/api/health/bootstrap", authenticated = false)
            val api = bootstrap["api"] as? JsonObject
            // Aurral 3 app contract. Discovery, trending, flows, activity and track requests are
            // part of version 2 itself; its `features` list only names capabilities added later.
            val version = (api?.get("version") as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            if (version != AppApiVersion) return fallback(ProviderDiscoveryStatus.Unsupported)
            if (connection.password.isNullOrBlank()) return fallback(ProviderDiscoveryStatus.PasswordRequired)
            // Only session creation is serialized; independent metadata routes must not queue behind one another.
            val active = mutex.withLock {
                if (session?.expiresAt?.let { it > nowMillis() + 30_000 } != true) signIn()
                checkNotNull(session)
            }
            usedSession = active
            routeStarted = true
            withContext(NativeContext(active)) { block() }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            fallback(when ((error as? NavidromeHttpException)?.statusCode) {
                401 -> {
                    mutex.withLock { if (session === usedSession) session = null }
                    ProviderDiscoveryStatus.SignInRequired
                }
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

    private suspend fun authorizedHeaders(): Map<String, String> =
        headers.filterKeys { !it.equals("Authorization", ignoreCase = true) } +
            ("Authorization" to "Bearer ${checkNotNull(currentSession()).token}")

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

    /** Request responses carry the Library album in `album`; activity rows carry `libraryAlbumId`. */
    private fun JsonObject.requestIdentity(): ProviderDiscoveryRequestIdentity? {
        val album = this["album"] as? JsonObject
        val libraryAlbum = (text("libraryAlbumId") ?: (this["albumGrab"] as? JsonObject)?.text("libraryAlbumId") ?: album?.text("id")).positiveId()
        val identity = ProviderDiscoveryRequestIdentity(
            requestGroupId = (text("requestGroupId") ?: album?.text("requestGroupId") ?: (this["albumStatus"] as? JsonObject)?.text("requestGroupId"))
                ?.takeIf { it.isNotBlank() }?.let(::DiscoveryRequestGroupId),
            jobIds = (strings("jobIds") + listOfNotNull(text("jobId")?.takeIf { it.isNotBlank() })).distinct().map(::DiscoveryJobId),
            canonicalAlbumId = libraryAlbum?.let(::DiscoveryCanonicalAlbumId),
            managedAlbumId = libraryAlbum?.takeIf { (flag("managed") ?: album?.flag("managed")) == true }
                ?.let { DiscoveryManagedAlbumId(DiscoveryLibraryManager.Aurral, it) },
        )
        return identity.takeUnless { it == ProviderDiscoveryRequestIdentity() }
    }

    private fun String?.positiveId(): String? = this?.takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }
    /** A JSON boolean only; a quoted "true" is not a contract value. */
    private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
    /** Display metadata only, including partial dates; never schedule or infer availability from it. */
    private fun JsonObject.displayReleaseDate(): String? = (this["releaseDate"] as? JsonPrimitive)?.takeIf { it.isString }
        ?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it.length <= 64 }

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
        val resolved = nativeCall<Pair<String, Map<String, String>>?>({ null }) {
            val images = get(url.removePrefix(base)).array("images").orEmpty()
                .mapNotNull { it as? JsonObject }
            val preferred = images.firstOrNull { it["front"]?.jsonPrimitive?.booleanOrNull == true }
            val image = (listOfNotNull(preferred) + images).firstNotNullOfOrNull { it.text("image")?.let(::imageUrl) }
            image?.let { it to if (it.startsWith("$base/")) authorizedHeaders() else emptyMap() }
        } ?: return null
        // Never send a user session or custom proxy credentials to an external artwork service.
        return http.getBytes(resolved.first, headers = resolved.second)
    }

    /**
     * Aurral 3 may report a release cover as its session-only image route, which redirects to an
     * external host. Never attach the session to that redirect: read the cover list lazily instead.
     */
    private fun releaseCoverUrl(id: String, coverUrl: String?, artistName: String?, title: String): String? {
        val sessionRoute = "/api/artists/release-group/$id/cover/image"
        return if (MusicBrainzId.matches(id) && (coverUrl == sessionRoute || coverUrl == "$base$sessionRoute"))
            lazyReleaseCoverUrl(id, artistName.orEmpty(), title) else coverUrl?.let(::imageUrl)
    }
    private fun lazyReleaseCoverUrl(id: String, artistName: String, title: String) =
        "$base/api/artists/release-group/$id/cover?artistName=${artistName.urlEncode()}&albumTitle=${title.urlEncode()}"

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
        /** Aurral 3.x. Version 1 (Aurral 2.x) used different identity and route names. */
        const val AppApiVersion = 2
        val MusicBrainzId = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
    }
}
