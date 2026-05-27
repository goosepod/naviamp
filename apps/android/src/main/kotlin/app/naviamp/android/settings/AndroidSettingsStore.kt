package app.naviamp.android

import android.content.Context
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PreviousButtonBehavior
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.StreamQualityMode
import app.naviamp.domain.settings.StreamQualityPreference
import app.naviamp.domain.settings.StreamingCodec
import app.naviamp.domain.settings.UpNextSelectionBehavior
import app.naviamp.provider.navidrome.NavidromeConnection
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AndroidSettingsStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun loadConnection(savedConnection: NavidromeConnection? = null): ConnectionFormState =
        ConnectionFormState(
            displayName = savedConnection?.displayName ?: preferences.getString(KeyDisplayName, "").orEmpty(),
            serverUrl = savedConnection?.baseUrl ?: preferences.getString(KeyServerUrl, "").orEmpty(),
            username = savedConnection?.username ?: preferences.getString(KeyUsername, "").orEmpty(),
            password = preferences.getString(KeyPassword, "").orEmpty(),
            skipTlsVerification = savedConnection?.tlsSettings?.insecureSkipTlsVerification
                ?: preferences.getBoolean(KeySkipTlsVerification, false),
            customCertificatePath = savedConnection?.tlsSettings?.customCertificatePath
                ?: preferences.getString(KeyCustomCertificatePath, "").orEmpty(),
            clientCertificatePath = savedConnection?.tlsSettings?.clientCertificateKeyStorePath
                ?: preferences.getString(KeyClientCertificatePath, "").orEmpty(),
            clientCertificatePassword = savedConnection?.tlsSettings?.clientCertificateKeyStorePassword
                ?: preferences.getString(KeyClientCertificatePassword, "").orEmpty(),
        )

    fun saveConnection(connection: ConnectionFormState) {
        preferences.edit()
            .putString(KeyDisplayName, connection.displayName.trim())
            .putString(KeyServerUrl, connection.serverUrl.trim())
            .putString(KeyUsername, connection.username)
            .putString(KeyPassword, connection.password)
            .putBoolean(KeySkipTlsVerification, connection.skipTlsVerification)
            .putString(KeyCustomCertificatePath, connection.customCertificatePath.trim())
            .putString(KeyClientCertificatePath, connection.clientCertificatePath.trim())
            .putString(KeyClientCertificatePassword, connection.clientCertificatePassword)
            .apply()
    }

    fun loadPlaybackSettings(): PlaybackSettings =
        PlaybackSettings(
            replayGainMode = enumPreference(KeyReplayGainMode, ReplayGainMode.Off),
            gaplessEnabled = preferences.getBoolean(KeyGaplessEnabled, true),
            crossfadeDurationSeconds = preferences.getInt(KeyCrossfadeDurationSeconds, 0),
            debugLoggingEnabled = preferences.getBoolean(KeyDebugLoggingEnabled, false),
            lrclibLyricsEnabled = preferences.getBoolean(KeyLrclibLyricsEnabled, false),
            previousButtonBehavior = enumPreference(
                KeyPreviousButtonBehavior,
                PreviousButtonBehavior.RestartThenPrevious,
            ),
            upNextSelectionBehavior = enumPreference(
                KeyUpNextSelectionBehavior,
                UpNextSelectionBehavior.MoveSelectedToCurrent,
            ),
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
            allowMobileDownloads = preferences.getBoolean(KeyAllowMobileDownloads, false),
        )

    fun savePlaybackSettings(settings: PlaybackSettings) {
        preferences.edit()
            .putString(KeyReplayGainMode, settings.replayGainMode.name)
            .putBoolean(KeyGaplessEnabled, settings.gaplessEnabled)
            .putInt(KeyCrossfadeDurationSeconds, settings.crossfadeDurationSeconds)
            .putBoolean(KeyDebugLoggingEnabled, settings.debugLoggingEnabled)
            .putBoolean(KeyLrclibLyricsEnabled, settings.lrclibLyricsEnabled)
            .putString(KeyPreviousButtonBehavior, settings.previousButtonBehavior.name)
            .putString(KeyUpNextSelectionBehavior, settings.upNextSelectionBehavior.name)
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
            .putBoolean(KeyAllowMobileDownloads, settings.allowMobileDownloads)
            .apply()
    }

    fun loadSelectedVisualizer(): String =
        preferences.getString(KeySelectedVisualizer, null) ?: DefaultSelectedVisualizer

    fun saveSelectedVisualizer(selectedVisualizer: String) {
        preferences.edit()
            .putString(KeySelectedVisualizer, selectedVisualizer)
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

    fun clear() {
        preferences.edit().clear().apply()
    }

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

    private fun <T> decodeList(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> =
        preferences.getString(key, null)
            ?.let { json ->
                runCatching { JsonSettings.decodeFromString(ListSerializer(serializer), json) }.getOrDefault(emptyList())
            }
            ?: emptyList()
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

private const val PreferencesName = "naviamp_android_settings"
private const val KeyDisplayName = "display_name"
private const val KeyServerUrl = "server_url"
private const val KeyUsername = "username"
private const val KeyPassword = "password"
private const val KeySkipTlsVerification = "skip_tls_verification"
private const val KeyCustomCertificatePath = "custom_certificate_path"
private const val KeyClientCertificatePath = "client_certificate_path"
private const val KeyClientCertificatePassword = "client_certificate_password"
private const val KeyReplayGainMode = "replay_gain_mode"
private const val KeyGaplessEnabled = "gapless_enabled"
private const val KeyCrossfadeDurationSeconds = "crossfade_duration_seconds"
private const val KeyDebugLoggingEnabled = "debug_logging_enabled"
private const val KeyLrclibLyricsEnabled = "lrclib_lyrics_enabled"
private const val KeyPreviousButtonBehavior = "previous_button_behavior"
private const val KeyUpNextSelectionBehavior = "up_next_selection_behavior"
private const val KeyWifiStreamQualityMode = "wifi_stream_quality_mode"
private const val KeyWifiStreamCodec = "wifi_stream_codec"
private const val KeyWifiStreamBitrate = "wifi_stream_bitrate"
private const val KeyMobileStreamQualityMode = "mobile_stream_quality_mode"
private const val KeyMobileStreamCodec = "mobile_stream_codec"
private const val KeyMobileStreamBitrate = "mobile_stream_bitrate"
private const val KeyDownloadQualityMode = "download_quality_mode"
private const val KeyDownloadCodec = "download_codec"
private const val KeyDownloadBitrate = "download_bitrate"
private const val KeyAllowMobileDownloads = "allow_mobile_downloads"
private const val KeySelectedVisualizer = "selected_visualizer"
private const val KeyRecentRadioStreams = "recent_radio_streams"
private const val KeyRecentInternetRadioStations = "recent_internet_radio_stations"
private const val DefaultSelectedVisualizer = "AudioSphere"
private val JsonSettings = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
