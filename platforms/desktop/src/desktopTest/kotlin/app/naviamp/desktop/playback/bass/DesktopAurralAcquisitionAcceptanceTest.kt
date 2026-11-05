package app.naviamp.desktop.playback.bass

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import app.naviamp.storage.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.test.*

/** Read-only after the separately approved one-shot submission; never has an acquisition action. */
class DesktopAurralAcquisitionAcceptanceTest {
    @Test fun submittedRecordingReconcilesAfterDatabaseReopenAndPlaysRealNativeAudio() = runBlocking {
        val candidatePath = System.getenv("NAVIAMP_AURRAL_CANDIDATE_FILE")?.let(Path::of) ?: return@runBlocking
        val attempted = candidatePath.resolveSibling(candidatePath.fileName.toString() + ".attempted")
        require(Files.exists(attempted)) { "This check only observes an existing one-shot attempt" }
        fun read(path: Path) = Json.parseToJsonElement(Files.readString(path)).jsonObject
        fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        val candidate = read(candidatePath)
        val response = read(candidatePath.resolveSibling(candidatePath.fileName.toString() + ".result.json"))
        val connection = NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
            baseUrl = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_URL")), username = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_USER")),
            password = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_PASSWORD")),
            tlsSettings = NavidromeTlsSettings(insecureSkipTlsVerification = System.getenv("NAVIAMP_AURRAL_TEST_INSECURE") == "true"))
        val transport = createDefaultNavidromeHttpClient(connection.tlsSettings)
        val base = connection.normalizedBaseUrl
        val reads = object : NavidromeHttpClient by transport {
            override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
                require(url == "$base/api/auth/login" || url == "$base/api/library/albums/lookup/batch") { "Reconciliation cannot submit mutations" }
                return transport.postJson(url, body, headers)
            }
            override suspend fun postJsonResponse(url: String, body: String, headers: Map<String, String>) =
                NavidromeHttpResponse(postJson(url, body, headers))
            override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String = error("Read-only acceptance")
            override suspend fun deleteJson(url: String, headers: Map<String, String>): String = error("Read-only acceptance")
        }
        val provider = NavidromeProvider(connection, reads)
        provider.validateConnection()
        val discovery = assertNotNull(provider.discovery)
        val recordId = DiscoveryAcquisitionTracker.key(checkNotNull(candidate.text("artistMbid")),
            ProviderDiscoveryAction.RequestTrack(checkNotNull(candidate.text("trackMbid"))))
        val manager = response.text("manager")?.let(DiscoveryLibraryManager::valueOf)
        val identity = ProviderDiscoveryRequestIdentity(
            response.text("requestGroupId")?.let(::DiscoveryRequestGroupId),
            (response["jobIds"] as? JsonArray).orEmpty().map { DiscoveryJobId(it.jsonPrimitive.content) },
            response.text("canonicalAlbumId")?.let(::DiscoveryCanonicalAlbumId),
            response.text("managedAlbumId")?.let { id -> manager?.let { DiscoveryManagedAlbumId(it, id) } })
        val operation = ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.valueOf(checkNotNull(response.text("status"))), identity)
        val database = candidatePath.resolveSibling(candidatePath.fileName.toString() + ".reconciliation.db")
        val url = "jdbc:sqlite:$database"
        val scope = JdbcSqliteDriver(url).use { driver ->
            val db = initializeNaviampStorageDatabase(driver)
            // No credential is persisted in this acceptance database.
            val source = StorageMediaSourceStore(db.naviampStorageQueries, System::currentTimeMillis).upsertProviderMediaSource(
                ProviderMediaSourceConnection("Aurral acceptance", "https://acceptance.invalid", "test", "", ""), "accepted-live", "aurral").id
            val scope = DiscoveryAcquisitionScope(source, provider.discoveryAccountScope)
            val store = StorageDiscoveryAcquisitionStore(db.naviampStorageQueries)
            if (store.record(scope, recordId) == null) store.save(scope, DiscoveryAcquisitionRecord(recordId,
                checkNotNull(candidate.text("artistMbid")), checkNotNull(candidate.text("artistName")), checkNotNull(candidate.text("trackMbid")), false,
                candidate.text("albumMbid"), candidate.text("albumName"), operation, operation.acquisitionPhase(), System.currentTimeMillis(), trackTitle = candidate.text("trackName")))
            scope
        }
        var owned: ProviderDiscoveryReleaseOwnership? = null
        JdbcSqliteDriver(url).use { driver ->
            val store = StorageDiscoveryAcquisitionStore(initializeNaviampStorageDatabase(driver).naviampStorageQueries)
            val tracker = DiscoveryAcquisitionTracker(store, System::currentTimeMillis)
            assertEquals(identity, store.record(scope, recordId)?.operation?.identity)
            for (attempt in 0 until 8) {
                val snapshot = tracker.refresh(scope, discovery)
                val record = snapshot.records.single { it.id == recordId }
                println("Existing approved request reconciliation ${attempt + 1}: ${record.phase}; service=${snapshot.status}")
                owned = snapshot.ownership[recordId]
                if (record.phase == DiscoveryAcquisitionPhase.Available) break
                val wait = DiscoveryAcquisitionTracker.nextPollMillis(snapshot, attempt + 1, 0) ?: break
                delay(wait)
            }
            assertEquals(DiscoveryAcquisitionPhase.Available, store.record(scope, recordId)?.phase,
                "Request is not yet validated as playable; preserve its state and never acquire again")
        }
        val ownership = assertNotNull(owned)
        assertTrue(DiscoveryRecordingMbid(checkNotNull(candidate.text("trackMbid"))) in ownership.ownedRecordings)
        val album = provider.album(assertNotNull(ownership.libraryAlbum).id)
        val track = album.tracks.single() // The explicitly approved candidate is a one-recording single.
        val media = Files.createTempFile("naviamp-aurral-acquired-", ".media")
        val binding = DesktopBassJniBinding.load().getOrThrow()
        val backend = DesktopBassAudioBackend(binding)
        val handle = MutableStateFlow<BassStreamHandle?>(null)
        val observed = object : BassAudioBackend by backend {
            override fun positionSeconds(stream: BassStreamHandle): Double? = backend.positionSeconds(stream).also { handle.value = stream }
        }
        val engine = CoreBassPlaybackEngine(Result.success(observed), DesktopBassPlaybackEngineRuntime())
        try {
            var bytes = 0L
            Files.newOutputStream(media).use { output ->
                assertTrue(provider.streamTrackBytes(StreamRequest(track.id, StreamQuality.Original), rangeHeader = null, headOnly = false,
                    onResponse = { assertTrue(it.statusCode in listOf(200, 206)) },
                    writeChunk = { chunk, count -> output.write(chunk, 0, count); bytes += count }))
            }
            assertTrue(bytes > 1000)
            val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
            val progress = MutableStateFlow(PlaybackProgress.Unknown)
            engine.setVolume(0)
            engine.play(this, PlaybackRequest(media.toUri().toString()), { state.value = it }, { progress.value = it })
            withTimeout(10_000) { combine(state, progress) { status, clock ->
                check(status !is PlaybackState.Error) { "Native acquired-media playback failed" }; clock
            }.first { (it.positionSeconds ?: 0.0) > .2 } }
            engine.pause(); engine.seek(1.0)
            withTimeout(10_000) { progress.first { abs((it.positionSeconds ?: -1.0) - 1.0) < .15 } }
            assertEquals(PlaybackState.Paused, state.value)
            assertTrue(abs(assertNotNull(handle.value).let { assertNotNull(backend.positionSeconds(it)) } - 1.0) < .3)
            engine.resume()
            withTimeout(10_000) { progress.first { (it.positionSeconds ?: 0.0) > 1.2 } }
            println("Approved recording became validated Library media after SQL reopen; streamed $bytes bytes; muted real BASS play/pause/seek/resume advanced to ${progress.value.positionSeconds} seconds.")
            engine.stop()
        } finally { engine.release(); Files.deleteIfExists(media) }
    }
}
