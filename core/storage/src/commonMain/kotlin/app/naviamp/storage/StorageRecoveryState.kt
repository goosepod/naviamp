package app.naviamp.storage

import app.naviamp.host.StorageRecoveryDocumentPort
import app.naviamp.host.StorageRecoveryLock
import app.naviamp.host.StorageRecoveryWriteResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.naviamp.domain.radio.RadioDjPreset

enum class StorageRecoveryFailure { Capacity, Unreadable, UnsupportedVersion, Conflict, NativeWrite, TransactionAborted }

/** No native or English UI message: the shared presentation maps the reason to resources. */
class StorageRecoveryException(val reason: StorageRecoveryFailure) : RuntimeException()

/**
 * One bounded authoritative document. SQLite/audio/artwork/playback queues remain purgeable.
 * A native single-key write is not a promise of synchronous power-loss durability.
 */
class StorageRecoveryState(
    private val document: StorageRecoveryDocumentPort,
    private val protector: StorageCredentialProtector,
    private val lock: StorageRecoveryLock,
    private val limits: StorageRecoveryLimits = StorageRecoveryLimits(),
    private val files: StorageRecoveryFiles? = null,
) {
    private val json = Json { encodeDefaults = true }
    private var expected: String? = null
    private var committed = StorageRecoveryEnvelope()
    private var staged: StorageRecoveryEnvelope? = null
    private var depth = 0
    private var aborted = false
    private var requiresReload = false

    init {
        require(limits.plaintextBytes > 0 && limits.documentBytes > 0 && limits.settingCount > 0 && limits.connectionCount > 0)
        require(limits.domainReserveBytes >= 0 && limits.domainBytes > limits.domainReserveBytes)
        locked {
            val stored = document.read()
            expected = stored.encoded
            if (stored.exists) {
                val encoded = stored.encoded ?: throw StorageRecoveryException(StorageRecoveryFailure.Unreadable)
                if (!protector.isProtected(encoded)) throw StorageRecoveryException(StorageRecoveryFailure.Unreadable)
                if (encoded.encodeToByteArray().size > limits.documentBytes) throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
                val clear = protector.reveal(encoded) ?: throw StorageRecoveryException(StorageRecoveryFailure.Unreadable)
                if (clear.encodeToByteArray().size > limits.plaintextBytes) throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
                committed = try { json.decodeFromString<StorageRecoveryEnvelope>(clear) }
                catch (_: Exception) { throw StorageRecoveryException(StorageRecoveryFailure.Unreadable) }
                if (committed.version != 1) throw StorageRecoveryException(StorageRecoveryFailure.UnsupportedVersion)
                validate(committed)
            }
        }
    }

    fun read(key: String): String? = locked { current().values[key] }
    fun write(key: String, value: String) = transaction {
        require(key.isNotBlank() && key.length <= 256)
        if (value.length > limits.plaintextBytes) throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
        stage(current().copy(values = current().values + (key to value)))
    }
    fun remove(key: String) = transaction { stage(current().copy(values = current().values - key)) }

    internal fun connections(): List<StorageRecoveryConnection> = locked { current().connections }
    internal fun replaceConnections(connections: List<StorageRecoveryConnection>) = transaction {
        val captured = captureRecoveryCertificates(connections, current().certificates, current().nextCertificateId, files, protector)
        val ids = connections.map { it.id }.toSet()
        stage(current().copy(connections = captured.connections, certificates = captured.certificates, nextCertificateId = captured.nextId,
            playbackProfiles = current().playbackProfiles.filter { it.sourceId in ids },
            lyricsOffsets = current().lyricsOffsets.filter { it.sourceId in ids }))
    }
    internal fun radioDjPresets(): List<RadioDjPreset> = locked { current().radioDjPresets }
    internal fun replaceRadioDjPresets(presets: List<RadioDjPreset>) = transaction {
        stage(current().copy(radioDjPresets = presets))
    }

    internal fun replaceUserConfiguration(
        profiles: List<StorageRecoveryPlaybackProfile>, offsets: List<StorageRecoveryLyricsOffset>,
    ) = transaction { stage(current().copy(playbackProfiles = profiles, lyricsOffsets = offsets)) }

    internal fun restoreConnections(queries: NaviampStorageQueries) = transaction {
        queries.transaction {
            val saved = restoreRecoveryCertificates(connections(), current().certificates, files, protector)
            val ids = saved.map { it.id }.toSet()
            queries.selectMediaSources().executeAsList().filter { row ->
                val configured = saved.firstOrNull { it.id == row.id }
                row.id !in ids || configured?.cache_namespace != row.cache_namespace || configured.provider_id != row.provider_id
            }.forEach { queries.deleteMediaSource(it.id) }
            saved.forEach { it.restore(queries) }
            queries.clearRecoveryPlaybackProfiles()
            current().playbackProfiles.forEach { it.restore(queries) }
            queries.clearRecoveryLyricsOffsets()
            current().lyricsOffsets.forEach { it.restore(queries) }
        }
    }

    /** Nested writes form one commit. A caught nested failure still aborts the entire batch. */
    fun <T> transaction(block: () -> T): T = locked {
        val outer = depth == 0
        if (outer) { staged = committed; aborted = false }
        depth++
        try {
            val result = block()
            if (outer) {
                if (aborted) throw StorageRecoveryException(StorageRecoveryFailure.TransactionAborted)
                val candidate = requireNotNull(staged)
                if (candidate != committed) commit(candidate)
            }
            result
        } catch (failure: Throwable) {
            aborted = true
            if ((failure as? StorageRecoveryException)?.reason == StorageRecoveryFailure.Conflict) requiresReload = true
            throw failure
        } finally {
            depth--
            if (outer) staged = null
        }
    }

    /** The document commits while SQL can still roll back if preflight/native persistence fails. */
    fun <T> transaction(queries: NaviampStorageQueries, block: () -> T): T = locked {
        val before = expected
        var blockFailure: Throwable? = null
        try {
            queries.transactionWithResult {
                try { transaction(block) } catch (failure: Throwable) { blockFailure = failure; throw failure }
            }
        } catch (failure: Throwable) {
            // A driver commit/rollback failure, or a failure after the authoritative document
            // changed, leaves the SQL projection uncertain. Never journal its stale rows again.
            if (failure !== blockFailure || expected != before) {
                requiresReload = true
                throw StorageRecoveryException(StorageRecoveryFailure.Conflict).also { it.addSuppressed(failure) }
            }
            throw failure
        }
    }

    private fun current() = staged ?: committed
    private fun stage(value: StorageRecoveryEnvelope) { validate(value); staged = value }

    private fun validate(value: StorageRecoveryEnvelope): String {
        val sourceIds = value.connections.map { it.id }.toSet()
        if (value.values.size > limits.settingCount || value.connections.size > limits.connectionCount ||
            value.connections.map { it.id }.toSet().size != value.connections.size ||
            value.connections.any { it.id.isBlank() || it.cache_namespace.isBlank() } ||
            value.connections.map { it.cache_namespace }.toSet().size != value.connections.size ||
            value.nextCertificateId < 0 || value.certificates.size > 64 || value.certificates.map { it.id }.toSet().size != value.certificates.size ||
            value.certificates.any { it.id < 0 || it.id >= value.nextCertificateId } ||
            value.playbackProfiles.any { it.sourceId !in sourceIds } || value.lyricsOffsets.any { it.sourceId !in sourceIds } ||
            value.values.keys.any { it.isBlank() || it.length > 256 }) {
            throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
        }
        val clear = json.encodeToString(value)
        if (clear.encodeToByteArray().size > limits.plaintextBytes) throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
        return clear
    }

    private fun commit(value: StorageRecoveryEnvelope) {
        val clear = validate(value)
        val encoded = protector.protect(clear) ?: throw StorageRecoveryException(StorageRecoveryFailure.NativeWrite)
        if (!protector.isProtected(encoded)) throw StorageRecoveryException(StorageRecoveryFailure.NativeWrite)
        if (encoded.encodeToByteArray().size > limits.documentBytes) throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
        val domainBytes = document.projectedDomainBytes(encoded)
        if (domainBytes < 0) throw StorageRecoveryException(StorageRecoveryFailure.NativeWrite)
        if (domainBytes > limits.domainBytes - limits.domainReserveBytes) throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
        when (document.compareAndSet(expected, encoded)) {
            StorageRecoveryWriteResult.Committed -> {
                val obsolete = committed.certificates.filterNot { old -> value.certificates.any { it.id == old.id } }
                committed = value; expected = encoded
                obsolete.forEach { item -> runCatching { files?.remove(item.fileName()) } }
            }
            StorageRecoveryWriteResult.Conflict -> throw StorageRecoveryException(StorageRecoveryFailure.Conflict)
            StorageRecoveryWriteResult.Failed -> throw StorageRecoveryException(StorageRecoveryFailure.NativeWrite)
        }
    }

    private fun <T> locked(block: () -> T): T {
        lock.lock()
        return try {
            if (requiresReload) throw StorageRecoveryException(StorageRecoveryFailure.Conflict)
            block()
        } finally { lock.unlock() }
    }
}

data class StorageRecoveryLimits(
    val plaintextBytes: Int = 128 * 1024,
    val documentBytes: Int = 192 * 1024,
    val domainBytes: Long = 384 * 1024L,
    val domainReserveBytes: Long = 4 * 1024L,
    val settingCount: Int = 192,
    val connectionCount: Int = 32,
)

@Serializable private data class StorageRecoveryEnvelope(
    val version: Int = 1,
    val values: Map<String, String> = emptyMap(),
    val connections: List<StorageRecoveryConnection> = emptyList(),
    val radioDjPresets: List<RadioDjPreset> = emptyList(),
    val playbackProfiles: List<StorageRecoveryPlaybackProfile> = emptyList(),
    val lyricsOffsets: List<StorageRecoveryLyricsOffset> = emptyList(),
    val certificates: List<StorageRecoveryCertificate> = emptyList(),
    val nextCertificateId: Long = 0,
)
