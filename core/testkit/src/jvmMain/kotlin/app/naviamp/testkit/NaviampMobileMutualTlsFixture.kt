package app.naviamp.testkit

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CountDownLatch

/** Companion for emulator/simulator tests. Only disposable test material is exposed on loopback. */
fun withNaviampMobileMutualTlsFixture(block: (awaitStop: () -> Unit) -> Unit) {
    NaviampMutualTlsFixture(audioSeconds = 180).use { fixture ->
        NaviampMutualTlsFixture(serverSubjectAlternativeNames = "DNS:wrong.invalid").use { wrongHost ->
            val mutual = fixture.startServer(pauseAudioAfterBytes = 262_144, port = 59975)
            val privateCa = fixture.startServer(requireClientCertificate = false, port = 59976)
            val wrongName = wrongHost.startServer(port = 59977)
            val control = HttpServer.create(InetSocketAddress("127.0.0.1", 59974), 0)
            val done = CountDownLatch(1)
            val manifest = mapOf(
                "ca" to Files.readAllBytes(fixture.serverCa),
                "identity" to Files.readAllBytes(fixture.clientKeyStore),
                "untrusted" to Files.readAllBytes(fixture.untrustedClientKeyStore),
                "wrongHostCa" to Files.readAllBytes(wrongHost.serverCa),
                "wrongHostIdentity" to Files.readAllBytes(wrongHost.clientKeyStore),
            ).entries.joinToString("\n") { (name, bytes) -> "$name=${Base64.getEncoder().encodeToString(bytes)}" } +
                "\npassword=${fixture.password}\naudioLength=${fixture.audio.size}\n"
            control.createContext("/") { exchange ->
                val response = when (exchange.requestURI.path) {
                    "/fixture" -> manifest
                    "/state" -> listOf(
                        "bytes=${mutual.audioBytesSent.get()}",
                        "peers=${mutual.peerSubjects.toList().joinToString("|")}",
                        "ranges=${mutual.requestedRanges.toList().joinToString("|")}",
                        "privatePeers=${privateCa.peerSubjects.toList().joinToString("|")}",
                        "wrongHostRequests=${wrongName.requestedPaths.size}",
                    ).joinToString("\n")
                    "/stop" -> "stopping".also { done.countDown() }
                    else -> ""
                }.encodeToByteArray()
                exchange.sendResponseHeaders(if (response.isEmpty()) 404 else 200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
                exchange.close()
            }
            control.start()
            println("Disposable mobile mTLS fixture ready at http://127.0.0.1:59974/fixture")
            try { block { done.await() } } finally { control.stop(0) }
        }
    }
}

fun main() = withNaviampMobileMutualTlsFixture { awaitStop -> awaitStop() }
