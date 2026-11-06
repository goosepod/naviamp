package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AurralReleaseOwnershipTest {
    @Test fun partialOwnershipKeepsCanonicalAndManagedNamespacesAndValidatesThePlaybackBridge() = runTest {
        val http = OwnershipHttp()
        val ownership = ownershipProvider(http).discovery!!.releaseOwnership(DiscoveryReleaseGroupMbid(Group))
        assertEquals(ProviderDiscoveryStatus.Ready, ownership.status)
        assertEquals(ProviderDiscoveryReleaseAvailability.Partial, ownership.availability)
        assertEquals(DiscoveryCanonicalAlbumId("71"), ownership.canonicalAlbumId)
        assertEquals(DiscoveryManagedAlbumId(DiscoveryLibraryManager.Aurral, "7"), ownership.managedAlbumId)
        assertEquals(setOf(DiscoveryRecordingMbid(Recording)), ownership.ownedRecordings)
        assertEquals("album:mbid:$Group", ownership.libraryAlbum?.id?.value)
        assertEquals(1, http.lookupReads)
        assertEquals(1, http.restReads)
    }

    @Test fun cataloguedWithoutFilesAndMissingCanonicalIdentityDoNotProducePlayableLinks() = runTest {
        for (canonical in listOf(true, false)) {
            val http = OwnershipHttp().also { if (canonical) it.files = 0 else it.canonical = false }
            val ownership = ownershipProvider(http).discovery!!.releaseOwnership(DiscoveryReleaseGroupMbid(Group))
            assertNull(ownership.libraryAlbum)
            assertEquals(0, http.restReads)
            if (canonical) assertEquals(ProviderDiscoveryReleaseAvailability.Catalogued, ownership.availability)
        }
    }

    @Test fun mismatchedCanonicalOrSubsonicIdentityAndAbsentManagerAreNeverGuessed() = runTest {
        for (canonical in listOf(true, false)) {
            val http = OwnershipHttp().also { it.manager = false; if (canonical) it.canonicalMbid = Other else it.restMbid = Other }
            val ownership = ownershipProvider(http).discovery!!.releaseOwnership(DiscoveryReleaseGroupMbid(Group))
            assertNull(ownership.libraryAlbum)
            assertNull(ownership.managedAlbumId)
            assertEquals(DiscoveryCanonicalAlbumId("71"), ownership.canonicalAlbumId)
        }
    }

    @Test fun availableAndAbsentOwnershipAreDistinct() = runTest {
        val full = OwnershipHttp().also { it.count = 1; it.state = "available" }
        assertEquals(ProviderDiscoveryReleaseAvailability.Available,
            ownershipProvider(full).discovery!!.releaseOwnership(DiscoveryReleaseGroupMbid(Group)).availability)
        val absent = OwnershipHttp().also { it.absent = true }
        assertEquals(ProviderDiscoveryReleaseAvailability.NotOwned,
            ownershipProvider(absent).discovery!!.releaseOwnership(DiscoveryReleaseGroupMbid(Group)).availability)
        assertEquals(0, absent.restReads)
    }
}

private const val Group = "33333333-3333-3333-3333-333333333333"
private const val Recording = "55555555-5555-5555-5555-555555555555"
private const val Other = "66666666-6666-6666-6666-666666666666"
private fun ownershipProvider(http: OwnershipHttp) = NavidromeProvider(NavidromeConnection.fromPassword(
    providerId = ProviderIdAurral, baseUrl = "https://aurral.test", username = "test", password = "fixture"), http)

private class OwnershipHttp : NavidromeHttpClient {
    var files = 1
    var count = 2
    var state = "partial"
    var canonical = true
    var manager = true
    var absent = false
    var canonicalMbid = Group
    var restMbid = Group
    var lookupReads = 0
    var restReads = 0
    override suspend fun get(url: String) = get(url, emptyMap())
    override suspend fun get(url: String, headers: Map<String, String>): String {
        if (url.contains("/rest/getAlbum.view?")) {
            restReads++
            return """{"subsonic-response":{"status":"ok","version":"1.16.1","album":{"id":"album:mbid:$Group","name":"Owned","artist":"Artist","musicBrainzId":"$restMbid","songCount":1,"song":[{"id":"track:fixture","title":"Track"}]}}}"""
        }
        if (url.endsWith("/bootstrap")) return """{"api":{"version":1,"features":["discover"]}}"""
        assertTrue(url.contains("/api/library/canonical?kind=albums") && "albumId=71" in url)
        return """{"items":[{"id":71,"mbid":"$canonicalMbid","identityKey":"mbid:$Group"}]}"""
    }
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
        if (url.endsWith("/login")) return """{"token":"fixture","expiresAt":9999999999999,"user":{"permissions":{"addAlbum":true}}}"""
        assertTrue(url.endsWith("/api/library/albums/lookup/batch"))
        lookupReads++
        if (absent) return "{}"
        return """{"$Group":{"inLibrary":true,"canonicalAlbumId":${if (canonical) "71" else "null"},"libraryAlbumId":7,"managedBy":${if (manager) "\"aurral\"" else "null"},"status":"$state","trackCount":$count,"trackFileCount":$files,"ownedTrackMbids":["$Recording","invalid"]}}"""
    }
}
