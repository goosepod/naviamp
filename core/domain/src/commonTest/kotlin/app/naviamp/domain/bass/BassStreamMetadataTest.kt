package app.naviamp.domain.bass

import kotlin.test.Test
import kotlin.test.assertEquals

class BassStreamMetadataTest {
    @Test
    fun parsesEqualsColonAndIcyMetadataOnceForEveryNativeBackend() {
        assertEquals(
            mapOf(
                "icy-name" to "Example FM",
                "icy-title" to "Artist - Track",
                "genre" to "Jazz",
            ),
            bassStreamProperties(
                listOf(
                    "icy-name=Example FM",
                    "icy-title: StreamTitle='Artist - Track';StreamUrl='';",
                    "'genre' = 'Jazz'",
                    "invalid",
                    "empty=",
                ),
            ),
        )
    }
}
