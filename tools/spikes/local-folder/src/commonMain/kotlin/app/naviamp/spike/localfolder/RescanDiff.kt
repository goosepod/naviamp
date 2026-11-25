package app.naviamp.spike.localfolder

/**
 * What a manual rescan can compare without opening files. [documentId] is the host's entry ID;
 * on Android's external storage provider it is path-derived, so it changes on rename like the path.
 */
data class FileFingerprint(
    val relativePath: String,
    val documentId: String,
    val sizeBytes: Long?,
    val modifiedEpochMillis: Long?,
)

data class IndexedFile(val trackId: String, val fingerprint: FileFingerprint)

data class RescanPlan(
    val unchanged: List<IndexedFile>,
    /** Same path, different size or time: re-read tags, keep the track ID. */
    val changed: List<Pair<IndexedFile, FileFingerprint>>,
    /** Disappeared from one path and appeared at another with identical name, size and time. */
    val moved: List<Pair<IndexedFile, FileFingerprint>>,
    val added: List<FileFingerprint>,
    val removed: List<IndexedFile>,
) {
    val filesToRead: Int get() = changed.size + added.size
}

fun planRescan(previous: List<IndexedFile>, current: List<FileFingerprint>): RescanPlan {
    val previousByPath = previous.associateBy { it.fingerprint.relativePath }
    val currentPaths = current.mapTo(HashSet()) { it.relativePath }
    val unchanged = mutableListOf<IndexedFile>()
    val changed = mutableListOf<Pair<IndexedFile, FileFingerprint>>()
    val appeared = mutableListOf<FileFingerprint>()
    current.forEach { file ->
        val known = previousByPath[file.relativePath]
        when {
            known == null -> appeared += file
            known.fingerprint.sameContentAs(file) -> unchanged += known
            else -> changed += known to file
        }
    }
    val vanished = previous.filter { it.fingerprint.relativePath !in currentPaths }
    // A move is only trusted when size and time are both known and the pairing is unambiguous.
    val vanishedByKey = vanished.filter { it.fingerprint.moveKey() != null }.groupBy { it.fingerprint.moveKey() }
    val appearedByKey = appeared.filter { it.moveKey() != null }.groupBy { it.moveKey() }
    val moved = vanishedByKey.mapNotNull { (key, sources) ->
        val targets = appearedByKey[key] ?: return@mapNotNull null
        if (sources.size == 1 && targets.size == 1) sources.single() to targets.single() else null
    }
    val movedSources = moved.mapTo(HashSet()) { it.first.trackId }
    val movedTargets = moved.mapTo(HashSet()) { it.second.relativePath }
    return RescanPlan(
        unchanged = unchanged,
        changed = changed,
        moved = moved,
        added = appeared.filter { it.relativePath !in movedTargets },
        removed = vanished.filter { it.trackId !in movedSources },
    )
}

private fun FileFingerprint.sameContentAs(other: FileFingerprint): Boolean =
    sizeBytes != null && modifiedEpochMillis != null &&
        sizeBytes == other.sizeBytes && modifiedEpochMillis == other.modifiedEpochMillis

private fun FileFingerprint.moveKey(): String? {
    val size = sizeBytes ?: return null
    val modified = modifiedEpochMillis ?: return null
    return "$size:$modified:${relativePath.substringAfterLast('/')}"
}
