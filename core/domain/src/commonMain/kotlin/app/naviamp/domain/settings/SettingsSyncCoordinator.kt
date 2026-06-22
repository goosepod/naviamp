package app.naviamp.domain.settings

data class SettingsSyncRuntimeState(
    val autoExportEnabled: Boolean = false,
    val lastLocalUpdateEpochMillis: Long = 0L,
    val lastAppliedSyncUpdateEpochMillis: Long = 0L,
) {
    fun normalized(): SettingsSyncRuntimeState =
        copy(
            lastLocalUpdateEpochMillis = lastLocalUpdateEpochMillis.coerceAtLeast(0L),
            lastAppliedSyncUpdateEpochMillis = lastAppliedSyncUpdateEpochMillis.coerceAtLeast(0L),
        )
}

data class SettingsSyncOperationResult(
    val kind: SettingsSyncOperationKind,
    val documentToWrite: SettingsSyncDocument? = null,
    val hasServerProfiles: Boolean = false,
)

enum class SettingsSyncOperationKind {
    Imported,
    Exported,
    NoOp,
    UnsupportedSyncFile,
    NeedsSetupChoice,
    MissingSyncLocation,
}

enum class SettingsSyncMirrorDocumentSource {
    LocalMirror,
    Provider,
    None,
}

data class SettingsSyncMirrorDocumentSelection(
    val document: SettingsSyncDocument?,
    val source: SettingsSyncMirrorDocumentSource,
)

fun selectSettingsSyncMirrorDocument(
    localMirrorDocument: SettingsSyncDocument?,
    providerDocument: SettingsSyncDocument?,
): SettingsSyncMirrorDocumentSelection =
    when {
        providerDocument == null && localMirrorDocument == null ->
            SettingsSyncMirrorDocumentSelection(null, SettingsSyncMirrorDocumentSource.None)
        providerDocument == null ->
            SettingsSyncMirrorDocumentSelection(localMirrorDocument, SettingsSyncMirrorDocumentSource.LocalMirror)
        localMirrorDocument == null ->
            SettingsSyncMirrorDocumentSelection(providerDocument, SettingsSyncMirrorDocumentSource.Provider)
        providerDocument.updatedAtEpochMillis > localMirrorDocument.updatedAtEpochMillis ->
            SettingsSyncMirrorDocumentSelection(providerDocument, SettingsSyncMirrorDocumentSource.Provider)
        else ->
            SettingsSyncMirrorDocumentSelection(localMirrorDocument, SettingsSyncMirrorDocumentSource.LocalMirror)
    }

class SettingsSyncCoordinator(
    private val deviceId: String,
    private val state: () -> SettingsSyncRuntimeState,
    private val saveState: (SettingsSyncRuntimeState) -> Unit,
    private val nowEpochMillis: () -> Long,
    private val buildLocalDocument: (Long) -> SettingsSyncDocument,
    private val applyDocument: (SettingsSyncDocument) -> Unit,
) {
    fun nextTimestamp(): Long =
        nowEpochMillis().coerceAtLeast(state().lastLocalUpdateEpochMillis + 1L)

    fun markLocalChanged() {
        saveState(state().copy(lastLocalUpdateEpochMillis = nextTimestamp()).normalized())
    }

    fun markDocumentApplied(updatedAtEpochMillis: Long) {
        saveState(
            state().copy(
                lastLocalUpdateEpochMillis = updatedAtEpochMillis,
                lastAppliedSyncUpdateEpochMillis = updatedAtEpochMillis,
            ).normalized(),
        )
    }

    fun applySyncedDocument(document: SettingsSyncDocument): SettingsSyncOperationResult {
        applyDocument(document)
        markDocumentApplied(document.updatedAtEpochMillis)
        return SettingsSyncOperationResult(
            kind = SettingsSyncOperationKind.Imported,
            hasServerProfiles = document.serverProfiles.isNotEmpty(),
        )
    }

    fun exportCurrent(markChanged: Boolean = false): SettingsSyncOperationResult {
        if (markChanged) markLocalChanged()
        val timestamp = state().lastLocalUpdateEpochMillis.takeIf { it > 0L } ?: nextTimestamp()
        return SettingsSyncOperationResult(
            kind = SettingsSyncOperationKind.Exported,
            documentToWrite = buildLocalDocument(timestamp),
        )
    }

    fun documentWritten(document: SettingsSyncDocument) {
        markDocumentApplied(document.updatedAtEpochMillis)
    }

    fun autoExport(): SettingsSyncOperationResult? =
        if (state().autoExportEnabled) exportCurrent() else null

    fun reconcileStartup(
        syncedDocument: SettingsSyncDocument?,
        syncLocationConfigured: Boolean,
    ): SettingsSyncOperationResult {
        val localDocument = state().lastLocalUpdateEpochMillis
            .takeIf { it > 0L }
            ?.let(buildLocalDocument)
        return executePlan(
            planSettingsSync(
                localDocument = localDocument,
                syncedDocument = syncedDocument,
                syncLocationConfigured = syncLocationConfigured,
                nowEpochMillis = nextTimestamp(),
                deviceId = deviceId,
            ),
        )
    }

    private fun executePlan(plan: SettingsSyncPlan): SettingsSyncOperationResult =
        when (plan.action) {
            SettingsSyncAction.ImportFromSyncFile -> plan.documentToImport
                ?.let(::applySyncedDocument)
                ?: SettingsSyncOperationResult(SettingsSyncOperationKind.NoOp)
            SettingsSyncAction.ExportToSyncFile -> SettingsSyncOperationResult(
                kind = SettingsSyncOperationKind.Exported,
                documentToWrite = plan.documentToWrite,
            )
            SettingsSyncAction.UnsupportedSyncFile ->
                SettingsSyncOperationResult(SettingsSyncOperationKind.UnsupportedSyncFile)
            SettingsSyncAction.NoOp ->
                SettingsSyncOperationResult(SettingsSyncOperationKind.NoOp)
            SettingsSyncAction.AskForInitialSetupChoice ->
                SettingsSyncOperationResult(SettingsSyncOperationKind.NeedsSetupChoice)
        }
}
