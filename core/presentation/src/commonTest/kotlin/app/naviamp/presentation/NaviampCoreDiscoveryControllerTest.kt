package app.naviamp.presentation

import app.naviamp.domain.ProviderId
import app.naviamp.domain.provider.*
import app.naviamp.domain.media.AlbumReleaseSection
import app.naviamp.ui.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreDiscoveryControllerTest {
    @Test fun refreshedCompanionCapabilitiesSurviveTheFirstHubOpenAfterAnExplicitAccountReset() = runTest {
        val h = DiscoveryHarness()
        h.active = object : MediaProvider by h.active { override val discoveryAccountScope = "new-companion" }
        h.controller.resetForSourceChange()
        val feed = ProviderDiscoveryFeed(ProviderDiscoveryStatus.Ready, trending = listOf(First), chartsAvailable = true, genreBlockingAvailable = true)
        h.store.updateShell { it.copy(home = it.home.copy(providerDiscovery = h.discovery().copy(feed = feed))) }
        val hub = NaviampCoreCommand.Home.Browse(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Hub))
        h.controller.dispatch(hub); h.controller.execute(hub)
        assertEquals(feed, h.discovery().feed)
        assertEquals(DiscoveryBrowsePage.Hub, h.discovery().browser!!.page)
    }

    @Test fun changingOnlyTheCompanionAccountRejectsItsLateReadWhileKeepingThePrimaryIdentity() = runTest {
        val waiting = CompletableDeferred<ProviderDiscoveryArtistResult>()
        val native = object : DiscoveryNative() {
            override suspend fun artistDetails(artist: ProviderDiscoveryArtist): ProviderDiscoveryArtistResult =
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { waiting.await() }
        }
        val h = DiscoveryHarness(native)
        val primaryNamespace = h.active.cacheNamespace
        val command = NaviampCoreCommand.Home.SelectDiscoveryArtist(First.id)
        h.controller.dispatch(command)
        val reading = launch { h.controller.execute(command) }
        runCurrent()
        h.active = object : MediaProvider by h.active {
            override val discoveryAccountScope = "other-companion"
            override val discovery = DiscoveryNative()
        }
        h.controller.dispatch(NaviampCoreCommand.Home.ShowDiscoveryRequests)
        waiting.complete(ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Ready,
            ProviderDiscoveryArtistDetails(First)))
        reading.join()
        assertEquals(primaryNamespace, h.active.cacheNamespace)
        assertNull(h.discovery().artistDetail)
        assertTrue(h.discovery().feed.trending.isEmpty())
        assertFalse(h.discovery().feed.chartsAvailable)
    }

    @Test fun radioContextSearchIsReadOnlyAndRejectsAStaleSourceOrMetadata() = runTest {
        val searches = mutableListOf<String>()
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun searchCatalog(query: String, scope: ProviderCatalogScope, offset: Int, limit: Int): ProviderCatalogPage {
                searches += query
                return ProviderCatalogPage(ProviderDiscoveryStatus.Ready, artists = listOf(First))
            }
            override suspend fun requestTrack(artist: ProviderDiscoveryArtist, sample: ProviderDiscoverySample): ProviderDiscoveryOperation =
                error("A context search must never acquire a recording")
            override suspend fun setMonitoring(artist: ProviderDiscoveryArtist, enabled: Boolean): ProviderDiscoveryOperation =
                error("A context search must never start monitoring")
        })
        val feed = ProviderDiscoveryFeed(ProviderDiscoveryStatus.Ready, trending = listOf(First), chartsAvailable = true, genreBlockingAvailable = true)
        h.store.updateShell { it.copy(home = it.home.copy(providerDiscovery = h.discovery().copy(feed = feed))) }
        h.store.updateShell { it.copy(nowPlaying = NowPlayingUi(id = "station", title = "Station", subtitle = "", stateLabel = "Playing",
            isLive = true, aurralSourceId = h.account, aurralRadioMetadata = "Artist - Recording")) }
        val old = NaviampCoreCommand.Home.FindInAurral(h.account, radioId = "station", radioMetadata = "Old recording")
        h.controller.dispatch(old); h.controller.execute(old)
        assertTrue(searches.isEmpty())
        val command = NaviampCoreCommand.Home.FindInAurral(h.account, radioId = "station", radioMetadata = "Artist - Recording")
        assertEquals(NaviampCoreImmediateCommandResult.Deferred, h.controller.dispatch(command))
        h.controller.execute(command)
        assertEquals(listOf("Artist"), searches)
        assertEquals("Artist - Recording", h.discovery().contextQuery)
        assertEquals(DiscoveryBrowsePage.Search, h.discovery().browser!!.page)
        assertEquals(feed, h.discovery().feed)
        val close = NaviampCoreCommand.Home.Browse(DiscoveryBrowseAction.Close)
        h.controller.dispatch(close); h.controller.execute(close)
        val hub = NaviampCoreCommand.Home.Browse(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Hub))
        h.controller.dispatch(hub); h.controller.execute(hub)
        assertEquals(DiscoveryBrowsePage.Hub, h.discovery().browser!!.page)
        assertTrue(h.discovery().feed.chartsAvailable)
        assertTrue(h.discovery().feed.genreBlockingAvailable)
        assertEquals(listOf(First), h.discovery().feed.trending)
        assertTrue(h.played.isEmpty())
        h.account = "another-account"
        h.controller.dispatch(command); h.controller.execute(command)
        assertEquals(listOf("Artist"), searches)
    }

    @Test fun hubNavigationDoesNotReadRecommendationsAndBackRestoresItsViewport() = runTest {
        var reads = 0
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun recommendations(offset: Int, limit: Int): ProviderRecommendationPage {
                reads++
                return ProviderRecommendationPage(ProviderDiscoveryStatus.Ready, listOf(First), offset, totalCount = 1)
            }
        })
        suspend fun send(action: DiscoveryBrowseAction) { val c = NaviampCoreCommand.Home.Browse(action); h.controller.dispatch(c); h.controller.execute(c) }
        send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Hub))
        assertEquals(0, reads)
        assertFalse(h.discovery().browser!!.loading)
        send(DiscoveryBrowseAction.Viewport(2, 37))
        send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Recommendations))
        assertEquals(1, reads)
        assertEquals(0, h.discovery().browser!!.firstVisibleItem)
        send(DiscoveryBrowseAction.Close)
        assertEquals(DiscoveryBrowsePage.Hub, h.discovery().browser!!.page)
        assertEquals(2, h.discovery().browser!!.firstVisibleItem)
        assertEquals(37, h.discovery().browser!!.firstVisibleOffset)
        send(DiscoveryBrowseAction.Close)
        assertNull(h.discovery().browser)
    }

    @Test fun recreatedProviderWrappersDoNotDiscardValidDiscoveryReads() = runTest {
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun recommendations(offset: Int, limit: Int) = ProviderRecommendationPage(
                ProviderDiscoveryStatus.Ready, listOf(First), offset, totalCount = 1)
        }, decorateProviders = true)
        val command = NaviampCoreCommand.Home.Browse(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Recommendations))
        h.controller.dispatch(command); h.controller.execute(command)
        assertEquals(listOf(First), h.discovery().browser!!.recommendations)
        assertFalse(h.discovery().browser!!.loading)
    }

    @Test fun genreFiltersNeverBlockAndUncertainBlocksReconcileBeforeAnotherMutation() = runTest {
        var mutations = 0; var blocked = false
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun blockedGenres() = ProviderDiscoveryBlockedGenres(ProviderDiscoveryStatus.Ready, if (blocked) listOf("rock") else emptyList())
            override suspend fun recommendations(offset: Int, limit: Int) = ProviderRecommendationPage(ProviderDiscoveryStatus.Ready,
                listOf(First), offset, genres = listOf("rock"))
            override suspend fun setGenreBlocked(tag: String, value: Boolean): ProviderDiscoveryGenreMutation {
                mutations++; blocked = value; return ProviderDiscoveryGenreMutation(ProviderDiscoveryOperationStatus.Uncertain)
            }
        })
        suspend fun send(action: DiscoveryBrowseAction) { val c = NaviampCoreCommand.Home.Browse(action); h.controller.dispatch(c); h.controller.execute(c) }
        send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Recommendations))
        send(DiscoveryBrowseAction.Genre("rock")); assertEquals(0, mutations)
        send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.BlockedGenres))
        send(DiscoveryBrowseAction.BlockGenre("rock", true)); send(DiscoveryBrowseAction.BlockGenre("rock", true))
        assertEquals(1, mutations)
        assertEquals(ProviderDiscoveryOperationStatus.Uncertain, h.discovery().browser!!.genreMutation)
        send(DiscoveryBrowseAction.Refresh)
        assertEquals(listOf("rock"), h.discovery().browser!!.blockedGenres.tags)
        assertTrue(h.discovery().browser!!.uncertainGenres.isEmpty())
        send(DiscoveryBrowseAction.Close)
        assertEquals(DiscoveryBrowsePage.Recommendations, h.discovery().browser!!.page)
        assertEquals("rock", h.discovery().browser!!.genre)
        assertEquals(listOf("rock"), h.discovery().browser!!.blockedGenres.tags)
    }

    @Test fun chartsUseIndependentPreviewsAndReconcileUncertainSavesWithoutReplay() = runTest {
        var saves = 0
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun charts() = ProviderDiscoveryCharts(ProviderDiscoveryStatus.Ready,
                songs = listOf(DiscoveryChartSong("chart-key", "Song", "Artist", previewId = DiscoveryDeezerPreviewId("42"))),
                playlistId = "55", canSave = true)
            override suspend fun saveChartPlaylist(id: String): ProviderDiscoveryChartSave { saves++; return ProviderDiscoveryChartSave(ProviderDiscoveryOperationStatus.Uncertain) }
            override suspend fun searchCatalog(query: String, scope: ProviderCatalogScope, offset: Int, limit: Int) = ProviderCatalogPage(ProviderDiscoveryStatus.Ready, artists = listOf(First))
        })
        suspend fun send(action: DiscoveryBrowseAction) { val c = NaviampCoreCommand.Home.Browse(action); h.controller.dispatch(c); h.controller.execute(c) }
        send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Trending))
        assertEquals(0, saves)
        send(DiscoveryBrowseAction.ChartPreview("chart-key"))
        assertEquals("discovery-preview:42", h.played.single().id.value)
        send(DiscoveryBrowseAction.SaveChart); send(DiscoveryBrowseAction.SaveChart)
        assertEquals(1, saves)
        send(DiscoveryBrowseAction.ChartSearch("Artist", ProviderCatalogScope.Artist))
        assertEquals(DiscoveryBrowsePage.Search, h.discovery().browser!!.page)
        h.open(First); h.back(); send(DiscoveryBrowseAction.Close)
        assertEquals(DiscoveryBrowsePage.Trending, h.discovery().browser!!.page)
        send(DiscoveryBrowseAction.SaveChart); assertEquals(1, saves)
        val stale = NaviampCoreCommand.Home.Browse(DiscoveryBrowseAction.Scoped(h.account, h.discovery().sourceGeneration, DiscoveryBrowseAction.SaveChart))
        h.account = "other"; h.active = discoveryProvider(DiscoveryNative(), h.account)
        assertTrue(h.controller.dispatch(stale) is NaviampCoreImmediateCommandResult.Handled)
        h.controller.execute(stale); assertEquals(1, saves)
    }

    @Test fun catalogSearchRejectsOldQueriesAndRetainsTheSubmittedScopeAcrossBack() = runTest {
        val entered = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>()
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun searchCatalog(query: String, scope: ProviderCatalogScope, offset: Int, limit: Int): ProviderCatalogPage {
                if (query == "old") { entered.complete(Unit); gate.await() }
                return ProviderCatalogPage(ProviderDiscoveryStatus.Ready, artists = listOf(if (query == "old") First else Second), offset = offset, totalCount = 1)
            }
        })
        suspend fun send(action: DiscoveryBrowseAction) { val c = NaviampCoreCommand.Home.Browse(action); h.controller.dispatch(c); h.controller.execute(c) }
        send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Search))
        send(DiscoveryBrowseAction.Query("old"))
        val old = NaviampCoreCommand.Home.Browse(DiscoveryBrowseAction.Search); h.controller.dispatch(old)
        val job = launch { h.controller.execute(old) }; entered.await()
        send(DiscoveryBrowseAction.Query(" Björk & 宇多田 "))
        send(DiscoveryBrowseAction.Search); gate.complete(Unit); job.join()
        assertEquals(listOf(Second), h.discovery().browser!!.catalog.artists)
        assertEquals("Björk & 宇多田", h.discovery().browser!!.submittedQuery)
        h.open(Second); h.back()
        assertEquals(" Björk & 宇多田 ", h.discovery().browser!!.query)
        assertEquals(ProviderCatalogScope.Artist, h.discovery().browser!!.scope)
    }
    @Test fun albumSearchUsesVerifiedReleaseCreditsAndNeverAcquiresOnNavigation() = runTest {
        var mutations = 0
        val album = ProviderCatalogAlbum(Release, First.name)
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun searchCatalog(query: String, scope: ProviderCatalogScope, offset: Int, limit: Int) =
                ProviderCatalogPage(ProviderDiscoveryStatus.Ready, albums = listOf(album), offset = offset, totalCount = 1)
            override suspend fun releaseDetails(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseDetails(
                ProviderDiscoveryStatus.Ready, Release, listOf(ProviderDiscoveryArtistCredit(First.name, First.artistMbid)))
            override suspend fun requestAlbum(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease): ProviderDiscoveryOperation { mutations++; return ProviderDiscoveryOperation() }
        })
        suspend fun send(action: DiscoveryBrowseAction) { val c = NaviampCoreCommand.Home.Browse(action); h.controller.dispatch(c); h.controller.execute(c) }
        send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Search)); send(DiscoveryBrowseAction.Scope(ProviderCatalogScope.Album))
        send(DiscoveryBrowseAction.Query("Album")); send(DiscoveryBrowseAction.Search); send(DiscoveryBrowseAction.Album(album))
        assertEquals(First.id, h.detail().artist.id)
        assertEquals(Release.id, h.discovery().releasePage?.release?.id)
        h.back(); h.back()
        assertEquals(ProviderCatalogScope.Album, h.discovery().browser!!.scope)
        assertEquals(0, mutations)
    }

    @Test fun recommendationsLoadAllPagesDeduplicateAndRetainBackState() = runTest {
        var calls = 0
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun recommendations(offset: Int, limit: Int): ProviderRecommendationPage {
                calls++
                val artists = (offset until minOf(offset + limit, 205)).map { index -> First.copy(
                    id = index.toString().padStart(8, '0') + "-0000-0000-0000-000000000000", name = "Pick $index") }
                return ProviderRecommendationPage(ProviderDiscoveryStatus.Ready, artists, offset,
                    (offset + limit).takeIf { it < 205 }, 205)
            }
        })
        val command = NaviampCoreCommand.Home.Browse(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Recommendations))
        h.controller.dispatch(command); h.controller.execute(command)
        assertEquals(3, calls); assertEquals(205, h.discovery().browser!!.recommendations.size)
        h.controller.dispatch(NaviampCoreCommand.Home.Browse(DiscoveryBrowseAction.Genre("Rock")))
        h.open(h.discovery().browser!!.recommendations.first()); h.back()
        assertEquals("Rock", h.discovery().browser!!.genre)
        assertFalse(h.discovery().artistPage)
    }
    @Test fun recommendationsRetainPartialResultsAndRetryAtTheFailedOffset() = runTest {
        var fail = true
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun recommendations(offset: Int, limit: Int): ProviderRecommendationPage = when {
                offset == 0 -> ProviderRecommendationPage(ProviderDiscoveryStatus.Ready, listOf(First), 0, 100, 205)
                fail -> ProviderRecommendationPage(ProviderDiscoveryStatus.Unavailable, offset = offset)
                else -> ProviderRecommendationPage(ProviderDiscoveryStatus.Ready, listOf(Second), offset, null, 205)
            }
        })
        suspend fun send(action: DiscoveryBrowseAction) { val command = NaviampCoreCommand.Home.Browse(action); h.controller.dispatch(command); h.controller.execute(command) }
        send(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Recommendations))
        assertEquals(listOf(First), h.discovery().browser!!.recommendations)
        assertEquals(100, h.discovery().browser!!.nextOffset)
        fail = false; send(DiscoveryBrowseAction.More)
        assertEquals(listOf(First, Second), h.discovery().browser!!.recommendations)
        assertNull(h.discovery().browser!!.nextOffset)
    }
    @Test fun anOldRecommendationReadCannotPublishAfterSourceSwitch() = runTest {
        val entered = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>()
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun recommendations(offset: Int, limit: Int): ProviderRecommendationPage {
                entered.complete(Unit); gate.await()
                return ProviderRecommendationPage(ProviderDiscoveryStatus.Ready, listOf(First))
            }
        })
        val command = NaviampCoreCommand.Home.Browse(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Recommendations))
        h.controller.dispatch(command)
        val loading = launch { h.controller.execute(command) }; entered.await()
        h.account = "other"; h.active = discoveryProvider(DiscoveryNative(), h.account)
        h.controller.dispatch(NaviampCoreCommand.Home.ShowDiscoveryRequests)
        gate.complete(Unit); loading.join()
        assertNull(h.discovery().browser)
    }

    @Test fun openingASavedRequestCannotFinishAgainstANewConnection() = runTest {
        val repository = MemoryDiscoveryAcquisitionRepository()
        val record = DiscoveryAcquisitionRecord("saved", First.id, First.name, Release.id, true, Release.id, Release.title,
            ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Queued), DiscoveryAcquisitionPhase.Queued, 42L)
        repository.save(DiscoveryAcquisitionScope("account-one", "account-one"), record)
        val gate = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>()
        val native = object : DiscoveryNative() {
            override suspend fun artistDetails(artist: ProviderDiscoveryArtist): ProviderDiscoveryArtistResult {
                entered.complete(Unit); gate.await(); return super.artistDetails(artist)
            }
        }
        val h = DiscoveryHarness(native, repository)
        h.controller.dispatch(NaviampCoreCommand.Home.ShowDiscoveryRequests)
        val command = NaviampCoreCommand.Home.OpenDiscoveryRequest(h.account, h.discovery().sourceGeneration, record.id)
        val opening = launch { h.controller.execute(command) }
        entered.await()
        h.account = "account-two"; h.active = discoveryProvider(DiscoveryNative(), h.account)
        h.browse(First)
        gate.complete(Unit); opening.join()
        assertEquals(First.id, h.detail().artist.id)
        assertNull(h.discovery().releasePage)
        assertTrue(h.discovery().acquisitions.isEmpty())
    }
    @Test fun persistedRequestsHydrateANewControllerWithoutReposting() = runTest {
        val repository = MemoryDiscoveryAcquisitionRepository()
        var calls = 0
        val native = object : DiscoveryNative() {
            override suspend fun requestAlbum(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease): ProviderDiscoveryOperation {
                calls++
                return ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Queued,
                    ProviderDiscoveryRequestIdentity(requestGroupId = DiscoveryRequestGroupId("group")))
            }
            override suspend fun requestActivity() = ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready,
                listOf(ProviderDiscoveryActivity("history", "completed", ProviderDiscoveryRequestIdentity(requestGroupId = DiscoveryRequestGroupId("group")))))
        }
        val first = DiscoveryHarness(native, repository)
        first.open(First); first.openRelease()
        val request = first.releaseAction(ProviderDiscoveryAction.RequestAlbum(Release.id))
        first.controller.dispatch(request); first.controller.execute(request)
        val reopened = DiscoveryHarness(native, repository)
        reopened.open(First); reopened.openRelease()
        assertEquals(1, reopened.detail().acquisitions.size)
        val duplicate = reopened.releaseAction(ProviderDiscoveryAction.RequestAlbum(Release.id))
        assertTrue(reopened.controller.dispatch(duplicate) is NaviampCoreImmediateCommandResult.Handled)
        reopened.controller.execute(duplicate)
        reopened.controller.refreshAcquisitions()
        assertEquals(DiscoveryAcquisitionPhase.Complete, reopened.discovery().acquisitions.single().phase)
        assertNull(reopened.discovery().releasePage?.ownership?.libraryAlbum)
        assertEquals(1, calls)
    }
    @Test fun staleStatusReadCannotPublishIntoTheNewAccount() = runTest {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val native = object : DiscoveryNative() {
            override suspend fun requestActivity(): ProviderDiscoveryActivities {
                entered.complete(Unit); gate.await()
                return ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready,
                    listOf(ProviderDiscoveryActivity("old-user", "processing", null)))
            }
        }
        val h = DiscoveryHarness(native)
        h.open(First)
        val pending = launch { h.controller.refreshAcquisitions() }
        entered.await()
        val oldGeneration = h.discovery().sourceGeneration
        h.account = "account-two"; h.active = discoveryProvider(DiscoveryNative(), h.account)
        h.open(First)
        gate.complete(Unit); pending.join()
        assertTrue(h.discovery().sourceGeneration > oldGeneration)
        assertTrue(h.discovery().requestActivity.items.isEmpty())
        assertTrue(h.discovery().acquisitions.isEmpty())
        assertFalse(h.discovery().requestsLoading)
    }
    @Test fun pollingCancellationStopsFurtherReadsAndManualReconnectRetainsRequests() = runTest {
        var calls = 0
        val native = object : DiscoveryNative() {
            override suspend fun requestAlbum(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease) =
                ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Queued)
            override suspend fun requestActivity(): ProviderDiscoveryActivities {
                calls++; return ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready)
            }
        }
        val h = DiscoveryHarness(native)
        h.open(First); h.openRelease()
        val request = h.releaseAction(ProviderDiscoveryAction.RequestAlbum(Release.id))
        h.controller.dispatch(request); h.controller.execute(request)
        val polling = launch { h.controller.maintainAcquisitions() }
        runCurrent(); assertEquals(1, calls)
        polling.cancelAndJoin()
        advanceTimeBy(120_000); runCurrent(); assertEquals(1, calls)
        h.controller.refreshAcquisitions(); assertEquals(2, calls)
        assertEquals(DiscoveryAcquisitionPhase.Queued, h.discovery().acquisitions.single().phase)
    }

    @Test fun waitingAlbumRetainsContextAndNeverResubmitsAcrossNavigationDateOrSourceChanges() = runTest {
        var albumCalls = 0
        var trackCalls = 0
        val waiting = ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.WaitingForRelease,
            ProviderDiscoveryRequestIdentity(canonicalAlbumId = DiscoveryCanonicalAlbumId("71")), 201,
            ProviderDiscoveryWaitingForRelease(DiscoveryLibraryManager.Aurral, "2027-03-05"))
        val native = object : DiscoveryNative() {
            override suspend fun requestAlbum(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease): ProviderDiscoveryOperation {
                albumCalls++
                return waiting
            }
            override suspend fun requestReleaseTrack(context: ProviderDiscoveryReleaseContext, track: ProviderDiscoveryReleaseTrack): ProviderDiscoveryOperation {
                trackCalls++
                return ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Queued)
            }
        }
        val h = DiscoveryHarness(native)
        val action = ProviderDiscoveryAction.RequestAlbum(Release.id)
        h.open(First); h.openRelease()
        val request = h.releaseAction(action)
        h.controller.dispatch(request); h.controller.execute(request)
        h.back(); h.open(Second); h.back(); h.openRelease()
        h.back(); h.back(); h.open(First); h.openRelease()
        assertEquals(waiting, h.detail().requestOperations[action])
        assertTrue(action in h.detail().blockedActions)
        assertNull(h.discovery().releasePage?.ownership?.libraryAlbum)
        for (duplicate in listOf(h.releaseAction(action), h.releaseAction(ProviderDiscoveryAction.RequestTrack(Recording)))) {
            assertTrue(h.controller.dispatch(duplicate) is NaviampCoreImmediateCommandResult.Handled)
            h.controller.execute(duplicate)
        }
        advanceTimeBy(400L * 86_400_000L)
        runCurrent()
        assertEquals(1, albumCalls)
        assertEquals(0, trackCalls)
        assertEquals(waiting, h.detail().requestOperations[action])

        h.account = "account-two"
        h.active = discoveryProvider(native, h.account)
        h.browse(First); h.openRelease()
        assertTrue(h.detail().requestOperations.isEmpty())
        h.controller.dispatch(request); h.controller.execute(request)
        assertEquals(1, albumCalls, "An old account command cannot acquire on the new account")
        val newRequest = h.releaseAction(action)
        h.controller.dispatch(newRequest); h.controller.execute(newRequest)
        assertEquals(2, albumCalls, "The new account has an independent explicit request")
    }

    @Test fun recordingRejectionsAllowExplicitRetryButConflictsStayBlockedAfterReopening() = runTest {
        for (status in listOf(ProviderDiscoveryOperationStatus.Rejected,
            ProviderDiscoveryOperationStatus.Conflict, ProviderDiscoveryOperationStatus.Uncertain)) {
            var calls = 0
            val h = DiscoveryHarness(object : DiscoveryNative() {
                override suspend fun requestReleaseTrack(context: ProviderDiscoveryReleaseContext, track: ProviderDiscoveryReleaseTrack): ProviderDiscoveryOperation {
                    calls++
                    return ProviderDiscoveryOperation(status)
                }
            })
            h.open(First); h.openRelease()
            val action = ProviderDiscoveryAction.RequestTrack(Recording)
            val request = h.releaseAction(action)
            h.controller.dispatch(request); h.controller.execute(request)
            h.back(); h.back(); h.open(First); h.openRelease()
            assertEquals(status, h.detail().requestOperations[action]?.status)
            val retry = h.releaseAction(action)
            h.controller.dispatch(retry); h.controller.execute(retry)
            assertEquals(if (status == ProviderDiscoveryOperationStatus.Rejected) 2 else 1, calls, status.name)
        }
    }

    @Test fun recordingRequestsKeepIdentityAndRemainBlockedAfterReturningToTheRelease() = runTest {
        var calls = 0
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun requestReleaseTrack(context: ProviderDiscoveryReleaseContext, track: ProviderDiscoveryReleaseTrack): ProviderDiscoveryOperation {
                calls++
                assertEquals(Release.id, context.release.id)
                assertEquals(Recording, track.recordingMbid?.value)
                return ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Queued,
                    ProviderDiscoveryRequestIdentity(jobIds = listOf(DiscoveryJobId("job-1"))))
            }
        })
        h.open(First); h.openRelease()
        val action = ProviderDiscoveryAction.RequestTrack(Recording)
        val request = h.releaseAction(action)
        assertEquals(NaviampCoreImmediateCommandResult.Deferred, h.controller.dispatch(request))
        assertTrue(h.controller.dispatch(h.releaseAction(action)) is NaviampCoreImmediateCommandResult.Handled)
        h.controller.execute(request)
        assertEquals("job-1", h.detail().requestOperations[action]?.identity?.jobIds?.single()?.value)
        h.back(); h.openRelease()
        assertTrue(h.controller.dispatch(h.releaseAction(action)) is NaviampCoreImmediateCommandResult.Handled)
        h.back(); h.back(); h.open(First); h.openRelease()
        assertEquals("job-1", h.detail().requestOperations[action]?.identity?.jobIds?.single()?.value)
        assertTrue(h.controller.dispatch(h.releaseAction(action)) is NaviampCoreImmediateCommandResult.Handled)
        assertEquals(1, calls)
    }

    @Test fun timeoutsAndSavedWithoutSourceNeverOfferADuplicateSubmission() = runTest {
        for (status in listOf(ProviderDiscoveryOperationStatus.Uncertain, ProviderDiscoveryOperationStatus.MissingSource)) {
            var calls = 0
            val h = DiscoveryHarness(object : DiscoveryNative() {
                override suspend fun requestReleaseTrack(context: ProviderDiscoveryReleaseContext, track: ProviderDiscoveryReleaseTrack): ProviderDiscoveryOperation {
                    calls++
                    if (status == ProviderDiscoveryOperationStatus.Uncertain) error("Response lost after submission")
                    return ProviderDiscoveryOperation(status)
                }
            })
            h.open(First); h.openRelease()
            val action = ProviderDiscoveryAction.RequestTrack(Recording)
            val request = h.releaseAction(action)
            h.controller.dispatch(request); h.controller.execute(request)
            assertEquals(status, h.detail().requestOperations[action]?.status)
            assertTrue(h.controller.dispatch(h.releaseAction(action)) is NaviampCoreImmediateCommandResult.Handled)
            assertEquals(1, calls)
        }
    }

    @Test fun cancelledSubmissionIsUncertainAndOldPageCommandsCannotTargetANewAccount() = runTest {
        val waiting = CompletableDeferred<ProviderDiscoveryOperation>()
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun requestReleaseTrack(context: ProviderDiscoveryReleaseContext, track: ProviderDiscoveryReleaseTrack) = waiting.await()
        })
        h.open(First); h.openRelease()
        val action = ProviderDiscoveryAction.RequestTrack(Recording)
        val request = h.releaseAction(action)
        h.controller.dispatch(request)
        val submitting = launch { h.controller.execute(request) }
        runCurrent()
        submitting.cancelAndJoin()
        assertEquals(ProviderDiscoveryOperationStatus.Uncertain, h.detail().requestOperations[action]?.status)
        assertTrue(action in h.detail().blockedActions)
        h.account = "account-two"
        h.active = discoveryProvider(DiscoveryNative(), h.account)
        h.browse(First); h.openRelease()
        assertTrue(h.detail().requestOperations.isEmpty())
        assertTrue(h.controller.dispatch(request) is NaviampCoreImmediateCommandResult.Handled)
    }

    @Test fun deferredRequestAbandonedBeforeExecutionDoesNotSubmitOrLeaveBusyState() = runTest {
        var calls = 0
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun requestReleaseTrack(context: ProviderDiscoveryReleaseContext, track: ProviderDiscoveryReleaseTrack): ProviderDiscoveryOperation {
                calls++; return ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Queued)
            }
        })
        h.open(First); h.openRelease()
        val request = h.releaseAction(ProviderDiscoveryAction.RequestTrack(Recording))
        h.controller.dispatch(request)
        assertTrue(h.detail().actionBusy)
        h.back()
        h.controller.execute(request)
        assertFalse(h.detail().actionBusy)
        assertEquals(0, calls)
        h.openRelease()
        assertEquals(NaviampCoreImmediateCommandResult.Deferred, h.controller.dispatch(h.releaseAction(ProviderDiscoveryAction.RequestTrack(Recording))))
    }

    @Test fun conflictingCreditsAndOwnedRecordingsCannotSubmitAcquisition() = runTest {
        for (conflictingCredits in listOf(true, false)) {
            val h = DiscoveryHarness(object : DiscoveryNative() {
                override suspend fun releaseDetails(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Ready, Release,
                    credits = if (conflictingCredits) listOf(ProviderDiscoveryArtistCredit("Other", DiscoveryArtistMbid(Second.id))) else emptyList())
                override suspend fun releaseOwnership(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.Ready,
                    ProviderDiscoveryReleaseAvailability.Partial, ownedRecordings = if (conflictingCredits) emptySet() else setOf(DiscoveryRecordingMbid(Recording)))
            })
            h.open(First); h.openRelease()
            assertTrue(h.controller.dispatch(h.releaseAction(ProviderDiscoveryAction.RequestTrack(Recording))) is NaviampCoreImmediateCommandResult.Handled)
            val preview = h.releaseAction(ProviderDiscoveryAction.PlaySample("99"))
            assertEquals(NaviampCoreImmediateCommandResult.Deferred, h.controller.dispatch(preview))
            h.controller.execute(preview)
            assertEquals(1, h.played.size)
        }
    }

    @Test fun onlyAVerifiedAlbumOnTheCurrentSourceAndReleaseCanOpenInLibrary() = runTest {
        val album = app.naviamp.domain.Album(app.naviamp.domain.AlbumId("album:verified"), "Owned", "Artist", null, null)
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun releaseOwnership(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseOwnership(
                ProviderDiscoveryStatus.Ready, ProviderDiscoveryReleaseAvailability.Partial, libraryAlbum = album)
        })
        h.open(First)
        val opening = NaviampCoreCommand.Home.SelectDiscoveryRelease(Release.id)
        h.controller.dispatch(opening); h.controller.execute(opening)
        val link = NaviampCoreCommand.Home.OpenDiscoveryLibraryRelease(h.account, h.discovery().sourceGeneration, Release.id)
        h.controller.dispatch(link); h.controller.execute(link)
        assertEquals(listOf(album), h.albumsOpened)
        h.back()
        h.controller.execute(link)
        assertEquals(1, h.albumsOpened.size)
        h.account = "account-two"
        h.active = discoveryProvider(DiscoveryNative(), h.account)
        h.controller.execute(link)
        assertEquals(1, h.albumsOpened.size)
    }

    @Test fun releaseTracksLoadIndependentlySortDiscsAndUseOnlyTheIsolatedPreviewCallback() = runTest {
        val waiting = CompletableDeferred<ProviderDiscoveryReleaseDetails>()
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun releaseDetails(release: DiscoveryReleaseGroupMbid) = waiting.await()
            override suspend fun releaseTracks(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseTracks(ProviderDiscoveryStatus.Ready,
                listOf(ProviderDiscoveryReleaseTrack(null, "Disc two", 2, 1, 150000),
                    ProviderDiscoveryReleaseTrack(null, "Disc one", 1, 2, 90000, DiscoveryDeezerPreviewId("99"))))
        })
        h.open(First)
        val command = NaviampCoreCommand.Home.SelectDiscoveryRelease(Release.id)
        h.controller.dispatch(command)
        val pending = launch { h.controller.execute(command) }
        runCurrent()
        val page = h.discovery().releasePage!!
        assertTrue(page.loading)
        assertFalse(page.tracksLoading)
        assertEquals(listOf("Disc one", "Disc two"), page.tracks!!.tracks.map { it.title })
        val preview = NaviampCoreCommand.Home.DiscoveryAction(First.id, ProviderDiscoveryAction.PlaySample("99"))
        h.controller.dispatch(preview); h.controller.execute(preview)
        assertEquals("discovery-preview:99", h.played.single().id.value)
        assertEquals("Release", h.played.single().albumTitle)
        assertFalse(h.detail().actionBusy)
        waiting.complete(ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Unavailable))
        pending.join()
        assertEquals(ProviderDiscoveryStatus.Ready, h.discovery().releasePage?.tracks?.status)
    }

    @Test fun abandonedReleaseLoadsAndDeferredPreviewsCannotAffectTheArtistPage() = runTest {
        val waiting = CompletableDeferred<ProviderDiscoveryReleaseTracks>()
        val h = DiscoveryHarness(object : DiscoveryNative() {
            override suspend fun releaseTracks(release: DiscoveryReleaseGroupMbid) = waiting.await()
        })
        h.open(First)
        val opening = NaviampCoreCommand.Home.SelectDiscoveryRelease(Release.id)
        h.controller.dispatch(opening)
        val pending = launch { h.controller.execute(opening) }
        runCurrent()
        h.back()
        waiting.complete(ProviderDiscoveryReleaseTracks(ProviderDiscoveryStatus.Ready,
            listOf(ProviderDiscoveryReleaseTrack(null, "Late", 1, 1, null, DiscoveryDeezerPreviewId("42")))))
        pending.join()
        assertNull(h.discovery().releasePage)
        // Dispatch an artist sample, then switch to a release before deferred execution.
        val old = NaviampCoreCommand.Home.DiscoveryAction(First.id, ProviderDiscoveryAction.PlaySample("42"))
        h.controller.dispatch(old)
        h.controller.dispatch(NaviampCoreCommand.Home.SelectDiscoveryRelease(Release.id))
        h.controller.execute(old)
        assertTrue(h.played.isEmpty())
    }

    @Test fun requestOutcomesRetainRetryPolicyAfterRelatedArtistAndReleaseNavigation() = runTest {
        for (status in listOf(ProviderDiscoveryOperationStatus.Rejected,
            ProviderDiscoveryOperationStatus.Conflict, ProviderDiscoveryOperationStatus.Uncertain)) {
            var writes = 0
            val native = object : DiscoveryNative() {
                override suspend fun requestAlbum(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease): ProviderDiscoveryOperation {
                    writes++
                    return ProviderDiscoveryOperation(status)
                }
            }
            val h = DiscoveryHarness(native)
            h.open(First)
            val action = ProviderDiscoveryAction.RequestAlbum(Release.id)
            val request = NaviampCoreCommand.Home.DiscoveryAction(First.id, action)
            h.controller.dispatch(request); h.controller.execute(request)
            assertEquals(status, h.detail().operationStatus)
            h.open(Second)
            h.back()
            val release = NaviampCoreCommand.Home.SelectDiscoveryRelease(Release.id)
            h.controller.dispatch(release); h.controller.execute(release)
            h.back()
            assertEquals(status, h.detail().operationStatus)
            assertEquals(status != ProviderDiscoveryOperationStatus.Rejected, action in h.detail().blockedActions)
            val retry = NaviampCoreCommand.Home.DiscoveryAction(First.id, action)
            h.controller.dispatch(retry)
            // Execution must also enforce the policy if a caller runs a handled command.
            h.controller.execute(retry)
            assertEquals(if (status == ProviderDiscoveryOperationStatus.Rejected) 2 else 1, writes, status.name)
        }
    }

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
private const val Recording = "55555555-5555-5555-5555-555555555555"
private open class DiscoveryNative : ProviderDiscovery {
    override suspend fun load() = ProviderDiscoveryFeed(trending = listOf(First))
    override suspend fun artistDetails(artist: ProviderDiscoveryArtist) = ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Ready,
        ProviderDiscoveryArtistDetails(artist, releases = listOf(Release), canRequestAlbums = true, canRequestTracks = true))
    override suspend fun samples(artist: ProviderDiscoveryArtist) = ProviderDiscoverySamples(ProviderDiscoveryStatus.Ready,
        listOf(ProviderDiscoverySample("42", "Sample")))
    override suspend fun similarArtists(artist: DiscoveryArtistMbid) = ProviderDiscoverySimilarArtists(ProviderDiscoveryStatus.Ready,
        if (artist.value == First.id) listOf(Second) else listOf(Third))
    override suspend fun releaseDetails(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Ready, Release)
    override suspend fun releaseTracks(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseTracks(ProviderDiscoveryStatus.Ready,
        listOf(ProviderDiscoveryReleaseTrack(DiscoveryRecordingMbid(Recording), "Recording", 1, 1, 150000, DiscoveryDeezerPreviewId("99"))))
}
private fun discoveryProvider(native: ProviderDiscovery, account: String) = object : MediaProvider by FakeCoreMediaProvider() {
    override val id = ProviderId("aurral")
    override val cacheNamespace = account
    override val discoveryAccountScope = account
    override val discovery = native
}
private class DiscoveryHarness(native: ProviderDiscovery = DiscoveryNative(),
    repository: DiscoveryAcquisitionRepository = MemoryDiscoveryAcquisitionRepository(),
    decorateProviders: Boolean = false) {
    var account = "account-one"
    var active: MediaProvider = discoveryProvider(native, account)
    val store = NaviampCoreStateStore()
    var stops = 0
    var libraryOpens = 0
    val played = mutableListOf<app.naviamp.domain.Track>()
    val albumsOpened = mutableListOf<app.naviamp.domain.Album>()
    val returned = mutableListOf<SharedRoute>()
    init { store.updateShell { it.copy(home = it.home.copy(providerDiscovery = SharedProviderDiscoveryUi("aurral",
        ProviderDiscoveryFeed(trending = listOf(First)))), shellChrome = it.shellChrome.copy(selectedRoute = SharedRoute.Search)) } }
    val controller = NaviampCoreDiscoveryController(store, { if (decorateProviders) object : MediaProvider by active {} else active }, { account }, {}, { played += it }, { stops++ }, { libraryOpens++ }, {},
        restoreRoute = { returned += it }, openLibraryAlbum = { albumsOpened += it }, acquisitionRepository = repository)
    fun discovery() = store.state.value.shell.home.providerDiscovery!!
    fun detail() = discovery().artistDetail!!
    fun releaseAction(action: ProviderDiscoveryAction) = NaviampCoreCommand.Home.DiscoveryReleaseAction(account, discovery().sourceGeneration, Release.id, action)
    suspend fun openRelease() {
        val command = NaviampCoreCommand.Home.SelectDiscoveryRelease(Release.id)
        controller.dispatch(command); controller.execute(command)
    }
    suspend fun browse(artist: ProviderDiscoveryArtist) {
        val command = NaviampCoreCommand.Home.BrowseDiscoveryArtist(account, artist)
        controller.dispatch(command); controller.execute(command)
    }
    suspend fun open(artist: ProviderDiscoveryArtist) {
        val command = NaviampCoreCommand.Home.SelectDiscoveryArtist(artist.id)
        controller.dispatch(command); controller.execute(command)
    }
    suspend fun back() {
        controller.dispatch(NaviampCoreCommand.Home.DismissDiscoveryArtist)
        controller.execute(NaviampCoreCommand.Home.DismissDiscoveryArtist)
    }
}
