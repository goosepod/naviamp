package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageKeepDownloadedStoreTest {
    @Test
    fun persistsPolicyMembershipAndManagedOwnership() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val store = StorageKeepDownloadedStore(
                NaviampStorageDatabase(driver).naviampStorageQueries,
                nowEpochMillis = { 42L },
            )
            val policy = KeepDownloadedCollectionPolicy(
                sourceId = "source",
                kind = KeepDownloadedCollectionKind.Playlist,
                collectionId = "playlist",
                name = "Playlist",
            )

            store.replaceKeepDownloadedTrackIds(policy, setOf("one", "two"))
            store.markManagedKeepDownloadedTracks("source", setOf("one", "two"))
            store.unmarkManagedKeepDownloadedTracks("source", setOf("two"))

            assertEquals(policy, store.keepDownloadedPolicy("source", policy.kind, policy.collectionId))
            assertEquals(setOf("one", "two"), store.keepDownloadedTrackIds("source", policy.kind, policy.collectionId))
            assertEquals(setOf("one"), store.managedKeepDownloadedTrackIds("source"))

            store.deleteKeepDownloadedPolicy("source", policy.kind, policy.collectionId)
            assertNull(store.keepDownloadedPolicy("source", policy.kind, policy.collectionId))
        } finally {
            driver.close()
        }
    }
}
