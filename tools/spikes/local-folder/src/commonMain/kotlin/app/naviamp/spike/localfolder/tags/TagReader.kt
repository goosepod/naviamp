package app.naviamp.spike.localfolder.tags

import app.naviamp.spike.localfolder.LocalByteSource

data class TagReadResult(val tags: AudioTags, val nativeReads: Int, val bytesRead: Long)

/** Detects the container from content, not the extension, then reads only header and tail regions. */
fun readAudioTags(source: LocalByteSource, chunkSize: Int = 32 * 1024): TagReadResult {
    val window = ByteWindow(source, chunkSize)
    val tags = runCatching { detectAndRead(window) }.getOrNull() ?: AudioTags(AudioContainer.Unknown)
    return TagReadResult(tags, window.nativeReads, window.bytesRead)
}

private fun detectAndRead(window: ByteWindow): AudioTags {
    val magic = window.bytes(0, 12)
    return when {
        magic.latin1(4, 4) == "ftyp" -> readMp4(window)
        magic.latin1(0, 4) == "fLaC" -> readFlac(window, 0)
        magic.latin1(0, 4) == "OggS" -> readOgg(window)
        magic.latin1(0, 4) == "MAC " -> readMonkeysAudio(window)?.withFallback(readApeTag(window, AudioContainer.MonkeysAudio)?.tags)
        magic.latin1(0, 4) == "wvpk" -> readWavPack(window)?.withFallback(readApeTag(window, AudioContainer.WavPack)?.tags)
        magic.latin1(0, 3) == "ID3" -> readId3Prefixed(window)
        else -> readMpegWithTrailingTags(window, AudioTags(AudioContainer.Mp3), 0)
    } ?: AudioTags(AudioContainer.Unknown)
}

private fun readId3Prefixed(window: ByteWindow): AudioTags? {
    val id3 = readId3v2(window) ?: return null
    // Some taggers prepend ID3v2 to FLAC; the native FLAC metadata wins.
    if (window.ascii(id3.endOffset, 4) == "fLaC") return readFlac(window, id3.endOffset)?.withFallback(id3.tags)
    return readMpegWithTrailingTags(window, id3.tags, id3.endOffset, id3.lengthMillis)
}

private fun readMpegWithTrailingTags(window: ByteWindow, primary: AudioTags, audioStart: Long, lengthMillis: Long? = null): AudioTags? {
    val ape = readApeTag(window, AudioContainer.Mp3)
    val id3v1 = readId3v1(window)
    val audioEnd = ape?.startOffset ?: (window.length - if (id3v1 != null) 128 else 0)
    val mpeg = readMpegInfo(window, audioStart, audioEnd) ?: return if (primary.title != null) primary else null
    return primary.withFallback(ape?.tags).withFallback(id3v1).copy(
        container = AudioContainer.Mp3,
        codec = mpeg.codec,
        sampleRateHz = mpeg.sampleRateHz,
        channels = mpeg.channels,
        durationMillis = mpeg.durationMillis ?: lengthMillis,
    )
}
