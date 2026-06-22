package app.naviamp.android

import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.stableMediaSourceId
import app.naviamp.storage.Media_source
import app.naviamp.storage.NaviampStorageQueries
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AndroidMediaSourceStore(
    private val queries: NaviampStorageQueries,
    private val nowMillis: () -> Long,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MediaSourceRepository,
    ProviderMediaSourceRepository {
    override fun latestMediaSource(): SavedMediaSource? =
        queries.selectLatestMediaSource().executeAsOneOrNull()?.toSavedMediaSource()

    override fun mediaSources(): List<SavedMediaSource> =
        queries.selectMediaSources().executeAsList().map { it.toSavedMediaSource() }

    override fun mediaSource(sourceId: String): SavedMediaSource? =
        queries.selectMediaSourceById(sourceId).executeAsOneOrNull()?.toSavedMediaSource()

    override fun deleteMediaSource(sourceId: String) {
        queries.deleteMediaSource(sourceId)
    }

    override fun upsertProviderMediaSource(
        connection: ProviderMediaSourceConnection,
        cacheNamespace: String,
        providerId: String,
    ): MediaSourceIdentity {
        val now = nowMillis()
        val existing = queries.selectMediaSourceByCacheNamespace(cacheNamespace).executeAsOneOrNull()
        val id = existing?.id ?: stableMediaSourceId(cacheNamespace)
        val displayName = connection.displayName
        queries.upsertMediaSource(
            id = id,
            provider_id = providerId,
            cache_namespace = cacheNamespace,
            display_name = displayName,
            base_url = connection.baseUrl,
            username = connection.username,
            token = connection.token,
            salt = connection.salt,
            native_token = connection.nativeToken,
            insecure_skip_tls_verification = if (connection.tlsSettings.insecureSkipTlsVerification) 1 else 0,
            custom_certificate_path = connection.tlsSettings.customCertificatePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_path = connection.tlsSettings.clientCertificateKeyStorePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_password = connection.tlsSettings.clientCertificateKeyStorePassword,
            secondary_urls_json = encodeSecondaryUrls(connection.secondaryUrls),
            custom_headers_json = encodeCustomHeaders(connection.customHeaders),
            created_at_epoch_millis = existing?.created_at_epoch_millis ?: now,
            last_connected_at_epoch_millis = now,
            last_sync_started_at_epoch_millis = existing?.last_sync_started_at_epoch_millis,
            last_sync_completed_at_epoch_millis = existing?.last_sync_completed_at_epoch_millis,
            last_library_scan_signature = existing?.last_library_scan_signature,
            last_library_scan_checked_at_epoch_millis = existing?.last_library_scan_checked_at_epoch_millis,
        )
        queries.updateMediaSource(
            id = id,
            provider_id = providerId,
            cache_namespace = cacheNamespace,
            display_name = displayName,
            base_url = connection.baseUrl,
            username = connection.username,
            token = connection.token,
            salt = connection.salt,
            native_token = connection.nativeToken,
            insecure_skip_tls_verification = if (connection.tlsSettings.insecureSkipTlsVerification) 1 else 0,
            custom_certificate_path = connection.tlsSettings.customCertificatePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_path = connection.tlsSettings.clientCertificateKeyStorePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_password = connection.tlsSettings.clientCertificateKeyStorePassword,
            secondary_urls_json = encodeSecondaryUrls(connection.secondaryUrls),
            custom_headers_json = encodeCustomHeaders(connection.customHeaders),
            last_connected_at_epoch_millis = now,
            last_sync_started_at_epoch_millis = existing?.last_sync_started_at_epoch_millis,
            last_sync_completed_at_epoch_millis = existing?.last_sync_completed_at_epoch_millis,
            last_library_scan_signature = existing?.last_library_scan_signature,
            last_library_scan_checked_at_epoch_millis = existing?.last_library_scan_checked_at_epoch_millis,
        )
        return MediaSourceIdentity(
            id = id,
            cacheNamespace = cacheNamespace,
            displayName = displayName,
        )
    }

    fun markLibrarySyncStarted(sourceId: String) {
        queries.markMediaSourceSyncStarted(nowMillis(), sourceId)
    }

    fun markLibrarySyncCompleted(sourceId: String) {
        queries.markMediaSourceSyncCompleted(nowMillis(), sourceId)
    }

    fun markLibraryScanChecked(sourceId: String, signature: String) {
        queries.markMediaSourceLibraryScanChecked(signature, nowMillis(), sourceId)
    }

    private fun encodeSecondaryUrls(urls: List<ConnectionSecondaryUrl>): String? =
        json.encodeToString(
            ListSerializer(ConnectionSecondaryUrl.serializer()),
            urls.mapNotNull { it.normalized() },
        ).takeUnless { it == "[]" }

    private fun encodeCustomHeaders(headers: List<ConnectionHeaderDefinition>): String? =
        json.encodeToString(
            ListSerializer(ConnectionHeaderDefinition.serializer()),
            headers.mapNotNull { it.normalized() },
        ).takeUnless { it == "[]" }

    private fun decodeSecondaryUrls(text: String?): List<ConnectionSecondaryUrl> =
        text?.let {
            runCatching {
                json.decodeFromString(ListSerializer(ConnectionSecondaryUrl.serializer()), it)
                    .mapNotNull { url -> url.normalized() }
            }.getOrDefault(emptyList())
        }.orEmpty()

    private fun decodeCustomHeaders(text: String?): List<ConnectionHeaderDefinition> =
        text?.let {
            runCatching {
                json.decodeFromString(ListSerializer(ConnectionHeaderDefinition.serializer()), it)
                    .mapNotNull { header -> header.normalized() }
            }.getOrDefault(emptyList())
        }.orEmpty()

    private fun Media_source.toSavedMediaSource(): SavedMediaSource =
        toSavedMediaSource(
            secondaryUrls = decodeSecondaryUrls(secondary_urls_json),
            customHeaders = decodeCustomHeaders(custom_headers_json),
        )
}

private fun Media_source.toSavedMediaSource(
    secondaryUrls: List<ConnectionSecondaryUrl>,
    customHeaders: List<ConnectionHeaderDefinition>,
): SavedMediaSource =
    SavedMediaSource(
        id = id,
        providerId = provider_id,
        cacheNamespace = cache_namespace,
        displayName = display_name.takeUnless { it == "Navidrome" } ?: base_url,
        baseUrl = base_url,
        username = username,
        token = token,
        salt = salt,
        nativeToken = native_token,
        tlsSettings = ConnectionTlsSettings(
            insecureSkipTlsVerification = insecure_skip_tls_verification != 0L,
            customCertificatePath = custom_certificate_path,
            clientCertificateKeyStorePath = client_certificate_keystore_path,
            clientCertificateKeyStorePassword = client_certificate_keystore_password,
        ),
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
        createdAtEpochMillis = created_at_epoch_millis,
        lastConnectedAtEpochMillis = last_connected_at_epoch_millis,
        lastSyncStartedAtEpochMillis = last_sync_started_at_epoch_millis,
        lastSyncCompletedAtEpochMillis = last_sync_completed_at_epoch_millis,
    )
