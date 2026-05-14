package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackStreamMetadataTest {
    @Test
    fun fromPropertiesPrefersIcyTitle() {
        val metadata = PlaybackStreamMetadata.fromProperties(
            mapOf(
                "title" to "Generic Title",
                "icy-title" to "Artist - Song",
            ),
            fallbackTitle = "Fallback",
        )

        assertEquals("Artist - Song", metadata.title)
    }

    @Test
    fun fromPropertiesUsesFallbackWhenNoKnownTitleKeysExist() {
        val metadata = PlaybackStreamMetadata.fromProperties(
            properties = mapOf("station" to "Example FM"),
            fallbackTitle = "Fallback Title",
        )

        assertEquals("Fallback Title", metadata.title)
    }
}
