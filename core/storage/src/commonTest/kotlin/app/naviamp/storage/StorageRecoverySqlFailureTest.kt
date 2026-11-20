package app.naviamp.storage

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.*
import kotlin.test.*

class StorageRecoverySqlFailureTest {
    @Test fun sqlCommitFailurePreservesNewAuthorityAndRequiresReloadBeforeAnyFurtherMutation() {
        val fixture = RecoveryFixture()
        val state = fixture.open()
        val queries = NaviampStorageDatabase(FailingTransactionDriver()).naviampStorageQueries
        assertEquals(StorageRecoveryFailure.Conflict, assertFailsWith<StorageRecoveryException> {
            state.transaction(queries) { state.write("setting", "committed document") }
        }.reason)
        val document = fixture.document.value
        assertEquals("committed document", fixture.open().read("setting"))
        assertFailsWith<StorageRecoveryException> { state.write("setting", "stale overwrite") }
        assertFailsWith<StorageRecoveryException> { state.read("setting") }
        assertEquals(document, fixture.document.value)
    }

    @Test fun sqlRollbackFailureAlsoRequiresReloadButNormalPreflightFailureDoesNotWrite() {
        val fixture = RecoveryFixture()
        val state = fixture.open()
        val queries = NaviampStorageDatabase(FailingTransactionDriver()).naviampStorageQueries
        assertEquals(StorageRecoveryFailure.Conflict, assertFailsWith<StorageRecoveryException> {
            state.transaction(queries) { state.write("setting", "x".repeat(200_000)) }
        }.reason)
        assertEquals(0, fixture.document.writes)
        assertFailsWith<StorageRecoveryException> { state.write("setting", "stale") }
        assertNull(fixture.open().read("setting"))
    }
}

/** Only the driver's completion effect fails; the real SQLDelight transaction owner still executes. */
private class FailingTransactionDriver : SqlDriver {
    private var current: Transacter.Transaction? = null
    override fun currentTransaction() = current
    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val parent = current
        val transaction = object : Transacter.Transaction() {
            override val enclosingTransaction = parent
            override fun endTransaction(successful: Boolean): QueryResult<Unit> {
                current = parent
                throw IllegalStateException("Injected SQL completion failure")
            }
        }
        current = transaction
        return QueryResult.Value(transaction)
    }
    override fun execute(identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> = error("Unexpected SQL")
    override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> = error("Unexpected query")
    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit
    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit
    override fun notifyListeners(vararg queryKeys: String) = Unit
    override fun close() = Unit
}
