package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectProvisioningProfile
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class NaviampCoreConnectProvisioningTest {
    @Test fun companionCredentialsAndDisabledConfigurationSurviveEncryptedProvisioning() {
        val companion = app.naviamp.domain.source.AurralCompanion(true, " https://aurral.example/ ", " secondary ", "companion-secret",
            app.naviamp.domain.source.ConnectionTlsSettings(insecureSkipTlsVerification = true),
            listOf(app.naviamp.domain.source.ConnectionHeaderDefinition("X-Secret", "header-secret", true)))
        for (enabled in listOf(true, false)) {
            val value = companion.copy(enabled = enabled)
            val ready = assertIs<NaviampCoreConnectProvisioningExport.Ready>(ConnectionFormState(
                serverUrl = "https://primary.example", username = "primary", password = "primary-secret",
                aurralCompanion = value).toConnectProvisioningExport())
            val envelope = app.naviamp.domain.connect.NaviampConnectEnvelope(1, "session", 1,
                message = app.naviamp.domain.connect.NaviampConnectCommandRequest(
                    app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning(ready.profile)))
            val decoded = app.naviamp.domain.connect.NaviampConnectWireCodec.decode(
                app.naviamp.domain.connect.NaviampConnectWireCodec.encode(envelope))
            val profile = ((decoded.message as app.naviamp.domain.connect.NaviampConnectCommandRequest).command as
                app.naviamp.domain.connect.NaviampConnectOfferConnectionProvisioning).profile
            assertEquals(value.normalized(), profile.toConnectionFormState().aurralCompanion)
            assertEquals("primary-secret", profile.toConnectionFormState().password)
            for (secret in listOf("primary-secret", "companion-secret", "header-secret")) {
                assertFalse(profile.toString().contains(secret))
                assertFalse(profile.aurralCompanion.toString().contains(secret))
            }
        }
    }

    @Test fun companionLocalCertificatesBlockTransferInsteadOfSavingUnusablePaths() {
        for (tls in listOf(
            app.naviamp.domain.source.ConnectionTlsSettings(customCertificatePath = "/phone/ca.pem"),
            app.naviamp.domain.source.ConnectionTlsSettings(clientCertificateKeyStorePath = "/phone/client.p12"),
        )) for (enabled in listOf(true, false)) {
            assertIs<NaviampCoreConnectProvisioningExport.Unsupported>(ConnectionFormState(
                serverUrl = "https://primary.example", username = "primary", password = "primary-secret",
                aurralCompanion = app.naviamp.domain.source.AurralCompanion(enabled, "https://aurral.example", "secondary", "secret", tls)
            ).toConnectProvisioningExport())
        }
    }

    @Test
    fun missingCredentialRequestsCredentialRepair() {
        val unsupported = assertIs<NaviampCoreConnectProvisioningExport.Unsupported>(
            ConnectionFormState(
                serverUrl = "https://music.example.test",
                username = "listener",
            ).toConnectProvisioningExport(),
        )

        assertEquals(true, unsupported.credentialUnavailable)
    }

    @Test
    fun exportsCredentialAndLibrariesWithoutMachineLocalCertificatePaths() {
        val ready = assertIs<NaviampCoreConnectProvisioningExport.Ready>(
            ConnectionFormState(
                providerId = "navidrome",
                displayName = "Home",
                serverUrl = "https://music.example.test",
                username = "listener",
                password = "secret-value",
                selectedMusicFolderIds = listOf("music", "music", " spoken "),
            ).toConnectProvisioningExport(),
        )

        assertEquals(listOf("music", "spoken"), ready.profile.selectedLibraryIds)
        assertFalse(ready.profile.toString().contains("secret-value"))
        assertIs<NaviampCoreConnectProvisioningExport.Unsupported>(
            ConnectionFormState(
                serverUrl = "https://music.example.test",
                username = "listener",
                password = "secret",
                customCertificatePath = "/local/certificate.pem",
            ).toConnectProvisioningExport(),
        )
    }

    @Test
    fun targetFormRetainsEncryptedConnectionFields() {
        val form = NaviampConnectProvisioningProfile(
            providerId = "navidrome",
            displayName = "Home",
            serverUrl = "https://music.example.test",
            username = "listener",
            password = "secret",
            skipTlsVerification = true,
            selectedLibraryIds = listOf("music"),
        ).toConnectionFormState()

        assertEquals("secret", form.password)
        assertEquals(true, form.skipTlsVerification)
        assertEquals(listOf("music"), form.selectedMusicFolderIds)
    }

    @Test
    fun portableSettingsPreserveTargetOnlyDeviceAndHostPolicy() {
        val currentInterface = InterfaceSettings(checkForUpdates = true)
        val offeredInterface = InterfaceSettings(
            checkForUpdates = false,
            keepScreenAwake = true,
            appBackgroundStyle = AppBackgroundStyle.SingleColor,
        )
        val currentPlayback = PlaybackSettings(volumePercent = 77, allowMobileDownloads = false)
        val offeredPlayback = PlaybackSettings(
            gaplessEnabled = false,
            crossfadeDurationSeconds = 8,
            volumePercent = 20,
            allowMobileDownloads = true,
        )

        val mergedInterface = currentInterface.withConnectPortableValues(offeredInterface)
        val mergedPlayback = currentPlayback.withConnectPortableValues(offeredPlayback)

        assertEquals(AppBackgroundStyle.SingleColor, mergedInterface.appBackgroundStyle)
        assertEquals(true, mergedInterface.checkForUpdates)
        assertEquals(true, mergedInterface.keepScreenAwake)
        assertEquals(false, mergedPlayback.gaplessEnabled)
        assertEquals(8, mergedPlayback.crossfadeDurationSeconds)
        assertEquals(77, mergedPlayback.volumePercent)
        assertEquals(false, mergedPlayback.allowMobileDownloads)
    }
}
