package app.naviamp.desktop.platform

import java.awt.Rectangle
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopWindowGeometryStoreTest {
    @Test
    fun geometryRoundTripsSizeAndPosition() {
        val directory = createTempDirectory("naviamp-window-geometry")
        try {
            val store = DesktopWindowGeometryStore(directory.resolve("window.properties"))
            val geometry = DesktopWindowGeometry(widthDp = 720f, heightDp = 560f, xDp = 120f, yDp = 80f)

            store.save(geometry)

            assertEquals(geometry, store.load())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun normalizationClampsSizeAndDropsAnOffscreenPosition() {
        val normalized = DesktopWindowGeometry(widthDp = 100f, heightDp = 200f, xDp = 4_000f, yDp = 4_000f)
            .normalized(listOf(Rectangle(0, 0, 1920, 1080)))

        assertEquals(MinDesktopWindowWidthDp, normalized.widthDp)
        assertEquals(MinDesktopWindowHeightDp, normalized.heightDp)
        assertNull(normalized.xDp)
        assertNull(normalized.yDp)
    }
}
