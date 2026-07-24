package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.settings.PlaybackSessionSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class StorageCoreRepositoryCatalogTest {
    @Test
    fun assemblesPortableRepositoriesAndDelegatesOnlyNativeFileCleanup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NaviampStorageDatabase.Schema.create(driver)
        var cacheFileClears = 0
        var downloadFileClears = 0
        try {
            val catalog = StorageCoreRepositoryCatalog(
                database = NaviampStorageDatabase(driver),
                credentialProtector = PassthroughStorageCredentialProtector,
                nowEpochMillis = { 42L },
                databaseLabel = "test.db",
                databaseBytes = { 123L },
                clearAudioCacheFiles = { cacheFileClears += 1 },
                clearDownloadFiles = { downloadFileClears += 1 },
            )
            val source = catalog.mediaSources.upsertProviderMediaSource(
                connection = ProviderMediaSourceConnection(
                    displayName = "Server",
                    baseUrl = "https://music.example.test",
                    username = "listener",
                    token = "token",
                    salt = "salt",
                ),
                cacheNamespace = "cache",
                providerId = "navidrome",
            )
            catalog.playbackSessions.savePlaybackSession(PlaybackSessionSettings(), source.id)

            assertEquals(1L, catalog.maintenance.stats().mediaSourceCount)
            assertEquals(1L, catalog.maintenance.stats().playbackSessionCount)
            assertEquals(123L, catalog.maintenance.stats().databaseBytes)

            catalog.maintenance.clearAll()

            assertEquals(1, cacheFileClears)
            assertEquals(1, downloadFileClears)
            assertEquals(0L, catalog.maintenance.stats().mediaSourceCount)
        } finally {
            driver.close()
        }
    }
}
