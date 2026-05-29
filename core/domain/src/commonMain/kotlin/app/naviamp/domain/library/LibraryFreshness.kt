package app.naviamp.domain.library

data class LibraryFreshness(
    val signature: String?,
    val previousSignature: String?,
    val scanning: Boolean,
)

data class LibraryFreshnessUpdate(
    val signatureToMarkChecked: String? = null,
    val status: String? = null,
    val clearStatus: Boolean = false,
)

fun LibraryFreshness.evaluateLibraryFreshness(currentStatus: String?): LibraryFreshnessUpdate {
    val currentSignature = signature ?: return LibraryFreshnessUpdate()
    return when {
        previousSignature == null -> LibraryFreshnessUpdate(signatureToMarkChecked = currentSignature)
        previousSignature != currentSignature -> LibraryFreshnessUpdate(
            status = if (scanning) {
                "Navidrome is scanning. Refresh library after the scan finishes."
            } else {
                "Library changed on server. Refresh library to import updates."
            },
        )
        currentStatus?.startsWith("Library changed on server") == true ||
            currentStatus?.startsWith("Navidrome is scanning") == true -> LibraryFreshnessUpdate(clearStatus = true)
        else -> LibraryFreshnessUpdate()
    }
}
