package app.naviamp.domain.lyrics

data class LrclibApiCall(
    val endpoint: String,
    val sanitizedUrl: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val success: Boolean,
    val errorMessage: String?,
)

class LrclibApiCallHistory(
    private val maxCalls: Int = 150,
) {
    private val calls = ArrayDeque<LrclibApiCall>()

    fun record(call: LrclibApiCall) {
        calls.addLast(call)
        while (calls.size > maxCalls) {
            calls.removeFirst()
        }
    }

    fun recent(limit: Int = 50): List<LrclibApiCall> =
        calls.takeLast(limit.coerceAtLeast(0)).asReversed()
}

fun lrclibApiCall(
    url: String,
    startedAtEpochMillis: Long,
    durationMillis: Long,
    success: Boolean,
    errorMessage: String?,
): LrclibApiCall =
    LrclibApiCall(
        endpoint = url.lrclibEndpointLabel(),
        sanitizedUrl = url.sanitizedLrclibUrl(),
        startedAtEpochMillis = startedAtEpochMillis,
        durationMillis = durationMillis,
        success = success,
        errorMessage = errorMessage,
    )

private fun String.lrclibEndpointLabel(): String {
    val withoutQuery = substringBefore('?')
    val path = withoutQuery
        .substringAfter("://", withoutQuery)
        .substringAfter('/', "")
        .trim('/')
    return path.ifBlank { "unknown" }
}

private fun String.sanitizedLrclibUrl(): String =
    replace(Regex("""([?&](track_name|artist_name|album_name)=)[^&]+"""), "$1***")
