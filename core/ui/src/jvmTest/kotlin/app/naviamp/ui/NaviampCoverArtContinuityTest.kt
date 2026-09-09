package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampCoverArtContinuityTest {
    @Test fun changingDecodeSizeDoesNotBlankAnAlreadyDisplayedCover() = runDesktopComposeUiTest(180, 180) {
        val size = mutableStateOf(96.dp)
        val gate = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val bytes = assertNotNull(jvmGeneratedCoverArtBytes("naviamp-radio-tile://cover?label=A&from=AA2244&to=AA2244"))
        resetNaviampCoverArtCache()
        setJvmPlatformCoverArtByteLoader {
            if (calls.incrementAndGet() > 1) gate.await()
            bytes
        }
        try {
            setContent { NaviampCoverArt("https://art.invalid/cover", NaviampColors.Dark, size.value, 0.dp) }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Album art").fetchSemanticsNodes().isNotEmpty() }
            mainClock.advanceTimeBy(300)
            val before = onRoot().captureToImage().toPixelMap()[20, 20]
            runOnIdle { size.value = 160.dp }
            waitUntil(timeoutMillis = 5_000) { calls.get() > 1 }
            mainClock.advanceTimeBy(500)
            onNodeWithContentDescription("Album art").assertExists()
            val during = onRoot().captureToImage().toPixelMap()[20, 20]
            assertEquals(before, during)
        } finally {
            gate.complete(Unit)
            resetJvmPlatformCoverArtByteLoader()
            resetNaviampCoverArtCache()
        }
    }
}
