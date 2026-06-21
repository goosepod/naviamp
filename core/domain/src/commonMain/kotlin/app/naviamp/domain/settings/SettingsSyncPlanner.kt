package app.naviamp.domain.settings

data class SettingsSyncPlan(
    val action: SettingsSyncAction,
    val documentToWrite: SettingsSyncDocument? = null,
    val documentToImport: SettingsSyncDocument? = null,
    val requiresServerSecrets: Boolean = false,
    val reason: SettingsSyncPlanReason,
)

enum class SettingsSyncAction {
    AskForInitialSetupChoice,
    ImportFromSyncFile,
    ExportToSyncFile,
    NoOp,
    UnsupportedSyncFile,
}

enum class SettingsSyncPlanReason {
    NoLocalOrSyncedSettings,
    FirstRunWithSyncedSettings,
    LocalSettingsNeedInitialExport,
    SyncedSettingsAreNewer,
    LocalSettingsAreNewer,
    DocumentsMatch,
    SyncedSchemaIsNewer,
}

fun planSettingsSync(
    localDocument: SettingsSyncDocument?,
    syncedDocument: SettingsSyncDocument?,
    syncLocationConfigured: Boolean,
    nowEpochMillis: Long,
    deviceId: String,
): SettingsSyncPlan {
    val local = localDocument?.normalized()
    val synced = syncedDocument?.normalized()

    if (synced != null && synced.schemaVersion > CurrentSettingsSyncSchemaVersion) {
        return SettingsSyncPlan(
            action = SettingsSyncAction.UnsupportedSyncFile,
            documentToImport = synced,
            reason = SettingsSyncPlanReason.SyncedSchemaIsNewer,
        )
    }

    if (!syncLocationConfigured && local == null && synced == null) {
        return SettingsSyncPlan(
            action = SettingsSyncAction.AskForInitialSetupChoice,
            reason = SettingsSyncPlanReason.NoLocalOrSyncedSettings,
        )
    }

    if (local == null && synced != null) {
        return SettingsSyncPlan(
            action = SettingsSyncAction.ImportFromSyncFile,
            documentToImport = synced,
            requiresServerSecrets = synced.serverProfiles.isNotEmpty(),
            reason = SettingsSyncPlanReason.FirstRunWithSyncedSettings,
        )
    }

    if (local != null && synced == null) {
        return SettingsSyncPlan(
            action = SettingsSyncAction.ExportToSyncFile,
            documentToWrite = local.withSyncWriteMetadata(nowEpochMillis, deviceId),
            reason = SettingsSyncPlanReason.LocalSettingsNeedInitialExport,
        )
    }

    if (local != null && synced != null) {
        return when {
            synced.updatedAtEpochMillis > local.updatedAtEpochMillis -> SettingsSyncPlan(
                action = SettingsSyncAction.ImportFromSyncFile,
                documentToImport = synced,
                requiresServerSecrets = synced.serverProfiles.isNotEmpty(),
                reason = SettingsSyncPlanReason.SyncedSettingsAreNewer,
            )
            local.updatedAtEpochMillis > synced.updatedAtEpochMillis -> SettingsSyncPlan(
                action = SettingsSyncAction.ExportToSyncFile,
                documentToWrite = local.withSyncWriteMetadata(nowEpochMillis, deviceId),
                reason = SettingsSyncPlanReason.LocalSettingsAreNewer,
            )
            else -> SettingsSyncPlan(
                action = SettingsSyncAction.NoOp,
                reason = SettingsSyncPlanReason.DocumentsMatch,
            )
        }
    }

    return SettingsSyncPlan(
        action = SettingsSyncAction.NoOp,
        reason = SettingsSyncPlanReason.DocumentsMatch,
    )
}

fun SettingsSyncDocument.withSyncWriteMetadata(
    nowEpochMillis: Long,
    deviceId: String,
): SettingsSyncDocument =
    normalized().copy(
        updatedAtEpochMillis = nowEpochMillis.coerceAtLeast(updatedAtEpochMillis),
        lastWriterDeviceId = deviceId.trim().takeIf { it.isNotEmpty() },
    )
