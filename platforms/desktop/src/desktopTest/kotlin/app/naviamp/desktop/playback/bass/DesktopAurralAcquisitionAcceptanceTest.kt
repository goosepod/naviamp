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
    /** Explicitly selected only after approval: one existing Library track, one timestamped listen. */
    @Test fun oneApprovedExistingTrackQualifiesOnceThroughRealNativeProgress() = runBlocking {
        val approvedId = System.getenv("NAVIAMP_AURRAL_APPROVED_LISTEN_TRACK_ID") ?: return@runBlocking
        val marker = Path.of(checkNotNull(System.getenv("NAVIAMP_AURRAL_LISTEN_ATTEMPT_FILE")))
        require(!Files.exists(marker)) { "This listen approval already has an attempt; never replay it" }
        val connection = NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
            baseUrl = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_URL")),
            username = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_USER")),
            password = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_PASSWORD")))
        require(connection.username == "test") { "Only the dedicated test account is authorized" }
        val provider = NavidromeProvider(connection)
        provider.validateConnection()
        val track = assertNotNull(provider.track(app.naviamp.domain.TrackId(approvedId)))
        require(!track.id.isDiscoveryPreview())
        val duration = assertNotNull(track.durationSeconds)
        require(duration >= 30)
        val threshold = minOf(duration / 2.0, 240.0)
        var attempts = 0
        var submissions = 0
        val reported = mutableListOf<app.naviamp.domain.TrackId>()
        val guarded = object : MediaProvider by provider {
            override suspend fun reportLegacyNowPlaying(trackId: app.naviamp.domain.TrackId) {
                require(trackId == track.id)
                reported += trackId
                provider.reportLegacyNowPlaying(trackId)
            }
            override suspend fun reportPlaybackState(trackId: app.naviamp.domain.TrackId,
                state: PlaybackReportState, positionSeconds: Double?) {
                require(trackId == track.id)
                reported += trackId
                provider.reportPlaybackState(trackId, state, positionSeconds)
            }
            override suspend fun submitListen(trackId: app.naviamp.domain.TrackId, startedAtEpochMillis: Long) {
                require(trackId == track.id && attempts == 0)
                attempts++
                Files.createFile(marker) // Persist the no-replay boundary before touching the server.
                provider.submitListen(trackId, startedAtEpochMillis)
                submissions++
            }
        }
        val reporter = app.naviamp.app.NaviampListenReporting()
        val preview = track.copy(id = app.naviamp.domain.TrackId("discovery-preview:42"))
        val before = System.currentTimeMillis()
        reporter.observe(guarded, preview, PlaybackState.Playing, PlaybackProgress(0.0, duration.toDouble()), before)
        reporter.observe(guarded, preview, PlaybackState.Finished, PlaybackProgress(duration.toDouble(), duration.toDouble()),
            before + duration * 1000L)
        assertTrue(reported.isEmpty())
        assertEquals(0, attempts)
        println("Preview policy crossing a simulated qualification threshold: zero presence and listen requests")
        val media = Files.createTempFile("naviamp-aurral-qualified-listen-", ".media")
        val engine = CoreBassPlaybackEngine(
            Result.success(DesktopBassAudioBackend(DesktopBassJniBinding.load().getOrThrow())), DesktopBassPlaybackEngineRuntime())
        try {
            Files.newOutputStream(media).use { output ->
                assertTrue(provider.streamTrackBytes(StreamRequest(track.id, StreamQuality.Original), null, false,
                    onResponse = { assertTrue(it.statusCode in listOf(200, 206)) },
                    writeChunk = { bytes, count -> output.write(bytes, 0, count) }))
            }
            val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
            val progress = MutableStateFlow(PlaybackProgress.Unknown)
            engine.setVolume(0)
            engine.play(this, PlaybackRequest(media.toUri().toString()), { state.value = it }, { progress.value = it })
            withTimeout(((threshold + 40) * 1000).toLong()) {
                var printed = -1
                while (submissions == 0) {
                    delay(1000)
                    assertTrue(state.value !is PlaybackState.Error)
                    reporter.observe(guarded, track, state.value, progress.value, System.currentTimeMillis())
                    check(attempts == 0 || submissions == 1) { "Listen result is uncertain; preserve marker and never replay" }
                    if ((progress.value.positionSeconds ?: 0.0) < threshold - 1) assertEquals(0, submissions)
                    val bucket = ((progress.value.positionSeconds ?: 0.0) / 30).toInt()
                    if (bucket != printed) {
                        printed = bucket
                        println("Muted native listen progress ${progress.value.positionSeconds}s; qualification threshold=${threshold}s; submissions=$submissions")
                    }
                }
            }
            repeat(3) {
                delay(1000)
                reporter.observe(guarded, track, state.value, progress.value, System.currentTimeMillis())
            }
            assertEquals(1, submissions)
            assertEquals(1, attempts)
            println("Exactly one approved listen accepted after real native progress; repeated observations did not resubmit")
        } finally { engine.release(); Files.deleteIfExists(media) }
    }

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
        var offline = false
        val reads = object : NavidromeHttpClient by transport {
            override suspend fun get(url: String, headers: Map<String, String>): String {
                check(!offline) { "Simulated connection loss for read-only recovery acceptance" }
                return transport.get(url, headers)
            }
            override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
                check(!offline) { "Simulated connection loss for read-only recovery acceptance" }
                require(url == "$base/api/auth/login" || url == "$base/api/library/albums/lookup/batch") { "Reconciliation cannot submit mutations" }
                return transport.postJson(url, body, headers)
            }
            override suspend fun postJsonResponse(url: String, body: String, headers: Map<String, String>) =
                NavidromeHttpResponse(postJson(url, body, headers))
            override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String = error("Read-only acceptance")
            override suspend fun deleteJson(url: String, headers: Map<String, String>): String = error("Read-only acceptance")
        }
        var provider = NavidromeProvider(connection, reads)
        provider.validateConnection()
        var discovery = assertNotNull(provider.discovery)
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
            val sources = StorageMediaSourceStore(db.naviampStorageQueries, System::currentTimeMillis)
            val savedSources = sources.mediaSources()
            require(savedSources.size <= 1) { "The isolated acceptance database contains unexpected sources" }
            val source = savedSources.singleOrNull()?.id ?: sources.upsertProviderMediaSource(
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
            val beforeDisconnect = assertNotNull(store.record(scope, recordId))
            assertTrue(store.records(scope.copy(accountKey = scope.accountKey + ":different-account")).isEmpty())
            offline = true
            try {
                val stale = tracker.refresh(scope, discovery)
                assertEquals(ProviderDiscoveryStatus.Unavailable, stale.status)
                val retained = stale.records.single { it.id == recordId }
                // Availability requires a fresh validated Library bridge. Offline keeps the
                // accepted identity but cannot advertise a stale playable destination.
                assertEquals(if (beforeDisconnect.phase == DiscoveryAcquisitionPhase.Available)
                    DiscoveryAcquisitionPhase.Complete else beforeDisconnect.phase, retained.phase)
                assertEquals(beforeDisconnect.operation.identity, retained.operation.identity)
                assertTrue(retained.operation.status.blocksRepeatedRequest)
                assertEquals(ProviderDiscoveryStatus.Unavailable, stale.ownership[recordId]?.status)
            } finally { offline = false }
            provider = NavidromeProvider(connection, reads)
            provider.validateConnection()
            discovery = assertNotNull(provider.discovery)
            assertEquals(scope.accountKey, provider.discoveryAccountScope)
            println("SQL-reopened request survives simulated connection loss and account isolation; recreated provider reconnects without submission")

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
