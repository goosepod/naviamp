package app.naviamp.provider.navidrome

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NavidromeTlsChallengePolicyTest {
    private val identity = NavidromeTlsSettings(clientCertificateKeyStorePath = "/identity.p12")

    @Test fun configuredIdentityIsUsedOnlyForTheClientChallengeAndNeverRetried() {
        assertEquals(NavidromeTlsChallengeAction.UseClientIdentity, identity.challengeAction(NavidromeTlsChallenge.ClientIdentity, 0))
        assertEquals(NavidromeTlsChallengeAction.DefaultHandling, identity.challengeAction(NavidromeTlsChallenge.ServerTrust, 0))
        assertEquals(NavidromeTlsChallengeAction.DefaultHandling, identity.challengeAction(NavidromeTlsChallenge.Other, 0))
        assertEquals(NavidromeTlsChallengeAction.Cancel, identity.challengeAction(NavidromeTlsChallenge.ClientIdentity, 1))
        assertEquals(NavidromeTlsChallengeAction.Cancel, NavidromeTlsSettings().challengeAction(NavidromeTlsChallenge.ClientIdentity, 0))
    }

    @Test fun privateTrustUsesAnExplicitServerEvaluation() {
        for (settings in listOf(NavidromeTlsSettings(customCertificatePath = "/ca.pem"), NavidromeTlsSettings(insecureSkipTlsVerification = true))) {
            assertEquals(NavidromeTlsChallengeAction.EvaluateServerTrust, settings.challengeAction(NavidromeTlsChallenge.ServerTrust, 0))
            assertEquals(NavidromeTlsChallengeAction.EvaluateServerTrust, settings.challengeAction(NavidromeTlsChallenge.ServerTrust, 1))
        }
    }

    @Test fun certificateBundlesPreserveAllDerEntries() {
        val first = byteArrayOf(0x30, 1, 0)
        assertContentEquals(first, navidromeCertificateDerChain(first).single())
        val pem = "-----BEGIN CERTIFICATE-----\nMAEA\n-----END CERTIFICATE-----\n\n-----BEGIN CERTIFICATE-----\r\nMAEB\r\n-----END CERTIFICATE-----"
        val chain = navidromeCertificateDerChain(pem.encodeToByteArray())
        assertEquals(2, chain.size)
        assertContentEquals(first, chain[0])
        assertContentEquals(byteArrayOf(0x30, 1, 1), chain[1])
    }

    @Test fun malformedBundlesFailClosed() {
        for (bad in listOf("", "-----BEGIN PRIVATE KEY-----", "-----BEGIN CERTIFICATE-----\nMAEA", "-----BEGIN CERTIFICATE----------END CERTIFICATE-----", "-----BEGIN CERTIFICATE-----!-----END CERTIFICATE-----")) {
            assertFailsWith<IllegalArgumentException> { navidromeCertificateDerChain(bad.encodeToByteArray()) }
        }
    }
}
