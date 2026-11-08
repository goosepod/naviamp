package app.naviamp.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.desktop.settings.DesktopCoreSettingsValueStore
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.provider.ProviderIdAurral
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.presentation.naviampCoreSettingsValueCatalog
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeHttpClient
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.storage.DefaultStorageDatabaseFileName
import app.naviamp.storage.StorageMediaSourceStore
import app.naviamp.storage.initializeNaviampStorageDatabase
import java.nio.file.Files
import java.nio.file.Path

/** Seeds only a fresh disposable profile; never reads an owner profile or contacts the server. */
fun main(args: Array<String>) {
    require(args.size == 1)
    val profile = Path.of(args.single()).toAbsolutePath().normalize()
    require(!Files.exists(profile)) { "Choose a new disposable profile directory" }
    fun environment(key: String) = requireNotNull(System.getenv(key)?.takeIf(String::isNotBlank)) { "Missing $key" }
    val connection = NavidromeConnection.fromPassword(
        providerId = ProviderIdAurral,
        baseUrl = environment("NAVIAMP_AURRAL_TEST_URL"),
        username = environment("NAVIAMP_AURRAL_TEST_USER"),
        password = environment("NAVIAMP_AURRAL_TEST_PASSWORD"),
        tlsSettings = NavidromeTlsSettings(insecureSkipTlsVerification =
            System.getenv("NAVIAMP_AURRAL_TEST_INSECURE").equals("true", ignoreCase = true)),
    )
    val directory = profile.resolve("Roaming").resolve("Naviamp Development")
    val noNetwork = object : NavidromeHttpClient {
        override suspend fun get(url: String): String = error("Profile preparation cannot contact the server")
    }
    Files.createDirectories(directory)
    JdbcSqliteDriver("jdbc:sqlite:${directory.resolve(DefaultStorageDatabaseFileName)}").use { driver ->
        val queries = initializeNaviampStorageDatabase(driver).naviampStorageQueries
        // The application applies its existing protected-storage migration on first launch.
        StorageMediaSourceStore(queries, System::currentTimeMillis).upsertProviderMediaSource(
            ProviderMediaSourceConnection(displayName = "Aurral acceptance", baseUrl = connection.baseUrl,
                username = connection.username, token = connection.token, salt = connection.salt,
                password = connection.password, tlsSettings = connection.tlsSettings),
            NavidromeProvider(connection, noNetwork).cacheNamespace, ProviderIdAurral, null)
    }
    val settings = naviampCoreSettingsValueCatalog(DesktopCoreSettingsValueStore(directory.resolve("settings.json")))
    settings.storedSettings.saveCache(CacheSettings(audioCachingEnabled = false, audioPrefetchDepth = 0, waveformsEnabled = false))
    settings.savePlayback(PlaybackSettings(volumePercent = 0))
    println("Disposable Aurral profile ready; launch with isolated APPDATA and user.home.")
}
