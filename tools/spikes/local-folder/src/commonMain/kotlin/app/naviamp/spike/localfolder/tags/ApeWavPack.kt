package app.naviamp.spike.localfolder.tags

internal class ApeTagResult(val tags: AudioTags, val startOffset: Long)

/** APEv2 (and v1) footer before an optional trailing ID3v1 tag. */
internal fun readApeTag(window: ByteWindow, container: AudioContainer): ApeTagResult? {
    val end = window.length - if (window.length >= 128 && window.ascii(window.length - 128, 3) == "TAG") 128 else 0
    if (end < 32) return null
    val footer = window.bytes(end - 32, 32)
    if (footer.latin1(0, 8) != "APETAGEX") return null
    val size = footer.u32le(12)
    val count = footer.u32le(16)
    val flags = footer.u32le(20)
    if (size < 32 || size > end || count > 10_000) return null
    val itemsStart = end - size
    val headerBytes = if (flags and 0x80000000L != 0L) 32 else 0
    val items = window.bytes(itemsStart, (size - 32).toInt())
    val fields = TagFields()
    var artwork: ArtworkLocation? = null
    var at = 0
    repeat(count.toInt()) {
        if (at + 8 > items.size) return@repeat
        val valueSize = items.u32le(at).toInt()
        val itemFlags = items.u32le(at + 4)
        var keyEnd = at + 8
        while (keyEnd < items.size && items[keyEnd] != 0.toByte()) keyEnd++
        val key = items.latin1(at + 8, keyEnd - at - 8)
        val valueStart = keyEnd + 1
        if (valueStart + valueSize > items.size || valueSize < 0) return@repeat
        val binary = (itemFlags shr 1) and 3L == 1L
        if (binary && key.startsWith("Cover Art", ignoreCase = true)) {
            var nameEnd = valueStart
            while (nameEnd < valueStart + valueSize && items[nameEnd] != 0.toByte()) nameEnd++
            val name = items.latin1(valueStart, nameEnd - valueStart).lowercase()
            val mime = when { name.endsWith(".png") -> "image/png"; name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"; else -> null }
            if (artwork == null || key.contains("Front", ignoreCase = true)) {
                artwork = ArtworkLocation(itemsStart + nameEnd + 1, (valueStart + valueSize - nameEnd - 1).toLong(), mime)
            }
        } else if (!binary) {
            items.utf8(valueStart, valueSize).split('\u0000').forEach { fields.put(key, it) }
        }
        at = valueStart + valueSize
    }
    return ApeTagResult(fields.toTags(container).copy(artwork = artwork), itemsStart - headerBytes)
}

/** Monkey's Audio descriptor and header (format version 3.98 and later). */
internal fun readMonkeysAudio(window: ByteWindow): AudioTags? {
    val head = window.bytes(0, 76)
    if (head.latin1(0, 4) != "MAC ") return null
    val version = head.u16le(4)
    if (version < 3980) return AudioTags(AudioContainer.MonkeysAudio, codec = "APE")
    val descriptorBytes = head.u32le(8)
    val header = window.bytes(descriptorBytes, 24)
    val blocksPerFrame = header.u32le(4)
    val finalFrameBlocks = header.u32le(8)
    val totalFrames = header.u32le(12)
    val bits = header.u16le(16)
    val channels = header.u16le(18)
    val rate = header.u32le(20)
    val samples = if (totalFrames == 0L) 0 else (totalFrames - 1) * blocksPerFrame + finalFrameBlocks
    return AudioTags(
        AudioContainer.MonkeysAudio, codec = "APE", sampleRateHz = rate.toInt(), channels = channels, bitsPerSample = bits,
        durationMillis = if (rate > 0) samples * 1000 / rate else null,
    )
}

private val WavPackRates = intArrayOf(6000, 8000, 9600, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 64000, 88200, 96000, 192000)

internal fun readWavPack(window: ByteWindow): AudioTags? {
    val head = window.bytes(0, 32)
    if (head.latin1(0, 4) != "wvpk") return null
    val totalSamples = head.u32le(12)
    val flags = head.u32le(24)
    val rate = WavPackRates.getOrNull(((flags shr 23) and 0xF).toInt())
    return AudioTags(
        AudioContainer.WavPack, codec = "WV", sampleRateHz = rate,
        channels = if (flags and 4L != 0L) 1 else 2, bitsPerSample = ((flags and 3L) + 1).toInt() * 8,
        durationMillis = if (rate != null && totalSamples != 0xFFFFFFFFL) totalSamples * 1000 / rate else null,
    )
}
