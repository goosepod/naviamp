package app.naviamp.testkit

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/** Real loopback HTTPS and disposable identities; never changes an OS or JVM trust store. */
class NaviampMutualTlsFixture : AutoCloseable {
    val directory: Path = Files.createTempDirectory("naviamp-mtls-")
    val serverCa: Path = directory.resolve("ca.crt")
    val clientKeyStore: Path = directory.resolve("client.p12")
    val untrustedClientKeyStore: Path = directory.resolve("untrusted.p12")
    val password = "disposable-test-password"
    val artwork: ByteArray = ByteArrayOutputStream().use { output ->
        check(ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output))
        output.toByteArray()
    }
    val audio: ByteArray = tone()
    private val servers = mutableListOf<Server>()

    init {
        try {
            key("ca.p12", "-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=Naviamp Test CA", "-validity", "2", "-ext", "bc=ca:true", "-ext", "ku=keyCertSign,cRLSign")
            key("ca.p12", "-exportcert", "-alias", "ca", "-rfc", "-file", serverCa.toString())
            issuedIdentity("server", "serverAuth", "CN=localhost", "DNS:localhost,IP:127.0.0.1")
            issuedIdentity("client", "clientAuth", "CN=Naviamp Test Client")
            key("untrusted.p12", "-genkeypair", "-alias", "identity", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=Untrusted Test Client", "-validity", "2", "-ext", "eku=clientAuth")
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    private fun issuedIdentity(name: String, usage: String, subject: String, san: String? = null) {
        key("$name.p12", "-genkeypair", "-alias", "identity", "-keyalg", "RSA", "-keysize", "2048",
            "-dname", subject, "-validity", "2")
        val request = directory.resolve("$name.csr").toString()
        val certificate = directory.resolve("$name.crt").toString()
        key("$name.p12", "-certreq", "-alias", "identity", "-file", request)
        val extensions = listOf("-ext", "bc=ca:false", "-ext", "ku=digitalSignature,keyEncipherment", "-ext", "eku=$usage") +
            (san?.let { listOf("-ext", "san=$it") } ?: emptyList())
        key("ca.p12", *(listOf("-gencert", "-alias", "ca", "-infile", request, "-outfile", certificate,
            "-validity", "2", "-rfc") + extensions).toTypedArray())
        key("$name.p12", "-importcert", "-alias", "ca", "-file", serverCa.toString())
        key("$name.p12", "-importcert", "-alias", "identity", "-file", certificate)
    }

    private fun key(store: String, vararg arguments: String) {
        val executable = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool")
        val output = directory.resolve("keytool.log")
        val process = ProcessBuilder(listOf(executable.toString()) + arguments + listOf(
            "-keystore", directory.resolve(store).toString(), "-storetype", "PKCS12",
            "-storepass", password, "-keypass", password, "-noprompt",
        )).redirectErrorStream(true).redirectOutput(output.toFile()).start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor()
            error("Disposable certificate generation timed out")
        }
        check(process.exitValue() == 0) { "Disposable certificate generation failed: ${Files.readString(output)}" }
    }

    fun startServer(): Server = Server().also { servers += it }

    inner class Server internal constructor() : AutoCloseable {
        val requestedPaths: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val peerSubjects: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val workers = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "naviamp-mtls-fixture").apply { isDaemon = true }
        }
        private val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private var closed = false
        val baseUrl: String get() = "https://127.0.0.1:${server.address.port}"

        init {
            val keys = KeyStore.getInstance("PKCS12").apply {
                Files.newInputStream(directory.resolve("server.p12")).use { load(it, password.toCharArray()) }
            }
            val identity = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keys, password.toCharArray())
            }
            val trust = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setCertificateEntry("ca", keys.getCertificate("ca"))
            }
            val authorities = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
            val context = SSLContext.getInstance("TLS").apply { init(identity.keyManagers, authorities.trustManagers, null) }
            server.httpsConfigurator = object : HttpsConfigurator(context) {
                override fun configure(parameters: HttpsParameters) {
                    parameters.setSSLParameters(context.defaultSSLParameters.apply {
                        protocols = arrayOf("TLSv1.2")
                        needClientAuth = true
                    })
                }
            }
            server.executor = workers
            server.createContext("/") { raw ->
                val exchange = raw as com.sun.net.httpserver.HttpsExchange
                exchange.use {
                    // Reaching HTTP is insufficient: assert the actual authenticated TLS identity too.
                    peerSubjects += exchange.sslSession.peerPrincipal.name
                    requestedPaths += exchange.requestURI.path
                    val endpoint = exchange.requestURI.path.substringAfterLast('/')
                    val bytes = when (endpoint) {
                        "getCoverArt.view" -> artwork
                        "stream.view", "download.view" -> audio
                        else -> response(endpoint)?.toByteArray()
                    }
                    if (bytes == null) {
                        exchange.sendResponseHeaders(404, -1)
                    } else {
                        exchange.responseHeaders.add("Content-Type", when (endpoint) {
                            "getCoverArt.view" -> "image/png"
                            "stream.view", "download.view" -> "audio/wav"
                            else -> "application/json"
                        })
                        if (exchange.requestMethod == "HEAD") {
                            exchange.responseHeaders.add("Content-Length", bytes.size.toString())
                            exchange.sendResponseHeaders(200, -1)
                        } else {
                            exchange.sendResponseHeaders(200, bytes.size.toLong())
                            exchange.responseBody.write(bytes)
                        }
                    }
                }
            }
            server.start()
        }

        override fun close() {
            if (closed) return
            closed = true
            server.stop(0)
            workers.shutdownNow()
            check(workers.awaitTermination(5, TimeUnit.SECONDS)) { "mTLS fixture workers did not stop" }
        }
    }

    private fun response(endpoint: String): String? {
        val content = when (endpoint) {
            "ping.view" -> ""
            "getOpenSubsonicExtensions.view" -> ",\"openSubsonicExtensions\":[]"
            "getUser.view" -> ",\"user\":{\"username\":\"fixture\",\"downloadRole\":true}"
            "getArtists.view" -> ",\"artists\":{\"index\":[{\"name\":\"T\",\"artist\":[{\"id\":\"artist\",\"name\":\"TLS Artist\",\"albumCount\":1}]}]}"
            "getRandomSongs.view" -> ",\"randomSongs\":{\"song\":[$song]}"
            "getSong.view" -> ",\"song\":$song"
            else -> return null
        }
        return """{"subsonic-response":{"status":"ok","version":"1.16.1","serverVersion":"fixture","type":"navidrome"$content}}"""
    }

    private val song = """{"id":"track","title":"TLS Tone","artist":"TLS Artist","artistId":"artist","album":"TLS Album","albumId":"album","coverArt":"cover","duration":1,"contentType":"audio/wav","suffix":"wav","bitRate":256,"samplingRate":16000,"channelCount":1}"""

    override fun close() {
        servers.forEach { it.close() }
        if (Files.exists(directory)) Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }

    private fun tone(): ByteArray {
        val rate = 16_000
        val samples = ShortArray(rate) { index ->
            (kotlin.math.sin(2 * Math.PI * 440 * index / rate) * 4_000).toInt().toShort()
        }
        return ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples.size * 2); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples.size * 2); samples.forEach { putShort(it) }
        }.array()
    }
}
