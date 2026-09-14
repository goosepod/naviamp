package app.naviamp.app

import kotlin.test.*

class NaviampConnectInitialSetupAuthorizationTest {
    @Test fun onlyAnExplicitLiveCodeCanAuthorizeItsAuthenticatedSession() {
        val policy = NaviampConnectInitialSetupAuthorization()
        assertFalse(policy.acceptsCodePairing("offer", 0))
        policy.showCode("offer", 100, true)
        assertTrue(policy.acceptsCodePairing("offer", 0))
        assertFalse(policy.acceptsCodePairing("other", 0))
        policy.paired("offer", "session", true, 10)
        assertFalse(policy.acceptsCodePairing("offer", 10))
        assertTrue(policy.permitsSetup("session", true, 10))
        assertFalse(policy.permitsSetup("other-session", true, 10))
        assertFalse(policy.permitsSetup("session", false, 10))
        assertFalse(policy.permitsSetup("session", true, 100))
    }

    @Test fun completionDisconnectCancellationAndExpirationDoNotAuthorizeAnotherSetup() {
        for (end in listOf("complete", "disconnect", "cancel", "expire")) {
            val policy = NaviampConnectInitialSetupAuthorization()
            policy.showCode("offer", 100, true)
            if (end == "cancel") policy.stopShowingCode()
            policy.paired("offer", "session", true, if (end == "expire") 100 else 10)
            when (end) {
                "complete" -> policy.setupCompleted("session")
                "disconnect" -> policy.sessionClosed("session")
            }
            assertFalse(policy.permitsSetup("session", true, 20), end)
            assertFalse(policy.permitsSetup("resumed-session", true, 20), end)
        }
    }

    @Test fun aConfiguredTargetOrReplacedOfferCannotGrantAutomaticProvisioning() {
        for (initiallyEmpty in listOf(false, true)) {
            val policy = NaviampConnectInitialSetupAuthorization()
            policy.showCode("offer", 100, initiallyEmpty)
            policy.paired("offer", "session", !initiallyEmpty, 10)
            assertFalse(policy.permitsSetup("session", true, 20))
        }
        val policy = NaviampConnectInitialSetupAuthorization()
        policy.showCode("old", 100, true)
        policy.showCode("new", 100, true)
        policy.paired("old", "session", true, 10)
        assertFalse(policy.permitsSetup("session", true, 20))
    }

    @Test fun backgroundAdvertisingDoesNotCancelAnAlreadyAuthenticatedSetupGrant() {
        val policy = NaviampConnectInitialSetupAuthorization()
        policy.showCode("offer", 100, true)
        policy.paired("offer", "session", true, 10)
        policy.stopShowingCode()
        assertTrue(policy.permitsSetup("session", true, 20))
        policy.sessionClosed("unrelated-session")
        assertTrue(policy.permitsSetup("session", true, 20))
    }
}
