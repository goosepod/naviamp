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
                "StreamTitle" to "Artist - Track",
                "StreamUrl" to "",
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

    @Test
    fun preservesAlbumCoverAcrossRawAndNativeWrappedIcyBlocks() {
        val block = "StreamTitle='Elvis Costello - Peace, Love and Understanding';StreamUrl='http://img.radioparadise.com/covers/l/8091.jpg';"
        val expected = mapOf("StreamTitle" to "Elvis Costello - Peace, Love and Understanding",
            "StreamUrl" to "http://img.radioparadise.com/covers/l/8091.jpg")
        assertEquals(expected, bassStreamProperties(listOf(block)))
        assertEquals(expected, bassStreamProperties(listOf("StreamTitle=$block")))
        assertEquals(expected + ("icy-title" to expected.getValue("StreamTitle")),
            bassStreamProperties(listOf("icy-title: $block")))
    }

    @Test
    fun acceptsQuotedPunctuationAndReorderedFieldsWithoutTruncatingValues() {
        assertEquals(mapOf("StreamUrl" to "https://images.test/a.jpg?x=one;two&y=3",
            "StreamTitle" to "Guns N' Roses - Don't Cry; live"), bassStreamProperties(listOf(
            "streamtitle=StreamUrl='https://images.test/a.jpg?x=one;two&y=3';StreamTitle=\"Guns N' Roses - Don't Cry; live\";")))
    }

    @Test
    fun emptyOrMissingCoverDoesNotRetainThePreviousSongsUrl() {
        val first = bassStreamProperties(listOf("StreamTitle='First';StreamUrl='https://images.test/first.jpg';"))
        val next = bassStreamProperties(listOf("StreamTitle='Next';StreamUrl='';"))
        assertEquals("https://images.test/first.jpg", first["StreamUrl"])
        assertEquals(mapOf("StreamTitle" to "Next", "StreamUrl" to ""), next)
        assertEquals(mapOf("StreamTitle" to "Last"), bassStreamProperties(listOf("StreamTitle='Last';")))
    }
}
