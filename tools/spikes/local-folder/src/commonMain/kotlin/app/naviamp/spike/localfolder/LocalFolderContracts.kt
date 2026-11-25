package app.naviamp.spike.localfolder

/**
 * A persisted, host-issued folder grant. Core stores [token] verbatim and never parses it: it is a
 * SAF tree URI on Android, a base64 security-scoped bookmark on Apple and an absolute path on
 * Windows and Linux.
 */
data class FolderGrant(val token: String, val displayName: String)

sealed interface FolderGrantState {
    data class Available(val grant: FolderGrant) : FolderGrantState
    /** The grant resolved, but the host had to refresh it; persist [refreshed] before scanning. */
    data class Refreshed(val refreshed: FolderGrant) : FolderGrantState
    /** Permission was revoked or the bookmark cannot resolve; the user must pick the folder again. */
    data object NeedsReselection : FolderGrantState
    /** The grant is valid but its volume is absent (unmounted SD card, disconnected drive). */
    data object Unreachable : FolderGrantState
}

/**
 * One child of a granted folder. [id] is host-opaque and stable for the provider (a document ID or a
 * path relative to the root). [modifiedEpochMillis] and [sizeBytes] are null when the host cannot
 * report them cheaply.
 */
data class FolderEntry(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val modifiedEpochMillis: Long?,
    val mimeType: String? = null,
)

/** Positional, blocking reads; hosts back it with pread on a file descriptor or a file channel. */
interface LocalByteSource : AutoCloseable {
    val lengthBytes: Long

    /** Returns the number of bytes copied, or -1 at end of file. Never advances shared state. */
    fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int
}

/** The complete native effect a host supplies. Everything else in this spike is common code. */
interface FolderTreeAccess {
    suspend fun resolve(grant: FolderGrant): FolderGrantState
    fun root(grant: FolderGrant): FolderEntry
    /** One native listing call per directory, returning names, types, sizes and modified times. */
    suspend fun children(grant: FolderGrant, directory: FolderEntry): List<FolderEntry>
    fun open(grant: FolderGrant, file: FolderEntry): LocalByteSource
}

fun LocalByteSource.readFully(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
    var copied = 0
    while (copied < count) {
        val read = read(position + copied, buffer, offset + copied, count - copied)
        if (read <= 0) break
        copied += read
    }
    return copied
}
