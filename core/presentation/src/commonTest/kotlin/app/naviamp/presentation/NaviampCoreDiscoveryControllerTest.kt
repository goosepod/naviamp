package app.naviamp.presentation

import app.naviamp.domain.ProviderId
import app.naviamp.domain.provider.*
import app.naviamp.domain.media.AlbumReleaseSection
import app.naviamp.ui.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreDiscoveryControllerTest {
    @Test fun relatedArtistsAndReleasesReturnToTheSameArtistViewportAndFilters() = runTest {
        val h = DiscoveryHarness()
        h.open(First)
        h.controller.dispatch(NaviampCoreCommand.Home.DiscoveryViewport(3, 17, AlbumReleaseSection.Live))
        h.open(Second)
        h.open(Third)
        assertEquals(Third.id, h.detail().artist.id)
        h.back()
        assertEquals(Second.id, h.detail().artist.id)
        h.back()
        assertEquals(First.id, h.detail().artist.id)
        assertEquals(3, h.detail().firstVisibleItem)
        assertEquals(17, h.detail().firstVisibleOffset)
        assertEquals(AlbumReleaseSection.Live, h.detail().releaseSection)
        val release = NaviampCoreCommand.Home.SelectDiscoveryRelease(Release.id)
        h.controller.dispatch(release); h.controller.execute(release)
        assertEquals(ProviderDiscoveryStatus.Ready, h.discovery().releasePage?.result?.status)
        h.back()
        assertNull(h.discovery().releasePage)
        assertEquals(17, h.detail().firstVisibleOffset)
        h.back()
        assertNull(h.discovery().artistDetail)
        assertEquals(listOf(SharedRoute.Search), h.returned)
        assertTrue(h.stops >= 5)
    }

    @Test fun metadataFailureKeepsSamplesAndRelatedArtistsIndependent() = runTest {
        val native = object : DiscoveryNative() {
            override suspend fun artistDetails(artist: ProviderDiscoveryArtist) = ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Unavailable)
        }
        val h = DiscoveryHarness(native)
        h.open(First)
        assertEquals(ProviderDiscoveryStatus.Unavailable, h.detail().status)
        assertEquals(listOf("42"), h.detail().samples.map { it.id })
        assertEquals(Second, h.detail().similar?.artists?.first())
        assertFalse(h.detail().samplesLoading)
        assertFalse(h.detail().similarLoading)
        h.controller.execute(NaviampCoreCommand.Home.OpenDiscoveryLibraryArtist(First.id))
        assertEquals(0, h.libraryOpens)
    }

    @Test fun aSlowSampleSectionDoesNotBlockSimilarArtists() = runTest {
        val waiting = CompletableDeferred<ProviderDiscoverySamples>()
        val native = object : DiscoveryNative() {
            override suspend fun samples(artist: ProviderDiscoveryArtist) = waiting.await()
        }
        val h = DiscoveryHarness(native)
        val opening = launch { h.open(First) }
        runCurrent()
        assertTrue(h.detail().samplesLoading)
        assertEquals(Second, h.detail().similar?.artists?.first())
        waiting.complete(ProviderDiscoverySamples())
        opening.join()
    }

    @Test fun staleAccountResultsAndDeferredCommandsCannotReopenAnOldDestination() = runTest {
        val waiting = CompletableDeferred<ProviderDiscoveryArtistResult>()
        val native = object : DiscoveryNative() {
            override suspend fun artistDetails(artist: ProviderDiscoveryArtist) = waiting.await()
        }
        val h = DiscoveryHarness(native)
        val pending = NaviampCoreCommand.Home.SelectDiscoveryArtist(First.id)
        h.controller.dispatch(pending)
        val loading = launch { h.controller.execute(pending) }
        runCurrent()
        h.account = "account-two"
        h.active = discoveryProvider(DiscoveryNative(), h.account)
        val oldBrowse = NaviampCoreCommand.Home.BrowseDiscoveryArtist("account-one", First)
        h.controller.dispatch(oldBrowse); h.controller.execute(oldBrowse)
        waiting.complete(ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Ready, ProviderDiscoveryArtistDetails(First)))
        loading.join()
        assertNull(h.discovery().artistDetail)
        assertTrue(h.discovery().artistBackStack.isEmpty())
    }

    @Test fun refreshRetainsViewStateAndInvalidMusicBrainzIdsCannotOpenPages() = runTest {
        val h = DiscoveryHarness()
        h.open(First)
        h.controller.dispatch(NaviampCoreCommand.Home.DiscoveryViewport(2, 11, AlbumReleaseSection.Live))
        h.open(First)
        assertEquals(2, h.detail().firstVisibleItem)
        assertEquals(11, h.detail().firstVisibleOffset)
        val invalid = NaviampCoreCommand.Home.BrowseDiscoveryArtist(h.account,
            ProviderDiscoveryArtist("native:7", "Same name", detailsAvailable = true))
        h.controller.dispatch(invalid); h.controller.execute(invalid)
        assertEquals(First.id, h.detail().artist.id)
        assertTrue(h.discovery().artistBackStack.isEmpty())
    }

    @Test fun leavingAnUncertainMutationPreventsDuplicateRequestsAfterBack() = runTest {
        val waiting = CompletableDeferred<ProviderDiscoveryOperation>()
        var writes = 0
        val native = object : DiscoveryNative() {
            override suspend fun requestAlbum(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease): ProviderDiscoveryOperation {
                writes++; return waiting.await()
            }
        }
        val h = DiscoveryHarness(native)
        h.open(First)
        val action = ProviderDiscoveryAction.RequestAlbum(Release.id)
        val request = NaviampCoreCommand.Home.DiscoveryAction(First.id, action)
        h.controller.dispatch(request)
        val loading = launch { h.controller.execute(request) }
        runCurrent()
        h.open(Second)
        h.back()
        waiting.complete(ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Queued))
        loading.join()
        assertFalse(h.detail().actionBusy)
        assertEquals(ProviderDiscoveryOperationStatus.Uncertain, h.detail().operationStatus)
        assertTrue(action in h.detail().blockedActions)
        assertTrue(h.controller.dispatch(NaviampCoreCommand.Home.DiscoveryAction(First.id, action)) is NaviampCoreImmediateCommandResult.Handled)
        assertEquals(1, writes)
    }

    @Test fun recreatedProviderDecoratorsDoNotEraseTheAccountNavigationStack() = runTest {
        val h = DiscoveryHarness()
        h.open(First)
        h.active = discoveryProvider(DiscoveryNative(), h.account)
        h.open(Second)
        h.back()
        assertEquals(First.id, h.detail().artist.id)
    }

    @Test fun backDoesNotLeaveAnAbandonedSampleLoadSpinningForever() = runTest {
        val waiting = CompletableDeferred<ProviderDiscoverySamples>()
        val native = object : DiscoveryNative() {
            override suspend fun samples(artist: ProviderDiscoveryArtist) = if (artist.id == First.id) waiting.await()
                else ProviderDiscoverySamples(ProviderDiscoveryStatus.Ready)
        }
        val h = DiscoveryHarness(native)
        val loading = launch { h.open(First) }
        runCurrent()
        h.open(Second)
        h.back()
        assertFalse(h.detail().samplesLoading)
        assertEquals(ProviderDiscoveryStatus.Unavailable, h.detail().samplesStatus)
        waiting.complete(ProviderDiscoverySamples(ProviderDiscoveryStatus.Ready))
        loading.join()
        assertEquals(ProviderDiscoveryStatus.Unavailable, h.detail().samplesStatus)
    }

    @Test fun leavingAnUncertainFeedbackToggleRequiresReconciliationBeforeAnotherToggle() = runTest {
        val waiting = CompletableDeferred<ProviderDiscoveryFeedbackResult>()
        val native = object : DiscoveryNative() {
            override suspend fun artistDetails(artist: ProviderDiscoveryArtist) = ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Ready,
                ProviderDiscoveryArtistDetails(artist, feedbackAvailable = true))
            override suspend fun toggleFeedback(artist: ProviderDiscoveryArtist, action: ProviderDiscoveryFeedbackAction) = waiting.await()
        }
        val h = DiscoveryHarness(native)
        h.open(First)
        val feedback = NaviampCoreCommand.Home.DiscoveryFeedback(First.id, ProviderDiscoveryFeedbackAction.MoreLikeThis)
        h.controller.dispatch(feedback)
        val loading = launch { h.controller.execute(feedback) }
        runCurrent()
        h.open(Second)
        h.back()
        assertFalse(h.detail().savingFeedback)
        assertTrue(h.detail().feedbackFailed)
        assertFalse(h.detail().details!!.feedbackAvailable)
        assertTrue(h.controller.dispatch(feedback) is NaviampCoreImmediateCommandResult.Handled)
        waiting.complete(ProviderDiscoveryFeedbackResult(ProviderDiscoveryStatus.Ready))
        loading.join()
    }
}

private val First = ProviderDiscoveryArtist("11111111-1111-1111-1111-111111111111", "First", detailsAvailable = true)
private val Second = ProviderDiscoveryArtist("22222222-2222-2222-2222-222222222222", "Second", detailsAvailable = true)
private val Third = ProviderDiscoveryArtist("33333333-3333-3333-3333-333333333333", "Third", detailsAvailable = true)
private val Release = ProviderDiscoveryRelease("44444444-4444-4444-4444-444444444444", "Release", type = "Album")
private open class DiscoveryNative : ProviderDiscovery {
    override suspend fun load() = ProviderDiscoveryFeed(trending = listOf(First))
    override suspend fun artistDetails(artist: ProviderDiscoveryArtist) = ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Ready,
        ProviderDiscoveryArtistDetails(artist, releases = listOf(Release), canRequestAlbums = true))
    override suspend fun samples(artist: ProviderDiscoveryArtist) = ProviderDiscoverySamples(ProviderDiscoveryStatus.Ready,
        listOf(ProviderDiscoverySample("42", "Sample")))
    override suspend fun similarArtists(artist: DiscoveryArtistMbid) = ProviderDiscoverySimilarArtists(ProviderDiscoveryStatus.Ready,
        if (artist.value == First.id) listOf(Second) else listOf(Third))
    override suspend fun releaseDetails(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Ready, Release)
}
private fun discoveryProvider(native: ProviderDiscovery, account: String) = object : MediaProvider by FakeCoreMediaProvider() {
    override val id = ProviderId("aurral")
    override val cacheNamespace = account
    override val discovery = native
}
private class DiscoveryHarness(native: ProviderDiscovery = DiscoveryNative()) {
    var account = "account-one"
    var active: MediaProvider = discoveryProvider(native, account)
    val store = NaviampCoreStateStore()
    var stops = 0
    var libraryOpens = 0
    val returned = mutableListOf<SharedRoute>()
    init { store.updateShell { it.copy(home = it.home.copy(providerDiscovery = SharedProviderDiscoveryUi("aurral",
        ProviderDiscoveryFeed(trending = listOf(First)))), shellChrome = it.shellChrome.copy(selectedRoute = SharedRoute.Search)) } }
    val controller = NaviampCoreDiscoveryController(store, { active }, { account }, {}, {}, { stops++ }, { libraryOpens++ }, {},
        restoreRoute = { returned += it })
    fun discovery() = store.state.value.shell.home.providerDiscovery!!
    fun detail() = discovery().artistDetail!!
    suspend fun open(artist: ProviderDiscoveryArtist) {
        val command = NaviampCoreCommand.Home.SelectDiscoveryArtist(artist.id)
        controller.dispatch(command); controller.execute(command)
    }
    suspend fun back() {
        controller.dispatch(NaviampCoreCommand.Home.DismissDiscoveryArtist)
        controller.execute(NaviampCoreCommand.Home.DismissDiscoveryArtist)
    }
}
