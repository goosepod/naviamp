package app.naviamp.domain.cache

import app.naviamp.domain.TrackId
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.provider.MediaProvider

class AudioByteStoreService(
    private val store: AudioByteStore,
    private val httpClient: SharedHttpClient,
) {
    suspend fun writeProviderAudio(
        sourceId: String,
        trackId: TrackId,
        qualityKey: String,
        contentType: String?,
        provider: MediaProvider,
        streamUrl: String,
        errorMessage: String,
    ): StoredAudioBytes =
        store.writeAudioBytes(
            fileName = stableAudioFileName(sourceId, trackId.value, qualityKey) + contentType.audioExtension(),
            errorMessage = errorMessage,
            writeBytes = { writer ->
                provider.downloadStream(streamUrl, httpClient) { bytes, count ->
                    writer.write(bytes, count)
                }
            },
        ).also { stored ->
            if (stored.sizeBytes <= 0L) {
                store.deleteAudioBytes(stored.filePath)
                throw IllegalStateException(errorMessage)
            }
        }

    fun deleteAudio(filePath: String) {
        store.deleteAudioBytes(filePath)
    }
}

fun stableAudioFileName(sourceId: String, trackId: String, qualityKey: String): String {
    val digest = sha256("$sourceId:$trackId:$qualityKey".encodeToByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }.take(32)
}

fun String?.audioExtension(): String =
    when (this?.lowercase()?.substringBefore(";")?.trim()) {
        "audio/mpeg", "audio/mp3" -> ".mp3"
        "audio/aac", "audio/aacp" -> ".aac"
        "audio/flac", "audio/x-flac" -> ".flac"
        "audio/ogg", "application/ogg" -> ".ogg"
        "audio/opus" -> ".opus"
        "audio/mp4", "audio/m4a", "audio/x-m4a" -> ".m4a"
        "audio/wav", "audio/wave", "audio/x-wav" -> ".wav"
        else -> ".audio"
    }

expect fun sha256(bytes: ByteArray): ByteArray
