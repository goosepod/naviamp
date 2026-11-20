package app.naviamp.storage

import app.naviamp.domain.source.AurralCompanion
import app.naviamp.domain.source.ConnectionTlsSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/** Scoped, bounded native file effects; Core selects files, references, limits and recovery order. */
interface StorageRecoveryFiles {
    fun ownedFilePath(fileName: String): String?
    fun read(filePath: String, maximumBytes: Int): ByteArray?
    fun write(fileName: String, bytes: ByteArray): String?
    fun remove(fileName: String): Boolean
}

/** Core owns the flat recovery-file namespace; native adapters only execute scoped file effects. */
internal fun recoveryOwnedFilePath(directoryPath: String, fileName: String): String? =
    if ('/' in fileName || '\\' in fileName || fileName == "." || fileName == ".." || fileName.isBlank()) null
    else "${directoryPath.trimEnd('/')}/$fileName"

@Serializable internal data class StorageRecoveryCertificate(val id: Long, val sourcePath: String, val bytes: String) {
    fun fileName(): String = "naviamp-recovery-certificate-$id.bin"
}

internal data class CapturedRecoveryCertificates(
    val connections: List<StorageRecoveryConnection>, val certificates: List<StorageRecoveryCertificate>, val nextId: Long,
)

private const val ReferencePrefix = "naviamp-recovery-certificate:"
private const val MaximumCertificateBytes = 32 * 1024
private val certificateJson = Json { encodeDefaults = true }

internal fun captureRecoveryCertificates(
    connections: List<StorageRecoveryConnection>, previous: List<StorageRecoveryCertificate>, firstId: Long,
    files: StorageRecoveryFiles?, protector: StorageCredentialProtector,
): CapturedRecoveryCertificates {
    if (files == null) return CapturedRecoveryCertificates(connections, previous, firstId)
    var nextId = firstId
    val captured = linkedMapOf<Long, StorageRecoveryCertificate>()
    val byPath = mutableMapOf<String, Long>()
    fun capture(path: String): String {
        byPath[path]?.let { return ReferencePrefix + it }
        val referenceId = path.removePrefix(ReferencePrefix).takeIf { path.startsWith(ReferencePrefix) }?.toLongOrNull()
        val previousItem = previous.firstOrNull { it.id == referenceId || it.sourcePath == path || path == files.ownedFilePath(it.fileName()) }
        val bytes = if (referenceId != null) null else files.read(path, MaximumCertificateBytes)
        if (bytes != null && bytes.size > MaximumCertificateBytes) throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
        val item = if (bytes != null) {
            val id = previousItem?.id ?: nextId.also {
                if (it == Long.MAX_VALUE) throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
                nextId++
            }
            StorageRecoveryCertificate(id, previousItem?.sourcePath ?: path, Base64.encode(bytes))
        } else previousItem ?: throw StorageRecoveryException(StorageRecoveryFailure.NativeWrite)
        captured[item.id] = item
        byPath[path] = item.id
        return ReferencePrefix + item.id
    }
    val mapped = connections.map { it.mapCertificatePaths(protector, ::capture) }
    return CapturedRecoveryCertificates(mapped, captured.values.toList(), nextId)
}

internal fun restoreRecoveryCertificates(
    connections: List<StorageRecoveryConnection>, certificates: List<StorageRecoveryCertificate>,
    files: StorageRecoveryFiles?, protector: StorageCredentialProtector,
): List<StorageRecoveryConnection> {
    if (certificates.isEmpty()) return connections
    val port = files ?: throw StorageRecoveryException(StorageRecoveryFailure.NativeWrite)
    val restored = certificates.associate { item ->
        val bytes = try { Base64.decode(item.bytes) } catch (_: Exception) { throw StorageRecoveryException(StorageRecoveryFailure.Unreadable) }
        if (bytes.size > MaximumCertificateBytes) throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
        val path = port.write(item.fileName(), bytes) ?: throw StorageRecoveryException(StorageRecoveryFailure.NativeWrite)
        (ReferencePrefix + item.id) to path
    }
    return connections.map { connection ->
        connection.mapCertificatePaths(protector) { path ->
            restored[path] ?: throw StorageRecoveryException(StorageRecoveryFailure.Unreadable)
        }
    }
}

private fun StorageRecoveryConnection.mapCertificatePaths(
    protector: StorageCredentialProtector, transform: (String) -> String,
): StorageRecoveryConnection {
    fun mapTls(tls: ConnectionTlsSettings) = tls.copy(
        customCertificatePath = tls.customCertificatePath?.takeIf { it.isNotBlank() }?.let(transform),
        clientCertificateKeyStorePath = tls.clientCertificateKeyStorePath?.takeIf { it.isNotBlank() }?.let(transform),
    )
    val companion = aurral_companion?.let { encoded ->
        val clear = protector.reveal(encoded) ?: throw StorageRecoveryException(StorageRecoveryFailure.Unreadable)
        val value = try { certificateJson.decodeFromString<AurralCompanion>(clear) }
            catch (_: Exception) { throw StorageRecoveryException(StorageRecoveryFailure.Unreadable) }
        val mapped = value.copy(tlsSettings = mapTls(value.tlsSettings))
        if (mapped == value) encoded else protector.protect(certificateJson.encodeToString(mapped))
            ?: throw StorageRecoveryException(StorageRecoveryFailure.NativeWrite)
    }
    return copy(custom_certificate_path = custom_certificate_path?.let(transform),
        client_certificate_keystore_path = client_certificate_keystore_path?.let(transform), aurral_companion = companion)
}
