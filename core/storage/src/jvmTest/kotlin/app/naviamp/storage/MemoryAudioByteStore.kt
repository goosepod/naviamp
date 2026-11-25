package app.naviamp.storage

import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.StoredAudioBytes

/** In-memory audio directory whose hooks let tests stop a write at each filesystem boundary. */
internal class MemoryAudioByteStore(
    private val directory: String,
    val files: MutableMap<String, ByteArray> = linkedMapOf(),
) : AudioByteStore {
    var beforeRename: suspend () -> Unit = {}
    var afterRename: suspend () -> Unit = {}
    var failWritesAfterBytes: Long? = null

    override fun audioFilePath(fileName: String): String = "$directory/$fileName"

    override suspend fun writeAudioBytes(
        temporaryFileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> String,
    ): StoredAudioBytes {
        val temporary = audioFilePath(temporaryFileName)
        files[temporary] = byteArrayOf()
        try {
            val fileName = writeBytes(AudioByteWriter { bytes, count ->
                val current = files.getValue(temporary)
                failWritesAfterBytes?.let { limit ->
                    if (current.size + count > limit) throw java.io.IOException("No space left on device")
                }
                files[temporary] = current + bytes.copyOf(count)
            })
            beforeRename()
            val target = audioFilePath(fileName)
            files[target] = files.remove(temporary)!!
            afterRename()
            return StoredAudioBytes(target, files.getValue(target).size.toLong())
        } catch (failure: Throwable) {
            files.remove(temporary)
            throw failure
        }
    }

    override fun deleteAudioBytes(filePath: String) {
        files.remove(filePath)
    }

    fun exists(filePath: String): Boolean = filePath in files

    fun delete(filePath: String): Boolean {
        files.remove(filePath)
        return true
    }
}
