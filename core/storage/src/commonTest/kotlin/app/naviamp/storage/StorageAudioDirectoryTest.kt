package app.naviamp.storage

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.StoredAudioBytes
import kotlin.test.*

class StorageAudioDirectoryTest {
    @Test fun containerRelocationPreservesFileNameAndMetadataBeforeNativeUriConversion() {
        val paths = mutableListOf<String>()
        val directory = StorageAudioDirectory("/new container/audio/", NoBytes, { false }, { false }, {
            paths += it
            "file:///new%20container/audio/song.opus"
        })
        val audio = directory.playbackAudio("/old container/audio/song.opus", 42, StreamQuality.Original)
        assertEquals("/new container/audio/song.opus", audio.path)
        assertEquals("file:///new%20container/audio/song.opus", audio.uri)
        assertEquals(42L, audio.sizeBytes)
        assertEquals(StreamQuality.Original, audio.quality)
        assertEquals(listOf(audio.path), paths)
    }

    @Test fun existenceAndDeletionUseTheSameRelocatedPathAsPlayback() {
        val calls = mutableListOf<String>()
        val directory = StorageAudioDirectory("/cache", NoBytes,
            { calls += "exists:$it"; false }, { calls += "delete:$it"; false }, { it })
        val name = "a".repeat(64) + ".flac"
        assertFalse(directory.exists("/old/$name"))
        assertFalse(directory.delete("/old/$name"))
        assertEquals(listOf("exists:/cache/$name", "delete:/cache/$name"), calls)
    }

    @Test fun unknownAndNonChildFilesNeverReachTheDeletionEffect() {
        val directory = StorageAudioDirectory("/cache", NoBytes, { true }, { error("Unsafe delete") }, { it })
        assertFalse(directory.delete("notes.txt"))
        assertFalse(isKnownStoredAudioFilePath("/cache", "/other/" + "a".repeat(64) + ".flac"))
        assertFalse(isKnownStoredAudioFilePath("/cache", "/cache/../" + "a".repeat(64) + ".flac"))
        assertFalse(directory.delete("/old/"))
    }

    @Test fun currentAbsoluteAndRelativePathsResolveWithoutChangingTheFileName() {
        assertEquals("/cache/a.wav", resolveStoredAudioFilePath("/cache/", "/cache/a.wav"))
        assertEquals("/cache/a.wav", resolveStoredAudioFilePath("/cache", "a.wav"))
        assertEquals("", resolveStoredAudioFilePath("/cache", ""))
        assertEquals("/old/", resolveStoredAudioFilePath("/cache", "/old/"))
    }
}

private object NoBytes : AudioByteStore {
    override fun audioFilePath(fileName: String): String = error("Unexpected path")
    override suspend fun writeAudioBytes(temporaryFileName: String, errorMessage: String, writeBytes: suspend (AudioByteWriter) -> String): StoredAudioBytes = error("Unexpected write")
    override fun deleteAudioBytes(filePath: String) = error("Unexpected delete")
}
