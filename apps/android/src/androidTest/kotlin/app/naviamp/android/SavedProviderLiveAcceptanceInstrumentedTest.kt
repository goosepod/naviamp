package app.naviamp.android

import android.net.ConnectivityManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.presentation.*
import app.naviamp.ui.NaviampConnectionSettingsUi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.CompletableDeferred
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.presentation.NaviampCorePlaylistMembershipCoordinator
import app.naviamp.ui.NaviampTrackPlaylistMembershipUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.awaitCancellation
import org.json.JSONObject
import app.naviamp.domain.network.SharedHttpClient
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
                    val provider = restoreProvider(source)
                    if (InstrumentationRegistry.getArguments().getString("liveProcessDeathPhase") != null) {
                        stage = "process death"
                        checkProcessDeath(storage, source.id, provider, providerId, ::record)
                        return@use
                    }
                    if (InstrumentationRegistry.getArguments().getString("liveDownloadFailures") == "true") {
                        stage = "download failures"
                        checkDownloadFailures(storage, source.id, provider, providerId, ::record)
                        return@use
                    }
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
            val testLine = failure.stackTrace.firstOrNull { it.fileName == "SavedProviderLiveAcceptanceInstrumentedTest.kt" }?.lineNumber
            throw AssertionError("$providerId failed at $stage (${failure.javaClass.simpleName}; testLine=$testLine subsonic=$code http=$http)")
        }
    }
    private suspend fun restoreProvider(source: SavedMediaSource): MediaProvider = if (source.providerId == "jellyfin") {
        val factory = JellyfinSessionServiceFactory { tls -> JellyfinSessionService(
            KtorJellyfinHttpClient(createDefaultNavidromeKtorClient(tls)),
            jellyfinClientIdentity("naviamp-android", "Android"),
        ) }
        JellyfinProvider(factory.create(source.tlsSettings).restore(source), factory)
    } else NavidromeProvider(source.toNavidromeConnection())

    private suspend fun checkDownloadFailures(storage: AndroidStorageDependencies, sourceId: String,
        provider: MediaProvider, providerId: String, record: (String) -> Unit) = coroutineScope {
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
        suspend fun connected() = withTimeout(45_000) {
            while (runCatching { withTimeout(5_000) { provider.playlists(1) } }.isFailure) delay(250)
            // Let Wi-Fi/default-route handover and negative DNS results settle before the next case.
            delay(10_000)
            provider.playlists(1)
        }
        val tracks = provider.tracksPage(MediaPageRequest(limit = 20)).items.distinctBy { it.id }
            .filter { storage.downloadedAudioFile(sourceId, it.id) == null }.take(2)
        assertEquals(2, tracks.size)
        val original = tracks[0]
        val target = tracks[1]
        val directory = File(instrumentation.targetContext.cacheDir, "acceptance-download-failures-$providerId")
        check(!directory.exists())
        directory.mkdirs()
        storage.updateDownloadDirectory(directory)
        suspend fun download(p: MediaProvider = provider) = storage.downloadAudioTrack(
            sourceId, p, target, StreamQuality.Original, Long.MAX_VALUE,
        )
        fun noPartialFiles() = assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        try {
            val saved = storage.downloadAudioTrack(sourceId, provider, original, StreamQuality.Original, Long.MAX_VALUE)
            record("reference download saved")
            val originalHash = java.security.MessageDigest.getInstance("SHA-256").digest(File(saved.filePath).readBytes())
            suspend fun originalIntact() {
                assertEquals(saved, storage.downloadedAudioFile(sourceId, original.id))
                kotlin.test.assertContentEquals(originalHash,
                    java.security.MessageDigest.getInstance("SHA-256").digest(File(saved.filePath).readBytes()))
            }
            // Hold a real stream after a partial write while Android tears down its network.
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var firstChunk = true
            val cut = object : MediaProvider by provider {
                override suspend fun downloadStream(url: String, httpClient: SharedHttpClient,
                    writeChunk: suspend (ByteArray, Int) -> Unit): Boolean =
                    provider.downloadStream(url, httpClient) { bytes, count ->
                        writeChunk(bytes, count)
                        if (firstChunk) { firstChunk = false; entered.complete(Unit); release.await() }
                    }
            }
            val interrupted = async { runCatching { withTimeout(60_000) { download(cut) } } }
            entered.await()
            record("partial transfer held; disabling network")
            shell("svc data disable"); shell("svc wifi disable")
            // VPN/control networks may remain registered after user data is disabled.
            // Require a fresh real provider request to fail before releasing the held stream.
            withTimeout(20_000) {
                while (runCatching { withTimeout(1_500) { provider.playlists(1) } }.isSuccess) delay(250)
            }
            delay(10_000)
            assertTrue(runCatching { withTimeout(1_500) { provider.playlists(1) } }.isFailure)
            record("fresh provider requests confirmed sustained network loss")
            release.complete(Unit)
            val interruptedResult = interrupted.await()
            if (interruptedResult.isSuccess) {
                // A fully buffered response may finish safely even after connectivity disappears.
                record("mid-transfer network loss completed buffered audio; checking fresh offline request")
                storage.removeDownloadedAudio(sourceId, target.id)
                assertTrue(runCatching { withTimeout(5_000) { download() } }.isFailure)
            } else record("mid-transfer network loss failed as expected")
            assertNull(storage.downloadedAudioFile(sourceId, target.id))
            noPartialFiles()
            originalIntact()
            restoreNetwork(); connected()
            assertTrue(download().sizeBytes > 4096)
            storage.removeDownloadedAudio(sourceId, target.id)
            record("network failure/reconnect/retry passed; existing file preserved")

            val cancelEntered = CompletableDeferred<Unit>()
            val cancellable = object : MediaProvider by provider {
                override suspend fun downloadStream(url: String, httpClient: SharedHttpClient,
                    writeChunk: suspend (ByteArray, Int) -> Unit): Boolean =
                    provider.downloadStream(url, httpClient) { bytes, count ->
                        writeChunk(bytes, count); cancelEntered.complete(Unit); awaitCancellation()
                    }
            }
            val cancelling = async { download(cancellable) }
            cancelEntered.await()
            cancelling.cancel()
            val immediateRetry = async { download() }
            cancelling.join()
            assertTrue(immediateRetry.await().sizeBytes > 4096)
            noPartialFiles(); originalIntact()
            storage.removeDownloadedAudio(sourceId, target.id)
            record("cancel/immediate retry passed; existing file preserved")

            assertTrue(runCatching {
                storage.downloadAudioTrack(sourceId, provider, target, StreamQuality.Original, 0)
            }.isFailure)
            assertNull(storage.downloadedAudioFile(sourceId, target.id))
            assertTrue(runCatching {
                storage.replaceDownloadedAudioTrack(sourceId, provider, original, StreamQuality.Original, 0)
            }.isFailure)
            noPartialFiles(); originalIntact()
            record("new download and replacement quota failures preserved original bytes and row")

            val otherSource = storage.mediaSources().first { it.providerId != providerId }
            record("switch restoring second provider")
            val otherProvider = restoreProvider(otherSource)
            record("switch second provider restored")
            val gate = CompletableDeferred<Unit>()
            val started = CompletableDeferred<Unit>()
            val old = object : MediaProvider by provider {
                override suspend fun downloadStream(url: String, httpClient: SharedHttpClient,
                    writeChunk: suspend (ByteArray, Int) -> Unit): Boolean =
                    provider.downloadStream(url, httpClient) { bytes, count ->
                        writeChunk(bytes, count); started.complete(Unit); gate.await()
                        throw java.io.IOException("Acceptance old-source failure")
                    }
            }
            val services = repositoryNaviampCoreDownloadServices(storage, storage, storage,
                toCoreDownload = { NaviampCoreDownloadedTrack(it.filePath, it.track, it.sizeBytes, it.qualityKey) },
                isStoredDownloadAvailable = { File(it.filePath).isFile })
            val state = NaviampCoreStateStore()
            fun select(id: String) = state.updateShell { it.copy(
                connectionSettings = NaviampConnectionSettingsUi(currentSourceId = id),
                cache = it.cache.copy(settings = it.cache.settings.copy(maxDownloadBytes = Long.MAX_VALUE))) }
            select(sourceId)
            var active: MediaProvider = old
            var requests = 0
            val controller = NaviampCoreDownloadsController(this, state, { active }, services.storage,
                NaviampCoreDownloadTransferPort { request, status, update ->
                    requests++; services.transfer.transfer(request, status, update)
                }, services.keepDownloaded, NaviampCoreDownloadedPlaybackPort { _, _ -> })
            val launched = controller.downloadTracks("Acceptance", listOf(target))
            record("switch transfer launched=$launched")
            try { withTimeout(30_000) { started.await() } }
            catch (cause: Exception) {
                record("switch checkpoint failed; requests=$requests retryable=${state.state.value.shell.downloads.jobs.map { it.canRetry }}")
                throw cause
            }
            record("switch transfer held")
            val oldJob = state.state.value.shell.downloads.jobs.single().id
            active = otherProvider; select(otherSource.id); controller.resetForSourceChange()
            controller.refresh(reconcile = false)
            val otherStatus = state.state.value.shell.downloads.status
            gate.complete(Unit)
            // The second real provider remains selected while the old transfer unwinds.
            withTimeout(10_000) { while (directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") }) delay(25) }
            delay(100)
            assertEquals(otherStatus, state.state.value.shell.downloads.status)
            assertTrue(state.state.value.shell.downloads.jobs.isEmpty())
            controller.execute(NaviampCoreCommand.Downloads.RetryJob(oldJob))
            assertEquals(1, requests)
            active = provider; select(sourceId); controller.resetForSourceChange()
            controller.refresh(reconcile = false)
            assertTrue(state.state.value.shell.downloads.jobs.single().canRetry)
            controller.execute(NaviampCoreCommand.Downloads.RetryJob(oldJob))
            withTimeout(45_000) { while (state.state.value.shell.downloads.jobs.isNotEmpty()) delay(50) }
            assertEquals(2, requests)
            assertNotNull(storage.downloadedAudioFile(sourceId, target.id))
            noPartialFiles(); originalIntact()
            record("live provider switch isolated old job; retry resumed only on original source")
        } finally {
            val children = currentCoroutineContext().job.children.toList()
            children.forEach { it.cancel() }
            withContext(NonCancellable) {
                children.joinAll()
                restoreNetwork()
                for (track in tracks) storage.removeDownloadedAudio(sourceId, track.id)
                directory.deleteRecursively()
                connected()
            }
            record("download failure test files removed; network restored")
        }
    }

    /** Two instrumented processes, with a host ADB force-stop at the explicit checkpoint. */
    private suspend fun checkProcessDeath(
        storage: AndroidStorageDependencies, sourceId: String, provider: MediaProvider,
        providerId: String, record: (String) -> Unit,
    ) {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("liveProcessDeathPhase")!!
        val scenario = args.getString("liveProcessDeathCase")!!
        require(scenario in setOf("playlist-before", "playlist-after", "download-partial", "download-complete"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = File(context.filesDir, "acceptance-death-$providerId-$scenario.json")
        val directory = File(context.cacheDir, "acceptance-death-$providerId-$scenario")
        suspend fun checkpoint(): Nothing {
            record("PROCESS_READY $scenario")
            awaitCancellation() // Host force-stops the target, so finally blocks cannot simulate recovery.
        }
        if (phase == "prepare") {
            check(!marker.exists()) { "Previous recovery must finish first" }
            val sample = provider.tracksPage(MediaPageRequest(limit = 20)).items.distinctBy { it.id }
            val available = sample.filter { storage.downloadedAudioFile(sourceId, it.id) == null }
            assertTrue(available.size >= 2)
            val target = available[0]
            val other = available[1]
            val metadata = JSONObject().put("source", sourceId).put("target", target.id.value)
                .put("other", other.id.value)
            if (scenario.startsWith("playlist")) {
                val playlist = provider.createPlaylist("Naviamp termination ${System.currentTimeMillis()}", listOf(other.id))
                metadata.put("playlist", playlist.id)
                marker.writeText(metadata.toString())
                val controlled = object : MediaProvider by provider {
                    override suspend fun playlists(limit: Int) = listOf(playlist)
                    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
                        if (scenario == "playlist-after") provider.addTracksToPlaylist(playlistId, trackIds)
                        checkpoint()
                    }
                }
                var editor: NaviampTrackPlaylistMembershipUi? = null
                val coordinator = NaviampCorePlaylistMembershipCoordinator({ controlled }, { editor }, { editor = it })
                coordinator.open(target)
                coordinator.toggle(playlist.id)
                coordinator.apply()
                error("Checkpoint was not reached")
            } else {
                check(!directory.exists())
                directory.mkdirs()
                storage.updateDownloadDirectory(directory)
                marker.writeText(metadata.toString())
                var bytesWritten = 0L
                val controlled = object : MediaProvider by provider {
                    override suspend fun downloadStream(url: String, httpClient: SharedHttpClient,
                        writeChunk: suspend (ByteArray, Int) -> Unit): Boolean =
                        provider.downloadStream(url, httpClient) { bytes, count ->
                            writeChunk(bytes, count)
                            bytesWritten += count
                            if (scenario == "download-partial" && bytesWritten >= 16_384) checkpoint()
                        }
                }
                storage.downloadAudioTrack(sourceId, controlled, target, StreamQuality.Original, 500_000_000)
                assertTrue(bytesWritten > 4096)
                checkpoint()
            }
        }
        require(phase == "recover")
        check(marker.exists())
        val metadata = JSONObject(marker.readText())
        assertEquals(sourceId, metadata.getString("source"))
        val targetId = TrackId(metadata.getString("target"))
        val otherId = TrackId(metadata.getString("other"))
        val target = provider.tracksPage(MediaPageRequest(limit = 20)).items.first { it.id == targetId }
        if (scenario.startsWith("playlist")) {
            val playlistId = metadata.getString("playlist")
            try {
                val before = provider.playlistTracks(playlistId).map { it.id }
                assertEquals(if (scenario == "playlist-after") listOf(otherId, targetId) else listOf(otherId), before)
                val controlled = object : MediaProvider by provider {
                    override suspend fun playlists(limit: Int) = listOf(Playlist(playlistId, "Acceptance", before.size))
                }
                var editor: NaviampTrackPlaylistMembershipUi? = null
                val coordinator = NaviampCorePlaylistMembershipCoordinator({ controlled }, { editor }, { editor = it })
                coordinator.open(target)
                assertEquals(scenario == "playlist-after", editor!!.rows.single().selected)
                assertFalse(editor!!.saving)
                if (!editor!!.rows.single().selected) coordinator.toggle(playlistId)
                coordinator.apply()
                assertTrue(editor!!.saved)
                assertEquals(listOf(otherId, targetId), provider.playlistTracks(playlistId).map { it.id })
                record("$scenario recovery passed; exact contents, no duplicate")
            } finally {
                withContext(NonCancellable) { withTimeout(30_000) { provider.deletePlaylist(playlistId) } }
                marker.delete()
                record("termination playlist deleted")
            }
        } else {
            storage.updateDownloadDirectory(directory)
            try {
                val existing = storage.downloadedAudioFile(sourceId, targetId)
                if (scenario == "download-partial") {
                    assertNull(existing)
                    assertTrue(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") && it.length() >= 16_384 })
                } else assertNotNull(existing)
                var requests = 0
                val counted = object : MediaProvider by provider {
                    override suspend fun downloadStream(url: String, httpClient: SharedHttpClient,
                        writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
                        requests++
                        return provider.downloadStream(url, httpClient, writeChunk)
                    }
                }
                val stored = storage.downloadAudioTrack(sourceId, counted, target, StreamQuality.Original, 500_000_000)
                assertEquals(if (scenario == "download-partial") 1 else 0, requests)
                assertTrue(stored.sizeBytes > 4096)
                assertEquals(stored.sizeBytes, File(stored.filePath).length())
                assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
                assertEquals(stored, storage.downloadAudioTrack(sourceId, counted, target, StreamQuality.Original, 500_000_000))
                assertEquals(if (scenario == "download-partial") 1 else 0, requests)
                val bass = AndroidBassJni.load().getOrThrow()
                assertTrue(bass.init())
                try {
                    val stream = bass.createFileDecodeStream(stored.filePath)
                    assertTrue(stream != 0)
                    try { assertTrue(bass.readFloatData(stream, FloatArray(4096)) > 0) }
                    finally { bass.freeStream(stream) }
                } finally { bass.free() }
                record("$scenario recovery passed; requests=$requests; native decode passed")
            } finally {
                storage.removeDownloadedAudio(sourceId, targetId)
                directory.deleteRecursively() // Exclusively this test's isolated directory.
                marker.delete()
                record("termination download removed")
            }
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
