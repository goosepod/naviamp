package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampGeneratedCoverArtTest {
    @Test
    fun parsesRadioTilePresentationOnceForEveryRenderer() {
        val spec = naviampRadioTileSpec(
            "naviamp-radio-tile://tile?label=FM%2B&from=112233&to=aabbcc",
        )

        assertEquals("FM+", spec?.label)
        assertEquals(0x112233, spec?.fromRgb)
        assertEquals(0xaabbcc, spec?.toRgb)
        assertEquals(104f, spec?.textSizePx)
    }

    @Test
    fun rejectsOtherUrlsAndDefaultsInvalidParameters() {
        assertNull(naviampRadioTileSpec("https://example.test/cover.jpg"))
        assertEquals(
            NaviampRadioTileSpec("RAD", 0x465d7a, 0x161f2c),
            naviampRadioTileSpec("naviamp-radio-tile://tile?from=invalid"),
        )
    }
}
