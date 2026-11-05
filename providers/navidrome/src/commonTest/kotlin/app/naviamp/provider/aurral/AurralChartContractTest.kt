package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Synthetic v2.13 wire excerpts; no server writes. */
class AurralChartContractTest {
    @Test fun chartsRetainNullableSectionsRanksAndOnlyValidatedPreviewPaths() = runTest {
        val http = ChartHttp()
        val charts = chartClient(http).charts()
        assertEquals(ProviderDiscoveryStatus.Ready, charts.status)
        assertNull(charts.albums)
        assertEquals("US", charts.region)
        assertNull(charts.songs!!.first().movement)
        assertEquals(DiscoveryDeezerPreviewId("42"), charts.songs!!.first().previewId)
        assertNull(charts.songs!![1].previewId, "A chart key does not establish a preview identity")
        assertNull(charts.songs!![2].previewId, "External paths never receive the native session")
        assertEquals(listOf(1, null, 2), charts.spotlight?.ranks)
        assertNull(charts.artists!!.single().artist.artistMbid)
        assertTrue(charts.canSave)
        assertEquals(0, http.saves)
        assertEquals("Rock", chartClient(http).genreChart("152").name)
    }
    @Test fun absentCapabilityNeverCallsNewRoutesAndMissingGenresAreUnavailable() = runTest {
        val http = ChartHttp().apply { features = "\"discover\"" }
        assertEquals(ProviderDiscoveryStatus.Unsupported, chartClient(http).charts().status)
        assertEquals(ProviderDiscoveryStatus.Unsupported, chartClient(http).saveChartPlaylist("55").status)
        assertEquals(0, http.chartReads); assertEquals(0, http.saves)
        http.features = "\"discover\",\"trending\",\"flows\""; http.missingGenre = true
        assertEquals(ProviderDiscoveryStatus.Unavailable, chartClient(http).genreChart("152").status)
        assertFailsWith<IllegalArgumentException> { chartClient(http).genreChart("../bad") }
    }
    @Test fun chartSaveChecksPermissionReconcilesExistingAndNeverReplaysLostResponses() = runTest {
        val http = ChartHttp().apply { accessFlow = false }
        assertEquals(ProviderDiscoveryOperationStatus.Forbidden, chartClient(http).saveChartPlaylist("55").status)
        assertEquals(0, http.saves)
        http.accessFlow = true; http.saved = true
        assertEquals("library-playlist", chartClient(http).saveChartPlaylist("55").playlistId)
        assertEquals(0, http.saves)
        http.saved = false; http.loseSave = true
        assertEquals(ProviderDiscoveryOperationStatus.Uncertain, chartClient(http).saveChartPlaylist("55").status)
        assertEquals(1, http.saves)
        http.loseSave = false
        assertEquals(ProviderDiscoveryOperationStatus.Saved, chartClient(http).saveChartPlaylist("55").status)
        assertEquals(2, http.saves, "A separate explicit client invocation is not an automatic replay")
    }
}

private fun chartClient(http: ChartHttp) = AurralDiscoveryClient(NavidromeConnection.fromPassword(
    providerId = ProviderIdAurral, baseUrl = "https://music.test/proxy", username = "listener", password = "synthetic"), http, nowMillis = { 0L })
private class ChartHttp : NavidromeHttpClient {
    var features = "\"discover\",\"trending\",\"flows\""
    var accessFlow = true
    var saves = 0; var chartReads = 0; var saved = false; var loseSave = false; var missingGenre = false
    override suspend fun get(url: String) = get(url, emptyMap())
    override suspend fun get(url: String, headers: Map<String, String>): String = when {
        url.endsWith("/bootstrap") -> """{"api":{"version":1,"features":[$features]}}"""
        "/trending/genres/" in url -> {
            assertEquals("Bearer session", headers["Authorization"])
            if (missingGenre) throw NavidromeHttpException(404)
            """{"id":152,"name":"Rock","region":"US","updatedAt":42,"items":[]}"""
        }
        url.endsWith("/trending") -> {
            chartReads++; assertEquals("Bearer session", headers["Authorization"])
            """{"region":"US","updatedAt":42,"songs":{"playlistId":"55","libraryPlaylistId":${if (saved) "\"library-playlist\"" else "null"},"items":[{"key":"42","trackName":"Song","artistName":"Artist","position":1,"movement":null,"preview_url":"/api/deezer/preview/42.mp3"},{"key":"opaque","trackName":"Unknown","artistName":"Artist","preview_url":"/api/deezer/preview/opaque.mp3"},{"key":"43","trackName":"External","artistName":"Artist","preview_url":"https://foreign.test/api/deezer/preview/43.mp3"}],"spotlight":{"ranks":[1,null,2],"daysAtTop":1}},"albums":null,"artists":[{"key":"123","name":"Unmatched"}],"genres":[{"id":152,"name":"Rock"}]}"""
        }
        else -> error("Unexpected fixture read")
    }
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String = when {
        url.endsWith("/login") -> """{"token":"session","expiresAt":100000,"user":{"permissions":{"accessFlow":$accessFlow}}}"""
        url.endsWith("/editorial/55/library") -> { saves++; if (loseSave) error("Lost response"); """{"playlistId":"library-playlist","alreadyAdded":false,"tracksQueued":100}""" }
        else -> error("Unexpected fixture write")
    }
}
