package app.naviamp.android

import android.content.Context
import android.content.SharedPreferences
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.presentation.NaviampCoreLegacySettingsValueStore
import app.naviamp.presentation.NaviampCoreSettingsValueStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** SharedPreferences byte/string effect; Core owns the settings schema and migration. */
class AndroidCoreSettingsValueStore(
    context: Context,
) : NaviampCoreSettingsValueStore, NaviampCoreLegacySettingsValueStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        AndroidSettingsPreferencesName,
        Context.MODE_PRIVATE,
    )

    init {
        migrateLegacyCredentials(context.applicationContext, preferences)
    }

    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun read(key: String): String? = preferences.all[key]?.toString()

    override fun write(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

private fun migrateLegacyCredentials(
    context: Context,
    preferences: SharedPreferences,
) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val serializer = ListSerializer(ConnectionFormHeader.serializer())
    val legacyHeaders = preferences.getString(KeyLegacyCustomHeaders, null)
        ?.let { encoded -> runCatching { json.decodeFromString(serializer, encoded) }.getOrDefault(emptyList()) }
        .orEmpty()
    AndroidSettingsCredentialStore(context).migrateLegacy(
        password = preferences.getString(KeyLegacyPassword, null),
        clientCertificatePassword = preferences.getString(KeyLegacyClientCertificatePassword, null),
        headers = legacyHeaders,
    ) { sanitizedHeaders ->
        preferences.edit()
            .remove(KeyLegacyPassword)
            .remove(KeyLegacyClientCertificatePassword)
            .putString(KeyLegacyCustomHeaders, json.encodeToString(serializer, sanitizedHeaders))
            .apply()
    }
}

internal const val AndroidSettingsPreferencesName = "naviamp_android_settings"
private const val KeyLegacyPassword = "password"
private const val KeyLegacyClientCertificatePassword = "client_certificate_password"
private const val KeyLegacyCustomHeaders = "custom_headers"
