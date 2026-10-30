package app.naviamp.domain.media

import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistPlaylistsTest {
    private val artist = Artist(ArtistId("artist"), "Artist")

    @Test fun matchesPrimarySecondaryAliasesAndLegacyCreditsWithoutConfusingIdentities() {
        assertTrue(track().isCreditedTo(artist))
        assertTrue(track().copy(artistCredits = listOf(ArtistCredit(ArtistId("other"), "Lead"),
            ArtistCredit(artist.id, "Stage Name"))).isCreditedTo(artist))
        assertFalse(track().copy(artistId = ArtistId("namesake")).isCreditedTo(artist))
        assertFalse(track().copy(artistName = "Other Artist", artistId = null).isCreditedTo(artist))
        assertTrue(track().copy(artistName = "Lead; Artist", artistId = ArtistId("combined")).isCreditedTo(artist))
        assertTrue(track().copy(artistId = null, artistName = " ARTIST ").isCreditedTo(artist))
        assertTrue(track().isCreditedTo(nameOnlyArtistCredit("Artist")))
    }

    @Test fun ownershipIsIndependentOfEditingAndResultsAreUniqueSortedAndPartial() = runTest {
        val provider = ArtistPlaylistProvider().apply {
            choices = listOf(Playlist("smart", "Zed", 1, isSmart = true, canEdit = false),
                Playlist("owned", "Alpha", 1), Playlist("owned", "Alpha", 1),
                Playlist("shared", "Shared editable", 1), Playlist("failed", "Unreadable", 1),
                Playlist("unknown", "No owner", 1), Playlist("unrelated", "Unrelated", 1))
            owns = { when (it.id) { "shared" -> false; "unknown" -> null; else -> true } }
            contents = { when (it) { "failed" -> error("offline"); "unrelated" -> emptyList(); else -> listOf(track(), track()) } }
        }
        val updates = mutableListOf<ArtistPlaylists>()
        loadArtistPlaylists(provider, artist, updates::add)
        assertTrue(updates.first().loading)
        val final = updates.last()
        assertEquals(listOf("owned", "smart"), final.playlists.map { it.id })
        assertTrue(final.failed)
        assertFalse(final.loading)
        assertFalse(final.truncated)
        assertFalse("shared" in provider.readIds)
        assertFalse("unknown" in provider.readIds)
        assertEquals(1, provider.readIds.count { it == "owned" })
    }

    @Test fun boundedConcurrencyAndTruncationAreExplicit() = runTest {
        val provider = ArtistPlaylistProvider().apply {
            choices = List(501) { Playlist("$it", "$it", 1) }
            contents = { delay(1); listOf(track()) }
        }
        var final = ArtistPlaylists()
        loadArtistPlaylists(provider, artist, { final = it })
        assertEquals(500, final.playlists.size)
        assertTrue(final.truncated)
        assertEquals(4, provider.peakReads)
        assertEquals(501, provider.requestedLimit)
    }

    @Test fun timeoutsAndListFailuresRemainFailuresAndRetryCanRecover() = runTest {
        val provider = ArtistPlaylistProvider().apply { contents = { delay(60_000); listOf(track()) } }
        var final = ArtistPlaylists()
        loadArtistPlaylists(provider, artist, { final = it })
        assertTrue(final.failed)
        assertFalse(final.loading)
        provider.contents = { listOf(track()) }
        loadArtistPlaylists(provider, artist, { final = it })
        assertFalse(final.failed)
        assertEquals(1, final.playlists.size)
        provider.failList = true
        loadArtistPlaylists(provider, artist, { final = it })
        assertTrue(final.failed)
        assertTrue(final.playlists.isEmpty())
    }

    @Test fun cancellationAndStaleSourcesStopFurtherReadsWithoutPublishingEmptySuccess() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = ArtistPlaylistProvider().apply {
            choices = List(12) { Playlist("$it", "$it", 1) }
            contents = { gate.await(); listOf(track()) }
        }
        val updates = mutableListOf<ArtistPlaylists>()
        val job = launch { loadArtistPlaylists(provider, artist, updates::add) }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(4, provider.readIds.size)
        assertTrue(updates.all { it.loading })

        var current = true
        provider.readIds.clear()
        provider.contents = { current = false; listOf(track()) }
        loadArtistPlaylists(provider, artist, updates::add, isCurrent = { current })
        assertTrue(provider.readIds.size <= 4)
        assertTrue(updates.all { it.loading })
    }

    @Test fun slowLargeAccountsStopAtTheOverallBudgetAndRetainCompletedMatches() = runTest {
        val provider = ArtistPlaylistProvider().apply {
            choices = List(500) { Playlist("$it", "Playlist $it", 1) }
            contents = { id ->
                if (id.toInt() >= 4 && id != "9") delay(60_000)
                listOf(track())
            }
        }
        val updates = mutableListOf<ArtistPlaylists>()
        loadArtistPlaylists(provider, artist, updates::add)
        assertTrue(currentTime <= 30_000, "Whole lookup took $currentTime ms")
        assertEquals(listOf("0", "1", "2", "3", "9"), updates.last().playlists.map { it.id },
            "Keep completed reads even from the batch interrupted by the overall deadline")
        assertFalse(updates.last().loading)
        assertTrue(updates.last().failed, "Incomplete lookup must not claim an empty/complete success")
        assertFalse(updates.last().truncated, "The 500-playlist truncation message does not describe a timeout")
        assertEquals(0, provider.activeReads)
        assertTrue(provider.readIds.size <= 12)
        val completedUpdates = updates.size
        advanceUntilIdle()
        assertEquals(completedUpdates, updates.size)
    }

    @Test fun overallTimeoutDoesNotSwallowParentCancellation() = runTest {
        val provider = ArtistPlaylistProvider().apply {
            choices = List(500) { Playlist("$it", "$it", 1) }
            contents = { delay(60_000); listOf(track()) }
        }
        val updates = mutableListOf<ArtistPlaylists>()
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(100) { loadArtistPlaylists(provider, artist, updates::add) }
        }
        assertTrue(updates.all { it.loading })
        assertEquals(0, provider.activeReads)
    }
}

private class ArtistPlaylistProvider : MediaProvider {
    override val id = ProviderId("test")
    override val displayName = "Test"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    var choices = listOf(Playlist("owned", "Mine", 1))
    var owns: suspend (Playlist) -> Boolean? = { true }
    var contents: suspend (String) -> List<Track> = { listOf(track()) }
    var failList = false
    var requestedLimit = 0
    val readIds = mutableListOf<String>()
    var activeReads = 0
    var peakReads = 0
    override suspend fun playlists(limit: Int): List<Playlist> {
        requestedLimit = limit
        if (failList) error("offline")
        return choices.take(limit)
    }
    override suspend fun playlistOwnedByCurrentUser(playlist: Playlist) = owns(playlist)
    override suspend fun playlistTracks(playlistId: String): List<Track> {
        readIds += playlistId
        activeReads++
        peakReads = maxOf(peakReads, activeReads)
        return try { contents(playlistId) } finally { activeReads-- }
    }
    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("unused")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("unused")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun streamUrl(request: StreamRequest) = ""
    override fun coverArtUrl(coverArtId: String) = ""
}

private fun track() = Track(TrackId("track"), "Track", artistId = ArtistId("artist"), artistName = "Artist",
    albumTitle = null, durationSeconds = null, coverArtId = null, audioInfo = null, replayGain = null)
