package app.naviamp.app

fun noTracksToDownloadStatus(): String = "No tracks to download."

fun keepDownloadedDisabledStatus(name: String): String =
    "$name will no longer be kept downloaded. Existing files were kept."

fun keepDownloadedErrorStatus(name: String, error: Throwable): String =
    error.message ?: "Could not keep $name downloaded."

fun keepDownloadedRefreshErrorStatus(name: String, error: Throwable): String =
    error.message ?: "Could not refresh $name."

fun keepDownloadedUpToDateStatus(name: String): String = "$name is up to date."

fun keepingDownloadedLabel(name: String): String = "Keeping $name downloaded"

fun downloadsRefreshStatus(removedMissingFiles: Int): String =
    if (removedMissingFiles == 0) {
        "Downloads are up to date."
    } else {
        "Removed $removedMissingFiles missing ${downloadNoun(removedMissingFiles)}."
    }

fun downloadsDeletedStatus(count: Int): String = "Deleted $count ${downloadNoun(count)}."

private fun downloadNoun(count: Int): String = if (count == 1) "download" else "downloads"
