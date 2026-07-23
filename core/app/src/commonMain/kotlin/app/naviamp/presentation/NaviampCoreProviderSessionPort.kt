package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.provider.MediaProvider
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
    suspend fun connect(
        request: NaviampCoreConnectionRequest,
        plan: NaviampConnectionAttemptPlan,
    ): NaviampCoreConnectedSession

    suspend fun editableConnection(id: String): NaviampCoreEditableConnection
    suspend fun deleteConnection(id: String): NaviampCoreConnectionInventory
    suspend fun smartPlaylistProvider(password: String?): MediaProvider?
    suspend fun refreshActiveSession(): Boolean
    suspend fun persistActiveSession()
    suspend fun clearActiveSession()
}
