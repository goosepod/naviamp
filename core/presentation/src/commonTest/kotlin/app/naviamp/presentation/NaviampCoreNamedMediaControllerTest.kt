package app.naviamp.presentation

import app.naviamp.domain.playback.NamedMediaKind
import app.naviamp.domain.playback.NamedMediaRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NaviampCoreNamedMediaControllerTest {
    @Test fun namedRequestsUseProviderCatalogAndDistinctActions() = runTest {
        val provider = FakeCoreMediaProvider(supportsArtistRadio = true)
        val played = mutableListOf<NaviampNamedMediaSelection>()
        val controller = NaviampCoreNamedMediaController(
            currentProvider = { provider },
            isCurrent = { it === provider },
            playback = NaviampNamedMediaPlayback { played += it; true },
        )
        for ((kind, name) in listOf(
            NamedMediaKind.ArtistRadio to "Core Artist",
            NamedMediaKind.Artist to "Core Artist",
            NamedMediaKind.Album to "Core Album",
            NamedMediaKind.Playlist to "Core Playlist",
        )) {
            assertEquals(NaviampNamedMediaStatus.Started, controller.play(NamedMediaRequest(kind, name)).status)
        }
        assertIs<NaviampNamedMediaSelection.ArtistRadio>(played[0])
        assertIs<NaviampNamedMediaSelection.ArtistCatalog>(played[1])
        assertIs<NaviampNamedMediaSelection.AlbumCatalog>(played[2])
        assertIs<NaviampNamedMediaSelection.PlaylistCatalog>(played[3])
    }

    @Test fun unresolvedRequestsDoNotStartPlayback() = runTest {
        val provider = FakeCoreMediaProvider()
        var starts = 0
        val controller = NaviampCoreNamedMediaController(
            currentProvider = { provider },
            isCurrent = { true },
            playback = NaviampNamedMediaPlayback { starts++; true },
        )
        assertEquals(NaviampNamedMediaStatus.NoMatch,
            controller.play(NamedMediaRequest(NamedMediaKind.Album, "Unknown")).status)
        assertEquals(NaviampNamedMediaStatus.Unsupported,
            controller.play(NamedMediaRequest(NamedMediaKind.ArtistRadio, "Core Artist")).status)
        assertEquals(0, starts)
    }

    @Test fun sourceChangePreventsStalePlayback() = runTest {
        val provider = FakeCoreMediaProvider()
        var starts = 0
        val controller = NaviampCoreNamedMediaController(
            currentProvider = { provider },
            isCurrent = { false },
            playback = NaviampNamedMediaPlayback { starts++; true },
        )
        assertEquals(NaviampNamedMediaStatus.SourceChanged,
            controller.play(NamedMediaRequest(NamedMediaKind.Album, "Core Album")).status)
        assertEquals(0, starts)
    }

    @Test fun missingSourceAndRejectedPlaybackReportFailure() = runTest {
        val provider = FakeCoreMediaProvider()
        val noSource = NaviampCoreNamedMediaController(
            currentProvider = { null },
            isCurrent = { false },
            playback = NaviampNamedMediaPlayback { error("Must not play") },
        )
        assertEquals(NaviampNamedMediaStatus.NoSource,
            noSource.play(NamedMediaRequest(NamedMediaKind.Artist, "Core Artist")).status)
        val rejected = NaviampCoreNamedMediaController(
            currentProvider = { provider },
            isCurrent = { true },
            playback = NaviampNamedMediaPlayback { false },
        )
        assertEquals(NaviampNamedMediaStatus.Failed,
            rejected.play(NamedMediaRequest(NamedMediaKind.Artist, "Core Artist")).status)
    }
}
