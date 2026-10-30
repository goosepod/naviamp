package app.naviamp.desktop.security

import java.nio.file.Files
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopCredentialProtectorTest {
    private val protector = DesktopCredentialProtector {
        SecretKeySpec(ByteArray(32) { index -> index.toByte() }, "AES")
    }

    @Test
    fun protectsAndRevealsCredential() {
        val protected = protector.protect("secret-token")

        assertNotEquals("secret-token", protected)
        assertTrue(protector.isProtected(protected))
        assertEquals("secret-token", protector.reveal(protected))
    }

    @Test
    fun leavesEmptyAndAlreadyProtectedValuesStable() {
        val protected = protector.protect("secret-token")

        assertEquals("", protector.protect(""))
        assertEquals(protected, protector.protect(protected))
        assertFalse(protector.isProtected("plain"))
        assertEquals("plain", protector.reveal("plain"))
    }

    @Test
    fun rejectsEncryptedValuesFromAnotherKey() {
        val protected = protector.protect("secret-token")
        val other = DesktopCredentialProtector {
            SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")
        }

        assertEquals(null, other.reveal(protected))
    }

    @Test
    fun windowsDpapiStoreRoundTripsAKeyAtAPathContainingSpaces() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        val directory = Files.createTempDirectory("naviamp credential store test")
        val parent = directory.resolve("fresh profile").resolve("Naviamp")
        val path = parent.resolve("credential key.dpapi")
        val value = Base64.getEncoder().encodeToString(ByteArray(32) { index -> index.toByte() })
        try {
            val store = WindowsDpapiValueStore(path)
            assertFalse(parent.exists())
            assertEquals(null, store.read())

            store.write(value)

            assertTrue(path.exists())
            assertEquals(value, store.read())
            assertEquals(value, WindowsDpapiValueStore(path).read())
            val bytes = Files.readAllBytes(path)
            assertFalse(bytes.contentEquals(Base64.getDecoder().decode(value)))

            // A DPAPI failure must not truncate a previously persisted key.
            assertFailsWith<IllegalStateException> { store.write("not base64!") }
            assertContentEquals(bytes, Files.readAllBytes(path))
            assertEquals(value, WindowsDpapiValueStore(path).read())
        } finally {
            path.deleteIfExists()
            parent.deleteIfExists()
            parent.parent.deleteIfExists()
            directory.deleteIfExists()
        }
    }

    @Test
    fun windowsDpapiStoreReadsKeysWrittenByTheReleasedFileBasedHelper() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        val directory = Files.createTempDirectory("naviamp legacy credential test")
        val path = directory.resolve("credential key.dpapi")
        val value = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 7).toByte() })
        try {
            // Match the released helper's binary DPAPI file format, independently of the new writer.
            val process = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "Add-Type -AssemblyName System.Security;" +
                    "\$plain=[Convert]::FromBase64String([Console]::In.ReadToEnd());" +
                    "\$protected=[Security.Cryptography.ProtectedData]::Protect(" +
                    "\$plain,\$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);" +
                    "[IO.File]::WriteAllBytes(\$env:NAVIAMP_LEGACY_TEST_PATH,\$protected)",
            ).apply {
                environment()["NAVIAMP_LEGACY_TEST_PATH"] = path.toString()
                redirectErrorStream(true)
            }.start()
            process.outputStream.bufferedWriter().use { it.write(value) }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.waitFor(), output)
            val original = Files.readAllBytes(path)

            assertEquals(value, WindowsDpapiValueStore(path).read())
            assertContentEquals(original, Files.readAllBytes(path))
        } finally {
            path.deleteIfExists()
            directory.deleteIfExists()
        }
    }
}
