package app.naviamp.testkit

import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.presentation.NaviampCoreMediaProviderSource
import app.naviamp.presentation.NaviampCorePlaybackEngineAdapter
import app.naviamp.presentation.NaviampCorePlaybackObserver
import app.naviamp.provider.navidrome.KtorNavidromeHttpClient
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.provider.navidrome.createDefaultNavidromeKtorClient
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.io.encoding.Base64
import kotlin.time.TimeSource

/** Hosts supply only private test-file IO and their real native playback boundary. */
interface NaviampMobileMutualTlsHost {
    suspend fun writePrivateFile(name: String, bytes: ByteArray): String
    fun createEngine(): CoreBassPlaybackEngine
}

/** One real HTTPS contract runs unchanged against Android and Apple TLS/native decoder adapters. */
suspend fun verifyNaviampMobileMutualTls(host: NaviampMobileMutualTlsHost) = withContext(Dispatchers.Default) {
    suspend fun <T> phase(name: String, block: suspend () -> T): T {
        println("mTLS phase: $name")
        return try { block() } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("mTLS timed out during: $name", timeout)
        }
    }
    val manifest = phase("fixture bootstrap") { mobileTlsFixtureRequest("/fixture") }
    suspend fun file(key: String, name: String) = host.writePrivateFile(name, Base64.decode(manifest.getValue(key)))
    val settings = NavidromeTlsSettings(
        customCertificatePath = file("ca", "ca.pem"),
        clientCertificateKeyStorePath = file("identity", "client.p12"),
        clientCertificateKeyStorePassword = manifest.getValue("password"),
    )
    val untrustedIdentity = file("untrusted", "untrusted.p12")
    val wrongHostCa = file("wrongHostCa", "wrong-host-ca.pem")
    val wrongHostIdentity = file("wrongHostIdentity", "wrong-host.p12")
    fun provider(baseUrl: String, tls: NavidromeTlsSettings, client: io.ktor.client.HttpClient) = NavidromeProvider(
        NavidromeConnection(baseUrl = baseUrl, username = "fixture", password = "fixture", salt = "fixture-salt", token = "fixture-token", tlsSettings = tls),
        KtorNavidromeHttpClient(client),
    )
    suspend fun rejected(tls: NavidromeTlsSettings, url: String = "https://127.0.0.1:59975") {
        var failed = false
        withTimeout(25_000) {
            try {
                val client = createDefaultNavidromeKtorClient(tls)
                try { provider(url, tls, client).validateConnection() } finally { client.close() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
        check(failed) { "Invalid TLS credentials unexpectedly authenticated: url=$url client=${tls.hasClientCertificate} trust=${tls.hasCustomCertificate} passwordCorrect=${tls.clientCertificateKeyStorePassword == manifest.getValue("password")}" }
    }
    phase("missing client identity rejection") { rejected(settings.copy(clientCertificateKeyStorePath = null, clientCertificateKeyStorePassword = null)) }
    phase("untrusted identity rejection") { rejected(settings.copy(clientCertificateKeyStorePath = untrustedIdentity)) }
    phase("wrong identity password rejection") { rejected(settings.copy(clientCertificateKeyStorePassword = "wrong-password")) }
    phase("untrusted server rejection") { rejected(settings.copy(customCertificatePath = null)) }
    phase("hostname rejection") { rejected(settings.copy(customCertificatePath = wrongHostCa, clientCertificateKeyStorePath = wrongHostIdentity), "https://127.0.0.1:59977") }
    check(mobileTlsFixtureRequest("/state").getValue("wrongHostRequests") == "0")
    // A positive control on the same host prevents outages from passing every negative test.
    val challenges = MutableStateFlow<List<app.naviamp.provider.navidrome.NavidromeTlsChallengeEvent>>(emptyList())
    val client = createDefaultNavidromeKtorClient(settings) { event -> challenges.update { it + event } }
    try {
        val media = provider("https://127.0.0.1:59975", settings, client)
        try { phase("valid client authentication") { check(media.validateConnection().serverVersion == "fixture") } }
        catch (failure: Exception) { throw AssertionError("Valid mTLS connection failed; native TLS challenge facts=${challenges.value}", failure) }
        check(media.artists(10).single().name == "TLS Artist")
        check(checkNotNull(media.bytesForOwnedUrl(media.coverArtUrl("cover"))).isNotEmpty())
        val track = checkNotNull(media.track(TrackId("track")))
        coroutineScope {
            val engine = host.createEngine()
            val states = Channel<PlaybackState>(Channel.UNLIMITED)
            val progress = Channel<PlaybackProgress>(Channel.CONFLATED)
            val adapter = NaviampCorePlaybackEngineAdapter(
                scope = this, engine = engine, providerSource = NaviampCoreMediaProviderSource { media },
                settings = { PlaybackSettings() },
                cacheSettings = { CacheSettings(audioCachingEnabled = false, audioPrefetchDepth = 0) },
                cacheAudio = { _, _, _, _ -> error("Uncached playback must not download a complete file") },
            )
            adapter.attach(object : NaviampCorePlaybackObserver {
                override fun onStateChanged(state: PlaybackState) { states.trySend(state) }
                override fun onProgressChanged(value: PlaybackProgress) { progress.trySend(value) }
                override fun onMetadataChanged(metadata: PlaybackStreamMetadata) = Unit
            })
            suspend fun state(expected: PlaybackState) = phase("native state $expected") {
                withTimeout(20_000) {
                    while (true) {
                        val actual = states.receive()
                        println("mTLS native state: $actual; waiting for $expected")
                        check(actual !is PlaybackState.Error) { "Authenticated native playback failed: $actual" }
                        if (actual == expected) break
                    }
                }
            }
            try {
                adapter.playQueueSelection(PlaybackQueue(listOf(track), 0), 0)
                state(PlaybackState.Playing)
                check(mobileTlsFixtureRequest("/state").getValue("bytes").toLong() < manifest.getValue("audioLength").toLong())
                adapter.pause()
                state(PlaybackState.Paused)
                adapter.seek(90.0)
                phase("paused seek position") { withTimeout(20_000) { while ((progress.receive().positionSeconds ?: 0.0) < 89.5) { } } }
                adapter.resume()
                state(PlaybackState.Playing)
                phase("resumed seek position") { withTimeout(20_000) { while ((progress.receive().positionSeconds ?: 0.0) < 90.0) { } } }
                val report = mobileTlsFixtureRequest("/state")
                check(report.getValue("peers").split('|').all { it == "CN=Naviamp Test Client" })
                check(report.getValue("ranges").split('|').any {
                    it.substringAfter("bytes=", "").substringBefore('-').toLongOrNull()?.let { start -> start > 2_000_000 } == true
                })
                val stopping = TimeSource.Monotonic.markNow()
                adapter.stop()
                check(stopping.elapsedNow().inWholeMilliseconds < 3_000)
            } finally { adapter.stop(); engine.release(); states.close(); progress.close() }
        }
    } finally { client.close() }
    val caOnly = settings.copy(clientCertificateKeyStorePath = null, clientCertificateKeyStorePassword = null)
    val privateClient = createDefaultNavidromeKtorClient(caOnly)
    try { check(provider("https://127.0.0.1:59976", caOnly, privateClient).validateConnection().serverVersion == "fixture") }
    finally { privateClient.close() }
}

/** Plaintext loopback control serves generated test secrets only; production traffic stays HTTPS. */
private suspend fun mobileTlsFixtureRequest(path: String): Map<String, String> = withTimeout(10_000) {
    val manager = SelectorManager(Dispatchers.Default)
    try {
        val socket = aSocket(manager).tcp().connect("127.0.0.1", 59974)
        try {
            val output = socket.openWriteChannel(autoFlush = true)
            output.writeStringUtf8("GET $path HTTP/1.1\r\nHost: 127.0.0.1:59974\r\nConnection: close\r\n\r\n")
            val input = socket.openReadChannel()
            check(input.readUTF8Line()?.contains(" 200 ") == true)
            var length: Int? = null
            while (true) {
                val line = checkNotNull(input.readUTF8Line())
                if (line.isEmpty()) break
                if (line.startsWith("Content-length:", ignoreCase = true)) length = line.substringAfter(':').trim().toInt()
            }
            val size = checkNotNull(length)
            require(size in 1..1_048_576)
            val bytes = ByteArray(size)
            input.readFully(bytes)
            bytes.decodeToString().lineSequence().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        } finally { socket.close() }
    } finally { manager.close() }
}
