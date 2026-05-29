package app.naviamp.domain.source

data class DeletedMediaSourceUpdate(
    val clearConnectedSource: Boolean,
    val clearSavedConnectionForLogin: Boolean,
    val status: String,
)

fun deletedMediaSourceUpdate(
    source: SavedMediaSource,
    connectedSourceId: String?,
    savedConnectionBaseUrl: String?,
    savedConnectionUsername: String?,
): DeletedMediaSourceUpdate =
    DeletedMediaSourceUpdate(
        clearConnectedSource = connectedSourceId == source.id,
        clearSavedConnectionForLogin = savedConnectionBaseUrl == source.baseUrl &&
            savedConnectionUsername == source.username,
        status = "Deleted ${source.displayName}.",
    )
