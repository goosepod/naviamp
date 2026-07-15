package app.naviamp.android

import android.content.Context
import app.naviamp.android.security.AndroidCredentialProtector
import app.naviamp.android.security.AndroidKeystoreCredentialProtector
import app.naviamp.domain.playback.EqualizerBandFrequencies
import app.naviamp.domain.playback.EqualizerProfile
import app.naviamp.domain.playback.EqualizerPreset
import app.naviamp.domain.playback.EqualizerSettings
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.radio.RadioArtistSpread
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.radio.RadioFamiliarity
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormSecondaryUrl
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.DownloadedTrackPlayback
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.domain.settings.AlbumCollectionLayout
import app.naviamp.domain.settings.AlbumSortOrder
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import app.naviamp.domain.settings.LyricsSourcePreference
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.SampleRateConverter
import app.naviamp.domain.settings.SampleRateMatching
import app.naviamp.domain.settings.PreviousButtonBehavior
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.StreamQualityMode
import app.naviamp.domain.settings.StreamQualityPreference
import app.naviamp.domain.settings.StreamingCodec
import app.naviamp.domain.settings.TrackSwipeAction
import app.naviamp.domain.settings.TrackSwipeSettings
import app.naviamp.domain.settings.UpNextSelectionBehavior
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.provider.navidrome.NavidromeConnection
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class AndroidSettingsStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val credentialProtector: AndroidCredentialProtector = AndroidKeystoreCredentialProtector()
    private val preferences = appContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
    private val credentialPreferences = appContext.getSharedPreferences(
        CredentialPreferencesName,
        Context.MODE_PRIVATE,
    )

    init {
        migrateLegacyCredentials()
    }

    fun loadConnection(savedConnection: NavidromeConnection? = null): ConnectionFormState =
        ConnectionFormState(
            displayName = savedConnection?.displayName ?: preferences.getString(KeyDisplayName, "").orEmpty(),
            serverUrl = savedConnection?.baseUrl ?: preferences.getString(KeyServerUrl, "").orEmpty(),
            username = savedConnection?.username ?: preferences.getString(KeyUsername, "").orEmpty(),
            password = loadCredential(KeyPassword),
            skipTlsVerification = savedConnection?.tlsSettings?.insecureSkipTlsVerification
                ?: preferences.getBoolean(KeySkipTlsVerification, false),
            customCertificatePath = savedConnection?.tlsSettings?.customCertificatePath
                ?: preferences.getString(KeyCustomCertificatePath, "").orEmpty(),
            clientCertificatePath = savedConnection?.tlsSettings?.clientCertificateKeyStorePath
                ?: preferences.getString(KeyClientCertificatePath, "").orEmpty(),
            clientCertificatePassword = savedConnection?.tlsSettings?.clientCertificateKeyStorePassword
                ?: loadCredential(KeyClientCertificatePassword),
            secondaryUrls = savedConnection?.secondaryUrls?.map { url ->
                ConnectionFormSecondaryUrl(
                    url = url.url,
                    label = url.label.orEmpty(),
                )
            } ?: decodeList(KeySecondaryUrls, ConnectionFormSecondaryUrl.serializer()),
            customHeaders = savedConnection?.customHeaders?.map { header ->
                ConnectionFormHeader(
                    name = header.name,
                    value = header.value.orEmpty(),
                    valueIsSecret = header.valueIsSecret,
                )
            } ?: loadConnectionHeaders(),
            selectedMusicFolderIds = savedConnection?.selectedMusicFolderIds
                ?: decodeList(KeySelectedMusicFolderIds, String.serializer()),
        )

    fun saveConnection(connection: ConnectionFormState) {
        preferences.edit()
            .putString(KeyDisplayName, connection.displayName.trim())
            .putString(KeyServerUrl, connection.serverUrl.trim())
            .putString(KeyUsername, connection.username)
            .remove(KeyPassword)
            .putBoolean(KeySkipTlsVerification, connection.skipTlsVerification)
            .putString(KeyCustomCertificatePath, connection.customCertificatePath.trim())
            .putString(KeyClientCertificatePath, connection.clientCertificatePath.trim())
            .remove(KeyClientCertificatePassword)
            .putString(KeySecondaryUrls, encodeList(connection.secondaryUrls, ConnectionFormSecondaryUrl.serializer()))
            .putString(
                KeyCustomHeaders,
                encodeList(
                    connection.customHeaders.map { header ->
                        if (header.valueIsSecret) header.copy(value = "") else header
                    },
                    ConnectionFormHeader.serializer(),
                ),
            )
            .putString(KeySelectedMusicFolderIds, encodeList(connection.selectedMusicFolderIds, String.serializer()))
            .apply()
        credentialPreferences.edit()
            .putString(KeyPassword, credentialProtector.protect(connection.password))
            .putString(KeyClientCertificatePassword, credentialProtector.protect(connection.clientCertificatePassword))
            .putString(KeyCustomHeaders, encodeSecretHeaders(connection.customHeaders))
            .apply()
    }

    fun loadPlaybackSettings(): PlaybackSettings =
        PlaybackSettings(
            replayGainMode = enumPreference(KeyReplayGainMode, ReplayGainMode.Off),
            replayGainInspectorEnabled = preferences.getBoolean(KeyReplayGainInspectorEnabled, false),
            sampleRateConverter = enumPreference(KeySampleRateConverter, SampleRateConverter.Sinc16),
            sampleRateMatching = enumPreference(KeySampleRateMatching, SampleRateMatching.Disabled),
            gaplessEnabled = preferences.getBoolean(KeyGaplessEnabled, true),
            crossfadeDurationSeconds = preferences.getInt(KeyCrossfadeDurationSeconds, 0),
            equalizer = loadEqualizerSettings(),
            debugLoggingEnabled = preferences.getBoolean(KeyDebugLoggingEnabled, false),
            lrclibLyricsEnabled = preferences.getBoolean(KeyLrclibLyricsEnabled, false),
            preferSyncedLyrics = preferences.getBoolean(KeyPreferSyncedLyrics, false),
            lyricsSearchOrder = decodeList(KeyLyricsSearchOrder, LyricsSourcePreference.serializer()),
            sonicSimilarityEnabled = preferences.getBoolean(KeySonicSimilarityEnabled, false),
            sonicAutoplayEnabled = preferences.getBoolean(KeySonicAutoplayEnabled, false),
            previousButtonBehavior = enumPreference(
                KeyPreviousButtonBehavior,
                PreviousButtonBehavior.RestartThenPrevious,
            ),
            upNextSelectionBehavior = enumPreference(
                KeyUpNextSelectionBehavior,
                UpNextSelectionBehavior.MoveSelectedToCurrent,
            ),
            removePlayedTracksFromQueue = preferences.getBoolean(KeyRemovePlayedTracksFromQueue, false),
            radioTuning = RadioTuningSettings(
                familiarity = enumPreference(KeyRadioFamiliarity, RadioFamiliarity.Balanced),
                artistSpread = enumPreference(KeyRadioArtistSpread, RadioArtistSpread.Balanced),
                sameDecadeOnly = preferences.getBoolean(KeyRadioSameDecadeOnly, false),
            ),
            radioDjs = decodeList(KeyRadioDjs, RadioDjPreset.serializer()).map { it.normalized() },
            activeRadioDjId = preferences.getString(KeyActiveRadioDjId, null),
            wifiStreamingQuality = loadStreamQualityPreference(
                modeKey = KeyWifiStreamQualityMode,
                codecKey = KeyWifiStreamCodec,
                bitrateKey = KeyWifiStreamBitrate,
                defaultValue = StreamQualityPreference(),
            ),
            mobileStreamingQuality = loadStreamQualityPreference(
                modeKey = KeyMobileStreamQualityMode,
                codecKey = KeyMobileStreamCodec,
                bitrateKey = KeyMobileStreamBitrate,
                defaultValue = StreamQualityPreference(
                    mode = StreamQualityMode.Transcode,
                    codec = StreamingCodec.Opus,
                    bitrateKbps = 192,
                ),
            ),
            downloadQuality = loadStreamQualityPreference(
                modeKey = KeyDownloadQualityMode,
                codecKey = KeyDownloadCodec,
                bitrateKey = KeyDownloadBitrate,
                defaultValue = StreamQualityPreference(),
            ),
            downloadedTrackPlayback = enumPreference(
                KeyDownloadedTrackPlayback,
                DownloadedTrackPlayback.PreferDownloaded,
            ),
            allowMobileDownloads = preferences.getBoolean(KeyAllowMobileDownloads, false),
        )

    fun loadInterfaceSettings(): InterfaceSettings =
        InterfaceSettings(
            language = enumPreference(KeyInterfaceLanguage, InterfaceLanguage.System),
            checkForUpdates = preferences.getBoolean(KeyCheckForUpdates, true),
            startPlayingOnLaunch = preferences.getBoolean(KeyStartPlayingOnLaunch, false),
            albumCollectionLayout = enumPreference(KeyAlbumCollectionLayout, AlbumCollectionLayout.List),
            albumSortOrder = enumPreference(KeyAlbumSortOrder, AlbumSortOrder.ReleaseYearAscending),
            groupAlbumsByReleaseType = preferences.getBoolean(KeyGroupAlbumsByReleaseType, true),
            nowPlaying = NowPlayingDisplaySettings(
                showAlbumYear = preferences.getBoolean(KeyNowPlayingShowAlbumYear, true),
                showAudioInfo = preferences.getBoolean(KeyNowPlayingShowAudioInfo, true),
                showVolumeBar = preferences.getBoolean(KeyNowPlayingShowVolumeBar, true),
                scrollTrackTitle = preferences.getBoolean(KeyNowPlayingScrollTrackTitle, true),
                scrollArtistName = preferences.getBoolean(KeyNowPlayingScrollArtistName, false),
                scrollAlbumName = preferences.getBoolean(KeyNowPlayingScrollAlbumName, false),
            ),
            trackSwipes = TrackSwipeSettings(
                libraryRight = enumPreference(KeySwipeLibraryRight, TrackSwipeAction.PlayNext),
                libraryLeft = enumPreference(KeySwipeLibraryLeft, TrackSwipeAction.None),
                queueRight = enumPreference(KeySwipeQueueRight, TrackSwipeAction.PlayNext),
                queueLeft = enumPreference(KeySwipeQueueLeft, TrackSwipeAction.Remove),
                relatedRight = enumPreference(KeySwipeRelatedRight, TrackSwipeAction.PlayNext),
                relatedLeft = enumPreference(KeySwipeRelatedLeft, TrackSwipeAction.None),
                playlistEditRight = enumPreference(KeySwipePlaylistEditRight, TrackSwipeAction.None),
                playlistEditLeft = enumPreference(KeySwipePlaylistEditLeft, TrackSwipeAction.None),
                downloadsRight = enumPreference(KeySwipeDownloadsRight, TrackSwipeAction.Play),
                downloadsLeft = enumPreference(KeySwipeDownloadsLeft, TrackSwipeAction.Remove),
            ),
        ).normalized()

    fun saveInterfaceSettings(settings: InterfaceSettings) {
        preferences.edit()
            .putString(KeyInterfaceLanguage, settings.normalized().language.name)
            .putBoolean(KeyCheckForUpdates, settings.normalized().checkForUpdates)
            .putBoolean(KeyStartPlayingOnLaunch, settings.normalized().startPlayingOnLaunch)
            .putString(KeyAlbumCollectionLayout, settings.normalized().albumCollectionLayout.name)
            .putString(KeyAlbumSortOrder, settings.normalized().albumSortOrder.name)
            .putBoolean(KeyGroupAlbumsByReleaseType, settings.normalized().groupAlbumsByReleaseType)
            .putBoolean(KeyNowPlayingShowAlbumYear, settings.normalized().nowPlaying.showAlbumYear)
            .putBoolean(KeyNowPlayingShowAudioInfo, settings.normalized().nowPlaying.showAudioInfo)
            .putBoolean(KeyNowPlayingShowVolumeBar, settings.normalized().nowPlaying.showVolumeBar)
            .putBoolean(KeyNowPlayingScrollTrackTitle, settings.normalized().nowPlaying.scrollTrackTitle)
            .putBoolean(KeyNowPlayingScrollArtistName, settings.normalized().nowPlaying.scrollArtistName)
            .putBoolean(KeyNowPlayingScrollAlbumName, settings.normalized().nowPlaying.scrollAlbumName)
            .putString(KeySwipeLibraryRight, settings.normalized().trackSwipes.libraryRight.name)
            .putString(KeySwipeLibraryLeft, settings.normalized().trackSwipes.libraryLeft.name)
            .putString(KeySwipeQueueRight, settings.normalized().trackSwipes.queueRight.name)
            .putString(KeySwipeQueueLeft, settings.normalized().trackSwipes.queueLeft.name)
            .putString(KeySwipeRelatedRight, settings.normalized().trackSwipes.relatedRight.name)
            .putString(KeySwipeRelatedLeft, settings.normalized().trackSwipes.relatedLeft.name)
            .putString(KeySwipePlaylistEditRight, settings.normalized().trackSwipes.playlistEditRight.name)
            .putString(KeySwipePlaylistEditLeft, settings.normalized().trackSwipes.playlistEditLeft.name)
            .putString(KeySwipeDownloadsRight, settings.normalized().trackSwipes.downloadsRight.name)
            .putString(KeySwipeDownloadsLeft, settings.normalized().trackSwipes.downloadsLeft.name)
            .apply()
    }

    fun savePlaybackSettings(settings: PlaybackSettings) {
        preferences.edit()
            .putString(KeyReplayGainMode, settings.replayGainMode.name)
            .putString(KeySampleRateConverter, settings.sampleRateConverter.name)
            .putString(KeySampleRateMatching, settings.sampleRateMatching.name)
            .putBoolean(KeyReplayGainInspectorEnabled, settings.replayGainInspectorEnabled)
            .putBoolean(KeyGaplessEnabled, settings.gaplessEnabled)
            .putInt(KeyCrossfadeDurationSeconds, settings.crossfadeDurationSeconds)
            .putEqualizerSettings(settings.equalizer)
            .putBoolean(KeyDebugLoggingEnabled, settings.debugLoggingEnabled)
            .putBoolean(KeyLrclibLyricsEnabled, settings.lrclibLyricsEnabled)
            .putBoolean(KeyPreferSyncedLyrics, settings.preferSyncedLyrics)
            .putString(
                KeyLyricsSearchOrder,
                JsonSettings.encodeToString(
                    ListSerializer(LyricsSourcePreference.serializer()),
                    settings.lyricsSearchOrder,
                ),
            )
            .putBoolean(KeySonicSimilarityEnabled, settings.sonicSimilarityEnabled)
            .putBoolean(KeySonicAutoplayEnabled, settings.sonicAutoplayEnabled)
            .putString(KeyPreviousButtonBehavior, settings.previousButtonBehavior.name)
            .putString(KeyUpNextSelectionBehavior, settings.upNextSelectionBehavior.name)
            .putBoolean(KeyRemovePlayedTracksFromQueue, settings.removePlayedTracksFromQueue)
            .putString(KeyRadioFamiliarity, settings.radioTuning.familiarity.name)
            .putString(KeyRadioArtistSpread, settings.radioTuning.artistSpread.name)
            .putBoolean(KeyRadioSameDecadeOnly, settings.radioTuning.sameDecadeOnly)
            .remove(KeyRadioDjs)
            .putString(KeyActiveRadioDjId, settings.activeRadioDjId)
            .putStreamQualityPreference(
                modeKey = KeyWifiStreamQualityMode,
                codecKey = KeyWifiStreamCodec,
                bitrateKey = KeyWifiStreamBitrate,
                preference = settings.wifiStreamingQuality,
            )
            .putStreamQualityPreference(
                modeKey = KeyMobileStreamQualityMode,
                codecKey = KeyMobileStreamCodec,
                bitrateKey = KeyMobileStreamBitrate,
                preference = settings.mobileStreamingQuality,
            )
            .putStreamQualityPreference(
                modeKey = KeyDownloadQualityMode,
                codecKey = KeyDownloadCodec,
                bitrateKey = KeyDownloadBitrate,
                preference = settings.downloadQuality,
            )
            .putString(KeyDownloadedTrackPlayback, settings.downloadedTrackPlayback.name)
            .putBoolean(KeyAllowMobileDownloads, settings.allowMobileDownloads)
            .apply()
    }

    fun loadCacheSettings(): CacheSettings =
        CacheSettings(
            audioCachingEnabled = preferences.getBoolean(KeyAudioCachingEnabled, true),
            offlineModeEnabled = preferences.getBoolean(KeyOfflineModeEnabled, false),
            audioPrefetchDepth = preferences.getInt(KeyAudioPrefetchDepth, CacheSettings().audioPrefetchDepth),
            waveformsEnabled = preferences.getBoolean(KeyWaveformsEnabled, CacheSettings().waveformsEnabled),
            waveformBucketCount = preferences.getInt(KeyWaveformBucketCount, CacheSettings().waveformBucketCount),
            maxAudioCacheBytes = preferences.getLong(KeyMaxAudioCacheBytes, CacheSettings().maxAudioCacheBytes),
            maxDownloadBytes = preferences.getLong(KeyMaxDownloadBytes, CacheSettings().maxDownloadBytes),
            customAudioCacheDirectory = preferences.getString(KeyCustomAudioCacheDirectory, null),
            customDownloadDirectory = preferences.getString(KeyCustomDownloadDirectory, null),
        ).normalized()

    fun saveCacheSettings(settings: CacheSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putBoolean(KeyAudioCachingEnabled, normalized.audioCachingEnabled)
            .putBoolean(KeyOfflineModeEnabled, normalized.offlineModeEnabled)
            .putInt(KeyAudioPrefetchDepth, normalized.audioPrefetchDepth)
            .putBoolean(KeyWaveformsEnabled, normalized.waveformsEnabled)
            .putInt(KeyWaveformBucketCount, normalized.waveformBucketCount)
            .putLong(KeyMaxAudioCacheBytes, normalized.maxAudioCacheBytes)
            .putLong(KeyMaxDownloadBytes, normalized.maxDownloadBytes)
            .putString(KeyCustomAudioCacheDirectory, normalized.customAudioCacheDirectory)
            .putString(KeyCustomDownloadDirectory, normalized.customDownloadDirectory)
            .apply()
    }

    fun loadVisualizerSettings(): VisualizerSettings =
        VisualizerSettings(
            selectedVisualizer = preferences.getString(KeySelectedVisualizer, null)
                ?: VisualizerSettings().selectedVisualizer,
        )

    fun saveVisualizerSettings(settings: VisualizerSettings) {
        preferences.edit()
            .putString(KeySelectedVisualizer, settings.selectedVisualizer)
            .apply()
    }

    fun loadRecentRadioStreams(): List<RecentRadioStream> =
        decodeList(KeyRecentRadioStreams, RecentRadioStream.serializer())

    fun saveRecentRadioStreams(streams: List<RecentRadioStream>) {
        preferences.edit()
            .putString(
                KeyRecentRadioStreams,
                JsonSettings.encodeToString(ListSerializer(RecentRadioStream.serializer()), streams.take(12)),
            )
            .apply()
    }

    fun loadRecentInternetRadioStations(): List<SavedInternetRadioStation> =
        decodeList(KeyRecentInternetRadioStations, SavedInternetRadioStation.serializer())

    fun saveRecentInternetRadioStations(stations: List<SavedInternetRadioStation>) {
        preferences.edit()
            .putString(
                KeyRecentInternetRadioStations,
                JsonSettings.encodeToString(ListSerializer(SavedInternetRadioStation.serializer()), stations.take(12)),
            )
            .apply()
    }

    fun loadSettingsSync(): AndroidSettingsSyncSettings =
        AndroidSettingsSyncSettings(
            treeUri = preferences.getString(KeySettingsSyncTreeUri, null),
            autoExportEnabled = preferences.getBoolean(KeySettingsSyncAutoExportEnabled, false),
            lastLocalUpdateEpochMillis = preferences.getLong(KeySettingsSyncLastLocalUpdateEpochMillis, 0L),
            lastAppliedSyncUpdateEpochMillis = preferences.getLong(KeySettingsSyncLastAppliedUpdateEpochMillis, 0L),
            lastProviderPullEpochMillis = preferences.getLong(KeySettingsSyncLastProviderPullEpochMillis, 0L),
            lastProviderPushEpochMillis = preferences.getLong(KeySettingsSyncLastProviderPushEpochMillis, 0L),
            lastProviderError = preferences.getString(KeySettingsSyncLastProviderError, null),
            lastMirrorUpdateEpochMillis = preferences.getLong(KeySettingsSyncLastMirrorUpdateEpochMillis, 0L),
        ).normalized()

    fun saveSettingsSync(settings: AndroidSettingsSyncSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putString(KeySettingsSyncTreeUri, normalized.treeUri)
            .putBoolean(KeySettingsSyncAutoExportEnabled, normalized.autoExportEnabled)
            .putLong(KeySettingsSyncLastLocalUpdateEpochMillis, normalized.lastLocalUpdateEpochMillis)
            .putLong(KeySettingsSyncLastAppliedUpdateEpochMillis, normalized.lastAppliedSyncUpdateEpochMillis)
            .putLong(KeySettingsSyncLastProviderPullEpochMillis, normalized.lastProviderPullEpochMillis)
            .putLong(KeySettingsSyncLastProviderPushEpochMillis, normalized.lastProviderPushEpochMillis)
            .putString(KeySettingsSyncLastProviderError, normalized.lastProviderError)
            .putLong(KeySettingsSyncLastMirrorUpdateEpochMillis, normalized.lastMirrorUpdateEpochMillis)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
        credentialPreferences.edit().clear().apply()
    }

    private fun migrateLegacyCredentials() {
        val legacyPassword = preferences.getString(KeyPassword, null)
        val legacyCertificatePassword = preferences.getString(KeyClientCertificatePassword, null)
        val legacyHeaders = decodeList(KeyCustomHeaders, ConnectionFormHeader.serializer())
        val secretHeaders = legacyHeaders.map { header ->
            if (header.valueIsSecret && header.value.isNotEmpty()) {
                header.copy(value = credentialProtector.protect(header.value).orEmpty())
            } else {
                ConnectionFormHeader()
            }
        }
        val credentialEditor = credentialPreferences.edit()
        if (!credentialPreferences.contains(KeyPassword) && legacyPassword != null) {
            credentialEditor.putString(KeyPassword, credentialProtector.protect(legacyPassword))
        }
        if (!credentialPreferences.contains(KeyClientCertificatePassword) && legacyCertificatePassword != null) {
            credentialEditor.putString(
                KeyClientCertificatePassword,
                credentialProtector.protect(legacyCertificatePassword),
            )
        }
        if (!credentialPreferences.contains(KeyCustomHeaders) && secretHeaders.any { it.valueIsSecret }) {
            credentialEditor.putString(
                KeyCustomHeaders,
                JsonSettings.encodeToString(ListSerializer(ConnectionFormHeader.serializer()), secretHeaders),
            )
        }
        credentialEditor.apply()
        if (legacyPassword != null || legacyCertificatePassword != null || secretHeaders.any { it.valueIsSecret }) {
            preferences.edit()
                .remove(KeyPassword)
                .remove(KeyClientCertificatePassword)
                .putString(
                    KeyCustomHeaders,
                    encodeList(
                        legacyHeaders.map { header -> if (header.valueIsSecret) header.copy(value = "") else header },
                        ConnectionFormHeader.serializer(),
                    ),
                )
                .apply()
        }
    }

    private fun loadCredential(key: String): String =
        credentialProtector.reveal(credentialPreferences.getString(key, null)).orEmpty()

    private fun loadConnectionHeaders(): List<ConnectionFormHeader> {
        val headers = decodeList(KeyCustomHeaders, ConnectionFormHeader.serializer())
        val secretValues = credentialPreferences.getString(KeyCustomHeaders, null)
            ?.let { encoded ->
                runCatching {
                    JsonSettings.decodeFromString(ListSerializer(ConnectionFormHeader.serializer()), encoded)
                }.getOrDefault(emptyList())
            }.orEmpty()
        return headers.mapIndexed { index, header ->
            val secret = secretValues.getOrNull(index)
                ?.takeIf { it.valueIsSecret && it.name == header.name }
                ?.value
                ?.let(credentialProtector::reveal)
            if (header.valueIsSecret) header.copy(value = secret.orEmpty()) else header
        }
    }

    private fun encodeSecretHeaders(headers: List<ConnectionFormHeader>): String =
        JsonSettings.encodeToString(
            ListSerializer(ConnectionFormHeader.serializer()),
            headers.map { header ->
                if (header.valueIsSecret) {
                    header.copy(value = credentialProtector.protect(header.value).orEmpty())
                } else {
                    ConnectionFormHeader()
                }
            },
        )

    private inline fun <reified T : Enum<T>> enumPreference(key: String, defaultValue: T): T =
        preferences.getString(key, null)
            ?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
            ?: defaultValue

    private fun loadStreamQualityPreference(
        modeKey: String,
        codecKey: String,
        bitrateKey: String,
        defaultValue: StreamQualityPreference,
    ): StreamQualityPreference =
        StreamQualityPreference(
            mode = enumPreference(modeKey, defaultValue.mode),
            codec = enumPreference(codecKey, defaultValue.codec),
            bitrateKbps = preferences.getInt(bitrateKey, defaultValue.bitrateKbps),
        ).normalized()

    private fun loadEqualizerSettings(): EqualizerSettings =
        EqualizerSettings(
            enabled = preferences.getBoolean(KeyEqualizerEnabled, false),
            preset = enumPreference(KeyEqualizerPreset, EqualizerPreset.Flat),
            profileId = preferences.getString(KeyEqualizerProfileId, null),
            savedProfiles = decodeList(KeyEqualizerProfiles, EqualizerProfile.serializer()),
            bandsDb = EqualizerBandFrequencies.indices.map { index ->
                preferences.getFloat("${KeyEqualizerBandPrefix}_$index", 0f)
            },
        ).normalized()

    private fun <T> decodeList(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> =
        preferences.getString(key, null)
            ?.let { json ->
                runCatching { JsonSettings.decodeFromString(ListSerializer(serializer), json) }.getOrDefault(emptyList())
            }
            ?: emptyList()

    private fun <T> encodeList(
        values: List<T>,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): String =
        JsonSettings.encodeToString(ListSerializer(serializer), values)
}

data class AndroidSettingsSyncSettings(
    val treeUri: String? = null,
    val autoExportEnabled: Boolean = false,
    val lastLocalUpdateEpochMillis: Long = 0L,
    val lastAppliedSyncUpdateEpochMillis: Long = 0L,
    val lastProviderPullEpochMillis: Long = 0L,
    val lastProviderPushEpochMillis: Long = 0L,
    val lastProviderError: String? = null,
    val lastMirrorUpdateEpochMillis: Long = 0L,
) {
    fun normalized(): AndroidSettingsSyncSettings =
        copy(
            treeUri = treeUri?.trim()?.takeIf { it.isNotEmpty() },
            autoExportEnabled = autoExportEnabled && treeUri?.trim()?.isNotEmpty() == true,
            lastLocalUpdateEpochMillis = lastLocalUpdateEpochMillis.coerceAtLeast(0L),
            lastAppliedSyncUpdateEpochMillis = lastAppliedSyncUpdateEpochMillis.coerceAtLeast(0L),
            lastProviderPullEpochMillis = lastProviderPullEpochMillis.coerceAtLeast(0L),
            lastProviderPushEpochMillis = lastProviderPushEpochMillis.coerceAtLeast(0L),
            lastProviderError = lastProviderError?.trim()?.takeIf { it.isNotEmpty() },
            lastMirrorUpdateEpochMillis = lastMirrorUpdateEpochMillis.coerceAtLeast(0L),
        )
}

private fun android.content.SharedPreferences.Editor.putStreamQualityPreference(
    modeKey: String,
    codecKey: String,
    bitrateKey: String,
    preference: StreamQualityPreference,
): android.content.SharedPreferences.Editor {
    val normalized = preference.normalized()
    return putString(modeKey, normalized.mode.name)
        .putString(codecKey, normalized.codec.name)
        .putInt(bitrateKey, normalized.bitrateKbps)
}

private fun android.content.SharedPreferences.Editor.putEqualizerSettings(
    settings: EqualizerSettings,
): android.content.SharedPreferences.Editor {
    val normalized = settings.normalized()
    putBoolean(KeyEqualizerEnabled, normalized.enabled)
    putString(KeyEqualizerPreset, normalized.preset.name)
    putString(KeyEqualizerProfileId, normalized.profileId)
    putString(
        KeyEqualizerProfiles,
        JsonSettings.encodeToString(ListSerializer(EqualizerProfile.serializer()), normalized.savedProfiles),
    )
    EqualizerBandFrequencies.indices.forEach { index ->
        putFloat("${KeyEqualizerBandPrefix}_$index", normalized.bandsDb.getOrNull(index) ?: 0f)
    }
    return this
}

private const val PreferencesName = "naviamp_android_settings"
internal const val CredentialPreferencesName = "naviamp_android_credentials"
private const val KeyDisplayName = "display_name"
private const val KeyServerUrl = "server_url"
private const val KeyUsername = "username"
private const val KeyPassword = "password"
private const val KeySkipTlsVerification = "skip_tls_verification"
private const val KeyCustomCertificatePath = "custom_certificate_path"
private const val KeyClientCertificatePath = "client_certificate_path"
private const val KeyClientCertificatePassword = "client_certificate_password"
private const val KeySecondaryUrls = "secondary_urls"
private const val KeyCustomHeaders = "custom_headers"
private const val KeySelectedMusicFolderIds = "selected_music_folder_ids"
private const val KeyInterfaceLanguage = "interface_language"
private const val KeyReplayGainMode = "replay_gain_mode"
private const val KeySampleRateConverter = "sample_rate_converter"
private const val KeySampleRateMatching = "sample_rate_matching"
private const val KeyReplayGainInspectorEnabled = "replay_gain_inspector_enabled"
private const val KeyGaplessEnabled = "gapless_enabled"
private const val KeyCrossfadeDurationSeconds = "crossfade_duration_seconds"
private const val KeyEqualizerEnabled = "equalizer_enabled"
private const val KeyEqualizerPreset = "equalizer_preset"
private const val KeyEqualizerProfileId = "equalizer_profile_id"
private const val KeyEqualizerProfiles = "equalizer_profiles"
private const val KeyEqualizerBandPrefix = "equalizer_band"
private const val KeyDebugLoggingEnabled = "debug_logging_enabled"
private const val KeyCheckForUpdates = "check_for_updates"
private const val KeyStartPlayingOnLaunch = "start_playing_on_launch"
private const val KeyAlbumCollectionLayout = "album_collection_layout"
private const val KeyAlbumSortOrder = "album_sort_order"
private const val KeyGroupAlbumsByReleaseType = "group_albums_by_release_type"
private const val KeyNowPlayingShowAlbumYear = "now_playing_show_album_year"
private const val KeyNowPlayingShowAudioInfo = "now_playing_show_audio_info"
private const val KeyNowPlayingShowVolumeBar = "now_playing_show_volume_bar"
private const val KeyNowPlayingScrollTrackTitle = "now_playing_scroll_track_title"
private const val KeyNowPlayingScrollArtistName = "now_playing_scroll_artist_name"
private const val KeyNowPlayingScrollAlbumName = "now_playing_scroll_album_name"
private const val KeySwipeLibraryRight = "swipe_library_right"
private const val KeySwipeLibraryLeft = "swipe_library_left"
private const val KeySwipeQueueRight = "swipe_queue_right"
private const val KeySwipeQueueLeft = "swipe_queue_left"
private const val KeySwipeRelatedRight = "swipe_related_right"
private const val KeySwipeRelatedLeft = "swipe_related_left"
private const val KeySwipePlaylistEditRight = "swipe_playlist_edit_right"
private const val KeySwipePlaylistEditLeft = "swipe_playlist_edit_left"
private const val KeySwipeDownloadsRight = "swipe_downloads_right"
private const val KeySwipeDownloadsLeft = "swipe_downloads_left"
private const val KeyLrclibLyricsEnabled = "lrclib_lyrics_enabled"
private const val KeyPreferSyncedLyrics = "prefer_synced_lyrics"
private const val KeyLyricsSearchOrder = "lyrics_search_order"
private const val KeySonicSimilarityEnabled = "sonic_similarity_enabled"
private const val KeySonicAutoplayEnabled = "sonic_autoplay_enabled"
private const val KeyPreviousButtonBehavior = "previous_button_behavior"
private const val KeyUpNextSelectionBehavior = "up_next_selection_behavior"
private const val KeyRemovePlayedTracksFromQueue = "remove_played_tracks_from_queue"
private const val KeyRadioFamiliarity = "radio_familiarity"
private const val KeyRadioArtistSpread = "radio_artist_spread"
private const val KeyRadioSameDecadeOnly = "radio_same_decade_only"
private const val KeyRadioDjs = "radio_djs"
private const val KeyActiveRadioDjId = "active_radio_dj_id"
private const val KeyWifiStreamQualityMode = "wifi_stream_quality_mode"
private const val KeyWifiStreamCodec = "wifi_stream_codec"
private const val KeyWifiStreamBitrate = "wifi_stream_bitrate"
private const val KeyMobileStreamQualityMode = "mobile_stream_quality_mode"
private const val KeyMobileStreamCodec = "mobile_stream_codec"
private const val KeyMobileStreamBitrate = "mobile_stream_bitrate"
private const val KeyDownloadQualityMode = "download_quality_mode"
private const val KeyDownloadCodec = "download_codec"
private const val KeyDownloadBitrate = "download_bitrate"
private const val KeyDownloadedTrackPlayback = "downloaded_track_playback"
private const val KeyAllowMobileDownloads = "allow_mobile_downloads"
private const val KeyAudioCachingEnabled = "audio_caching_enabled"
private const val KeyOfflineModeEnabled = "offline_mode_enabled"
private const val KeyAudioPrefetchDepth = "audio_prefetch_depth"
private const val KeyWaveformsEnabled = "waveforms_enabled"
private const val KeyWaveformBucketCount = "waveform_bucket_count"
private const val KeyMaxAudioCacheBytes = "max_audio_cache_bytes"
private const val KeyMaxDownloadBytes = "max_download_bytes"
private const val KeyCustomAudioCacheDirectory = "custom_audio_cache_directory"
private const val KeyCustomDownloadDirectory = "custom_download_directory"
private const val KeySelectedVisualizer = "selected_visualizer"
private const val KeyRecentRadioStreams = "recent_radio_streams"
private const val KeyRecentInternetRadioStations = "recent_internet_radio_stations"
private const val KeySettingsSyncTreeUri = "settings_sync_tree_uri"
private const val KeySettingsSyncAutoExportEnabled = "settings_sync_auto_export_enabled"
private const val KeySettingsSyncLastLocalUpdateEpochMillis = "settings_sync_last_local_update_epoch_millis"
private const val KeySettingsSyncLastAppliedUpdateEpochMillis = "settings_sync_last_applied_update_epoch_millis"
private const val KeySettingsSyncLastProviderPullEpochMillis = "settings_sync_last_provider_pull_epoch_millis"
private const val KeySettingsSyncLastProviderPushEpochMillis = "settings_sync_last_provider_push_epoch_millis"
private const val KeySettingsSyncLastProviderError = "settings_sync_last_provider_error"
private const val KeySettingsSyncLastMirrorUpdateEpochMillis = "settings_sync_last_mirror_update_epoch_millis"
private val JsonSettings = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
