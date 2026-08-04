package app.naviamp.domain.network

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedUrlEncodingTest {
    @Test
    fun encodesFormParametersConsistentlyAcrossPlatforms() {
        assertEquals("hello+world", "hello world".urlEncodedParameter())
        assertEquals("artist%2Falbum%3Fx%3D1%26y%3D2", "artist/album?x=1&y=2".urlEncodedParameter())
        assertEquals("caf%C3%A9", "café".urlEncodedParameter())
        assertEquals("%F0%9F%8E%B5", "🎵".urlEncodedParameter())
        assertEquals("safe-_.*", "safe-_.*".urlEncodedParameter())
    }
}
