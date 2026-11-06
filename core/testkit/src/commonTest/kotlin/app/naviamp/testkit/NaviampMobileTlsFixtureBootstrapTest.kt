package app.naviamp.testkit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NaviampMobileTlsFixtureBootstrapTest {
    private val manifest = mapOf(
        "controlPort" to "41001", "mutualPort" to "41002",
        "privateCaPort" to "41003", "wrongHostPort" to "41004",
        "stopToken" to "owned-fixture", "ca" to "disposable-ca",
    )
    private fun encode(value: Map<String, String>) = value.entries.joinToString("\n") { "${it.key}=${it.value}" }

    @Test fun suppliedBootstrapStillRequiresALiveNativeRequest() = runTest {
        val requests = mutableListOf<Int>()
        val actual = loadVerifiedNaviampMobileTlsFixture(encode(manifest), 49999) { port ->
            requests += port
            manifest
        }
        assertEquals(listOf(41001), requests)
        assertEquals(manifest, actual)
    }

    @Test fun recycledPortsDoNotMakeAnotherFixtureAcceptable() = runTest {
        assertFailsWith<IllegalStateException> {
            loadVerifiedNaviampMobileTlsFixture(encode(manifest), null) {
                manifest + ("stopToken" to "another-fixture")
            }
        }
    }

    @Test fun absentBootstrapUsesTheRunnerPort() = runTest {
        val requests = mutableListOf<Int>()
        assertEquals(manifest, loadVerifiedNaviampMobileTlsFixture(null, 41001) {
            requests += it
            manifest
        })
        assertEquals(listOf(41001), requests)
    }

    @Test fun malformedBootstrapCannotFallBackToAnotherListener() = runTest {
        var requests = 0
        assertFailsWith<IllegalArgumentException> {
            loadVerifiedNaviampMobileTlsFixture(encode(manifest + ("mutualPort" to "41001")), 49999) {
                requests++
                manifest
            }
        }
        assertEquals(0, requests)
    }

    @Test fun failedNativeRequestCannotFallBackToTheRunnerFile() = runTest {
        val failure = IllegalStateException("Native fixture is unreachable")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            loadVerifiedNaviampMobileTlsFixture(encode(manifest), null) { throw failure }
        })
    }

    @Test fun liveResponseCannotRedirectAuditsToAnotherControlListener() = runTest {
        assertFailsWith<IllegalStateException> {
            loadVerifiedNaviampMobileTlsFixture(null, 41001) { manifest + ("controlPort" to "42000") }
        }
    }

    @Test fun missingRunnerInformationFailsBeforeNetworking() = runTest {
        var requests = 0
        assertFailsWith<IllegalArgumentException> {
            loadVerifiedNaviampMobileTlsFixture(null, null) { requests++; manifest }
        }
        assertEquals(0, requests)
    }

    @Test fun cancellationIsNotFixtureReadiness() = runTest {
        val cancelled = CancellationException("Cancelled native request")
        assertSame(cancelled, assertFailsWith<CancellationException> {
            loadVerifiedNaviampMobileTlsFixture(encode(manifest), null) { throw cancelled }
        })
    }
}
