package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InternetRadioStationOperationsTest {
    @Test
    fun readyOperationPublishesStationsBeforeClearingStatus() = runTest {
        val stations = listOf(InternetRadioStation("one", "One", "https://radio.test"))
        val calls = mutableListOf<String>()
        val result = internetRadioStationOperationResult("Failed") { stations }

        applyInternetRadioStationOperationResult(
            result,
            InternetRadioStationOperationApplier(
                setStations = { calls += "stations:${it.size}" },
                clearStatus = { calls += "clear" },
                setStatus = { calls += "error:$it" },
            ),
        )

        assertEquals(listOf("stations:1", "clear"), calls)
    }

    @Test
    fun failedOperationUsesFallbackForMessageLessErrors() = runTest {
        val result = internetRadioStationOperationResult("Could not refresh") {
            throw IllegalStateException()
        }

        assertEquals(
            InternetRadioStationOperationResult.Failed("Could not refresh"),
            result,
        )
    }
}
