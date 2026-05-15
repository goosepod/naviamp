package app.naviamp.android

import android.content.Context
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PreviousButtonBehavior
import app.naviamp.domain.settings.UpNextSelectionBehavior
import app.naviamp.provider.navidrome.NavidromeConnection

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
        ).copy(
            replayGainMode = ReplayGainMode.Off,
            crossfadeDurationSeconds = 0,
        )

    fun savePlaybackSettings(settings: PlaybackSettings) {
        preferences.edit()
            .putString(KeyReplayGainMode, settings.replayGainMode.name)
            .putInt(KeyCrossfadeDurationSeconds, settings.crossfadeDurationSeconds)
            .putBoolean(KeyDebugLoggingEnabled, settings.debugLoggingEnabled)
            .putBoolean(KeyLrclibLyricsEnabled, settings.lrclibLyricsEnabled)
            .putString(KeyPreviousButtonBehavior, settings.previousButtonBehavior.name)
            .putString(KeyUpNextSelectionBehavior, settings.upNextSelectionBehavior.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, defaultValue: T): T =
        preferences.getString(key, null)
            ?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
            ?: defaultValue
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
private const val KeyCrossfadeDurationSeconds = "crossfade_duration_seconds"
private const val KeyDebugLoggingEnabled = "debug_logging_enabled"
private const val KeyLrclibLyricsEnabled = "lrclib_lyrics_enabled"
private const val KeyPreviousButtonBehavior = "previous_button_behavior"
private const val KeyUpNextSelectionBehavior = "up_next_selection_behavior"
