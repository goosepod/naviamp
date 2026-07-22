package app.naviamp.desktop

import app.naviamp.domain.provider.PendingProviderAction
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.storage.NaviampStorageQueries
import app.naviamp.storage.Pending_provider_action

class DesktopPendingProviderActionStore(
    private val queries: NaviampStorageQueries,
    private val nowMillis: () -> Long,
) : PendingProviderActionRepository {
    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) {
        if (replaceMatchingEntityAction) {
            queries.deletePendingProviderActionsForEntity(sourceId, actionType, entityId)
        }
        queries.insertPendingProviderAction(
            source_id = sourceId,
            action_type = actionType,
            entity_id = entityId,
            bool_value = boolValue?.let { if (it) 1L else 0L },
            long_value = longValue,
            created_at_epoch_millis = nowMillis(),
        )
    }

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> =
        queries.selectPendingProviderActions(sourceId, limit.toLong())
            .executeAsList()
            .map { row -> row.toPendingProviderAction() }

    override fun deletePendingProviderAction(id: Long) {
        queries.deletePendingProviderAction(id)
    }

    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) {
        queries.markPendingProviderActionFailed(
            last_attempt_at_epoch_millis = nowMillis(),
            last_error = errorMessage,
            id = id,
        )
    }
}

private fun Pending_provider_action.toPendingProviderAction(): PendingProviderAction =
    PendingProviderAction(
        id = id,
        sourceId = source_id,
        actionType = action_type,
        entityId = entity_id,
        boolValue = bool_value?.let { it != 0L },
        longValue = long_value,
        createdAtEpochMillis = created_at_epoch_millis,
        lastAttemptAtEpochMillis = last_attempt_at_epoch_millis,
        attemptCount = attempt_count,
        lastError = last_error,
    )
