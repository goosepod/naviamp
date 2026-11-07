package app.naviamp.testkit

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import jdk.jfr.Configuration
import jdk.jfr.Recording

class NaviampMobileMutualTlsFixtureHandle internal constructor(
    val bootstrap: String,
    val endpoints: NaviampMobileTlsEndpoints,
    private val stopSignal: CountDownLatch,
) {
    fun awaitStop() = stopSignal.await()
}

/** Companion for emulator/simulator tests. Only disposable test material is exposed on loopback. */
fun withNaviampMobileMutualTlsFixture(block: (NaviampMobileMutualTlsFixtureHandle) -> Unit) {
    NaviampMutualTlsFixture(audioSeconds = 180).use { fixture ->
        NaviampMutualTlsFixture(serverSubjectAlternativeNames = "DNS:wrong.invalid").use { wrongHost ->
            val mutual = fixture.startServer(pauseAudioAfterBytes = 262_144)
            val privateCa = fixture.startServer(requireClientCertificate = false)
            val wrongName = wrongHost.startServer()
            val control = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val done = CountDownLatch(1)
            val endpoints = NaviampMobileTlsEndpoints(control.address.port,
                java.net.URI(mutual.baseUrl).port, java.net.URI(privateCa.baseUrl).port, java.net.URI(wrongName.baseUrl).port)
            val stopToken = UUID.randomUUID().toString()
            val manifest = mapOf(
                "ca" to Files.readAllBytes(fixture.serverCa),
                "identity" to Files.readAllBytes(fixture.clientKeyStore),
                "untrusted" to Files.readAllBytes(fixture.untrustedClientKeyStore),
                "wrongHostCa" to Files.readAllBytes(wrongHost.serverCa),
                "wrongHostIdentity" to Files.readAllBytes(wrongHost.clientKeyStore),
            ).entries.joinToString("\n") { (name, bytes) -> "$name=${Base64.getEncoder().encodeToString(bytes)}" } +
                "\npassword=${fixture.password}\naudioLength=${fixture.audio.size}\n" +
                "stopToken=$stopToken\ncontrolPort=${endpoints.controlPort}\nmutualPort=${endpoints.mutualPort}\n" +
                "privateCaPort=${endpoints.privateCaPort}\nwrongHostPort=${endpoints.wrongHostPort}\n"
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
                    "/stop" -> if (exchange.requestURI.rawQuery == "token=$stopToken") "stopping" else ""
                    else -> ""
                }.encodeToByteArray()
                exchange.sendResponseHeaders(if (response.isEmpty()) 404 else 200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
                exchange.close()
                if (exchange.requestURI.path == "/stop" && response.isNotEmpty()) done.countDown()
            }
            control.start()
            println("Disposable mobile mTLS fixture ready at http://127.0.0.1:${endpoints.controlPort}/fixture")
            try { block(NaviampMobileMutualTlsFixtureHandle(manifest, endpoints, done)) } finally { control.stop(0) }
        }
    }
}

fun main(args: Array<String>) {
    // Observe this disposable server only. Recording before first use preserves lazy TLS
    // initialization and host stalls that a thread dump taken after cancellation misses.
    val recording = if (System.getenv("NAVIAMP_MTLS_FIXTURE_JFR") == "true") {
        require(args.size == 1) { "Fixture recording requires its owned bootstrap destination" }
        Recording(Configuration.getConfiguration("profile")).apply {
            name = "naviamp-disposable-fixture"
            // A hosted runner's environment and unrelated process arguments can contain
            // credentials. They are not needed to inspect this fixture's TLS stacks.
            listOf("jdk.InitialEnvironmentVariable", "jdk.InitialSystemProperty",
                "jdk.SystemProcess", "jdk.ProcessStart").forEach { disable(it) }
            maxSize = 16L * 1024 * 1024
            destination = Path.of(args.single()).toAbsolutePath().parent.resolve("fixture.jfr")
            dumpOnExit = true
            start()
        }
    } else null
    try {
        serveMobileMutualTlsFixture(args)
    } finally {
        recording?.use { it.stop() }
    }
}

private fun serveMobileMutualTlsFixture(args: Array<String>) = withNaviampMobileMutualTlsFixture { fixture ->
    if (args.isNotEmpty()) {
        val target = Path.of(args.single()).toAbsolutePath()
        val pending = Files.createTempFile(target.parent, ".mobile-mtls-", ".bootstrap")
        try {
            Files.writeString(pending, fixture.bootstrap)
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE)
        } finally { Files.deleteIfExists(pending) }
    }
    fixture.awaitStop()
}
