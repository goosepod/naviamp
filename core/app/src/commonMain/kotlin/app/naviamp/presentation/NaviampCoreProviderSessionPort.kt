package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ProviderIdNavidrome
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.ConnectionFormState

fun interface NaviampCoreMediaProviderSource {
    fun current(): MediaProvider?
}

data class NaviampCoreSavedConnectionRecord(
    val id: String,
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val providerId: String = ProviderIdNavidrome,
    val selectedMusicFolderIds: List<String> = emptyList(),
)

data class NaviampCoreConnectionInventory(
    val connections: List<NaviampCoreSavedConnectionRecord> = emptyList(),
    val currentSourceId: String? = null,
)

data class NaviampCoreEditableConnection(
    val form: ConnectionFormState,
    val availableMusicFolders: List<ConnectionFormMusicFolder> = emptyList(),
    val musicFoldersLoadFailed: Boolean = false,
)

sealed interface NaviampCoreConnectionRequest {
    data class Form(
        val form: ConnectionFormState,
        val savedConnectionId: String? = null,
    ) : NaviampCoreConnectionRequest

    data class Saved(val id: String) : NaviampCoreConnectionRequest
}

data class NaviampCoreConnectedSession(
    val sourceId: String,
    val displayName: String,
    val serverVersion: String? = null,
    val inventory: NaviampCoreConnectionInventory,
)

/** Credential access and provider-session construction are effects; all connection policy is Core. */
interface NaviampCoreProviderSessionPort {
    val providerSource: NaviampCoreMediaProviderSource
        get() = NaviampCoreMediaProviderSource(::currentProvider)

    fun currentProvider(): MediaProvider? = null

    fun currentSourceId(): String? = initialInventory().currentSourceId

    fun initialInventory(): NaviampCoreConnectionInventory = NaviampCoreConnectionInventory()

    /** Publishes repository changes made outside connection commands, such as settings import. */
    fun replaceSavedConnections(connections: List<NaviampCoreSavedConnectionRecord>) = Unit

    suspend fun connect(
        request: NaviampCoreConnectionRequest,
        plan: NaviampConnectionAttemptPlan,
    ): NaviampCoreConnectedSession

    suspend fun saveAurralCompanion(sourceId: String, companion: app.naviamp.domain.source.AurralCompanion) {
        error("Companion persistence is unavailable.")
    }

    /** Signs in to [companion] once, without saving it, so bad settings are reported before they are kept. */
    suspend fun verifyAurralCompanion(
        companion: app.naviamp.domain.source.AurralCompanion,
    ): app.naviamp.domain.provider.ProviderDiscoveryStatus = app.naviamp.domain.provider.ProviderDiscoveryStatus.Unsupported

    suspend fun editableConnection(id: String): NaviampCoreEditableConnection

    /**
     * Validates [form] and saves it as an account without changing the active account (#714).
     * [savedConnectionId] names an existing account to update instead of adding a new one.
     * Returns the saved account's id; throws when validation fails.
     */
    suspend fun saveValidatedConnection(form: ConnectionFormState, savedConnectionId: String?): String =
        error("Saving an account without activating it is unavailable.")

    suspend fun currentProvisioningConnection(): NaviampCoreEditableConnection? =
        currentSourceId()?.let { id -> editableConnection(id) }
    suspend fun deleteConnection(id: String): NaviampCoreConnectionInventory
    suspend fun smartPlaylistProvider(password: String?): MediaProvider?
    suspend fun refreshActiveSession(): Boolean
    suspend fun persistActiveSession()
    suspend fun clearActiveSession()
}
