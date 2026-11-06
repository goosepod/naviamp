package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Synthetic payload excerpts from the pinned source in docs/verification/473-aurral-contract.md. */
class AurralCatalogContractTest {
    @Test fun recommendationsBeyondTwoHundredKeepRawOffsetsRanksAndOlderServerContext() = runTest {
        val http = ContractHttp()
        val client = client(http)
        val first = client.recommendations(0, 200)
        assertEquals(200, first.nextOffset)
        assertEquals(205, first.totalCount)
        assertEquals(listOf("Seed one", "Seed two"), first.seeds.map { it.name })
        assertEquals(listOf("rock", "jazz"), first.genres)
        assertEquals(listOf("alternative"), first.tags)
        assertEquals(199, first.artists.size) // Duplicate identity, not a missing server row.
        val context = assertNotNull(first.artists.first().recommendation)
        assertEquals(listOf("rock"), context.matchedTags)
        assertEquals(listOf("Seed one"), context.sourceArtists)
        assertEquals(listOf("Seed two"), context.supportingSeeds)
        assertEquals(1, context.rank)
        assertNull(context.depth)
        assertNull(context.firstDiscoveredAt)
        val last = client.recommendations(200, 200)
        assertNull(last.nextOffset)
        assertEquals(5, last.artists.size)
        assertEquals(201, last.artists.first().recommendation?.rank)
        assertEquals("unresolved:recommendations:200", last.artists.first().id)
        assertFalse(last.artists.first().detailsAvailable)
        assertNull(last.artists.first().externalUrl)
        assertEquals(1, http.logins)
        assertEquals(listOf("login"), http.writes)
    }

    @Test fun optionalNewMetadataIsPreservedOnlyWhenSupplied() = runTest {
        val http = ContractHttp().also { it.newMetadata = true }
        val artist = client(http).recommendations(0, 1).artists.single()
        assertEquals(2, artist.recommendation?.depth)
        assertEquals("2026-11-02T12:00:00Z", artist.recommendation?.firstDiscoveredAt)
    }

    @Test fun pagingDoesNotStopAtFilteredRowsOrInventCounts() = runTest {
        val http = ContractHttp().also { it.discoveryOverride = """{"recommendations":[{},{}]}""" }
        val page = client(http).recommendations(10, 2)
        assertTrue(page.artists.isEmpty())
        assertNull(page.totalCount)
        assertEquals(12, page.nextOffset)
        http.discoveryOverride = """{"recommendations":[],"recommendationCount":100}"""
        assertNull(client(http).recommendations(10, 2).nextOffset)
        http.discoveryOverride = """{"recommendationCount":100}"""
        assertEquals(ProviderDiscoveryStatus.Unavailable, client(http).recommendations(10, 2).status)
    }

    @Test fun catalogSearchEscapesUnicodeAndKeepsOwnershipHintsSeparateFromPlayback() = runTest {
        val http = ContractHttp()
        val client = client(http)
        val artists = client.searchCatalog(" Björk & A/B? ", ProviderCatalogScope.Artist, 24, 24)
        assertEquals(25, artists.nextOffset)
        assertEquals(73, artists.totalCount)
        assertTrue(http.urls.any { "q=Bj%C3%B6rk+%26+A%2FB%3F&scope=artist&limit=24&offset=24" in it })
        assertTrue(artists.artists.single().detailsAvailable)
        assertEquals("UK group", artists.artists.single().disambiguation)
        val album = client.searchCatalog("Release", ProviderCatalogScope.Album).albums.single()
        assertEquals(ArtistMbid, album.artistMbid?.value)
        assertEquals(true, album.inLibrary)
        assertEquals("available", album.availability)
        assertEquals(ReleaseMbid, album.release.id)
        assertTrue(http.urls.none { "/rest/" in it })
        assertEquals(listOf("login"), http.writes)
        http.albumSearchOverride = """{"scope":"album","count":1,"offset":0,"items":[{"id":null,"title":"Unresolved edition","artistName":"Artist"}]}"""
        val unresolved = client.searchCatalog("edition", ProviderCatalogScope.Album).albums.single()
        assertEquals("Unresolved edition", unresolved.release.title)
        assertNull(unresolved.release.releaseGroupMbid)
    }

    @Test fun blankQueriesAndInvalidPageSizesDoNotTouchTransport() = runTest {
        val http = ContractHttp()
        val client = client(http)
        assertEquals(0, client.searchCatalog("  ", ProviderCatalogScope.Album).totalCount)
        assertFailsWith<IllegalArgumentException> { client.recommendations(-1, 100) }
        assertFailsWith<IllegalArgumentException> { client.recommendations(0, 201) }
        assertFailsWith<IllegalArgumentException> { client.recommendations(Int.MAX_VALUE, 200) }
        assertFailsWith<IllegalArgumentException> { client.searchCatalog("x", ProviderCatalogScope.Artist, 0, 101) }
        assertTrue(http.urls.isEmpty())
    }

    @Test fun unavailableArtistMatchesRemainAnEmptyCatalogRatherThanFabricatedDiscoveryArtists() = runTest {
        val http = ContractHttp().also { it.artistSearchOverride = """{"scope":"artist","count":0,"offset":0,"items":[]}""" }
        val result = client(http).searchCatalog("Radiohead", ProviderCatalogScope.Artist)
        assertEquals(ProviderDiscoveryStatus.Ready, result.status)
        assertEquals(0, result.totalCount)
        assertTrue(result.artists.isEmpty())
        assertNull(result.nextOffset)
    }

    @Test fun releaseRecordingAndDeezerPreviewIdsNeverBecomeInterchangeable() = runTest {
        val http = ContractHttp()
        val client = client(http)
        val details = client.releaseDetails(DiscoveryReleaseGroupMbid(ReleaseMbid))
        assertEquals("Release", details.release?.title)
        assertEquals(listOf("Live"), details.release?.secondaryTypes)
        assertEquals(ArtistMbid, details.credits.single().mbid?.value)
        val tracks = client.releaseTracks(DiscoveryReleaseGroupMbid(ReleaseMbid)).tracks
        assertEquals(RecordingMbid, tracks.first().recordingMbid?.value)
        assertEquals("42", tracks.first().previewId?.value)
        assertEquals(210000L, tracks.first().durationMillis)
        assertEquals(2, tracks.first().discNumber)
        assertNull(tracks[1].recordingMbid)
        assertNull(tracks[1].previewId) // A recording ID is not a preview, even with a URL.
        assertNull(tracks[2].previewId) // A numeric ID from an unknown preview provider is not Deezer.
        http.releaseOverride = """{"id":"$ArtistMbid","title":"Wrong identity"}"""
        assertEquals(ProviderDiscoveryStatus.Unavailable, client.releaseDetails(DiscoveryReleaseGroupMbid(ReleaseMbid)).status)
    }

    @Test fun acquisitionCorrelationKeepsManagerCanonicalAndJobNamespaces() = runTest {
        val http = ContractHttp()
        val client = client(http)
        val operation = client.requestAlbum(ProviderDiscoveryArtist(ArtistMbid, "Artist"), ProviderDiscoveryRelease(ReleaseMbid, "Release"))
        assertEquals(ProviderDiscoveryOperationStatus.Queued, operation.status)
        val identity = assertNotNull(operation.identity)
        assertEquals("group-1", identity.requestGroupId?.value)
        assertEquals(listOf("job-1", "job-2"), identity.jobIds.map { it.value })
        assertEquals("71", identity.canonicalAlbumId?.value)
        assertEquals(DiscoveryManagedAlbumId(DiscoveryLibraryManager.Aurral, "7"), identity.managedAlbumId)
        val activity = client.requestActivity().items.single()
        assertEquals("completed", activity.status)
        assertEquals("group-1", activity.identity?.requestGroupId?.value)
        assertEquals("71", activity.identity?.canonicalAlbumId?.value)
        assertNull(activity.identity?.managedAlbumId) // Activity albumId has no declared manager.
        assertEquals(listOf("login", "album"), http.writes)
    }

    @Test fun unknownManagersAndAbsentCapabilitiesDoNotInventActionsOrIdentities() = runTest {
        val http = ContractHttp().also {
            it.operation = """{"success":true,"managedBy":"future","album":{"id":7},"jobId":null}"""
            it.features = listOf("discover", "downloadCancel")
        }
        val client = client(http)
        val result = client.requestAlbum(ProviderDiscoveryArtist(ArtistMbid, "Artist"), ProviderDiscoveryRelease(ReleaseMbid, "Release"))
        assertEquals(ProviderDiscoveryOperationStatus.Saved, result.status)
        assertNull(result.identity)
        assertEquals(ProviderDiscoveryStatus.Unsupported, client.requestActivity().status)
        assertTrue(http.urls.none { it.endsWith("/api/requests") })
    }

    @Test fun readFailuresRemainTypedAndDoNotAutomaticallyRetryMutations() = runTest {
        for ((code, status) in listOf(401 to ProviderDiscoveryStatus.SignInRequired,
            403 to ProviderDiscoveryStatus.Forbidden, 404 to ProviderDiscoveryStatus.Unsupported, 503 to ProviderDiscoveryStatus.Unavailable)) {
            val http = ContractHttp().also { it.failure = NavidromeHttpException(code) }
            val client = client(http)
            assertEquals(status, client.recommendations().status)
            assertEquals(status, client.searchCatalog("x", ProviderCatalogScope.Artist).status)
            val metadataStatus = if (code == 404) ProviderDiscoveryStatus.Unavailable else status
            assertEquals(metadataStatus, client.releaseTracks(DiscoveryReleaseGroupMbid(ReleaseMbid)).status)
            assertEquals(metadataStatus, client.releaseDetails(DiscoveryReleaseGroupMbid(ReleaseMbid)).status)
            assertEquals(listOf("login"), http.writes.take(1))
            assertTrue(http.writes.all { it == "login" })
        }
        val cancelled = ContractHttp().also { it.failure = CancellationException() }
        assertFailsWith<CancellationException> { client(cancelled).recommendations() }
    }

    @Test fun separateConnectionsNeverReuseAnotherAccountsSessionOrResults() = runTest {
        val http = ContractHttp()
        val one = client(http, "one")
        val two = client(http, "two")
        one.recommendations(0, 1)
        two.recommendations(0, 1)
        assertEquals(listOf("one", "two"), http.users)
        assertEquals(listOf("Bearer one", "Bearer two"), http.tokens)
    }

    private fun client(http: ContractHttp, user: String = "listener") = AurralDiscoveryClient(
        NavidromeConnection.fromPassword(providerId = ProviderIdAurral, baseUrl = "https://music.test/proxy/aurral/",
            username = user, password = "secret", customHeaders = listOf(ConnectionHeaderDefinition("X-Proxy", "value"))),
        http, nowMillis = { 0L })
}

private const val ArtistMbid = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
private const val ReleaseMbid = "b1392450-e666-3926-a536-22c65f834433"
private const val RecordingMbid = "01234567-89ab-cdef-0123-456789abcdef"

private class ContractHttp : NavidromeHttpClient {
    var features = listOf("discover", "activity", "trackDownloads")
    var logins = 0
    var newMetadata = false
    var discoveryOverride: String? = null
    var releaseOverride: String? = null
    var artistSearchOverride: String? = null
    var albumSearchOverride: String? = null
    var operation = """{"success":true,"status":"queued","managedBy":"aurral","requestGroupId":"group-1","jobIds":["job-1","job-2"],"album":{"id":7,"canonicalId":71}}"""
    var failure: Throwable? = null
    val writes = mutableListOf<String>()
    val urls = mutableListOf<String>()
    val users = mutableListOf<String>()
    val tokens = mutableListOf<String>()
    override suspend fun get(url: String) = get(url, emptyMap())
    override suspend fun get(url: String, headers: Map<String, String>): String {
        urls += url
        assertTrue(url.startsWith("https://music.test/proxy/aurral/api/"))
        assertEquals("value", headers["X-Proxy"])
        if (url.endsWith("/bootstrap")) return buildJsonObject {
            put("api", buildJsonObject { put("version", 1); put("features", JsonArray(features.map(::JsonPrimitive))) })
        }.toString()
        tokens += headers.getValue("Authorization")
        failure?.let { throw it }
        return when {
            "/discover?" in url -> discoveryOverride ?: buildJsonObject {
                val offset = url.substringAfter("offset=").toInt()
                val limit = url.substringAfter("limit=").substringBefore('&').toInt()
                put("recommendationCount", 205)
                put("recommendations", buildJsonArray {
                    repeat(minOf(limit, 205 - offset)) { index -> add(buildJsonObject {
                        val n = offset + index
                        put("id", if (n == 200) JsonNull else JsonPrimitive(if (n <= 1) ArtistMbid else "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"))
                        put("name", "Artist $n"); put("matchedTags", buildJsonArray { add("rock") })
                        put("sourceArtists", buildJsonArray { add("Seed one") })
                        put("supportingSeeds", buildJsonArray { add(buildJsonObject { put("artistName", "Seed two") }) })
                        if (newMetadata) { put("discoveryDepth", 2); put("firstDiscoveredAt", "2026-11-02T12:00:00Z") }
                    }) }
                })
                put("basedOn", buildJsonArray { add(buildJsonObject { put("name", "Seed one"); put("id", ArtistMbid) }); add(buildJsonObject { put("name", "Seed two") }) })
                put("topGenres", buildJsonArray { add("rock"); add("jazz") })
                put("topTags", buildJsonArray { add("alternative") })
            }.toString()
            "/search?" in url && "scope=artist" in url -> artistSearchOverride ?: """{"scope":"artist","count":73,"offset":24,"items":[{"type":"artist","id":"$ArtistMbid","name":"Artist","disambiguation":"UK group"}]}"""
            "/search?" in url -> albumSearchOverride ?: """{"scope":"album","count":1,"offset":0,"items":[{"id":"$ReleaseMbid","title":"Release","artistName":"Artist","artistMbid":"$ArtistMbid","inLibrary":true,"status":"available"}]}"""
            url.endsWith("/tracks") -> """[{"mbid":"$RecordingMbid","title":"Track","discNumber":2,"trackNumber":3,"length":210000,"previewProvider":"deezer","previewTrackId":"42","preview_url":"https://preview.test/42"},{"mbid":null,"title":"Unavailable","previewTrackId":"$RecordingMbid","preview_url":"https://preview.test/x"},{"title":"Future preview","previewTrackId":"43","previewProvider":"future","preview_url":"https://preview.test/43"}]"""
            "/release-group/" in url -> releaseOverride ?: """{"id":"$ReleaseMbid","title":"Release","primary-type":"Album","secondary-types":["Live"],"artist-credit":[{"name":"Artist","artist":{"id":"$ArtistMbid"}}]}"""
            url.endsWith("/requests") -> """[{"id":"activity-1","status":"completed","jobId":"job-1","requestGroupId":"group-1","canonicalAlbumId":"71","albumId":"7","albumMbid":"$ReleaseMbid"}]"""
            else -> error("Unexpected read: $url")
        }
    }
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
        if (url.endsWith("/login")) {
            logins++; writes += "login"
            val user = Json.parseToJsonElement(body).jsonObject.getValue("username").jsonPrimitive.content
            users += user
            return """{"token":"$user","expiresAt":100000,"user":{"permissions":{"addAlbum":true}}}"""
        }
        assertTrue(url.endsWith("/albums/request"))
        writes += "album"
        return operation
    }
}
