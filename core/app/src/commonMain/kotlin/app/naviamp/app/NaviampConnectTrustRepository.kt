package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectTrustRecord
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Narrow durable-string effect. Core owns the Connect trust schema and update policy. */
fun interface NaviampConnectTrustStorageEffect {
    fun write(value: String)

    fun read(): String? = null
}

/** Core-owned durable trust repository shared by every host. */
class NaviampConnectTrustRepository(
    private val storage: NaviampConnectTrustStorageEffect,
) {
    fun load(): List<NaviampConnectTrustRecord> = loadState().records
        .distinctBy { it.peerDevice.deviceId }
        .sortedByDescending(NaviampConnectTrustRecord::pairedAtEpochMillis)

    fun selfName(): String? = loadState().selfName

    fun setSelfName(name: String?): String? {
        val normalized = normalizeDeviceName(name)
        write(loadState().copy(selfName = normalized))
        return normalized
    }

    fun setAlias(trustedDeviceId: String, alias: String?): List<NaviampConnectTrustRecord> {
        val normalized = normalizeDeviceName(alias)
        val state = loadState()
        val updated = state.records.map { record ->
            if (record.trustedDeviceId == trustedDeviceId) record.copy(localAlias = normalized) else record
        }
        write(state.copy(records = updated))
        return updated
    }

    fun upsert(record: NaviampConnectTrustRecord): List<NaviampConnectTrustRecord> {
        val state = loadState()
        val previous = state.records.firstOrNull {
            it.trustedDeviceId == record.trustedDeviceId || it.peerDevice.deviceId == record.peerDevice.deviceId
        }
        val merged = record.copy(localAlias = previous?.localAlias ?: record.localAlias)
        val updated = (state.records.filterNot {
            it.trustedDeviceId == record.trustedDeviceId ||
                it.peerDevice.deviceId == record.peerDevice.deviceId
        } + merged)
            .sortedByDescending(NaviampConnectTrustRecord::pairedAtEpochMillis)
        write(state.copy(records = updated))
        return updated
    }

    fun remove(trustedDeviceId: String): List<NaviampConnectTrustRecord> {
        val state = loadState()
        val updated = state.records.filterNot { it.trustedDeviceId == trustedDeviceId }
        write(state.copy(records = updated))
        return updated
    }

    private fun loadState(): NaviampConnectTrustState {
        val value = storage.read()?.takeIf(String::isNotBlank) ?: return NaviampConnectTrustState()
        return if (value.trimStart().startsWith("[")) {
            NaviampConnectTrustState(
                records = runCatching {
                    json.decodeFromString<List<NaviampConnectTrustRecord>>(value)
                }.getOrNull().orEmpty(),
            )
        } else {
            runCatching {
                val root = json.parseToJsonElement(value).jsonObject
                NaviampConnectTrustState(
                    selfName = root["selfName"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank),
                    records = root["records"]?.let {
                        json.decodeFromJsonElement<List<NaviampConnectTrustRecord>>(it)
                    }.orEmpty(),
                )
            }.getOrNull() ?: NaviampConnectTrustState()
        }
    }

    private fun write(state: NaviampConnectTrustState) {
        storage.write(
            buildJsonObject {
                state.selfName?.let { put("selfName", it) }
                put("records", json.encodeToJsonElement(state.records))
            }.toString(),
        )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private data class NaviampConnectTrustState(
    val selfName: String? = null,
    val records: List<NaviampConnectTrustRecord> = emptyList(),
)

internal fun normalizeDeviceName(value: String?): String? = value
    ?.trim()
    ?.also { require(it.length <= 64) { "Device names may contain at most 64 characters." } }
    ?.takeIf(String::isNotEmpty)
