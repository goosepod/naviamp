package app.naviamp.desktop.acceptance

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.desktop.settings.DesktopCoreSettingsValueStore
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.presentation.naviampCoreSettingsValueCatalog
import app.naviamp.provider.navidrome.KtorNavidromeHttpClient
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.provider.navidrome.createDefaultNavidromeKtorClient
import app.naviamp.storage.DefaultStorageDatabaseFileName
import app.naviamp.storage.StorageMediaSourceStore
import app.naviamp.storage.initializeNaviampStorageDatabase
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** Opt-in visible-app setup: a new profile and disposable loopback credentials only. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Supply a new profile directory and the owned Navidrome loopback URL" }
    val profile = Path.of(args[0]).toAbsolutePath().normalize()
    val endpoint = URI(args[1])
    require(endpoint.scheme == "http" && endpoint.host == "127.0.0.1" && endpoint.port in 1..65535)
    require(endpoint.userInfo == null && endpoint.query == null && endpoint.fragment == null)
    require(!Files.exists(profile)) { "The acceptance profile must not exist" }
    val tls = NavidromeTlsSettings()
    val connection = NavidromeConnection.fromPassword(
        baseUrl = endpoint.toString(), username = "fixture", password = "fixture", salt = "radio-acceptance",
    )
    val client = createDefaultNavidromeKtorClient(tls)
    val namespace = try {
        NavidromeProvider(connection, KtorNavidromeHttpClient(client)).cacheNamespace
    } finally { client.close() }
    for (name in listOf("Naviamp", "Naviamp Development")) {
        val directory = profile.resolve("Roaming").resolve(name)
        Files.createDirectories(directory)
        JdbcSqliteDriver("jdbc:sqlite:${directory.resolve(DefaultStorageDatabaseFileName)}").use { driver ->
            val queries = initializeNaviampStorageDatabase(driver).naviampStorageQueries
            // Disposable fixture values only. The app performs its normal protected-storage
            // migration on first open; no Windows credential or existing profile is read.
            StorageMediaSourceStore(queries, System::currentTimeMillis).upsertProviderMediaSource(
                ProviderMediaSourceConnection(
                    displayName = "Navidrome Radio acceptance", baseUrl = endpoint.toString(),
                    username = "fixture", password = "fixture", token = connection.token, salt = connection.salt,
                ), namespace, "navidrome", null,
            )
        }
        val settings = naviampCoreSettingsValueCatalog(DesktopCoreSettingsValueStore(directory.resolve("settings.json")))
        settings.storedSettings.saveCache(CacheSettings(audioCachingEnabled = false, audioPrefetchDepth = 0, waveformsEnabled = false))
        settings.savePlayback(PlaybackSettings(volumePercent = 0))
    }
    Files.writeString(profile.resolve("ready.txt"), "Owned Radio profile ready\nAPPDATA=${profile.resolve("Roaming")}\n")
}
