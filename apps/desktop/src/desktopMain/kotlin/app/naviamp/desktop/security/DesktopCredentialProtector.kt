package app.naviamp.desktop.security

import app.naviamp.storage.StorageCredentialProtector
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class DesktopCredentialProtector(
    private val key: () -> SecretKey = ::loadOrCreateDesktopCredentialKey,
) : StorageCredentialProtector {
    override fun protect(value: String?): String? {
        value ?: return null
        if (value.isEmpty() || isProtected(value)) return value
        val cipher = Cipher.getInstance(CipherTransformation).apply {
            init(Cipher.ENCRYPT_MODE, key())
            updateAAD(CredentialAad)
        }
        val encrypted = cipher.doFinal(value.encodeToByteArray())
        return CredentialPrefix + Base64.getEncoder().encodeToString(cipher.iv + encrypted)
    }

    override fun reveal(value: String?): String? {
        value ?: return null
        if (!isProtected(value)) return value
        return runCatching {
            val payload = Base64.getDecoder().decode(value.removePrefix(CredentialPrefix))
            require(payload.size > GcmIvBytes)
            val cipher = Cipher.getInstance(CipherTransformation).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(GcmTagBits, payload.copyOfRange(0, GcmIvBytes)),
                )
                updateAAD(CredentialAad)
            }
            cipher.doFinal(payload.copyOfRange(GcmIvBytes, payload.size)).decodeToString()
        }.getOrNull()
    }

    override fun isProtected(value: String?): Boolean =
        value?.startsWith(CredentialPrefix) == true
}

private val DesktopCredentialKey: SecretKey by lazy {
    val keyStore = desktopSecureValueStore()
    val encoded = keyStore.read()?.let { stored ->
        runCatching { Base64.getDecoder().decode(stored.trim()) }.getOrNull()
    }?.takeIf { it.size == CredentialKeyBytes }
    val keyBytes = encoded ?: ByteArray(CredentialKeyBytes).also(SecureRandom()::nextBytes).also { generated ->
        keyStore.write(Base64.getEncoder().encodeToString(generated))
    }
    SecretKeySpec(keyBytes, "AES")
}

private fun loadOrCreateDesktopCredentialKey(): SecretKey = DesktopCredentialKey

private interface DesktopSecureValueStore {
    fun read(): String?

    fun write(value: String)
}

private fun desktopSecureValueStore(): DesktopSecureValueStore {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") -> MacOsKeychainValueStore
        os.contains("win") -> WindowsDpapiValueStore(defaultWindowsCredentialKeyPath())
        os.contains("linux") -> LinuxSecretServiceValueStore
        else -> error("Secure credential storage is not supported on ${System.getProperty("os.name")}.")
    }
}

private object MacOsKeychainValueStore : DesktopSecureValueStore {
    override fun read(): String? = runCredentialCommand(
        listOf(
            "security",
            "find-generic-password",
            "-s",
            CredentialService,
            "-a",
            CredentialAccount,
            "-w",
        ),
        allowMissing = true,
    )

    override fun write(value: String) {
        runCredentialCommand(
            listOf(
                "security",
                "add-generic-password",
                "-U",
                "-s",
                CredentialService,
                "-a",
                CredentialAccount,
                "-w",
                value,
            ),
        )
    }
}

private object LinuxSecretServiceValueStore : DesktopSecureValueStore {
    override fun read(): String? = runCredentialCommand(
        listOf("secret-tool", "lookup", "application", "app.naviamp", "purpose", "credential-key"),
        allowMissing = true,
    )

    override fun write(value: String) {
        runCredentialCommand(
            listOf(
                "secret-tool",
                "store",
                "--label=Naviamp credentials",
                "application",
                "app.naviamp",
                "purpose",
                "credential-key",
            ),
            input = value,
        )
    }
}

private class WindowsDpapiValueStore(
    private val path: Path,
) : DesktopSecureValueStore {
    override fun read(): String? {
        if (!Files.isRegularFile(path)) return null
        return runCredentialCommand(
            listOf(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Unprotect(" +
                    "[IO.File]::ReadAllBytes(\$args[0]),\$null," +
                    "[Security.Cryptography.DataProtectionScope]::CurrentUser))",
                path.toString(),
            ),
        )
    }

    override fun write(value: String) {
        Files.createDirectories(path.parent)
        runCredentialCommand(
            listOf(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "\$plain=[Convert]::FromBase64String([Console]::In.ReadToEnd());" +
                    "\$protected=[Security.Cryptography.ProtectedData]::Protect(" +
                    "\$plain,\$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);" +
                    "[IO.File]::WriteAllBytes(\$args[0],\$protected)",
                path.toString(),
            ),
            input = value,
        )
    }
}

private fun runCredentialCommand(
    command: List<String>,
    input: String? = null,
    allowMissing: Boolean = false,
): String? {
    val process = runCatching { ProcessBuilder(command).start() }.getOrElse { error ->
        throw IllegalStateException("Secure credential service is unavailable.", error)
    }
    if (input != null) {
        process.outputStream.bufferedWriter().use { writer -> writer.write(input) }
    } else {
        process.outputStream.close()
    }
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    val errorOutput = process.errorStream.bufferedReader().use { it.readText() }.trim()
    val exitCode = process.waitFor()
    if (exitCode == 0) return output.takeIf { it.isNotEmpty() }
    if (allowMissing) return null
    throw IllegalStateException(
        "Secure credential service failed with exit code $exitCode" +
            errorOutput.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty(),
    )
}

private fun defaultWindowsCredentialKeyPath(): Path {
    val home = Path.of(System.getProperty("user.home"))
    return Path.of(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString())
        .resolve("Naviamp")
        .resolve("credential-key.dpapi")
}

private const val CredentialService = "app.naviamp.credentials.v1"
private val CredentialAccount = System.getProperty("user.name").orEmpty().ifBlank { "Naviamp" }
private const val CredentialPrefix = "naviamp-desktop-secure-v1:"
private const val CipherTransformation = "AES/GCM/NoPadding"
private const val CredentialKeyBytes = 32
private const val GcmIvBytes = 12
private const val GcmTagBits = 128
private val CredentialAad = "app.naviamp.desktop.credentials.v1".encodeToByteArray()
