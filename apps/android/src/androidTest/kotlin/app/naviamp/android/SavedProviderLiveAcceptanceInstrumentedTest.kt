package app.naviamp.android

import android.net.ConnectivityManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.presentation.NaviampCorePlaylistMembershipCoordinator
import app.naviamp.ui.NaviampTrackPlaylistMembershipUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.assertFalse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.android.playback.AndroidBassJni
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.library.AlbumCatalogRepository
import app.naviamp.domain.library.AlbumCatalogScope
import app.naviamp.domain.library.AlbumCatalogSnapshot
import app.naviamp.domain.library.AlbumLibraryIndex
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.provider.jellyfin.JellyfinProvider
import app.naviamp.provider.jellyfin.JellyfinSessionService
import app.naviamp.provider.jellyfin.JellyfinSessionServiceFactory
import app.naviamp.provider.jellyfin.KtorJellyfinHttpClient
import app.naviamp.provider.jellyfin.jellyfinClientIdentity
import app.naviamp.provider.navidrome.NavidromeException
import app.naviamp.provider.navidrome.NavidromeHttpException
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.createDefaultNavidromeKtorClient
import app.naviamp.provider.navidrome.toNavidromeConnection
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Explicit opt-in: real Android Keystore credentials, server I/O and native audio decoding. */
@RunWith(AndroidJUnit4::class)
class SavedProviderLiveAcceptanceInstrumentedTest {
    @Test fun navidrome() = checkProvider("navidrome")
    @Test fun jellyfin() = checkProvider("jellyfin")
    @Test fun bandcamp() = checkProvider("bandcamp")

    private fun checkProvider(providerId: String) = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSavedProviders") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var stage = "saved connection"
        fun record(message: String) = instrumentation.sendStatus(2, Bundle().apply {
            putString("stream", "\nLIVE $providerId $message\n")
        })
        try {
            withTimeout(240_000) {
                AndroidStorageDependencies(instrumentation.targetContext).use { storage ->
                    val source = storage.mediaSources().filter { it.providerId == providerId }
                        .maxByOrNull { it.lastConnectedAtEpochMillis ?: it.createdAtEpochMillis }
                    assertNotNull(source, "Missing saved provider connection")
                    stage = "restore"
                    val provider: MediaProvider = if (providerId == "jellyfin") {
                        val factory = JellyfinSessionServiceFactory { tls ->
                            JellyfinSessionService(
                                KtorJellyfinHttpClient(createDefaultNavidromeKtorClient(tls)),
                                jellyfinClientIdentity("naviamp-android", "Android"),
                            )
                        }
                        JellyfinProvider(factory.create(source.tlsSettings).restore(source), factory)
                    } else NavidromeProvider(source.toNavidromeConnection())
                    stage = "validate"
                    provider.validateConnection()
                    if (InstrumentationRegistry.getArguments().getString("liveInterruptionCleanup") == "true") {
                        val threshold = InstrumentationRegistry.getArguments().getString("liveInterruptionCleanupAfter")!!.toLong()
                        val owned = provider.playlists(200).filter {
                            it.name.startsWith("Naviamp interruption ") &&
                                (it.name.removePrefix("Naviamp interruption ").toLongOrNull() ?: 0L) >= threshold
                        }
                        for (item in owned) provider.deletePlaylist(item.id)
                        record("interruption cleanup deleted=${owned.size}")
                        return@use
                    }
                    record("connection passed; downloads=${provider.capabilities.supportsDownloads}")
                    if (InstrumentationRegistry.getArguments().getString("livePlaylistInterruptions") == "true") {
                        stage = "interrupted playlist saves"
                        checkInterruptedSaves(provider, ::record)
                        return@use
                    }
                    stage = "album page"
                    val page = provider.albumsPage(MediaPageRequest(limit = 20))
                    assertTrue(page.items.isNotEmpty(), "No albums in selected library")
                    record("album page=${page.items.size} hasMore=${page.hasMore}")
                    stage = "album detail"
                    val detail = provider.album(page.items.first().id)
                    assertEquals(page.items.first().id, detail.album.id)
                    assertTrue(detail.tracks.isNotEmpty(), "Selected album has no tracks")
                    stage = "search"
                    val results = provider.search(detail.album.title, limit = 20)
                    assertTrue(!results.isEmpty, "Album-title search returned no results")
                    stage = "artists"
                    val artists = provider.artistsPage(MediaPageRequest(limit = 20))
                    assertTrue(artists.items.isNotEmpty(), "No artists")
                    provider.artist(artists.items.first().id)
                    stage = "playlists"
                    val playlists = provider.playlists(limit = 200)
                    playlists.firstOrNull()?.let { provider.playlistTracks(it.id) }
                    record("detail/search/artists passed; playlists=${playlists.size}")
                    stage = "artwork"
                    val cover = detail.album.coverArtId ?: page.items.first().coverArtId
                    if (cover != null) {
                        val bytes = provider.bytesForOwnedUrl(provider.coverArtUrl(cover))
                        assertTrue(bytes != null && bytes.isNotEmpty(), "Artwork returned no bytes")
                        record("artwork bytes=${bytes.size}")
                    } else record("artwork unavailable on sampled album")
                    stage = "audio download"
                    val track = detail.tracks.first()
                    val request = StreamRequest(track.id, StreamQuality.Original)
                    val streamUrl = provider.streamUrl(request)
                    assertTrue(streamUrl.isNotBlank())
                    val useDownload = provider.capabilities.supportsDownloads &&
                        InstrumentationRegistry.getArguments().getString("liveAudioMode") != "stream"
                    val audioUrl = if (useDownload) provider.downloadUrl(request) else streamUrl
                    record("audio mode=${if (useDownload) "download" else "stream"}")
                    val file = File.createTempFile("naviamp-live-", ".audio", instrumentation.targetContext.cacheDir)
                    try {
                        val started = System.nanoTime()
                        var downloaded = 0L
                        file.outputStream().use { output ->
                            assertTrue(provider.downloadStream(audioUrl, KtorSharedHttpClient()) { bytes, count ->
                                downloaded += count
                                check(downloaded <= 150_000_000) { "Sample exceeds test download bound" }
                                output.write(bytes, 0, count)
                            }, "Audio download failed")
                        }
                        assertTrue(downloaded > 4096)
                        record("audio bytes=$downloaded downloadMs=${(System.nanoTime() - started) / 1_000_000}")
                        stage = "native audio decode"
                        val bass = AndroidBassJni.load().getOrThrow()
                        assertTrue(bass.init())
                        try {
                            val stream = bass.createFileDecodeStream(file.absolutePath)
                            assertTrue(stream != 0, "Native stream creation failed")
                            try {
                                assertTrue((bass.durationSeconds(stream) ?: 0.0) > 0)
                                assertTrue(bass.readFloatData(stream, FloatArray(4096)) > 0)
                                assertTrue(bass.seek(stream, 1.0))
                            } finally { bass.freeStream(stream) }
                        } finally { bass.free() }
                        record("native decode/seek passed")
                    } finally { file.delete() }
                    if (InstrumentationRegistry.getArguments().getString("livePlaylistWrites") == "true") {
                        stage = "disposable playlist"
                        val tracks = (detail.tracks + provider.tracksPage(MediaPageRequest(limit = 2)).items)
                            .map { it.id }.distinct().take(2)
                        assertEquals(2, tracks.size, "Need two sample tracks for occurrence checks")
                        val initial = listOf(tracks[0], tracks[1], tracks[0])
                        val playlist = provider.createPlaylist("Naviamp acceptance ${System.currentTimeMillis()}", initial)
                        try {
                            assertEquals(initial, provider.playlistTracks(playlist.id).map { it.id })
                            provider.removeTrackFromPlaylist(playlist.id, tracks[0])
                            assertEquals(listOf(tracks[1]), provider.playlistTracks(playlist.id).map { it.id })
                            provider.addTracksToPlaylist(playlist.id, listOf(tracks[0]))
                            assertEquals(listOf(tracks[1], tracks[0]), provider.playlistTracks(playlist.id).map { it.id })
                            record("playlist create/duplicate-removal/add passed")
                        } finally {
                            withContext(NonCancellable) {
                                withTimeout(30_000) { provider.deletePlaylist(playlist.id) }
                            }
                            record("disposable playlist deleted")
                        }
                    }
                    if (providerId == "navidrome" && InstrumentationRegistry.getArguments().getString("liveCatalogScale") == "true") {
                        stage = "complete album catalog"
                        var snapshot: AlbumCatalogSnapshot? = null
                        val repository = object : AlbumCatalogRepository {
                            override fun readAlbumCatalog(scope: AlbumCatalogScope) = snapshot
                            override fun replaceAlbumCatalog(scope: AlbumCatalogScope, value: AlbumCatalogSnapshot) { snapshot = value }
                        }
                        val index = AlbumLibraryIndex(repository, { source.id }, System::currentTimeMillis)
                        val scope = index.scope(provider)
                        val started = System.nanoTime()
                        var pages = 0
                        val result = assertNotNull(index.refresh(scope, provider, onProgress = { pages++ }))
                        assertTrue(result.albums.isNotEmpty())
                        assertEquals(result.albums.size, result.albums.map { it.id }.distinct().size)
                        assertEquals(result, index.snapshot(scope))
                        record("complete album catalog=${result.albums.size} pages=$pages elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
                    }
                }
            }
        } catch (failure: Throwable) {
            // Provider exception messages can contain authenticated URLs. Report only stage and type.
            val code = (failure as? NavidromeException)?.subsonicErrorCode
            val http = (failure as? NavidromeHttpException)?.statusCode
            throw AssertionError("$providerId failed at $stage (${failure.javaClass.simpleName}; subsonic=$code http=$http)")
        }
    }
    /** Native network toggles surround the real shared membership controller and provider calls. */
    private suspend fun checkInterruptedSaves(provider: MediaProvider, record: (String) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }
        val wifi = shell("settings get global wifi_on") == "1"
        val data = shell("settings get global mobile_data") == "1"
        fun restoreNetwork() {
            shell("svc wifi ${if (wifi) "enable" else "disable"}")
            shell("svc data ${if (data) "enable" else "disable"}")
        }
        suspend fun awaitConnection() {
            withTimeout(45_000) {
                while (runCatching { withTimeout(5_000) { provider.playlists(1) } }.isFailure) delay(500)
            }
        }
        val tracks = provider.tracksPage(MediaPageRequest(limit = 2)).items.distinctBy { it.id }
        assertTrue(tracks.size >= 2)
        val target = tracks[0]
        val other = tracks[1]
        val playlist = provider.createPlaylist("Naviamp interruption ${System.currentTimeMillis()}", listOf(other.id))
        try {
            for (loseResponse in listOf(false, true)) {
                provider.removeTrackFromPlaylist(playlist.id, target.id)
                var injecting = true
                var failedWrite = false
                val controlled = object : MediaProvider by provider {
                    override suspend fun playlists(limit: Int): List<Playlist> = listOf(playlist)
                    override suspend fun playlistTracks(playlistId: String): List<Track> = try {
                        withTimeoutOrNull(5_000) { provider.playlistTracks(playlistId) }
                            ?: throw java.io.IOException("Acceptance read timed out")
                    } catch (cause: Exception) {
                        record("interruption read failure=${cause.javaClass.simpleName}")
                        throw cause
                    }
                    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
                        if (!injecting) { provider.addTracksToPlaylist(playlistId, trackIds); return }
                        if (loseResponse) {
                            provider.addTracksToPlaylist(playlistId, trackIds)
                            failedWrite = true
                            throw java.io.IOException("Acceptance response lost after commit")
                        }
                        record("disabling data")
                        shell("svc data disable")
                        record("disabling wifi")
                        shell("svc wifi disable")
                        val connectivity = instrumentation.targetContext.getSystemService(ConnectivityManager::class.java)
                        withTimeout(20_000) {
                            while (connectivity.activeNetwork != null) delay(250)
                        }
                        record("no active network confirmed")
                        val result = runCatching {
                            withTimeout(5_000) { provider.addTracksToPlaylist(playlistId, trackIds) }
                        }
                        record("offline request failed=${result.isFailure} type=${result.exceptionOrNull()?.javaClass?.simpleName}")
                        failedWrite = result.isFailure
                        check(failedWrite) { "Offline write unexpectedly succeeded" }
                        throw java.io.IOException("Acceptance offline write failed")
                    }
                }
                var editor: NaviampTrackPlaylistMembershipUi? = null
                val coordinator = NaviampCorePlaylistMembershipCoordinator(
                    { controlled }, { editor }, { editor = it },
                )
                coordinator.open(target)
                record("interruption editor rows=${editor?.rows?.size} loadingFailed=${editor?.loadingFailed} editable=${playlist.canEdit} rows=${editor?.rows?.map { "selected=${it.selected},failed=${it.failed},ruleBased=${it.ruleBased}" }}")
                coordinator.toggle(playlist.id)
                try { coordinator.apply() } finally { restoreNetwork() }
                record("interruption attempted=$failedWrite saved=${editor?.saved} saving=${editor?.saving} failedRows=${editor?.rows?.count { it.failed }}")
                assertTrue(failedWrite)
                assertFalse(editor!!.saved)
                assertFalse(editor!!.saving)
                assertTrue(editor!!.rows.single().failed)
                awaitConnection()
                injecting = false
                coordinator.retry()
                if (!editor!!.rows.single().selected) coordinator.toggle(playlist.id)
                coordinator.apply()
                record("retry saved=${editor?.saved} failedRows=${editor?.rows?.count { it.failed }}")
                assertTrue(editor!!.saved)
                assertEquals(listOf(other.id, target.id), provider.playlistTracks(playlist.id).map { it.id })
                record("${if (loseResponse) "lost-response" else "real-offline"} failure/reconciliation/retry passed; no duplicates")
            }
        } finally {
            withContext(NonCancellable) {
                restoreNetwork()
                awaitConnection()
                withTimeout(30_000) { provider.deletePlaylist(playlist.id) }
            }
            record("interruption playlist deleted; network restored")
        }
    }

}
