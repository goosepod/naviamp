package app.naviamp.presentation

import app.naviamp.domain.*
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.provider.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVehicleArtworkTest {
    private fun ref(url: String = "image", kind: SharedMediaItemKind = SharedMediaItemKind.Album) = NaviampVehicleArtworkReference(kind, url)
    private fun NaviampVehicleArtworkController.rows(vararg refs: NaviampVehicleArtworkReference, source: String = "source"): List<NaviampVehicleArtwork> {
        begin(listOf(source)); return refs.map(::describe).also { end() }
    }
    @Test fun explicitKindsUseSharedShapePolicyAndOpaqueStableIdentities() = runTest {
        val owner = NaviampVehicleArtworkController(backgroundScope, null)
        val refs = SharedMediaItemKind.entries.map { ref("same-private-url", it) }
        val descriptors = owner.rows(*refs.toTypedArray())
        assertEquals(listOf(SharedMediaItemKind.Artist), refs.zip(descriptors).filter { it.second.circular }.map { it.first.kind })
        assertEquals(descriptors.size, descriptors.map { it.id }.distinct().size)
        assertTrue(descriptors.none { "url" in it.id })
        assertEquals(descriptors, owner.rows(*refs.toTypedArray()))
        assertEquals("Artist", descriptors[SharedMediaItemKind.Artist.ordinal].placeholder)
        owner.close()
    }
    @Test fun requestsDeduplicateCacheAndNeverHoldUpRows() = runTest {
        val gate = CompletableDeferred<Unit>(); var reads = 0
        val owner = NaviampVehicleArtworkController(backgroundScope, NaviampVehicleArtworkSource { reads++; gate.await(); byteArrayOf(7) })
        val row = owner.rows(ref()).single(); val received = mutableListOf<ByteArray?>()
        owner.request(row.id) { received += it }; owner.request(row.id) { received += it }
        runCurrent(); assertEquals(1, reads); assertTrue(received.isEmpty())
        gate.complete(Unit); runCurrent(); assertEquals(2, received.size)
        owner.request(row.id) { assertContentEquals(byteArrayOf(7), it) }
        assertEquals(1, reads); owner.close()
    }
    @Test fun pagingSourceChangesAndDetachCompleteCallbacksOnceAndRejectLateBytes() = runTest {
        for (change in listOf("page", "source", "detach")) {
            val gate = CompletableDeferred<Unit>(); val received = mutableListOf<ByteArray?>()
            val owner = NaviampVehicleArtworkController(backgroundScope, NaviampVehicleArtworkSource {
                withContext(NonCancellable) { gate.await() }; byteArrayOf(3)
            })
            val old = owner.rows(ref()).single(); owner.request(old.id) { received += it }; runCurrent()
            when (change) {
                "page" -> owner.rows(ref("other"))
                "source" -> owner.rows(ref(), source = "other")
                else -> owner.close()
            }
            assertEquals(listOf<ByteArray?>(null), received)
            gate.complete(Unit); runCurrent(); assertEquals(listOf<ByteArray?>(null), received)
            owner.request(old.id) { assertNull(it) }; owner.close()
        }
    }
    @Test fun concurrencyAndByteCacheAreBounded() = runTest {
        val gate = CompletableDeferred<Unit>(); var active = 0; var peak = 0; var reads = 0
        val owner = NaviampVehicleArtworkController(backgroundScope, NaviampVehicleArtworkSource {
            reads++; active++; peak = maxOf(peak, active); gate.await(); active--; byteArrayOf(1, 2)
        }, maximumImageBytes = 2, maximumCacheBytes = 4)
        val rows = owner.rows(*(0..5).map { ref("$it") }.toTypedArray())
        rows.forEach { owner.request(it.id) {} }; runCurrent(); assertEquals(3, peak); assertEquals(3, reads)
        gate.complete(Unit); runCurrent(); assertEquals(6, reads)
        owner.request(rows.last().id) {}; runCurrent(); assertEquals(6, reads)
        owner.request(rows.first().id) {}; runCurrent(); assertEquals(7, reads)
        owner.close()
    }
    @Test fun eagerCompletionAllowsReloadAfterCacheEviction() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        var reads = 0
        val owner = NaviampVehicleArtworkController(scope, NaviampVehicleArtworkSource { reads++; byteArrayOf(1, 2) },
            maximumImageBytes = 2, maximumCacheBytes = 2)
        val rows = owner.rows(ref("first"), ref("second"))
        var completions = 0
        for (row in listOf(rows[0], rows[1], rows[0])) {
            owner.request(row.id) { assertContentEquals(byteArrayOf(1, 2), it); completions++ }
        }
        assertEquals(3, reads)
        assertEquals(3, completions, "An already-completed job must not suppress a cache-miss reload")
        owner.close()
    }

    @Test fun failuresEmptyOversizedAndNonCooperativeTimeoutReturnPlaceholderOnce() = runTest {
        for (mode in listOf("failure", "empty", "large", "timeout")) {
            val gate = CompletableDeferred<Unit>(); val received = mutableListOf<ByteArray?>()
            val owner = NaviampVehicleArtworkController(backgroundScope, NaviampVehicleArtworkSource {
                when (mode) {
                    "failure" -> error("bad image")
                    "empty" -> byteArrayOf()
                    "large" -> byteArrayOf(1, 2, 3)
                    else -> { withContext(NonCancellable) { gate.await() }; byteArrayOf(1) }
                }
            }, timeoutMillis = 50, maximumImageBytes = 2)
            val row = owner.rows(ref()).single(); owner.request(row.id) { received += it }; runCurrent()
            advanceTimeBy(51); runCurrent(); assertEquals(listOf<ByteArray?>(null), received, mode)
            gate.complete(Unit); runCurrent(); assertEquals(listOf<ByteArray?>(null), received, mode)
            owner.request(row.id) { assertNull(it) }; owner.close()
        }
    }

    @Test fun browserProjectsExplicitArtworkWithoutChangingSelectionRevisionOrPlaying() = runTest {
        val fake = FakeCoreMediaProvider()
        val state = product()
        state.value = state.value.copy(shell = state.value.shell.copy(home = state.value.shell.home.copy(
            content = state.value.shell.home.content.copy(recentlyPlayedTracks = listOf(SharedTrackRowUi(
                id = "track", title = "Track", subtitle = "Artist", coverArtUrl = "private:track"))))))
        val labels = NaviampVehicleBrowseLabels("Home", "Playlists", "Radio", "Empty", "Loading", "Disconnected", "Failed", "Next", "Previous", "Queue", "Play", "Shuffle", "Retry")
        val libraryLabels = NaviampVehicleLibraryLabels("Library", "Artists", "Albums", "Tracks", "Favorites", "Unavailable")
        val provider = object : MediaProvider by fake {
            override suspend fun artistsPage(request: MediaPageRequest) = request.toMediaPage(listOf(fake.artist))
        }
        val controller = NaviampVehicleBrowseController(backgroundScope, state, NaviampCoreExternalPlaybackBridge(state, { error("Artwork played audio") }),
            { error("Artwork ran command") }, labels, 8, maximumTabs = 4,
            library = NaviampCoreVehicleLibraryAccess(state, NaviampCoreMediaProviderSource { provider }, { error("Artwork played audio") }, libraryLabels),
            artworkSource = NaviampVehicleArtworkSource { byteArrayOf(8) })
        runCurrent()
        val home = controller.state.value.tabs.first().rows.first { it.title == "Track" }
        assertEquals("Track", assertNotNull(home.artwork).placeholder); assertFalse(home.artwork.circular)
        val revision = controller.state.value.revision
        controller.requestArtwork(home.artwork.id) { assertContentEquals(byteArrayOf(8), it) }
        runCurrent(); assertEquals(revision, controller.state.value.revision)
        val tab = controller.state.value.tabs.first { it.destination == NaviampVehicleBrowseDestination.Library }
        controller.select(tab.destination, tab.rows.first { it.title == "Artists" }.id, revision) {}
        runCurrent()
        val artist = controller.state.value.pages.last().rows.single { it.title == fake.artist.name }
        assertTrue(assertNotNull(artist.artwork).circular)
        controller.close()
    }

    @Test fun browserPagingCancelsArtworkForRowsOutsideTheProjectedPage() = runTest {
        val state = product()
        state.value = state.value.copy(shell = state.value.shell.copy(home = NaviampHomeScreenUi(SharedHomeUi(
            recentlyPlayedTracks = (1..7).map { SharedTrackRowUi("$it", "Track $it", "Artist", coverArtUrl = "image:$it") }))))
        val gate = CompletableDeferred<Unit>()
        val controller = NaviampVehicleBrowseController(backgroundScope, state, NaviampCoreExternalPlaybackBridge(state, {}), {},
            NaviampVehicleBrowseLabels("Home", "Playlists", "Radio", "Empty", "Loading", "Disconnected", "Failed", "Next", "Previous", "Queue", "Play", "Shuffle", "Retry"), 5,
            artworkSource = NaviampVehicleArtworkSource { withContext(NonCancellable) { gate.await() }; byteArrayOf(6) })
        runCurrent()
        val initial = controller.state.value
        val first = initial.tabs.first().rows.first { it.title == "Track 1" }
        val received = mutableListOf<ByteArray?>()
        controller.requestArtwork(assertNotNull(first.artwork).id) { received += it }; runCurrent()
        val next = controller.state.value.tabs.first().rows.single { it.title == "Next" }
        controller.select(NaviampVehicleBrowseDestination.Home, next.id, controller.state.value.revision) {}
        assertEquals(listOf<ByteArray?>(null), received)
        assertTrue(controller.state.value.tabs.first().rows.none { it.title == "Track 1" })
        gate.complete(Unit); runCurrent(); assertEquals(listOf<ByteArray?>(null), received)
        controller.requestArtwork(first.artwork.id) { assertNull(it) }
        controller.close()
    }

    @Test fun libraryAndSearchResultsCarryMediaIdentityInsteadOfGuessingFromLabels() = runTest {
        val fake = FakeCoreMediaProvider()
        val provider = object : MediaProvider by fake {
            override suspend fun artistsPage(request: MediaPageRequest) = request.toMediaPage(listOf(fake.artist))
            override suspend fun albumsPage(request: MediaPageRequest) = request.toMediaPage(listOf(fake.album.copy(coverArtId = "album-cover")))
            override suspend fun tracksPage(request: MediaPageRequest) = request.toMediaPage(listOf(fake.track.copy(coverArtId = "track-cover")))
            override suspend fun searchArtistsPage(query: String, request: MediaPageRequest) = artistsPage(request)
            override suspend fun searchAlbumsPage(query: String, request: MediaPageRequest) = albumsPage(request)
            override suspend fun searchTracksPage(query: String, request: MediaPageRequest) = tracksPage(request)
        }
        val access = NaviampCoreVehicleLibraryAccess(product(), NaviampCoreMediaProviderSource { provider }, { error("Browsing played audio") },
            NaviampVehicleLibraryLabels("Library", "Artists", "Albums", "Tracks", "Favorites", "Unavailable"))
        for ((kind, media) in listOf(NaviampVehicleLibraryKind.Artists to SharedMediaItemKind.Artist,
            NaviampVehicleLibraryKind.Albums to SharedMediaItemKind.Album, NaviampVehicleLibraryKind.Tracks to SharedMediaItemKind.Track,
            NaviampVehicleLibraryKind.SearchArtists to SharedMediaItemKind.Artist, NaviampVehicleLibraryKind.SearchAlbums to SharedMediaItemKind.Album,
            NaviampVehicleLibraryKind.SearchTracks to SharedMediaItemKind.Track)) {
            val entry = access.load(NaviampVehicleLibraryReference(kind, "Same title", query = "find"), MediaPageRequest()).entries.single()
            val reference = assertNotNull(entry.artwork)
            assertEquals(media, reference.kind); assertFalse(reference.cacheOnly)
            if (media == SharedMediaItemKind.Artist) assertEquals(fake.artist.id.value, reference.artistId)
            else assertEquals(if (media == SharedMediaItemKind.Album) "album-cover" else "track-cover", reference.coverArtId)
        }
    }
    @Test fun downloadedArtistsAlbumsAndTracksAlwaysDeclareCachedOnlyArtwork() = runTest {
        val fake = FakeCoreMediaProvider()
        val storage = object : NaviampCoreDownloadStoragePort {
            override suspend fun availableDownloads(sourceId: String) = listOf(NaviampCoreDownloadedTrack(fake.track.id.value, fake.track.copy(coverArtId = "saved-cover"), 10))
            override suspend fun snapshot(sourceId: String): NaviampCoreDownloadStorageSnapshot = error("Unexpected snapshot")
            override suspend fun pruneMissing(sourceId: String): Int = error("Unexpected prune")
            override suspend fun remove(sourceId: String, track: Track) = error("Unexpected remove")
            override suspend fun deleteAll(sourceId: String): Int = error("Unexpected delete")
        }
        val access = NaviampVehicleDownloadsAccess(product(), storage, { true }, { _, _, _, _ -> error("Browsing played audio") },
            NaviampVehicleLibraryLabels("Library", "Artists", "Albums", "Tracks", "Favorites", "Unavailable"),
            NaviampVehicleDownloadsLabels("Downloads", "Refresh", "Play", "Shuffle", "Empty"))
        for ((kind, media) in listOf(NaviampVehicleLibraryKind.DownloadedArtists to SharedMediaItemKind.Artist,
            NaviampVehicleLibraryKind.DownloadedAlbums to SharedMediaItemKind.Album, NaviampVehicleLibraryKind.DownloadedTracks to SharedMediaItemKind.Track)) {
            val entries = access.load(NaviampVehicleLibraryReference(kind, "Same title"), MediaPageRequest()).entries
            val artwork = assertNotNull(entries.single { it.artwork != null }.artwork)
            assertEquals(media, artwork.kind); assertTrue(artwork.cacheOnly)
            if (media != SharedMediaItemKind.Artist) assertEquals("saved-cover", artwork.coverArtId)
        }
    }

    private class Cache : ImageCacheRepository {
        val values = mutableMapOf<String, ByteArray>(); val lookups = mutableListOf<String>()
        override suspend fun cachedImageBytes(url: String): ByteArray? { lookups += url; return values[url] }
        override suspend fun imageBytes(url: String): ByteArray = error("Unscoped fetch")
        override suspend fun imageBytes(url: String, fetch: suspend () -> ByteArray): ByteArray {
            lookups += url; return values.getOrPut(url) { fetch() }
        }
    }
    private val http = object : SharedHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>): String? = error("Unexpected GET")
        override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = error("Unauthenticated fetch")
        override suspend fun getResponse(url: String, headers: Map<String, String>): app.naviamp.domain.network.SharedHttpResponse? = error("Unexpected GET")
        override suspend fun download(url: String, headers: Map<String, String>, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean = error("Unexpected download")
    }
    private fun product() = MutableStateFlow(NaviampCoreState(shell = NaviampAppShellUiState(
        connectionSettings = NaviampConnectionSettingsUi(currentSourceId = "source"))))
    @Test fun providerAuthenticationAndStableCacheAreReusedWithoutNativeUrls() = runTest {
        val cache = Cache(); val fake = FakeCoreMediaProvider(); var fetches = 0
        val provider = object : MediaProvider by fake {
            override fun coverArtUrl(coverArtId: String) = "private:$coverArtId"
            override fun artworkCacheKey(url: String) = "stable:$url"
            override suspend fun bytesForOwnedUrl(url: String): ByteArray { fetches++; return byteArrayOf(5) }
        }
        val source = NaviampCoreVehicleArtworkSource(product(), NaviampCoreMediaProviderSource { provider }, NaviampCoreArtworkServices(cache, http))
        val reference = NaviampVehicleArtworkReference(SharedMediaItemKind.Album, coverArtId = "cover")
        assertContentEquals(byteArrayOf(5), source.load(reference)); assertContentEquals(byteArrayOf(5), source.load(reference))
        assertEquals(1, fetches); assertEquals(listOf("stable:private:cover", "stable:private:cover"), cache.lookups)
    }
    @Test fun offlineArtworkOnlyReadsCacheAndNeverFetchesArtistDetails() = runTest {
        val fake = FakeCoreMediaProvider(); val cache = Cache(); cache.values["stable:cover"] = byteArrayOf(4)
        val provider = object : MediaProvider by fake {
            override fun coverArtUrl(coverArtId: String) = coverArtId
            override fun artworkCacheKey(url: String) = "stable:$url"
            override suspend fun bytesForOwnedUrl(url: String): ByteArray = error("Offline fetch")
            override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Offline details")
        }
        val source = NaviampCoreVehicleArtworkSource(product(), NaviampCoreMediaProviderSource { provider }, NaviampCoreArtworkServices(cache, http))
        assertContentEquals(byteArrayOf(4), source.load(NaviampVehicleArtworkReference(SharedMediaItemKind.Track, coverArtId = "cover", cacheOnly = true)))
        assertNull(source.load(NaviampVehicleArtworkReference(SharedMediaItemKind.Album, coverArtId = "missing", cacheOnly = true)))
        assertNull(source.load(NaviampVehicleArtworkReference(SharedMediaItemKind.Artist, artistId = "artist", cacheOnly = true)))
    }
    @Test fun artistPortraitAndSourceChangesAreCheckedBeforeAndAfterAsyncWork() = runTest {
        val fake = FakeCoreMediaProvider(); val state = product(); val cache = Cache(); var fetches = 0
        val provider = object : MediaProvider by fake {
            override suspend fun artist(artistId: ArtistId) = ArtistDetails(fake.artist, emptyList(), ArtistInfo(null, "small", "portrait", null))
            override suspend fun bytesForOwnedUrl(url: String): ByteArray { assertEquals("portrait", url); fetches++; return byteArrayOf(9) }
        }
        var selected: MediaProvider? = provider
        val source = NaviampCoreVehicleArtworkSource(state, NaviampCoreMediaProviderSource { selected }, NaviampCoreArtworkServices(cache, http))
        assertContentEquals(byteArrayOf(9), source.load(NaviampVehicleArtworkReference(SharedMediaItemKind.Artist, artistId = "artist")))
        selected = null
        assertNull(source.load(ref())); assertEquals(1, fetches)
        val gate = CompletableDeferred<Unit>()
        val delayed = object : MediaProvider by provider {
            override suspend fun artist(artistId: ArtistId): ArtistDetails { gate.await(); return provider.artist(artistId) }
        }
        selected = delayed
        val result = async { source.load(NaviampVehicleArtworkReference(SharedMediaItemKind.Artist, artistId = "other")) }
        runCurrent(); state.value = state.value.copy(sourceGeneration = state.value.sourceGeneration + 1)
        gate.complete(Unit); assertNull(result.await()); assertEquals(1, fetches)
    }
}
