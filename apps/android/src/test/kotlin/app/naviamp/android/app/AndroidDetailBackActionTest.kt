package app.naviamp.android

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.provider.PendingProviderAction
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.ui.NaviampVisualizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidDetailBackActionTest {
    @Test
    fun albumUiBackClearsTheActiveDetail() {
        val state = appState().apply {
            contentState = contentState.showAlbum(
                AlbumDetails(
                    album = Album(AlbumId("album"), "Album", "Artist", null, null),
                    tracks = emptyList(),
                ),
            )
        }
        val navigation = AndroidNavigationController(state) { _, _, _ -> }
        state.sharedNavigationController.recordAlbumDetailOpened()

        androidDetailBackAction(navigation)()

        assertNull(state.albumDetail)
    }

    @Test
    fun artistUiBackUsesTheExistingNestedArtistHistory() {
        val previous = Artist(ArtistId("previous"), "Previous")
        val state = appState().apply {
            contentState = contentState.showArtist(
                ArtistDetails(Artist(ArtistId("current"), "Current"), emptyList()),
            )
        }
        state.sharedNavigationController.recordArtistDetailOpened(previous)
        state.navigationState = state.navigationState.copy(route = NaviampRoute.ArtistDetail)
        state.sharedNavigationController.recordArtistDetailOpened(
            state.artistDetail!!.artist,
            continuingArtistDetail = true,
        )
        var reopened: Triple<ArtistId, String?, Boolean>? = null
        val navigation = AndroidNavigationController(state) { id, name, pushCurrent ->
            reopened = Triple(id, name, pushCurrent)
        }

        androidDetailBackAction(navigation)()

        assertEquals(Triple(previous.id, previous.name, false), reopened)
    }

    private fun appState() = AndroidAppState(
        savedConnection = ConnectionFormState(),
        savedInterfaceSettings = InterfaceSettings(),
        savedPlaybackSettings = PlaybackSettings(),
        savedCacheSettings = CacheSettings(),
        pendingProviderActions = EmptyDetailPendingProviderActions,
        savedSourceId = "source",
        initialSavedMediaSources = emptyList(),
        initialSavedConnectionForLogin = null,
        initialStorageStats = StorageCacheStats(),
        initialOpenNowPlayingRequest = 0,
        initialAutoPlayMediaIdRequest = null,
        initialAutoCommandRequest = null,
        initialSelectedVisualizer = NaviampVisualizer.SpectrumBars,
    )
}

private object EmptyDetailPendingProviderActions : PendingProviderActionRepository {
    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) = Unit

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> = emptyList()
    override fun deletePendingProviderAction(id: Long) = Unit
    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) = Unit
}
