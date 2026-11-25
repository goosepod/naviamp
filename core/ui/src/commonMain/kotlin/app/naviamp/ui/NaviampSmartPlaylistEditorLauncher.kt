package app.naviamp.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import app.naviamp.domain.library.LibrarySyncProgress
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.smartplaylist.SmartPlaylistDraft
import app.naviamp.domain.smartplaylist.SmartPlaylistEditingResult
import app.naviamp.domain.smartplaylist.SmartPlaylistGenreOption
import app.naviamp.domain.smartplaylist.SmartPlaylistPreview
import app.naviamp.domain.smartplaylist.SmartPlaylistReference
import app.naviamp.domain.smartplaylist.duplicateDraft
import app.naviamp.domain.smartplaylist.duplicateSmartPlaylistName
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

internal enum class SmartPlaylistEditorMode { Create, Edit, Duplicate }

internal data class SmartPlaylistEditorSession(
    val mode: SmartPlaylistEditorMode,
    val initialDraft: SmartPlaylistDraft = SmartPlaylistDraft(),
    val source: SharedMediaItemUi? = null,
) {
    /** Only an edit may write to the source; create and duplicate always create a new playlist. */
    val updateTarget: SharedMediaItemUi?
        get() = source.takeIf { mode == SmartPlaylistEditorMode.Edit }

    suspend fun save(
        definition: SmartPlaylistDefinition,
        password: String?,
        create: suspend (SmartPlaylistDefinition, String?) -> Unit,
        update: suspend (SharedMediaItemUi, SmartPlaylistDefinition, String?) -> Unit,
    ) {
        val target = updateTarget
        if (target == null) create(definition, password) else update(target, definition, password)
    }
}

internal data class SmartPlaylistEditorRequest(val playlist: SharedMediaItemUi, val mode: SmartPlaylistEditorMode)

internal sealed interface SmartPlaylistEditorLoadFailure {
    data class PasswordRequired(val mode: SmartPlaylistEditorMode) : SmartPlaylistEditorLoadFailure
    data object Unsupported : SmartPlaylistEditorLoadFailure
    data class Message(val text: String) : SmartPlaylistEditorLoadFailure
}

/** Shared load policy for editing and duplicating, so a lookup failure never opens a fallback editor. */
@Stable
internal class SmartPlaylistEditorLauncher(
    private val scope: CoroutineScope,
    private val inspect: suspend (SharedMediaItemUi) -> SmartPlaylistEditingResult,
    private val existingNames: () -> Collection<String>,
    private val duplicateName: suspend (String, Int) -> String,
) {
    var session by mutableStateOf<SmartPlaylistEditorSession?>(null)
        private set
    var inspection by mutableStateOf<SmartPlaylistEditingResult?>(null)
        private set
    var failure by mutableStateOf<SmartPlaylistEditorLoadFailure?>(null)
        private set
    var passwordRequest by mutableStateOf<SmartPlaylistEditorRequest?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    fun create() {
        failure = null
        session = SmartPlaylistEditorSession(SmartPlaylistEditorMode.Create)
    }

    fun request(playlist: SharedMediaItemUi, mode: SmartPlaylistEditorMode) {
        if (loading) return
        loading = true
        failure = null
        scope.launch {
            try {
                open(SmartPlaylistEditorRequest(playlist, mode), inspect(playlist))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error.requiresSmartPlaylistPassword()) {
                    passwordRequest = SmartPlaylistEditorRequest(playlist, mode)
                    failure = SmartPlaylistEditorLoadFailure.PasswordRequired(mode)
                } else {
                    failure = if (error is IllegalArgumentException) SmartPlaylistEditorLoadFailure.Unsupported
                        else SmartPlaylistEditorLoadFailure.Message(error.message.orEmpty())
                }
            } finally {
                loading = false
            }
        }
    }

    fun openAfterPassword(editing: SmartPlaylistEditingResult) {
        val request = passwordRequest ?: return
        passwordRequest = null
        scope.launch { open(request, editing) }
    }

    fun dismissPassword() {
        passwordRequest = null
    }

    fun dismissInspection() {
        inspection = null
    }

    fun close() {
        session = null
    }

    private suspend fun open(request: SmartPlaylistEditorRequest, editing: SmartPlaylistEditingResult) {
        if (editing !is SmartPlaylistEditingResult.Editable) {
            inspection = editing
            failure = null
            return
        }
        val draft = when (request.mode) {
            SmartPlaylistEditorMode.Duplicate -> editing.definition.duplicateDraft(
                duplicateSmartPlaylistName(editing.definition.name, existingNames() + editing.definition.name, duplicateName),
            )
            else -> SmartPlaylistDraft.fromDefinition(editing.definition)
        }
        failure = null
        session = SmartPlaylistEditorSession(request.mode, draft, request.playlist)
    }
}

@Composable
internal fun rememberSmartPlaylistEditorLauncher(
    inspect: suspend (SharedMediaItemUi) -> SmartPlaylistEditingResult,
    existingNames: Collection<String>,
): SmartPlaylistEditorLauncher {
    val scope = rememberCoroutineScope()
    val currentInspect by rememberUpdatedState(inspect)
    val currentNames by rememberUpdatedState(existingNames)
    return remember(scope) {
        SmartPlaylistEditorLauncher(scope, { currentInspect(it) }, { currentNames }, ::localizedDuplicateName)
    }
}

private suspend fun localizedDuplicateName(base: String, ordinal: Int): String =
    if (ordinal == 1) getString(Res.string.smart_playlist_duplicate_name, base)
    else getString(Res.string.smart_playlist_duplicate_name_numbered, base, ordinal)

@Composable
internal fun SmartPlaylistEditorLoadFailureText(failure: SmartPlaylistEditorLoadFailure?, colors: NaviampColors) {
    val message = when (failure) {
        null -> return
        is SmartPlaylistEditorLoadFailure.PasswordRequired -> when (failure.mode) {
            SmartPlaylistEditorMode.Duplicate -> stringResource(Res.string.smart_playlist_duplicate_password)
            else -> "Enter your Navidrome password to edit this smart playlist."
        }
        SmartPlaylistEditorLoadFailure.Unsupported -> stringResource(Res.string.smart_playlist_unsupported_import)
        is SmartPlaylistEditorLoadFailure.Message -> failure.text.ifBlank { stringResource(Res.string.playlists_load_smart_failed) }
    }
    Text(message, color = colors.secondaryText, fontSize = 12.sp)
}

/** Renders whichever editor, inspector, or password prompt the launcher currently needs. */
@Composable
internal fun SmartPlaylistEditorHost(
    launcher: SmartPlaylistEditorLauncher,
    colors: NaviampColors,
    availableLibraries: List<ConnectionFormMusicFolder>,
    selectedConnectionLibraryIds: List<String>,
    genreCatalog: List<SmartPlaylistGenreOption>,
    onReferences: suspend () -> List<SmartPlaylistReference>,
    onPreview: suspend (SmartPlaylistDefinition) -> SmartPlaylistPreview,
    onSyncLibrary: (suspend (suspend (LibrarySyncProgress) -> Unit) -> Unit)?,
    onInspectWithPassword: suspend (SharedMediaItemUi, String) -> SmartPlaylistEditingResult,
    onCreate: suspend (SmartPlaylistDefinition, String?) -> Unit,
    onUpdate: suspend (SharedMediaItemUi, SmartPlaylistDefinition, String?) -> Unit,
) {
    launcher.session?.let { session ->
        SmartPlaylistBuilderDialog(
            colors = colors,
            initialDraft = session.initialDraft,
            currentPlaylistId = session.updateTarget?.id,
            title = stringResource(
                when (session.mode) {
                    SmartPlaylistEditorMode.Create -> Res.string.playlists_smart
                    SmartPlaylistEditorMode.Edit -> Res.string.playlists_edit_smart
                    SmartPlaylistEditorMode.Duplicate -> Res.string.playlists_duplicate_smart
                },
            ),
            saveLabel = stringResource(
                if (session.mode == SmartPlaylistEditorMode.Edit) Res.string.playlists_update else Res.string.common_save,
            ),
            availableLibraries = availableLibraries,
            selectedConnectionLibraryIds = selectedConnectionLibraryIds,
            genreCatalog = genreCatalog,
            onReferences = onReferences,
            onPreview = onPreview,
            onSyncLibrary = onSyncLibrary,
            onDismissRequest = launcher::close,
            onSave = { definition ->
                session.save(definition, null, onCreate, onUpdate)
                launcher.close()
            },
            onSaveWithPassword = { definition, password ->
                session.save(definition, password, onCreate, onUpdate)
                launcher.close()
            },
        )
    }
    launcher.inspection?.let { editing ->
        SmartPlaylistInspectionDialog(editing, colors, launcher::dismissInspection)
    }
    launcher.passwordRequest?.let { request ->
        SmartPlaylistLoadPasswordDialog(
            playlist = request.playlist,
            colors = colors,
            onDismissRequest = launcher::dismissPassword,
            onInspectWithPassword = onInspectWithPassword,
            onReadOnly = launcher::openAfterPassword,
            onLoaded = { definition -> launcher.openAfterPassword(SmartPlaylistEditingResult.Editable(definition, "")) },
        )
    }
}
