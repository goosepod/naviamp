package app.naviamp.presentation

import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampLibraryScreenUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampShellChromeUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampCoreDiagnosticsTest {
    @Test
    fun sharedDiagnosticsOwnAllProductSectionsAndMergeNarrowHostFacts() {
        val diagnostics = naviampCoreDiagnostics(
            shell = NaviampAppShellUiState(
                shellChrome = NaviampShellChromeUi(selectedRoute = SharedRoute.Library),
                library = NaviampLibraryScreenUi(
                    artists = listOf(SharedMediaItemUi("artist", "Artist", "")),
                    query = "art",
                    syncStatus = NaviampLibrarySyncStatusUi("Indexed", isSyncing = false),
                ),
            ),
            provider = FakeCoreMediaProvider(supportsSonicSimilarity = true),
            sidecars = NaviampCoreNowPlayingSidecars(),
            playbackEngineRows = listOf("BASS load state" to "Loaded"),
            external = NaviampCoreDiagnosticsSnapshot(
                platformRows = listOf("OS" to "Test OS"),
                storage = StorageCacheStats(
                    databaseLabel = "naviamp.db",
                    libraryArtistCount = 12,
                    libraryAlbumCount = 34,
                    libraryTrackCount = 56,
                    lyricsCount = 7,
                ),
            ),
        )

        val byTitle = diagnostics.sections.associateBy { it.title }
        assertEquals("Test OS", byTitle.getValue("Application").rows.toMap()["OS"])
        assertEquals("12", byTitle.getValue("Library").rows.toMap()["Indexed artists"])
        assertEquals("Loaded", byTitle.getValue("Playback engine").rows.toMap()["BASS load state"])
        assertEquals("7 (0 B)", byTitle.getValue("Storage").rows.toMap()["Lyrics"])
        assertTrue("Provider features" in byTitle)
        assertTrue("Track sidecars" in byTitle)
    }
}
