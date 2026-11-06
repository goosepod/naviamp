package app.naviamp.domain.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ConnectionCertificateTrustTest {
    @Test fun customAuthoritiesKeepNormallyTrustedMediaAvailable() {
        val policy = ConnectionTlsSettings(customCertificatePath = "private-ca.pem").serverCertificateTrust
        assertEquals(ServerCertificateTrust.PlatformAndCustom, policy)
        assertTrue(policy.includesPlatformAnchors)
    }

    @Test fun ordinaryConnectionsKeepPlatformValidation() {
        assertEquals(ServerCertificateTrust.Platform, ConnectionTlsSettings().serverCertificateTrust)
        assertEquals(ServerCertificateTrust.Platform,
            ConnectionTlsSettings(customCertificatePath = " ").serverCertificateTrust)
    }

    @Test fun explicitVerificationOptOutRemainsSeparateFromAddingAnAuthority() {
        val policy = ConnectionTlsSettings(insecureSkipTlsVerification = true,
            customCertificatePath = "private-ca.pem").serverCertificateTrust
        assertEquals(ServerCertificateTrust.Unverified, policy)
        assertFalse(policy.includesPlatformAnchors)
    }
}
