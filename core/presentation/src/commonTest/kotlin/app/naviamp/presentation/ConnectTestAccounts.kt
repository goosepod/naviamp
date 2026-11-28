package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.SubsonicAuthApiKey
import app.naviamp.domain.source.SubsonicAuthPassword

/**
 * A device's saved accounts with their stored secrets, behind the provider-session boundary. It
 * validates a form by its secret ([rejectedSecrets] fail), saves without switching accounts, and
 * records the settings documents applied to it.
 */
internal class ConnectTestAccounts(
    initial: List<SavedMediaSource> = emptyList(),
    active: String? = initial.firstOrNull()?.id,
    private val rejectedSecrets: Set<String> = emptySet(),
) : NaviampCoreProviderSessionPort {
    val sources = initial.toMutableList()
    var activeId: String? = active
        private set
    val appliedSettings = mutableListOf<SettingsSyncDocument>()
    val validatedSecrets = mutableListOf<String>()

    fun source(serverUrl: String, username: String = "ana"): SavedMediaSource? =
        sources.firstOrNull { it.baseUrl == serverUrl && it.username == username }

    override fun currentSourceId(): String? = activeId

    override fun initialInventory() = NaviampCoreConnectionInventory(sources.map { saved ->
        NaviampCoreSavedConnectionRecord(saved.id, saved.displayName, saved.baseUrl, saved.username,
            saved.providerId, saved.selectedMusicFolderIds)
    }, activeId)

    override suspend fun saveValidatedConnection(form: ConnectionFormState, savedConnectionId: String?): String {
        val secret = form.apiKey.ifBlank { form.password }
        validatedSecrets += secret
        check(secret.isNotBlank() && secret !in rejectedSecrets) { "Synthetic validation failure" }
        val id = savedConnectionId ?: "${form.providerId}|${form.serverUrl}|${form.username}"
        val apiKey = form.authenticationMode == SubsonicAuthApiKey
        val saved = testAccount(
            id = id,
            url = form.serverUrl,
            name = form.displayName.ifBlank { form.serverUrl },
            username = form.username,
            providerId = form.providerId,
            password = form.password.takeIf { !apiKey && it.isNotBlank() },
            apiKey = form.apiKey.takeIf { apiKey },
            headers = form.customHeaders.map { ConnectionHeaderDefinition(it.name, it.value, it.valueIsSecret) },
            folders = form.selectedMusicFolderIds,
        ).copy(aurralCompanion = form.aurralCompanion)
        sources.removeAll { it.id == id }
        sources += saved
        return id
    }

    override suspend fun connect(request: NaviampCoreConnectionRequest, plan: NaviampConnectionAttemptPlan): NaviampCoreConnectedSession {
        val id = when (request) {
            is NaviampCoreConnectionRequest.Saved -> request.id
            is NaviampCoreConnectionRequest.Form -> saveValidatedConnection(request.form, request.savedConnectionId)
        }
        val saved = sources.first { it.id == id }
        activeId = id
        return NaviampCoreConnectedSession(id, saved.displayName, "test", initialInventory())
    }

    override suspend fun editableConnection(id: String) = sources.first { it.id == id }.let { saved ->
        NaviampCoreEditableConnection(ConnectionFormState(
            providerId = saved.providerId, displayName = saved.displayName, serverUrl = saved.baseUrl,
            username = saved.username, password = saved.password.orEmpty(),
            customHeaders = saved.customHeaders.map { ConnectionFormHeader(it.name, it.value.orEmpty(), it.valueIsSecret) },
        ))
    }

    override suspend fun deleteConnection(id: String): NaviampCoreConnectionInventory {
        sources.removeAll { it.id == id }
        if (activeId == id) activeId = null
        return initialInventory()
    }

    override suspend fun smartPlaylistProvider(password: String?) = null
    override suspend fun refreshActiveSession() = true
    override suspend fun persistActiveSession() = Unit
    override suspend fun clearActiveSession() { activeId = null }

    fun settingsSync(): NaviampCoreSettingsSyncServices {
        var runtime = app.naviamp.domain.settings.SettingsSyncRuntimeState()
        return NaviampCoreSettingsSyncServices(
            controller = app.naviamp.app.NaviampSettingsSyncController(
                deviceId = "test",
                state = { runtime },
                saveState = { runtime = it },
                nowEpochMillis = { 1L },
                snapshot = { SettingsSyncLocalSnapshot(serverProfiles = sources.toList()) },
                applyDocument = { appliedSettings += it },
            ),
            port = UnavailableNaviampCoreSettingsSyncPort,
        )
    }
}

internal fun testAccount(
    id: String,
    url: String,
    name: String = id,
    username: String = "ana",
    providerId: String = "navidrome",
    password: String? = "pw-$id",
    apiKey: String? = null,
    token: String = "",
    headers: List<ConnectionHeaderDefinition> = emptyList(),
    folders: List<String> = emptyList(),
) = SavedMediaSource(
    id = id, providerId = providerId, cacheNamespace = "$providerId:$url:$username:$folders", displayName = name,
    baseUrl = url, username = username, token = apiKey ?: token, salt = "", password = password,
    customHeaders = headers, selectedMusicFolderIds = folders,
    createdAtEpochMillis = 1L, lastConnectedAtEpochMillis = 1L, lastSyncStartedAtEpochMillis = null,
    lastSyncCompletedAtEpochMillis = null,
    authenticationMode = if (apiKey != null) SubsonicAuthApiKey else SubsonicAuthPassword,
)
