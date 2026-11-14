package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.provider.*
import kotlin.test.*

class StorageDiscoveryAcquisitionStoreTest {
    private val account = "aurral:https://music.test:listener"
    private fun source(db: NaviampStorageDatabase, user: String = "listener") = StorageMediaSourceStore(db.naviampStorageQueries, nowMillis = { 42L })
        .upsertProviderMediaSource(ProviderMediaSourceConnection("Music", "https://music.test", user, "", ""), "cache:$user", "aurral").id
    private fun record() = DiscoveryAcquisitionRecord("artist:album:release", "artist", "Artist", "release", true,
        releaseTitle = "Album", operation = ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.WaitingForRelease,
            ProviderDiscoveryRequestIdentity(DiscoveryRequestGroupId("group"), listOf(DiscoveryJobId("job")),
                DiscoveryCanonicalAlbumId("71"), DiscoveryManagedAlbumId(DiscoveryLibraryManager.Aurral, "71")), 201,
            ProviderDiscoveryWaitingForRelease(DiscoveryLibraryManager.Aurral, "2027-03")),
        phase = DiscoveryAcquisitionPhase.WaitingForRelease, createdAtMillis = 42L)

    @Test fun restartPreservesTypedIdentityWaitingDateAndNoReplayIntent() {
        val directory = java.nio.file.Files.createTempDirectory("naviamp-acquisition").toFile()
        val url = "jdbc:sqlite:${directory.resolve("store.db").absolutePath}"
        try {
            val scope = JdbcSqliteDriver(url).use { driver ->
                NaviampStorageDatabase.Schema.create(driver)
                val db = NaviampStorageDatabase(driver)
                DiscoveryAcquisitionScope(source(db), account).also { StorageDiscoveryAcquisitionStore(db.naviampStorageQueries).save(it, record()) }
            }
            JdbcSqliteDriver(url).use { driver ->
                val store = StorageDiscoveryAcquisitionStore(NaviampStorageDatabase(driver).naviampStorageQueries)
                assertEquals(record(), store.record(scope, record().id))
                assertEquals(listOf(record()), store.records(scope))
                assertTrue(store.records(scope.copy(accountKey = "other-user")).isEmpty())
            }
        } finally { directory.deleteRecursively() }
    }
    @Test fun scopesCacheClearAndSourceDeletionPreserveTheRightRecords() = database { driver, db ->
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        val first = DiscoveryAcquisitionScope(source(db), account)
        val second = DiscoveryAcquisitionScope(source(db, "second"), account)
        val store = StorageDiscoveryAcquisitionStore(db.naviampStorageQueries)
        store.save(first, record()); store.save(second, record())
        db.naviampStorageQueries.clearResponses(); db.naviampStorageQueries.clearLibraryForSource(first.sourceId)
        assertEquals(listOf(record()), store.records(first))
        assertTrue(store.records(first.copy(accountKey = "other")).isEmpty())
        db.naviampStorageQueries.deleteMediaSource(first.sourceId)
        assertTrue(store.records(first).isEmpty())
        assertEquals(listOf(record()), store.records(second))
    }
    @Test fun corruptIntentIsHiddenFromListButNeverTreatedAsAnAbsentSubmission() = database { _, db ->
        val scope = DiscoveryAcquisitionScope(source(db), account)
        db.naviampStorageQueries.upsertDiscoveryAcquisition(scope.sourceId, scope.accountKey, record().id, "broken", 42L)
        val store = StorageDiscoveryAcquisitionStore(db.naviampStorageQueries)
        assertTrue(store.records(scope).isEmpty())
        assertFails { store.record(scope, record().id) }
    }
    @Test fun oldIntentsRemainProtectedAfterTheyLeaveTheRecentList() = database { _, db ->
        val scope = DiscoveryAcquisitionScope(source(db), account)
        val store = StorageDiscoveryAcquisitionStore(db.naviampStorageQueries)
        val original = record().copy(createdAtMillis = 0L)
        store.save(scope, original)
        repeat(201) { store.save(scope, original.copy(id = "new-$it", createdAtMillis = it + 1L)) }
        assertEquals(200, store.records(scope).size)
        assertFalse(store.records(scope).any { it.id == original.id })
        assertEquals(original, store.record(scope, original.id))
    }
    @Test fun migrationAfterAcceptedBaselineAddsTheStoreWithoutChangingSources() = database { driver, db ->
        val source = source(db)
        driver.execute(null, "DROP TABLE discovery_acquisition", 0)
        driver.execute(null, "ALTER TABLE media_source DROP COLUMN aurral_companion", 0)
        NaviampStorageDatabase.Schema.migrate(driver, 29L, NaviampStorageDatabase.Schema.version)
        val preserved = assertNotNull(db.naviampStorageQueries.selectMediaSourceById(source).executeAsOneOrNull())
        assertNull(preserved.aurral_companion)
        val scope = DiscoveryAcquisitionScope(source, account)
        val store = StorageDiscoveryAcquisitionStore(db.naviampStorageQueries)
        store.save(scope, record()); assertEquals(record(), store.record(scope, record().id))
    }
    private fun database(block: (JdbcSqliteDriver, NaviampStorageDatabase) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            NaviampStorageDatabase.Schema.create(driver)
            block(driver, NaviampStorageDatabase(driver))
        }
    }
}
