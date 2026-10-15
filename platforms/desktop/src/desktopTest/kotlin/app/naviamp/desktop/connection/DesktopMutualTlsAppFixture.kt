package app.naviamp.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.desktop.settings.DesktopCoreSettingsValueStore
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.settings.AudioOutputDeviceMode
import app.naviamp.domain.settings.AudioOutputDevicePreference
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.presentation.naviampCoreSettingsValueCatalog
import app.naviamp.provider.navidrome.KtorNavidromeHttpClient
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.provider.navidrome.createDefaultNavidromeKtorClient
import app.naviamp.storage.DefaultStorageDatabaseFileName
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageMediaSourceStore
import app.naviamp.storage.StoragePlaybackSessionStore
import app.naviamp.testkit.NaviampMutualTlsFixture
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Optional visible-app acceptance fixture. Uses only a newly created, explicitly selected profile. */
fun main(args: Array<String>) = runBlocking {
    require(args.size == 1)
    val profile = Path.of(args.single()).toAbsolutePath().normalize()
    require(!Files.exists(profile)) { "Choose a new disposable profile directory" }
    Files.createDirectories(profile)
    NaviampMutualTlsFixture(audioSeconds = 180).use { fixture ->
        val server = fixture.startServer()
        val tls = NavidromeTlsSettings(customCertificatePath = fixture.serverCa.toString(),
            clientCertificateKeyStorePath = fixture.clientKeyStore.toString(), clientCertificateKeyStorePassword = fixture.password)
        val connection = NavidromeConnection.fromPassword(baseUrl = server.baseUrl, username = "fixture",
            password = "fixture", salt = "fixture-salt", tlsSettings = tls)
        val client = createDefaultNavidromeKtorClient(tls)
        val provider = NavidromeProvider(connection, KtorNavidromeHttpClient(client))
        val track = try { requireNotNull(provider.track(TrackId("track"))) } finally { client.close() }
        for (name in listOf("Naviamp", "Naviamp Development")) {
            val directory = profile.resolve("Roaming").resolve(name)
            Files.createDirectories(directory)
            JdbcSqliteDriver("jdbc:sqlite:${directory.resolve(DefaultStorageDatabaseFileName)}").use { driver ->
                NaviampStorageDatabase.Schema.create(driver)
                val queries = NaviampStorageDatabase(driver).naviampStorageQueries
                // Only disposable fixture credentials. The real app reads the legacy plain value
                // and applies its normal protected-storage migration; no OS secret is extracted.
                val source = StorageMediaSourceStore(queries, System::currentTimeMillis).upsertProviderMediaSource(
                    ProviderMediaSourceConnection(displayName = "Local mTLS acceptance", baseUrl = server.baseUrl,
                        username = "fixture", token = connection.token, salt = connection.salt, tlsSettings = tls),
                    provider.cacheNamespace, "navidrome", null)
                StoragePlaybackSessionStore(queries, System::currentTimeMillis).savePlaybackSession(
                    PlaybackSessionSettings.fromTracks(listOf(track), 0)?.copy(nowPlayingOpen = true), source.id)
            }
            val settings = naviampCoreSettingsValueCatalog(DesktopCoreSettingsValueStore(directory.resolve("settings.json")))
            settings.storedSettings.saveCache(CacheSettings(audioCachingEnabled = false, audioPrefetchDepth = 0, waveformsEnabled = false))
            settings.savePlayback(PlaybackSettings(volumePercent = 0,
                outputDevice = AudioOutputDevicePreference(AudioOutputDeviceMode.Pinned, "0", "No sound")))
        }
        Files.writeString(profile.resolve("ready.txt"), "Loopback mTLS fixture ready: ${server.baseUrl}\nAPPDATA=${profile.resolve("Roaming")}\n")
        val stop = profile.resolve("stop")
        try {
            repeat(1_200) {
                if (Files.exists(stop)) return@runBlocking
                Files.writeString(profile.resolve("requests.txt"),
                    "paths=${server.requestedPaths.toList()}\nranges=${server.requestedRanges.toList()}\nidentities=${server.peerSubjects.toList().distinct()}\n")
                delay(500)
            }
        } finally {
            Files.writeString(profile.resolve("completed.txt"), "Disposable mTLS fixture stopped\n")
        }
    }
}
