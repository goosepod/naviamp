package app.naviamp.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.desktop.playback.bass.DesktopBassJniBinding
import app.naviamp.desktop.playback.bass.DesktopBassLibraryResolver
import app.naviamp.desktop.security.DesktopCredentialProtector
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import app.naviamp.provider.navidrome.KtorNavidromeHttpClient
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.provider.navidrome.createDefaultNavidromeKtorClient
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageMediaSourceStore
import app.naviamp.testkit.NaviampMutualTlsFixture
import app.naviamp.desktop.playback.bass.DesktopBassAudioBackend
import app.naviamp.desktop.playback.bass.DesktopBassPlaybackEngineRuntime
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.presentation.NaviampCorePlaybackEngineAdapter
import app.naviamp.presentation.NaviampCoreMediaProviderSource
import app.naviamp.presentation.NaviampCorePlaybackObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercises the real SQLDelight, encrypted credential, provider TLS, file and BASS boundaries. */
class DesktopMutualTlsIntegrationTest {
    @Test fun uncachedMutualTlsPlaybackStartsBeforeTheDownloadCompletesAndSeeks() =
        exerciseNormalPlayback(serverRequiresIdentity = true, provideIdentity = true)

    @Test fun uncachedPlaybackUsesThePrivateServerAuthorityWithoutAClientIdentity() =
        exerciseNormalPlayback(serverRequiresIdentity = false, provideIdentity = false)

    @Test fun normalPlaybackRejectsAMissingClientIdentityWithoutInsecureFallback() =
        exerciseNormalPlayback(serverRequiresIdentity = true, provideIdentity = false)

    @Test fun uncachedOrdinaryHttpPlaybackSeeksBeyondTheBufferedPrefixWhilePaused() =
        exerciseNormalPlayback(serverRequiresIdentity = false, provideIdentity = false, useTls = false)

    private fun exerciseNormalPlayback(serverRequiresIdentity: Boolean, provideIdentity: Boolean, useTls: Boolean = true) = runBlocking {
        val originalContext = SSLContext.getDefault()
        val originalSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        val originalVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        try {
            withContext(Dispatchers.Default) {
                coroutineScope {
                    NaviampMutualTlsFixture(audioSeconds = 60).use { fixture ->
                        val server = fixture.startServer(serverRequiresIdentity, pauseAudioAfterBytes = 262_144, useTls = useTls)
                        val trusted = NavidromeTlsSettings(customCertificatePath = fixture.serverCa.toString(),
                            clientCertificateKeyStorePath = fixture.clientKeyStore.toString(),
                            clientCertificateKeyStorePassword = fixture.password)
                        val validClient = createDefaultNavidromeKtorClient(trusted)
                        val track = try {
                            assertNotNull(NavidromeProvider(NavidromeConnectionForTest(server.baseUrl, trusted),
                                KtorNavidromeHttpClient(validClient)).track(TrackId("track")))
                        } finally { validClient.close() }
                        val settings = if (!useTls) NavidromeTlsSettings() else if (provideIdentity) trusted else trusted.copy(
                            clientCertificateKeyStorePath = null, clientCertificateKeyStorePassword = null)
                        val client = createDefaultNavidromeKtorClient(settings)
                        val provider = NavidromeProvider(NavidromeConnectionForTest(server.baseUrl, settings), KtorNavidromeHttpClient(client))
                        server.requestedPaths.clear(); server.peerSubjects.clear()
                        val libraries = assertNotNull(DesktopBassLibraryResolver().resolveWithLibraries("bass", "naviamp_bass"))
                        val backend = DesktopBassAudioBackend(DesktopBassJniBinding.loadFrom(libraries).getOrThrow())
                        val engine = CoreBassPlaybackEngine(Result.success(backend), DesktopBassPlaybackEngineRuntime())
                        System.getProperty("naviamp.bass.test.outputDevice")?.let { output ->
                            backend.init(output).getOrThrow()
                            engine.setAudioOutputDevice(output).getOrThrow()
                        }
                        val states = Channel<PlaybackState>(Channel.UNLIMITED)
                        val observedStates = java.util.Collections.synchronizedList(mutableListOf<PlaybackState>())
                        val progressUpdates = Channel<PlaybackProgress>(Channel.CONFLATED)
                        val adapter = NaviampCorePlaybackEngineAdapter(
                            scope = this, engine = engine, providerSource = NaviampCoreMediaProviderSource { provider },
                            settings = { PlaybackSettings() },
                            cacheSettings = { CacheSettings(audioCachingEnabled = false, audioPrefetchDepth = 0) },
                            cacheAudio = { _, _, _, _ -> error("mTLS streaming must not wait for a full cached file") },
                        )
                        adapter.attach(object : NaviampCorePlaybackObserver {
                            override fun onStateChanged(state: PlaybackState) { observedStates += state; states.trySend(state) }
                            override fun onProgressChanged(progress: PlaybackProgress) { progressUpdates.trySend(progress) }
                            override fun onMetadataChanged(metadata: PlaybackStreamMetadata) = Unit
                        })
                        suspend fun awaitState(expected: PlaybackState) {
                            try {
                                withTimeout(15_000) {
                                    while (true) {
                                        val state = states.receive()
                                        assertFalse(state is PlaybackState.Error, "Playback failed: $state; ${engine.statsRows()}")
                                        if (state == expected) break
                                    }
                                }
                            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                                throw AssertionError("Waiting for $expected; states=$observedStates; bytes=${server.audioBytesSent}; ranges=${server.requestedRanges}; ${engine.statsRows()}", timeout)
                            }
                        }
                        try {
                            adapter.playQueueSelection(PlaybackQueue(listOf(track), 0), 0)
                            if (serverRequiresIdentity && !provideIdentity) {
                                withTimeout(15_000) {
                                    while (states.receive() !is PlaybackState.Error) { }
                                }
                                assertTrue(server.requestedPaths.isEmpty())
                                assertEquals(0L, server.audioBytesSent.get())
                            } else {
                                awaitState(PlaybackState.Playing)
                                assertTrue(server.audioBytesSent.get() < fixture.audio.size.toLong(),
                                    "Playback must start while the upstream tail is still blocked")
                                assertEquals(1L, server.releaseRemainingAudio.count)
                                adapter.pause(); awaitState(PlaybackState.Paused)
                                val beforeSeek = server.requestedRanges.size
                                adapter.seek(30.0); awaitState(PlaybackState.Paused)
                                withTimeout(5_000) {
                                    while ((progressUpdates.receive().positionSeconds ?: 0.0) < 29.5) { }
                                }
                                adapter.resume(); awaitState(PlaybackState.Playing)
                                withTimeout(5_000) {
                                    while (server.requestedRanges.toList().drop(beforeSeek).none {
                                        it.substringAfter("bytes=").substringBefore('-').toLongOrNull()?.let { start -> start in 800_000..1_100_000 } == true
                                    }) kotlinx.coroutines.delay(10)
                                }
                                assertTrue(server.peerSubjects.isNotEmpty())
                                assertTrue(server.peerSubjects.toList().all { it == if (!useTls) "plaintext" else if (provideIdentity) "CN=Naviamp Test Client" else "anonymous" })
                                val stopping = kotlin.time.TimeSource.Monotonic.markNow()
                                adapter.stop()
                                assertTrue(stopping.elapsedNow().inWholeMilliseconds < 3_000, "Stop must cancel pending authenticated reads")
                            }
                        } finally {
                            server.releaseRemainingAudio.countDown()
                            adapter.stop(); engine.release(); client.close(); states.close(); progressUpdates.close()
                        }
                    }
                }
            }
        } finally {
            SSLContext.setDefault(originalContext)
            HttpsURLConnection.setDefaultSSLSocketFactory(originalSocketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier(originalVerifier)
        }
    }

    private fun NavidromeConnectionForTest(url: String, tls: NavidromeTlsSettings) =
        app.naviamp.provider.navidrome.NavidromeConnection.fromPassword(baseUrl = url, username = "fixture", password = "fixture", salt = "fixture-salt", tlsSettings = tls)

    @Test fun reopenedConnectionCanBrowseFetchArtworkAndPlayAuthenticatedAudio() = runBlocking {
        val originalContext = SSLContext.getDefault()
        val originalSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        val originalVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        try {
            NaviampMutualTlsFixture().use { fixture ->
                val server = fixture.startServer()
                val databaseFile = fixture.directory.resolve("source.db")
                // A disposable key exercises the production encryption implementation without reading
                // or changing the user's OS credential store.
                val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
                val tls = NavidromeTlsSettings(customCertificatePath = fixture.serverCa.toString(),
                    clientCertificateKeyStorePath = fixture.clientKeyStore.toString(),
                    clientCertificateKeyStorePassword = fixture.password)
                val sourceId = JdbcSqliteDriver("jdbc:sqlite:$databaseFile").use { driver ->
                    NaviampStorageDatabase.Schema.create(driver)
                    val database = NaviampStorageDatabase(driver)
                    val store = StorageMediaSourceStore(database.naviampStorageQueries, { 1L }, DesktopCredentialProtector { key })
                    val source = store.upsertProviderMediaSource(ProviderMediaSourceConnection(
                        displayName = "Disposable mTLS", baseUrl = server.baseUrl, username = "fixture",
                        token = "fixture-token", salt = "fixture-salt", tlsSettings = tls,
                    ), "fixture-cache", "navidrome", null)
                    val raw = database.naviampStorageQueries.selectMediaSourceById(source.id).executeAsOne()
                    assertFalse(raw.client_certificate_keystore_password.orEmpty().contains(fixture.password))
                    assertTrue(raw.client_certificate_keystore_password.orEmpty().startsWith("naviamp-desktop-secure-v1:"))
                    source.id
                }
                val restored = JdbcSqliteDriver("jdbc:sqlite:$databaseFile").use { driver ->
                    StorageMediaSourceStore(NaviampStorageDatabase(driver).naviampStorageQueries, { 2L },
                        DesktopCredentialProtector { key }).mediaSource(sourceId)
                }
                val connection = assertNotNull(restored).toNavidromeConnection()
                assertEquals(tls, connection.tlsSettings)
                val ktor = createDefaultNavidromeKtorClient(connection.tlsSettings)
                try {
                    val provider = NavidromeProvider(connection, KtorNavidromeHttpClient(ktor))
                    assertEquals("fixture", provider.validateConnection().serverVersion)
                    assertEquals("TLS Artist", provider.artists(10).single().name)
                    assertContentEquals(fixture.artwork, provider.bytesForOwnedUrl(provider.coverArtUrl("cover")))
                    val request = StreamRequest(TrackId("track"), StreamQuality.Original)
                    val bytes = AudioByteStoreService(DesktopAudioByteStore(fixture.directory.resolve("audio")), NoFallbackHttp)
                        .writeProviderAudio(sourceId, request.trackId, "original", "audio/wav", provider,
                            provider.streamUrl(request), "mTLS audio download failed")
                    assertContentEquals(fixture.audio, Files.readAllBytes(java.nio.file.Path.of(bytes.filePath)))
                    val libraries = assertNotNull(DesktopBassLibraryResolver().resolveWithLibraries("bass", "naviamp_bass"))
                    val bass = DesktopBassJniBinding.loadFrom(libraries).getOrThrow()
                    try {
                        val output = System.getProperty("naviamp.bass.test.outputDevice")
                        assertTrue(if (output == null) bass.init() else bass.init(output), "BASS init: ${bass.lastErrorCode}")
                        val stream = bass.createFileStream(bytes.filePath)
                        assertTrue(stream != 0, "Downloaded mTLS audio must decode: ${bass.lastErrorCode}")
                        try {
                            assertEquals(16_000, bass.channelInfoFrequency(stream))
                            assertTrue((bass.durationSeconds(stream) ?: 0.0) > 0.9)
                            assertTrue(bass.play(stream))
                            assertEquals(BassActiveState.Playing, bass.activeState(stream))
                            assertTrue(bass.pause(stream))
                            assertTrue(bass.seek(stream, 0.25))
                        } finally { bass.freeStream(stream) }
                    } finally { bass.free() }
                    assertTrue(server.peerSubjects.isNotEmpty())
                    assertTrue(server.peerSubjects.all { it == "CN=Naviamp Test Client" })
                } finally { ktor.close() }
            }
        } finally {
            SSLContext.setDefault(originalContext)
            HttpsURLConnection.setDefaultSSLSocketFactory(originalSocketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier(originalVerifier)
        }
    }

    private object NoFallbackHttp : SharedHttpClient {
        private fun forbidden(): Nothing = error("Provider-owned mTLS bytes must use the provider's authenticated transport")
        override suspend fun get(url: String, headers: Map<String, String>): String? = forbidden()
        override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = forbidden()
        override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse? = forbidden()
        override suspend fun download(url: String, headers: Map<String, String>, writeChunk: suspend (ByteArray, Int) -> Unit): Boolean = forbidden()
    }
}
