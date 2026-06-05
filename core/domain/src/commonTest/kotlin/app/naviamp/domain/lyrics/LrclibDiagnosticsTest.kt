package app.naviamp.domain.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LrclibDiagnosticsTest {
    @Test
    fun lrclibApiCallSanitizesQueryParametersAndLabelsEndpoint() {
        val call = lrclibApiCall(
            url = "https://lrclib.net/api/get?track_name=Song&artist_name=Artist&album_name=Album&duration=200",
            startedAtEpochMillis = 10,
            durationMillis = 25,
            success = true,
            errorMessage = null,
        )

        assertEquals("api/get", call.endpoint)
        assertEquals(
            "https://lrclib.net/api/get?track_name=***&artist_name=***&album_name=***&duration=200",
            call.sanitizedUrl,
        )
        assertEquals(10, call.startedAtEpochMillis)
        assertEquals(25, call.durationMillis)
        assertEquals(true, call.success)
    }
}
