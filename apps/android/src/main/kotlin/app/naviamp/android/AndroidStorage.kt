package app.naviamp.android

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.naviamp.android.storage.Media_source
import app.naviamp.android.storage.NaviampAndroidDatabase
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import java.io.File
import java.security.MessageDigest

class AndroidStorage(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val driver = AndroidSqliteDriver(
        schema = NaviampAndroidDatabase.Schema,
        context = appContext,
        name = DatabaseName,
    ).also {
        it.execute(null, "PRAGMA foreign_keys=ON", 0)
    }
    private val database = NaviampAndroidDatabase(driver)
    private val queries = database.naviampAndroidQueries

    val audioCacheDirectory: File = File(appContext.cacheDir, "audio-cache")
    val downloadDirectory: File = File(appContext.filesDir, "downloads")

    fun latestNavidromeConnection(): NavidromeConnection? =
        queries.selectLatestMediaSource()
            .executeAsOneOrNull()
            ?.toNavidromeConnection()

    fun upsertNavidromeSource(connection: NavidromeConnection, cacheNamespace: String, providerId: String): AndroidMediaSource {
        val now = System.currentTimeMillis()
        val existing = queries.selectMediaSourceByCacheNamespace(cacheNamespace).executeAsOneOrNull()
        val id = existing?.id ?: stableSourceId(cacheNamespace)
        val displayName = connection.resolvedDisplayName()
        queries.upsertMediaSource(
            id = id,
            provider_id = providerId,
            cache_namespace = cacheNamespace,
            display_name = displayName,
            base_url = connection.baseUrl,
            username = connection.username,
            token = connection.token,
            salt = connection.salt,
            insecure_skip_tls_verification = if (connection.tlsSettings.insecureSkipTlsVerification) 1 else 0,
            custom_certificate_path = connection.tlsSettings.customCertificatePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_path = connection.tlsSettings.clientCertificateKeyStorePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_password = connection.tlsSettings.clientCertificateKeyStorePassword,
            created_at_epoch_millis = existing?.created_at_epoch_millis ?: now,
            last_connected_at_epoch_millis = now,
            last_sync_started_at_epoch_millis = existing?.last_sync_started_at_epoch_millis,
            last_sync_completed_at_epoch_millis = existing?.last_sync_completed_at_epoch_millis,
        )
        return AndroidMediaSource(
            id = id,
            cacheNamespace = cacheNamespace,
            displayName = displayName,
        )
    }

    fun stats(): AndroidStorageStats =
        AndroidStorageStats(
            databaseName = DatabaseName,
            mediaSourceCount = queries.mediaSourceCount().executeAsOne(),
            imageBytes = queries.imageCacheSize().executeAsOne(),
            responseCount = queries.responseCacheCount().executeAsOne(),
            audioBytes = queries.audioCacheSize().executeAsOne(),
            downloadBytes = queries.downloadedAudioSize().executeAsOne(),
            audioWaveformBytes = queries.audioWaveformCacheSize().executeAsOne(),
            lyricsBytes = queries.lyricsCacheSize().executeAsOne() + queries.lrclibLyricsCacheSize().executeAsOne(),
            audioCacheDirectory = audioCacheDirectory.absolutePath,
            downloadDirectory = downloadDirectory.absolutePath,
        )
}

data class AndroidMediaSource(
    val id: String,
    val cacheNamespace: String,
    val displayName: String,
)

data class AndroidStorageStats(
    val databaseName: String,
    val mediaSourceCount: Long,
    val imageBytes: Long,
    val responseCount: Long,
    val audioBytes: Long,
    val downloadBytes: Long,
    val audioWaveformBytes: Long,
    val lyricsBytes: Long,
    val audioCacheDirectory: String,
    val downloadDirectory: String,
)

private fun Media_source.toNavidromeConnection(): NavidromeConnection =
    NavidromeConnection(
        baseUrl = base_url,
        username = username,
        token = token,
        salt = salt,
        displayName = display_name.takeUnless { it == "Navidrome" } ?: base_url,
        tlsSettings = NavidromeTlsSettings(
            insecureSkipTlsVerification = insecure_skip_tls_verification != 0L,
            customCertificatePath = custom_certificate_path,
            clientCertificateKeyStorePath = client_certificate_keystore_path,
            clientCertificateKeyStorePassword = client_certificate_keystore_password,
        ),
    )

private fun NavidromeConnection.resolvedDisplayName(): String =
    displayName?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedBaseUrl

private fun stableSourceId(cacheNamespace: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(cacheNamespace.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "source_${digest.take(24)}"
}

private const val DatabaseName = "naviamp-android.db"
