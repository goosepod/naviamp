package app.naviamp.spike.localfolder.tags

private class Atom(val type: String, val start: Long, val headerLength: Int, val size: Long) {
    val bodyStart: Long get() = start + headerLength
    val end: Long get() = start + size
}

private fun atomAt(window: ByteWindow, at: Long, limit: Long): Atom? {
    if (at + 8 > limit) return null
    val header = window.bytes(at, 16)
    if (header.size < 8) return null
    var size = header.u32be(0)
    val type = header.latin1(4, 4)
    var headerLength = 8
    when (size) {
        1L -> { size = header.u64be(8); headerLength = 16 }
        0L -> size = limit - at
    }
    if (size < headerLength || at + size > limit) return null
    return Atom(type, at, headerLength, size)
}

private fun children(window: ByteWindow, parentStart: Long, parentEnd: Long): Sequence<Atom> = sequence {
    var at = parentStart
    while (true) {
        val atom = atomAt(window, at, parentEnd) ?: break
        yield(atom)
        at = atom.end
    }
}

private fun child(window: ByteWindow, parent: Atom, vararg path: String, skip: Int = 0): Atom? {
    var current = parent
    var offset = skip
    for (type in path) {
        current = children(window, current.bodyStart + offset, current.end).firstOrNull { it.type == type } ?: return null
        offset = 0
    }
    return current
}

/**
 * MP4/M4A: walks atom headers only, so a trailing moov after a large mdat costs two header reads.
 * Supports iTunes ilst items, freeform ReplayGain keys and covr artwork.
 */
internal fun readMp4(window: ByteWindow): AudioTags? {
    if (window.ascii(4, 4) != "ftyp") return null
    val moov = children(window, 0, window.length).firstOrNull { it.type == "moov" } ?: return AudioTags(AudioContainer.Mp4)
    val fields = TagFields()
    var duration: Long? = null
    var codec: String? = null
    var rate: Int? = null
    var channels: Int? = null
    var bits: Int? = null
    child(window, moov, "mvhd")?.let { mvhd ->
        val body = window.bytes(mvhd.bodyStart, 32)
        val (timescale, length) = if (body.u8(0) == 1) body.u32be(20) to body.u64be(24) else body.u32be(12) to body.u32be(16)
        if (timescale > 0) duration = length * 1000 / timescale
    }
    children(window, moov.bodyStart, moov.end).filter { it.type == "trak" }.forEach { trak ->
        if (codec != null) return@forEach
        val handler = child(window, trak, "mdia", "hdlr")?.let { window.ascii(it.bodyStart + 8, 4) }
        if (handler != "soun") return@forEach
        val stsd = child(window, trak, "mdia", "minf", "stbl", "stsd") ?: return@forEach
        val entry = atomAt(window, stsd.bodyStart + 8, stsd.end) ?: return@forEach
        val sample = window.bytes(entry.bodyStart, 28)
        codec = when (entry.type) { "mp4a" -> "AAC"; "alac" -> "ALAC"; "Opus" -> "OPUS"; "fLaC" -> "FLAC"; else -> entry.type.uppercase() }
        channels = sample.u16be(16)
        bits = sample.u16be(18)
        rate = (sample.u32be(24) shr 16).toInt()
    }
    var artwork: ArtworkLocation? = null
    val meta = child(window, moov, "udta", "meta")
    if (meta != null) {
        // ISO meta is a full box; some QuickTime writers omit the version/flags word.
        val skip = if (window.ascii(meta.bodyStart + 4, 4) == "hdlr") 0 else 4
        child(window, meta, "ilst", skip = skip)?.let { ilst ->
            children(window, ilst.bodyStart, ilst.end).forEach { item ->
                if (item.type == "covr") {
                    if (artwork == null) children(window, item.bodyStart, item.end).firstOrNull { it.type == "data" }?.let { data ->
                        val kind = window.u32be(data.bodyStart) and 0xFFFFFF
                        artwork = ArtworkLocation(data.bodyStart + 8, data.size - data.headerLength - 8,
                            when (kind) { 13L -> "image/jpeg"; 14L -> "image/png"; else -> null })
                    }
                    return@forEach
                }
                if (item.size > 64 * 1024) return@forEach
                val atoms = children(window, item.bodyStart, item.end).toList()
                val data = atoms.firstOrNull { it.type == "data" } ?: return@forEach
                val payload = window.bytes(data.bodyStart + 8, (data.size - data.headerLength - 8).toInt())
                val text by lazy { payload.utf8() }
                when (item.type) {
                    "©nam" -> fields.put("TITLE", text)
                    "©ART" -> fields.put("ARTIST", text)
                    "aART" -> fields.put("ALBUMARTIST", text)
                    "©alb" -> fields.put("ALBUM", text)
                    "©day" -> fields.put("DATE", text)
                    "©gen" -> fields.put("GENRE", text)
                    "gnre" -> Id3v1Genres.getOrNull(payload.u16be(0) - 1)?.let { fields.put("GENRE", it) }
                    "trkn" -> fields.track = payload.u16be(2).takeIf { it > 0 } to payload.u16be(4).takeIf { it > 0 }
                    "disk" -> fields.disc = payload.u16be(2).takeIf { it > 0 } to payload.u16be(4).takeIf { it > 0 }
                    "----" -> atoms.firstOrNull { it.type == "name" }?.let { name ->
                        fields.put(window.bytes(name.bodyStart + 4, (name.size - name.headerLength - 4).toInt()).utf8(), text)
                    }
                }
            }
        }
    }
    return fields.toTags(AudioContainer.Mp4).copy(
        codec = codec, sampleRateHz = rate, channels = channels, bitsPerSample = bits, durationMillis = duration, artwork = artwork,
    )
}
