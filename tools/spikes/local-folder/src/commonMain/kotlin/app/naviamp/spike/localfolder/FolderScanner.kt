package app.naviamp.spike.localfolder

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

val SupportedAudioExtensions = setOf(
    "mp3", "flac", "ogg", "oga", "opus", "m4a", "m4b", "mp4", "aac", "alac", "ape", "wv", "wav", "aif", "aiff",
)

data class ScannedFile(val entry: FolderEntry, val relativePath: String) {
    val fingerprint: FileFingerprint
        get() = FileFingerprint(relativePath, entry.id, entry.sizeBytes, entry.modifiedEpochMillis)
}

data class FolderScan(
    val files: List<ScannedFile>,
    val directories: Int,
    val listingCalls: Int,
    /** Directories that failed to list. A scan with failures must not delete catalog rows. */
    val failedDirectories: List<String>,
) {
    val complete: Boolean get() = failedDirectories.isEmpty()
}

/** Iterative walk so deep trees cannot overflow the stack; ordering is deterministic. */
suspend fun FolderTreeAccess.scan(grant: FolderGrant): FolderScan {
    val files = mutableListOf<ScannedFile>()
    val failures = mutableListOf<String>()
    val pending = ArrayDeque<Pair<FolderEntry, String>>()
    pending.addLast(root(grant) to "")
    var directories = 0
    var calls = 0
    while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val (directory, prefix) = pending.removeLast()
        directories++
        calls++
        val children = runCatching { children(grant, directory) }.getOrElse {
            failures += prefix
            continue
        }
        // Android media scanners honor .nomedia; users expect the same exclusion here.
        if (children.any { !it.isDirectory && it.name == ".nomedia" }) continue
        val subdirectories = mutableListOf<Pair<FolderEntry, String>>()
        children.sortedBy(FolderEntry::name).forEach { child ->
            if (child.name.startsWith('.')) return@forEach
            val path = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            when {
                child.isDirectory -> subdirectories += child to path
                child.name.substringAfterLast('.', "").lowercase() in SupportedAudioExtensions ->
                    files += ScannedFile(child, path)
            }
        }
        subdirectories.asReversed().forEach(pending::addLast)
    }
    return FolderScan(files, directories, calls, failures)
}
