package app.naviamp.testkit

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import javax.net.ssl.SSLHandshakeException

class NaviampMobileMutualTlsFixtureTest {
    @Test fun readinessRequiresTrustedIdentityAndHostnameWithoutContaminatingHttpAudits() {
        NaviampMutualTlsFixture().use { fixture ->
            val server = fixture.startServer()
            fixture.verifyTlsServerReady(server)
            assertTrue(server.requestedPaths.isEmpty())
            assertTrue(server.peerSubjects.isEmpty())
            assertTrue(server.requestedRanges.isEmpty())
            assertEquals(0L, server.audioBytesSent.get())
            assertFailsWith<SSLHandshakeException> {
                fixture.verifyTlsServerReady(server, hostname = "wrong.invalid")
            }
            assertTrue(server.requestedPaths.isEmpty())
        }
    }

    @Test fun concurrentFixturesIgnoreOccupiedLegacyPortsAndReleaseTheirOwnSockets() {
        val sentinels = (59974..59977).mapNotNull { port ->
            // A preexisting listener already supplies the occupied-port condition; never stop it.
            runCatching { HttpServer.create(InetSocketAddress("127.0.0.1", port), 0) }.getOrNull()?.apply {
                createContext("/") { exchange ->
                    val bytes = "unrelated listener".toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                start()
            }
        }
        val release = CountDownLatch(1)
        val published = List(2) { CompletableFuture<NaviampMobileMutualTlsFixtureHandle>() }
        val workers = published.map { result -> CompletableFuture.runAsync {
            try {
                withNaviampMobileMutualTlsFixture { fixture ->
                    result.complete(fixture)
                    check(release.await(120, TimeUnit.SECONDS))
                }
            } catch (failure: Throwable) { result.completeExceptionally(failure); throw failure }
        } }
        var ports = emptyList<Int>()
        try {
            val fixtures = published.map { it.get(120, TimeUnit.SECONDS) }
            ports = fixtures.flatMap { listOf(it.endpoints.controlPort, it.endpoints.mutualPort, it.endpoints.privateCaPort, it.endpoints.wrongHostPort) }
            assertEquals(8, ports.distinct().size)
            assertTrue(ports.none { it in 59974..59977 })
            assertNotEquals(fixtures[0].bootstrap, fixtures[1].bootstrap)
            for (fixture in fixtures) {
                val manifest = decodeNaviampMobileTlsControlResponse(fixture.bootstrap)
                assertEquals(fixture.endpoints, NaviampMobileTlsEndpoints.fromManifest(manifest))
                assertEquals(fixture.bootstrap, URI("http://127.0.0.1:${fixture.endpoints.controlPort}/fixture").toURL().readText())
                val rejectedStop = URI("http://127.0.0.1:${fixture.endpoints.controlPort}/stop?token=other-run").toURL().openConnection() as java.net.HttpURLConnection
                try { assertEquals(404, rejectedStop.responseCode) } finally { rejectedStop.disconnect() }
            }
            for (sentinel in sentinels) {
                assertEquals("unrelated listener", URI("http://127.0.0.1:${sentinel.address.port}/fixture").toURL().readText())
            }
        } finally {
            release.countDown()
            try { workers.forEach { it.get(30, TimeUnit.SECONDS) } }
            finally { sentinels.forEach { it.stop(0) } }
        }
        for (port in ports) {
            assertFalse(runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 500) }; true }.getOrDefault(false))
        }
    }
}
