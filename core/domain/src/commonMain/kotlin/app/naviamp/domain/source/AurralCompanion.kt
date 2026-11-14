package app.naviamp.domain.source

import kotlinx.serialization.Serializable

/** An independent discovery account. It never changes the primary library or playback identity. */
@Serializable
data class AurralCompanion(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val tlsSettings: ConnectionTlsSettings = ConnectionTlsSettings(),
    val customHeaders: List<ConnectionHeaderDefinition> = emptyList(),
) {
    fun normalized(): AurralCompanion = copy(
        baseUrl = normalizedBaseUrl(baseUrl),
        username = username.trim(),
        customHeaders = customHeaders.mapNotNull { it.normalized() }.distinctBy { it.name.lowercase() },
    )

    fun withoutSecrets(): AurralCompanion {
        val value = normalized()
        return value.copy(
            password = "",
            tlsSettings = value.tlsSettings.copy(clientCertificateKeyStorePassword = null),
            customHeaders = value.customHeaders.map { header ->
                header.copy(value = header.value.takeUnless { header.valueIsSecret })
            },
        )
    }

    /** Imported profiles retain local secrets only for the exact same remote account. */
    fun retainingSecretsFrom(previous: AurralCompanion?): AurralCompanion {
        val value = normalized()
        if (previous == null || value.baseUrl != previous.baseUrl || value.username != previous.username) return value
        return value.copy(
            password = value.password.ifEmpty { previous.password },
            tlsSettings = value.tlsSettings.copy(clientCertificateKeyStorePassword =
                value.tlsSettings.clientCertificateKeyStorePassword ?: previous.tlsSettings.clientCertificateKeyStorePassword
                    .takeIf { value.tlsSettings.clientCertificateKeyStorePath == previous.tlsSettings.clientCertificateKeyStorePath }),
            customHeaders = value.customHeaders.map { header ->
                if (!header.valueIsSecret || !header.value.isNullOrEmpty()) header else header.copy(value =
                    previous.customHeaders.firstOrNull { it.valueIsSecret && it.name.equals(header.name, true) }?.value)
            },
        )
    }
}

fun AurralCompanion.toConnectionForm(): app.naviamp.domain.settings.ConnectionFormState =
    app.naviamp.domain.settings.ConnectionFormState(
        providerId = app.naviamp.domain.provider.ProviderIdAurral,
        serverUrl = baseUrl, username = username, password = password,
        skipTlsVerification = tlsSettings.insecureSkipTlsVerification,
        customCertificatePath = tlsSettings.customCertificatePath.orEmpty(),
        clientCertificatePath = tlsSettings.clientCertificateKeyStorePath.orEmpty(),
        clientCertificatePassword = tlsSettings.clientCertificateKeyStorePassword.orEmpty(),
        customHeaders = customHeaders.map { app.naviamp.domain.settings.ConnectionFormHeader(it.name, it.value.orEmpty(), it.valueIsSecret) },
    )

fun app.naviamp.domain.settings.ConnectionFormState.toAurralCompanion(enabled: Boolean): AurralCompanion = AurralCompanion(
    enabled = enabled, baseUrl = serverUrl, username = username, password = password,
    tlsSettings = ConnectionTlsSettings(skipTlsVerification, customCertificatePath.takeIf { it.isNotBlank() },
        clientCertificatePath.takeIf { it.isNotBlank() }, clientCertificatePassword.takeIf { it.isNotEmpty() }),
    customHeaders = customHeaders.map { ConnectionHeaderDefinition(it.name, it.value, it.valueIsSecret) },
)

/** Editing a different account must not silently carry credentials to its endpoint. */
fun AurralCompanion.withForm(form: app.naviamp.domain.settings.ConnectionFormState): AurralCompanion {
    val next = form.toAurralCompanion(enabled)
    if (normalizedBaseUrl(baseUrl) == normalizedBaseUrl(next.baseUrl) && username.trim() == next.username.trim()) return next
    return next.copy(
        password = "",
        tlsSettings = next.tlsSettings.copy(clientCertificateKeyStorePath = null, clientCertificateKeyStorePassword = null),
        customHeaders = next.customHeaders.map { if (it.valueIsSecret) it.copy(value = null) else it },
    )
}
