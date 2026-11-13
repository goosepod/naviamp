package app.naviamp.domain.source

import app.naviamp.domain.settings.*
import kotlin.test.*

class AurralCompanionTest {
    private val configured = AurralCompanion(true, " https://aurral.example/ ", " alice ", "private-password",
        ConnectionTlsSettings(clientCertificateKeyStorePath = "/client.p12", clientCertificateKeyStorePassword = "client-secret"),
        listOf(ConnectionHeaderDefinition(" X-Private ", "header-secret", true), ConnectionHeaderDefinition("X-Public", "public")))

    @Test fun editingAnotherCompanionEndpointClearsItsOldCredentialsBeforeTheyCanBeSubmitted() {
        val changed = configured.withForm(configured.toConnectionForm().copy(serverUrl = "https://other.example"))
        assertEquals("", changed.password)
        assertNull(changed.tlsSettings.clientCertificateKeyStorePath)
        assertNull(changed.tlsSettings.clientCertificateKeyStorePassword)
        assertNull(changed.customHeaders.first().value)
        assertEquals("public", changed.customHeaders.last().value)
        assertEquals(configured, configured.withForm(configured.toConnectionForm()))
    }

    @Test fun portableExportNormalizesTheAccountAndNeverContainsSecrets() {
        val document = SettingsSyncDocument(serverProfiles = listOf(SettingsSyncServerProfile(
            id = "primary", displayName = "Music", username = "primary", primaryUrl = "https://music.example",
            aurralCompanion = configured)))
        val encoded = SettingsSyncJson.encode(document)
        assertFalse(encoded.contains("private-password"))
        assertFalse(encoded.contains("client-secret"))
        assertFalse(encoded.contains("header-secret"))
        val restored = SettingsSyncJson.decode(encoded).serverProfiles.single().aurralCompanion!!
        assertTrue(restored.enabled)
        assertEquals("https://aurral.example", restored.baseUrl)
        assertEquals("alice", restored.username)
        assertEquals("", restored.password)
        assertEquals("public", restored.customHeaders.last().value)
        assertNull(restored.customHeaders.first().value)
    }

    @Test fun importingTheSameAccountRetainsSecretsButChangingAccountsCannotInheritThem() {
        val previous = configured.normalized()
        assertEquals(previous, previous.withoutSecrets().retainingSecretsFrom(previous))
        assertEquals("", previous.withoutSecrets().copy(username = "bob").retainingSecretsFrom(previous).password)
        assertEquals("", previous.withoutSecrets().copy(baseUrl = "https://other.example").retainingSecretsFrom(previous).password)
    }

    @Test fun olderExportsHaveNoCompanionAndFormMappingPreservesAllConnectionControls() {
        assertNull(SettingsSyncJson.decode("""{"serverProfiles":[{"id":"old","displayName":"Old","username":"u","primaryUrl":"https://music.example"}]}""").serverProfiles.single().aurralCompanion)
        assertEquals(configured, configured.toConnectionForm().toAurralCompanion(enabled = true))
    }

    @Test fun enabledCompanionsRequireAnEndpointAndAnAccountWithoutDisablingOldForms() {
        val primary = ConnectionFormState(serverUrl = "https://music.example", username = "primary", password = "primary-password")
        assertNull(connectionFormError(primary, false))
        assertEquals("connection_aurral_companion_invalid", connectionFormError(primary.copy(aurralCompanion = AurralCompanion(enabled = true)), false))
        assertNull(connectionFormError(primary.copy(aurralCompanion = configured), false))
    }
}
