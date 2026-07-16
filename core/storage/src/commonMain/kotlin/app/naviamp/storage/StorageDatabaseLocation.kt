package app.naviamp.storage

/**
 * A host-selected location for Naviamp's durable SQLDelight database.
 *
 * Platform hosts remain responsible for selecting and creating an OS-approved directory. Keeping
 * the filename separate lets native database drivers join the path without treating user-provided
 * text as a complete SQLite path.
 */
data class StorageDatabaseLocation(
    val directoryPath: String,
    val fileName: String = DefaultStorageDatabaseFileName,
) {
    init {
        require(directoryPath.isNotBlank()) { "The storage database directory must not be blank." }
        require('\u0000' !in directoryPath) { "The storage database directory must not contain a null byte." }
        require(fileName.isNotBlank()) { "The storage database filename must not be blank." }
        require(fileName != "." && fileName != "..") { "The storage database filename must name a file." }
        require('/' !in fileName && '\\' !in fileName) {
            "The storage database filename must not contain path separators."
        }
        require('\u0000' !in fileName) { "The storage database filename must not contain a null byte." }
    }
}

const val DefaultStorageDatabaseFileName = "naviamp-storage.db"
