package app.naviamp.android

import android.content.Context
import app.naviamp.android.security.AndroidCredentialProtector
import app.naviamp.android.security.AndroidKeystoreCredentialProtector
import app.naviamp.domain.settings.ConnectionFormHeader
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Focused encrypted persistence for connection secrets; ordinary settings remain in the settings store. */
internal class AndroidSettingsCredentialStore(
    context: Context,
    private val protector: AndroidCredentialProtector = AndroidKeystoreCredentialProtector(),
) {
    private val preferences = context.applicationContext.getSharedPreferences(CredentialPreferencesName, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun password(): String = reveal(KeyPassword)
    fun clientCertificatePassword(): String = reveal(KeyClientCertificatePassword)

    fun save(password: String, clientCertificatePassword: String, headers: List<ConnectionFormHeader>) {
        preferences.edit()
            .putString(KeyPassword, protector.protect(password))
            .putString(KeyClientCertificatePassword, protector.protect(clientCertificatePassword))
            .putString(KeyCustomHeaders, encodeSecretHeaders(headers))
            .apply()
    }

    fun applySecretHeaders(headers: List<ConnectionFormHeader>): List<ConnectionFormHeader> {
        val secretValues = preferences.getString(KeyCustomHeaders, null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(ConnectionFormHeader.serializer()), it) }.getOrDefault(emptyList()) }
            .orEmpty()
        return headers.mapIndexed { index, header ->
            val secret = secretValues.getOrNull(index)
                ?.takeIf { it.valueIsSecret && it.name == header.name }
                ?.value
                ?.let(protector::reveal)
            if (header.valueIsSecret) header.copy(value = secret.orEmpty()) else header
        }
    }

    fun migrateLegacy(
        password: String?,
        clientCertificatePassword: String?,
        headers: List<ConnectionFormHeader>,
        clearLegacy: (List<ConnectionFormHeader>) -> Unit,
    ) {
        val secretHeaders = headers.map { header ->
            if (header.valueIsSecret && header.value.isNotEmpty()) header.copy(value = protector.protect(header.value).orEmpty())
            else ConnectionFormHeader()
        }
        val editor = preferences.edit()
        if (!preferences.contains(KeyPassword) && password != null) editor.putString(KeyPassword, protector.protect(password))
        if (!preferences.contains(KeyClientCertificatePassword) && clientCertificatePassword != null) {
            editor.putString(KeyClientCertificatePassword, protector.protect(clientCertificatePassword))
        }
        if (!preferences.contains(KeyCustomHeaders) && secretHeaders.any { it.valueIsSecret }) {
            editor.putString(KeyCustomHeaders, json.encodeToString(ListSerializer(ConnectionFormHeader.serializer()), secretHeaders))
        }
        editor.apply()
        if (password != null || clientCertificatePassword != null || secretHeaders.any { it.valueIsSecret }) {
            clearLegacy(headers.map { if (it.valueIsSecret) it.copy(value = "") else it })
        }
    }

    fun clear() = preferences.edit().clear().apply()

    private fun reveal(key: String): String = protector.reveal(preferences.getString(key, null)).orEmpty()

    private fun encodeSecretHeaders(headers: List<ConnectionFormHeader>): String = json.encodeToString(
        ListSerializer(ConnectionFormHeader.serializer()),
        headers.map { if (it.valueIsSecret) it.copy(value = protector.protect(it.value).orEmpty()) else ConnectionFormHeader() },
    )
}

internal const val CredentialPreferencesName = "naviamp_android_credentials"
private const val KeyPassword = "password"
private const val KeyClientCertificatePassword = "client_certificate_password"
private const val KeyCustomHeaders = "custom_headers"
