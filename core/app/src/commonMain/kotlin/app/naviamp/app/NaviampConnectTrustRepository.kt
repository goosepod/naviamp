package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectTrustRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Narrow durable-string effect. Core owns the Connect trust schema and update policy. */
fun interface NaviampConnectTrustStorageEffect {
    fun write(value: String)

    fun read(): String? = null
}

/** Core-owned durable trust repository shared by every host. */
class NaviampConnectTrustRepository(
    private val storage: NaviampConnectTrustStorageEffect,
) {
    fun load(): List<NaviampConnectTrustRecord> = storage.read()
        ?.takeIf(String::isNotBlank)
        ?.let { value -> runCatching { json.decodeFromString<List<NaviampConnectTrustRecord>>(value) }.getOrNull() }
        .orEmpty()
        .distinctBy(NaviampConnectTrustRecord::trustedDeviceId)
        .sortedByDescending(NaviampConnectTrustRecord::pairedAtEpochMillis)

    fun upsert(record: NaviampConnectTrustRecord): List<NaviampConnectTrustRecord> {
        val updated = (load().filterNot { it.trustedDeviceId == record.trustedDeviceId } + record)
            .sortedByDescending(NaviampConnectTrustRecord::pairedAtEpochMillis)
        storage.write(json.encodeToString(updated))
        return updated
    }

    fun remove(trustedDeviceId: String): List<NaviampConnectTrustRecord> {
        val updated = load().filterNot { it.trustedDeviceId == trustedDeviceId }
        storage.write(json.encodeToString(updated))
        return updated
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
