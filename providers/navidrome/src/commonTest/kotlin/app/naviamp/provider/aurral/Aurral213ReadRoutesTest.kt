package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Synthetic wire fixtures pinned to 2.13.0 (3f01fa58), with the released 2.12 fallback.
 * Sources: artists/handlers/details.ts, releaseGroup.ts, preview.ts, library/handlers/misc.ts,
 * libraryIndex.ts and routes/requests.ts. These never perform real acquisition or feedback. */
class Aurral213ReadRoutesTest {
    @Test fun releasedReadRoutesKeepMetadataPermissionsPreviewAndIdentityNamespaces() = runTest {
        for (version in listOf("2.12.0", "2.13.0")) {
            val paths = mutableListOf<String>()
            val ktor = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                paths += path
                val body = when (path) {
                    "/api/health/bootstrap" -> {
                        assertNull(request.headers[HttpHeaders.Authorization])
                        """{"appVersion":"$version","api":{"version":1,"features":["discover","trackDownloads","activity","futureFeature"]}}"""
                    }
                    "/api/auth/login" -> {
                        assertEquals(HttpMethod.Post, request.method)
                        """{"token":"session","expiresAt":9999999999999,"user":{"isAdmin":false,"permissions":{"addAlbum":true,"addArtist":false,"changeMonitoring":false}}}"""
                    }
                    else -> {
                        assertEquals(HttpMethod.Get, request.method, "Read browsing must never send an acquisition or monitoring mutation")
                        if (!path.startsWith("/rest/")) assertEquals("Bearer session", request.headers[HttpHeaders.Authorization])
                        when (path) {
                            "/api/artists/$Artist" -> {
                                assertEquals(mapOf("mode" to listOf("core")), request.url.parameters.entries().associate { it.toPair() })
                                """{"id":"$Artist","name":"Artist","bio":"Biography","genres":["rock"],"release-groups":[null,{}, {"id":"$Release","title":"Release","first-release-date":"2026-01-01","primary-type":"Album","secondary-types":["Live"],"coverUrl":null}],"futureField":{}}"""
                            }
                            "/api/discover/feedback" -> """{"feedback":[]}"""
                            "/api/library/lookup/$Artist" -> """{"exists":true,"libraryArtistId":7}"""
                            "/api/library/canonical" -> {
                                assertEquals(mapOf("kind" to "artists", "pageSize" to "1", "availableOnly" to "true", "artistId" to "7"),
                                    request.url.parameters.entries().associate { it.key to it.value.single() })
                                """{"items":[{"id":7,"mbid":"$Artist","identityKey":"mbid:$Artist"}],"total":1}"""
                            }
                            "/rest/getArtist.view" -> {
                                assertNull(request.headers[HttpHeaders.Authorization])
                                assertEquals("artist:mbid:$Artist", request.url.parameters["id"])
                                """{"subsonic-response":{"status":"ok","artist":{"id":"artist:mbid:$Artist","name":"Artist","musicBrainzId":"$Artist","album":[]}}}"""
                            }
                            "/api/library/artists/$Artist/monitoring" -> """{"added":true,"manager":"aurral","monitored":false,"monitorOption":null}"""
                            "/api/artists/$Artist/preview" -> {
                                assertEquals("Artist", request.url.parameters["artistName"])
                                """{"tracks":[null,{}, {"id":42,"title":"Track","preview_url":"/api/deezer/preview/42.mp3","album":{"title":"Release","cover_medium":null}}]}"""
                            }
                            "/api/artists/release-group/$Release" -> """{"id":"$Release","title":"Release","first-release-date":"2026-01-01","primary-type":"Album","secondary-types":["Live"],"artist-credit":[null,{"name":"Artist","artist":{"id":"$Artist","name":"Artist"}}],"genres":["rock"],"overview":"Release notes","coverUrl":null}"""
                            "/api/artists/release-group/$Release/tracks" -> """[null,{}, {"mbid":"$Recording","title":"Track","discNumber":1,"trackNumber":2,"length":123000,"releaseMbid":null,"previewProvider":"deezer","previewTrackId":"42","preview_url":"/api/deezer/preview/42.mp3"},{"mbid":null,"title":"No preview","discNumber":1,"trackNumber":3,"length":null,"previewProvider":null,"previewTrackId":null,"preview_url":null}]"""
                            "/api/requests" -> """[null,{}, {"id":"request-1","status":"future_status","albumMbid":"$Release","canonicalAlbumId":71,"albumId":7,"source":"tidal","requestGroupId":"group-1","jobIds":["job-1"]}]"""
                            else -> error("Unexpected released route $path")
                        }
                    }
                }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            })
            try {
                val provider = NavidromeProvider(NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
                    baseUrl = "https://music.test", username = "listener", password = "password"), KtorNavidromeHttpClient(ktor))
                val client = assertNotNull(provider.discovery)
                val artist = ProviderDiscoveryArtist(Artist, "Artist", detailsAvailable = true)
                val details = client.artistDetails(artist)
                assertEquals(ProviderDiscoveryStatus.Ready, details.status)
                val detail = assertNotNull(details.details)
                assertEquals("Biography", detail.biography)
                assertEquals("artist:mbid:$Artist", detail.libraryArtist?.id?.value)
                assertEquals(listOf(Release), detail.releases.map { it.id })
                assertTrue(detail.canRequestAlbums && detail.canRequestTracks)
                assertFalse(detail.canChangeMonitoring)
                assertEquals(false, detail.monitored)
                val samples = client.samples(artist)
                assertEquals(ProviderDiscoveryStatus.Ready, samples.status)
                assertEquals(listOf("42"), samples.tracks.map { it.id })
                assertEquals("Release", samples.tracks.single().albumTitle)
                val release = client.releaseDetails(DiscoveryReleaseGroupMbid(Release))
                assertEquals(ProviderDiscoveryStatus.Ready, release.status)
                assertEquals("Release notes", release.overview)
                assertEquals(Artist, release.credits.single().mbid?.value)
                val tracks = client.releaseTracks(DiscoveryReleaseGroupMbid(Release))
                assertEquals(ProviderDiscoveryStatus.Ready, tracks.status)
                assertEquals(listOf("Track", "No preview"), tracks.tracks.map { it.title })
                assertEquals(123000L, tracks.tracks.first().durationMillis)
                assertNull(tracks.tracks.last().durationMillis)
                assertEquals("42", tracks.tracks.first().previewId?.value)
                assertNull(tracks.tracks.last().previewId)
                val activity = client.requestActivity()
                assertEquals(ProviderDiscoveryStatus.Ready, activity.status)
                assertEquals("future_status", activity.items.single().status)
                assertEquals("71", activity.items.single().identity?.canonicalAlbumId?.value)
                assertNull(activity.items.single().identity?.managedAlbumId)
                assertEquals(1, paths.count { it == "/api/auth/login" })
            } finally { ktor.close() }
        }
    }
}

private const val Artist = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
private const val Release = "b1392450-e666-3926-a536-22c65f834433"
private const val Recording = "19516266-e5d9-4774-b749-812bb76a6559"
