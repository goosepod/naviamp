package app.naviamp.android

import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.StoredAudioBytes
import app.naviamp.domain.cache.isNaviampOwnedAudioFileName
import app.naviamp.domain.network.SharedHttpClient
import java.io.File

internal class AndroidAudioFileServices(
    initialAudioCacheDirectory: File,
    initialDownloadDirectory: File,
    httpClient: SharedHttpClient,
) {
    var audioCacheDirectory: File = initialAudioCacheDirectory
        private set
    var downloadDirectory: File = initialDownloadDirectory
        private set

    private val audioCacheByteStore = AndroidMutableAudioByteStore(audioCacheDirectory)
    private val downloadAudioByteStore = AndroidMutableAudioByteStore(downloadDirectory)

    val audioCacheByteStoreService = AudioByteStoreService(
        store = audioCacheByteStore,
        httpClient = httpClient,
    )
    val downloadAudioByteStoreService = AudioByteStoreService(
        store = downloadAudioByteStore,
        httpClient = httpClient,
    )

    fun updateDownloadDirectory(directory: File) {
        directory.mkdirs()
        downloadDirectory = directory
        downloadAudioByteStore.updateDirectory(directory)
    }

    fun updateAudioCacheDirectory(directory: File) {
        directory.mkdirs()
        audioCacheDirectory = directory
        audioCacheByteStore.updateDirectory(directory)
    }

    fun deleteKnownAudioCacheFile(filePath: String): Boolean = deleteKnownRegularFile(audioCacheDirectory, filePath)

    fun deleteKnownDownloadFile(filePath: String): Boolean = deleteKnownRegularFile(downloadDirectory, filePath)

    private fun deleteKnownRegularFile(directory: File, filePath: String): Boolean {
        return runCatching {
            val root = directory.canonicalFile
            val file = File(filePath).canonicalFile
            if (file.parentFile != root || !isNaviampOwnedAudioFileName(file.name)) return@runCatching false
            if (!file.exists()) return@runCatching true
            file.isFile && file.delete()
        }.getOrDefault(false)
    }
}

internal class AndroidAudioByteStore(
    private val directory: File,
) : AudioByteStore {
    override suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes {
        directory.mkdirs()
        val target = File(directory, fileName)
        val temp = File(directory, "${target.name}.tmp")
        return try {
            temp.outputStream().use { output ->
                val writer = AudioByteWriter { bytes, count -> output.write(bytes, 0, count) }
                if (!writeBytes(writer)) throw IllegalStateException(errorMessage)
            }
            moveAudioFile(temp, target)
            StoredAudioBytes(
                filePath = target.absolutePath,
                sizeBytes = target.length(),
            )
        } catch (exception: Exception) {
            temp.delete()
            throw exception
        }
    }

    override fun deleteAudioBytes(filePath: String) {
        runCatching {
            val root = directory.canonicalFile
            File(filePath).canonicalFile.takeIf { file ->
                file.parentFile == root && isNaviampOwnedAudioFileName(file.name) && file.isFile
            }?.delete()
        }
    }
}

internal class AndroidMutableAudioByteStore(initialDirectory: File) : AudioByteStore {
    @Volatile
    private var store = AndroidAudioByteStore(initialDirectory)

    fun updateDirectory(directory: File) {
        store = AndroidAudioByteStore(directory)
    }

    override suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes = store.writeAudioBytes(fileName, errorMessage, writeBytes)

    override fun deleteAudioBytes(filePath: String) = store.deleteAudioBytes(filePath)
}

private fun moveAudioFile(temp: File, target: File) {
    if (!temp.renameTo(target)) {
        temp.copyTo(target, overwrite = true)
        temp.delete()
    }
}
