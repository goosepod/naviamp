package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation

sealed interface InternetRadioStationOperationResult {
    data class Ready(val stations: List<InternetRadioStation>) : InternetRadioStationOperationResult
    data class Failed(val message: String) : InternetRadioStationOperationResult
}

data class InternetRadioStationOperationApplier(
    val setStations: (List<InternetRadioStation>) -> Unit,
    val clearStatus: () -> Unit,
    val setStatus: (String) -> Unit,
)

suspend fun internetRadioStationOperationResult(
    fallbackError: String,
    operation: suspend () -> List<InternetRadioStation>,
): InternetRadioStationOperationResult =
    try {
        InternetRadioStationOperationResult.Ready(operation())
    } catch (error: Exception) {
        InternetRadioStationOperationResult.Failed(error.message ?: fallbackError)
    }

fun applyInternetRadioStationOperationResult(
    result: InternetRadioStationOperationResult,
    applier: InternetRadioStationOperationApplier,
) {
    when (result) {
        is InternetRadioStationOperationResult.Ready -> {
            applier.setStations(result.stations)
            applier.clearStatus()
        }
        is InternetRadioStationOperationResult.Failed -> applier.setStatus(result.message)
    }
}
