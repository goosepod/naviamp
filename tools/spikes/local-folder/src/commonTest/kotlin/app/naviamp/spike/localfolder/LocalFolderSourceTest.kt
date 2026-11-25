package app.naviamp.spike.localfolder

import app.naviamp.domain.playback.BassFileCallbacks
import app.naviamp.spike.localfolder.tags.AudioContainer
import app.naviamp.spike.localfolder.tags.AudioTags
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTree(private val tree: Map<String, List<FolderEntry>>, private val failing: Set<String> = emptySet()) : FolderTreeAccess {
    override suspend fun resolve(grant: FolderGrant) = FolderGrantState.Available(grant)
    override fun root(grant: FolderGrant) = FolderEntry("root", "Music", true, null, null)
    override suspend fun children(grant: FolderGrant, directory: FolderEntry): List<FolderEntry> {
        if (directory.id in failing) error("revoked")
        return tree[directory.id].orEmpty()
    }
    override fun open(grant: FolderGrant, file: FolderEntry): LocalByteSource = MemoryByteSource(ByteArray(0))
}

private fun dir(id: String, name: String = id) = FolderEntry(id, name, true, null, null)
private fun file(id: String, name: String = id, size: Long = 10, modified: Long = 1) = FolderEntry(id, name, false, size, modified)

class LocalFolderSourceTest {
    private val grant = FolderGrant("opaque", "Music")

    @Test
    fun scanFiltersAudioHonorsNomediaAndReportsFailures() = runTest {
        val access = FakeTree(mapOf(
            "root" to listOf(dir("a", "A"), dir("hidden", ".cache"), dir("skip", "Skip"), dir("bad", "Bad"), file("cover", "cover.jpg")),
            "a" to listOf(file("2", "02.flac"), file("1", "01.MP3"), file("notes", "notes.txt")),
            "hidden" to listOf(file("h", "h.mp3")),
            "skip" to listOf(file("nm", ".nomedia"), file("s", "s.mp3")),
        ), failing = setOf("bad"))
        val scan = access.scan(grant)
        assertEquals(listOf("A/01.MP3", "A/02.flac"), scan.files.map(ScannedFile::relativePath))
        assertEquals(listOf("Bad"), scan.failedDirectories)
        assertFalse(scan.complete)
    }

    @Test
    fun rescanKeepsIdentityForChangedAndMovedFiles() {
        val previous = listOf(
            IndexedFile("t1", FileFingerprint("A/01.mp3", "d1", 100, 5)),
            IndexedFile("t2", FileFingerprint("A/02.mp3", "d2", 200, 5)),
            IndexedFile("t3", FileFingerprint("A/03.mp3", "d3", 300, 5)),
            IndexedFile("t4", FileFingerprint("A/04.mp3", "d4", 400, 5)),
        )
        val current = listOf(
            FileFingerprint("A/01.mp3", "d1", 100, 5),
            FileFingerprint("A/02.mp3", "d2", 201, 9),
            FileFingerprint("B/03.mp3", "d9", 300, 5),
            FileFingerprint("A/05.mp3", "d5", 500, 5),
        )
        val plan = planRescan(previous, current)
        assertEquals(listOf("t1"), plan.unchanged.map(IndexedFile::trackId))
        assertEquals(listOf("t2"), plan.changed.map { it.first.trackId })
        assertEquals(listOf("t3" to "B/03.mp3"), plan.moved.map { it.first.trackId to it.second.relativePath })
        assertEquals(listOf("A/05.mp3"), plan.added.map(FileFingerprint::relativePath))
        assertEquals(listOf("t4"), plan.removed.map(IndexedFile::trackId))
        assertEquals(2, plan.filesToRead)
    }

    @Test
    fun unknownTimesForceRereadAndNoMoveGuess() {
        val previous = listOf(IndexedFile("t1", FileFingerprint("a.mp3", "a", 1, null)))
        val plan = planRescan(previous, listOf(FileFingerprint("a.mp3", "a", 1, null), FileFingerprint("b.mp3", "b", 1, null)))
        assertEquals(1, plan.changed.size)
        assertTrue(plan.moved.isEmpty())
    }

    @Test
    fun bassFileCallbacksReadAndSeekThroughLocalSource() {
        val bytes = ByteArray(300_000) { (it * 31).toByte() }
        val callbacks = BassFileCallbacks(LocalFolderRandomAccessSource(MemoryByteSource(bytes)))
        assertEquals(bytes.size.toLong(), callbacks.length())
        val copy = ArrayList<Byte>()
        while (true) {
            val chunk = callbacks.read(100_000)
            if (chunk.isEmpty()) break
            copy.addAll(chunk.toList())
        }
        assertEquals(bytes.toList(), copy)
        assertTrue(callbacks.seek(250_000))
        assertEquals(bytes.copyOfRange(250_000, 250_010).toList(), callbacks.read(10).toList())
        assertFalse(callbacks.seek(bytes.size + 1L))
        callbacks.close()
        assertTrue(callbacks.read(10).isEmpty())
    }

    @Test
    fun mappingFallsBackToFileAndFolderNames() {
        val scanned = ScannedFile(file("x", "07 Untitled.flac"), "Loose Rips/07 Untitled.flac")
        val track = localFolderTrack("source_a", scanned, AudioTags(AudioContainer.Flac, codec = "FLAC", durationMillis = 61_600))
        assertEquals("07 Untitled", track.title)
        assertEquals("Unknown Artist", track.artistName)
        assertEquals("Loose Rips", track.albumTitle)
        assertEquals(62, track.durationSeconds)
        assertNull(track.coverArtId)
        val again = localFolderTrack("source_a", scanned, AudioTags(AudioContainer.Flac))
        assertEquals(track.id, again.id)
        assertNotEquals(track.id, localFolderTrack("source_b", scanned, AudioTags(AudioContainer.Flac)).id)
        assertTrue(track.id.value.startsWith("lf-t-"))
    }
}
