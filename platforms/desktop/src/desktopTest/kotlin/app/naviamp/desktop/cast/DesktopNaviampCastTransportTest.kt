package app.naviamp.desktop.cast

import app.naviamp.app.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.Signature
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopNaviampCastTransportTest {
    @Test
    fun tlsFramesExposeTheSignedPeerCertificateAndCancellationClosesTheSocket() = runBlocking {
        val fixture = certificates()
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(fixture.store, "test-password".toCharArray())
        }
        val tls = SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, null, null) }
        val server = tls.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        val worker = Executors.newSingleThreadExecutor()
        val accepted = worker.submit<SSLSocket> { (server.accept() as SSLSocket).also { it.startHandshake() } }
        var connection: NaviampCastTransportConnection? = null
        try {
            connection = DesktopNaviampCastTransportFactory().connect(NaviampCastEndpoint("127.0.0.1", server.localPort,
                localAddress = "127.0.0.1"))
            val peer = accepted.get(10, TimeUnit.SECONDS)
            peer.use {
                assertContentEquals(fixture.store.getCertificate("tls").encoded, connection.peerCertificateDer)
                assertEquals("127.0.0.1", connection.localAddress)
                connection.send(byteArrayOf(1, 2, 3))
                val input = DataInputStream(peer.inputStream)
                assertEquals(3, input.readInt())
                assertContentEquals(byteArrayOf(1, 2, 3), ByteArray(3).also(input::readFully))
                val output = DataOutputStream(peer.outputStream)
                output.writeInt(2); output.write(byteArrayOf(4, 5)); output.flush()
                assertContentEquals(byteArrayOf(4, 5), connection.receive())
                val read = async { connection.receive() }
                delay(50)
                read.cancel(); read.join()
                assertTrue(read.isCancelled)
                peer.soTimeout = 2_000
                assertEquals(-1, peer.inputStream.read())
            }
            val crypto = JvmNaviampCastCryptoEffect()
            val leaf = fixture.store.getCertificate("tls").encoded
            assertTrue(crypto.verifyCertificateChain(listOf(leaf), listOf(fixture.root), System.currentTimeMillis()))
            assertFalse(crypto.verifyCertificateChain(listOf(fixture.root), listOf(leaf), System.currentTimeMillis()))
            val proof = "nonce plus tls certificate".encodeToByteArray()
            val signed = Signature.getInstance("SHA256withRSA").run {
                initSign(fixture.store.getKey("tls", "test-password".toCharArray()) as java.security.PrivateKey)
                update(proof); sign()
            }
            assertTrue(crypto.verifySha256Rsa(leaf, signed, proof))
            assertFalse(crypto.verifySha256Rsa(leaf, signed, proof + byteArrayOf(0)))
        } finally {
            connection?.close(); server.close(); worker.shutdownNow()
            fixture.close()
        }
    }

    private class Fixture(val store: KeyStore, val root: ByteArray, val directory: Path) : AutoCloseable {
        override fun close() { Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }

    private fun certificates(): Fixture {
        val directory = Files.createTempDirectory("naviamp-cast-tls-test")
        val store = directory.resolve("keys.p12")
        val keytool = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").lowercase().contains("win")) "keytool.exe" else "keytool").toString()
        fun key(vararg args: String) {
            val process = ProcessBuilder(listOf(keytool) + args + listOf("-keystore", store.toString(),
                "-storetype", "PKCS12", "-storepass", "test-password", "-keypass", "test-password", "-noprompt"))
                .redirectErrorStream(true).start()
            val log = process.inputStream.bufferedReader().readText()
            check(process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0) { log }
        }
        try {
            key("-genkeypair", "-alias", "root", "-keyalg", "RSA", "-dname", "CN=Test Cast Root", "-validity", "2", "-ext", "bc=ca:true")
            key("-genkeypair", "-alias", "tls", "-keyalg", "RSA", "-dname", "CN=localhost", "-validity", "1")
            key("-certreq", "-alias", "tls", "-file", directory.resolve("tls.csr").toString())
            key("-gencert", "-alias", "root", "-infile", directory.resolve("tls.csr").toString(),
                "-outfile", directory.resolve("tls.crt").toString(), "-validity", "1", "-ext", "bc=ca:false")
            key("-importcert", "-alias", "tls", "-file", directory.resolve("tls.crt").toString())
            val loaded = KeyStore.getInstance("PKCS12").apply { Files.newInputStream(store).use { load(it, "test-password".toCharArray()) } }
            val root = loaded.getCertificate("root").encoded
            loaded.deleteEntry("root") // Force the TLS key manager to use the issued leaf.
            return Fixture(loaded, root, directory)
        } catch (failure: Exception) {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
            throw failure
        }
    }
}
