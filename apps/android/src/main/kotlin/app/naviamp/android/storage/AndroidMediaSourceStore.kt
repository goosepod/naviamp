package app.naviamp.android

import app.naviamp.android.security.AndroidCredentialProtector
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
import app.naviamp.storage.Media_source
import app.naviamp.storage.NaviampStorageQueries
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class AndroidMediaSourceStore(
    private val queries: NaviampStorageQueries,
    private val nowMillis: () -> Long,
    private val credentialProtector: AndroidCredentialProtector,
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
        val displayName = connection.displayName
        val serverConnectionKey = connection.serverConnectionKey(providerId)
        val libraryScopeKey = connection.libraryScopeKey()
        queries.upsertMediaSource(
            id = id,
            provider_id = providerId,
            cache_namespace = cacheNamespace,
            server_connection_key = serverConnectionKey,
            library_scope_key = libraryScopeKey,
            display_name = displayName,
            base_url = connection.baseUrl,
            username = connection.username,
            token = credentialProtector.protect(connection.token).orEmpty(),
            salt = credentialProtector.protect(connection.salt).orEmpty(),
            native_token = credentialProtector.protect(connection.nativeToken),
            insecure_skip_tls_verification = if (connection.tlsSettings.insecureSkipTlsVerification) 1 else 0,
            custom_certificate_path = connection.tlsSettings.customCertificatePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_path = connection.tlsSettings.clientCertificateKeyStorePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_password = credentialProtector.protect(
                connection.tlsSettings.clientCertificateKeyStorePassword,
            ),
            secondary_urls_json = encodeSecondaryUrls(connection.secondaryUrls),
            custom_headers_json = encodeCustomHeaders(connection.customHeaders),
            selected_music_folder_ids_json = encodeMusicFolderIds(connection.selectedMusicFolderIds),
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
            display_name = displayName,
            base_url = connection.baseUrl,
            username = connection.username,
            token = credentialProtector.protect(connection.token).orEmpty(),
            salt = credentialProtector.protect(connection.salt).orEmpty(),
            native_token = credentialProtector.protect(connection.nativeToken),
            insecure_skip_tls_verification = if (connection.tlsSettings.insecureSkipTlsVerification) 1 else 0,
            custom_certificate_path = connection.tlsSettings.customCertificatePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_path = connection.tlsSettings.clientCertificateKeyStorePath?.takeIf { it.isNotBlank() },
            client_certificate_keystore_password = credentialProtector.protect(
                connection.tlsSettings.clientCertificateKeyStorePassword,
            ),
            secondary_urls_json = encodeSecondaryUrls(connection.secondaryUrls),
            custom_headers_json = encodeCustomHeaders(connection.customHeaders),
            selected_music_folder_ids_json = encodeMusicFolderIds(connection.selectedMusicFolderIds),
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
            serverConnectionKey = serverConnectionKey,
            libraryScopeKey = libraryScopeKey,
        )
    }

    fun pruneUnusedSourceScopes(
        activeSourceIds: Set<String>,
        lastConnectedBeforeEpochMillis: Long,
        limit: Long,
    ): Int {
        val candidateIds = queries.selectPrunableMediaSources(
            lastConnectedBeforeEpochMillis,
            limit,
        ).executeAsList()
            .filterNot { sourceId -> sourceId in activeSourceIds }
        candidateIds.forEach(queries::deleteMediaSource)
        return candidateIds.size
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
        toSavedMediaSource(
            secondaryUrls = decodeSecondaryUrls(secondary_urls_json),
            customHeaders = decodeCustomHeaders(custom_headers_json),
            selectedMusicFolderIds = decodeMusicFolderIds(selected_music_folder_ids_json),
            credentialProtector = credentialProtector,
        )

    private fun migrateStoredCredentials() {
        queries.selectMediaSources().executeAsList().forEach { source ->
            val customHeaders = source.custom_headers_json
                ?.let {
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
                    header.valueIsSecret && !header.value.isNullOrEmpty() && !credentialProtector.isProtected(header.value)
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

private fun Media_source.toSavedMediaSource(
    secondaryUrls: List<ConnectionSecondaryUrl>,
    customHeaders: List<ConnectionHeaderDefinition>,
    selectedMusicFolderIds: List<String>,
    credentialProtector: AndroidCredentialProtector,
): SavedMediaSource =
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
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = selectedMusicFolderIds,
        serverConnectionKey = server_connection_key.orEmpty(),
        libraryScopeKey = library_scope_key.orEmpty(),
        createdAtEpochMillis = created_at_epoch_millis,
        lastConnectedAtEpochMillis = last_connected_at_epoch_millis,
        lastSyncStartedAtEpochMillis = last_sync_started_at_epoch_millis,
        lastSyncCompletedAtEpochMillis = last_sync_completed_at_epoch_millis,
        lastLibraryScanSignature = last_library_scan_signature,
        lastLibraryScanCheckedAtEpochMillis = last_library_scan_checked_at_epoch_millis,
    )
