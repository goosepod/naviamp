package app.naviamp.storage

import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.normalizedMusicFolderIds
import app.naviamp.domain.source.stableMediaSourceId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Protects persisted credential values without exposing a platform security API to shared storage. */
interface StorageCredentialProtector {
    fun protect(value: String?): String?

    fun reveal(value: String?): String?

    fun isProtected(value: String?): Boolean
}

/** Explicit fallback for hosts that do not yet provide secure credential storage. */
object PassthroughStorageCredentialProtector : StorageCredentialProtector {
    override fun protect(value: String?): String? = value
    override fun reveal(value: String?): String? = value
    override fun isProtected(value: String?): Boolean = true
}

/** Shared SQLDelight-backed media-source persistence and credential migration policy. */
class StorageMediaSourceStore(
    private val queries: NaviampStorageQueries,
    private val nowMillis: () -> Long,
    private val credentialProtector: StorageCredentialProtector = PassthroughStorageCredentialProtector,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MediaSourceRepository,
    ProviderMediaSourceRepository {
    init {
        migrateStoredCredentials()
    }

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
        val serverConnectionKey = connection.serverConnectionKey(providerId)
        val libraryScopeKey = connection.libraryScopeKey()
        val values = connection.toStoredValues()
        queries.upsertMediaSource(
            id = id,
            provider_id = providerId,
            cache_namespace = cacheNamespace,
            server_connection_key = serverConnectionKey,
            library_scope_key = libraryScopeKey,
            display_name = connection.displayName,
            base_url = connection.baseUrl,
            username = connection.username,
            token = values.token,
            salt = values.salt,
            native_token = values.nativeToken,
            insecure_skip_tls_verification = values.insecureSkipTlsVerification,
            custom_certificate_path = values.customCertificatePath,
            client_certificate_keystore_path = values.clientCertificateKeyStorePath,
            client_certificate_keystore_password = values.clientCertificateKeyStorePassword,
            secondary_urls_json = values.secondaryUrlsJson,
            custom_headers_json = values.customHeadersJson,
            selected_music_folder_ids_json = values.selectedMusicFolderIdsJson,
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
            server_connection_key = serverConnectionKey,
            library_scope_key = libraryScopeKey,
            display_name = connection.displayName,
            base_url = connection.baseUrl,
            username = connection.username,
            token = values.token,
            salt = values.salt,
            native_token = values.nativeToken,
            insecure_skip_tls_verification = values.insecureSkipTlsVerification,
            custom_certificate_path = values.customCertificatePath,
            client_certificate_keystore_path = values.clientCertificateKeyStorePath,
            client_certificate_keystore_password = values.clientCertificateKeyStorePassword,
            secondary_urls_json = values.secondaryUrlsJson,
            custom_headers_json = values.customHeadersJson,
            selected_music_folder_ids_json = values.selectedMusicFolderIdsJson,
            last_connected_at_epoch_millis = now,
            last_sync_started_at_epoch_millis = existing?.last_sync_started_at_epoch_millis,
            last_sync_completed_at_epoch_millis = existing?.last_sync_completed_at_epoch_millis,
            last_library_scan_signature = existing?.last_library_scan_signature,
            last_library_scan_checked_at_epoch_millis = existing?.last_library_scan_checked_at_epoch_millis,
        )
        return MediaSourceIdentity(
            id = id,
            cacheNamespace = cacheNamespace,
            displayName = connection.displayName,
            serverConnectionKey = serverConnectionKey,
            libraryScopeKey = libraryScopeKey,
        )
    }

    fun pruneUnusedSourceScopes(
        activeSourceIds: Set<String>,
        lastConnectedBeforeEpochMillis: Long,
        limit: Long,
        deleteKnownAudioCacheFile: (String) -> Boolean,
        deleteKnownDownloadFile: (String) -> Boolean,
    ): Int {
        val candidateIds = queries.selectPrunableMediaSources(
            lastConnectedBeforeEpochMillis,
            limit,
        ).executeAsList().filterNot { it in activeSourceIds }
        return candidateIds.count { sourceId ->
            val cachedAudio = queries.selectCachedAudioForSource(sourceId).executeAsList()
            val downloadedAudio = queries.selectDownloadedAudio(sourceId).executeAsList()
            val cacheFilesDeleted = cachedAudio.all { row -> deleteKnownAudioCacheFile(row.file_path) }
            val downloadFilesDeleted = downloadedAudio.all { row -> deleteKnownDownloadFile(row.file_path) }
            if (cacheFilesDeleted && downloadFilesDeleted) {
                queries.transaction {
                    cachedAudio.forEach { row ->
                        queries.deleteCachedAudio(row.source_id, row.remote_track_id, row.quality_key)
                    }
                    downloadedAudio.forEach { row ->
                        queries.deleteDownloadedAudio(row.source_id, row.remote_track_id, row.quality_key)
                    }
                    queries.deleteMediaSource(sourceId)
                }
                true
            } else {
                false
            }
        }
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

    private fun ProviderMediaSourceConnection.toStoredValues() = StoredMediaSourceValues(
        token = credentialProtector.protect(token).orEmpty(),
        salt = credentialProtector.protect(salt).orEmpty(),
        nativeToken = credentialProtector.protect(nativeToken),
        insecureSkipTlsVerification = if (tlsSettings.insecureSkipTlsVerification) 1 else 0,
        customCertificatePath = tlsSettings.customCertificatePath?.takeIf { it.isNotBlank() },
        clientCertificateKeyStorePath = tlsSettings.clientCertificateKeyStorePath?.takeIf { it.isNotBlank() },
        clientCertificateKeyStorePassword = credentialProtector.protect(
            tlsSettings.clientCertificateKeyStorePassword,
        ),
        secondaryUrlsJson = encodeSecondaryUrls(secondaryUrls),
        customHeadersJson = encodeCustomHeaders(customHeaders),
        selectedMusicFolderIdsJson = encodeMusicFolderIds(selectedMusicFolderIds),
    )

    private fun encodeSecondaryUrls(urls: List<ConnectionSecondaryUrl>): String? =
        json.encodeToString(
            ListSerializer(ConnectionSecondaryUrl.serializer()),
            urls.mapNotNull { it.normalized() },
        ).takeUnless { it == "[]" }

    private fun encodeCustomHeaders(headers: List<ConnectionHeaderDefinition>): String? =
        json.encodeToString(
            ListSerializer(ConnectionHeaderDefinition.serializer()),
            headers.mapNotNull { header ->
                header.normalized()?.let { normalized ->
                    if (normalized.valueIsSecret) {
                        normalized.copy(value = credentialProtector.protect(normalized.value))
                    } else {
                        normalized
                    }
                }
            },
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
                    .mapNotNull { header ->
                        header.normalized()?.let { normalized ->
                            if (normalized.valueIsSecret) {
                                normalized.copy(value = credentialProtector.reveal(normalized.value))
                            } else {
                                normalized
                            }
                        }
                    }
            }.getOrDefault(emptyList())
        }.orEmpty()

    private fun encodeMusicFolderIds(ids: List<String>): String? =
        json.encodeToString(
            ListSerializer(String.serializer()),
            normalizedMusicFolderIds(ids),
        ).takeUnless { it == "[]" }

    private fun decodeMusicFolderIds(text: String?): List<String> =
        text?.let {
            runCatching {
                normalizedMusicFolderIds(json.decodeFromString(ListSerializer(String.serializer()), it))
            }.getOrDefault(emptyList())
        }.orEmpty()

    private fun Media_source.toSavedMediaSource(): SavedMediaSource =
        SavedMediaSource(
            id = id,
            providerId = provider_id,
            cacheNamespace = cache_namespace,
            displayName = display_name.takeUnless { it == "Navidrome" } ?: base_url,
            baseUrl = base_url,
            username = username,
            token = credentialProtector.reveal(token).orEmpty(),
            salt = credentialProtector.reveal(salt).orEmpty(),
            nativeToken = credentialProtector.reveal(native_token),
            tlsSettings = ConnectionTlsSettings(
                insecureSkipTlsVerification = insecure_skip_tls_verification != 0L,
                customCertificatePath = custom_certificate_path,
                clientCertificateKeyStorePath = client_certificate_keystore_path,
                clientCertificateKeyStorePassword = credentialProtector.reveal(client_certificate_keystore_password),
            ),
            secondaryUrls = decodeSecondaryUrls(secondary_urls_json),
            customHeaders = decodeCustomHeaders(custom_headers_json),
            selectedMusicFolderIds = decodeMusicFolderIds(selected_music_folder_ids_json),
            serverConnectionKey = server_connection_key.orEmpty(),
            libraryScopeKey = library_scope_key.orEmpty(),
            createdAtEpochMillis = created_at_epoch_millis,
            lastConnectedAtEpochMillis = last_connected_at_epoch_millis,
            lastSyncStartedAtEpochMillis = last_sync_started_at_epoch_millis,
            lastSyncCompletedAtEpochMillis = last_sync_completed_at_epoch_millis,
            lastLibraryScanSignature = last_library_scan_signature,
            lastLibraryScanCheckedAtEpochMillis = last_library_scan_checked_at_epoch_millis,
        )

    private fun migrateStoredCredentials() {
        if (credentialProtector === PassthroughStorageCredentialProtector) return
        queries.selectMediaSources().executeAsList().forEach { source ->
            val customHeaders = source.custom_headers_json?.let {
                runCatching {
                    json.decodeFromString(ListSerializer(ConnectionHeaderDefinition.serializer()), it)
                }.getOrDefault(emptyList())
            }.orEmpty()
            val needsMigration = listOf(
                source.token,
                source.salt,
                source.native_token,
                source.client_certificate_keystore_password,
            ).any { value -> !value.isNullOrEmpty() && !credentialProtector.isProtected(value) } ||
                customHeaders.any { header ->
                    header.valueIsSecret && !header.value.isNullOrEmpty() &&
                        !credentialProtector.isProtected(header.value)
                }
            if (!needsMigration) return@forEach
            queries.updateMediaSource(
                provider_id = source.provider_id,
                cache_namespace = source.cache_namespace,
                server_connection_key = source.server_connection_key,
                library_scope_key = source.library_scope_key,
                display_name = source.display_name,
                base_url = source.base_url,
                username = source.username,
                token = credentialProtector.protect(source.token).orEmpty(),
                salt = credentialProtector.protect(source.salt).orEmpty(),
                native_token = credentialProtector.protect(source.native_token),
                insecure_skip_tls_verification = source.insecure_skip_tls_verification,
                custom_certificate_path = source.custom_certificate_path,
                client_certificate_keystore_path = source.client_certificate_keystore_path,
                client_certificate_keystore_password = credentialProtector.protect(
                    source.client_certificate_keystore_password,
                ),
                secondary_urls_json = source.secondary_urls_json,
                custom_headers_json = encodeCustomHeaders(customHeaders),
                selected_music_folder_ids_json = source.selected_music_folder_ids_json,
                last_connected_at_epoch_millis = source.last_connected_at_epoch_millis,
                last_sync_started_at_epoch_millis = source.last_sync_started_at_epoch_millis,
                last_sync_completed_at_epoch_millis = source.last_sync_completed_at_epoch_millis,
                last_library_scan_signature = source.last_library_scan_signature,
                last_library_scan_checked_at_epoch_millis = source.last_library_scan_checked_at_epoch_millis,
                id = source.id,
            )
        }
    }
}

private data class StoredMediaSourceValues(
    val token: String,
    val salt: String,
    val nativeToken: String?,
    val insecureSkipTlsVerification: Long,
    val customCertificatePath: String?,
    val clientCertificateKeyStorePath: String?,
    val clientCertificateKeyStorePassword: String?,
    val secondaryUrlsJson: String?,
    val customHeadersJson: String?,
    val selectedMusicFolderIdsJson: String?,
)
