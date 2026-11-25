package app.naviamp.spike.localfolder

import app.naviamp.spike.localfolder.tags.AudioContainer
import app.naviamp.spike.localfolder.tags.readAudioTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagReaderTest {
    private fun read(bytes: ByteArray) = readAudioTags(MemoryByteSource(bytes))

    @Test
    fun id3v24TextReplayGainAndArtworkLocation() {
        val cover = ByteArray(200_000) { it.toByte() }
        val apic = Bytes().u8(0).ascii("image/jpeg").u8(0).u8(3).ascii("front").u8(0).bytes(cover).build()
        val tag = id3Tag(4, listOf(
            id3Frame(4, "TIT2", id3Text("Night River")),
            id3Frame(4, "TPE1", Bytes().bytes(id3Text("One")).u8(0).ascii("Two").build()),
            id3Frame(4, "TALB", id3Text("Signal")),
            id3Frame(4, "TRCK", id3Text("3/12")),
            id3Frame(4, "TPOS", id3Text("2/2")),
            id3Frame(4, "TDRC", id3Text("1999-04-01")),
            id3Frame(4, "TCON", id3Text("(17)")),
            id3Frame(4, "TXXX", Bytes().u8(3).ascii("REPLAYGAIN_TRACK_GAIN").u8(0).ascii("-6.50 dB").build()),
            id3Frame(4, "APIC", apic),
        ))
        val file = tag + mpegXingFrame(100) + ByteArray(2000)
        val result = read(file)
        val tags = result.tags
        assertEquals(AudioContainer.Mp3, tags.container)
        assertEquals("Night River", tags.title)
        assertEquals(listOf("One", "Two"), tags.artists)
        assertEquals("Signal", tags.album)
        assertEquals(3 to 12, tags.trackNumber to tags.trackTotal)
        assertEquals(2, tags.discNumber)
        assertEquals(1999, tags.year)
        assertEquals(listOf("Rock"), tags.genres)
        assertEquals(-6.5, tags.replayGain.trackGainDb)
        assertEquals(100L * 1152 * 1000 / 44100, tags.durationMillis)
        assertEquals(44100, tags.sampleRateHz)
        val artwork = requireNotNull(tags.artwork)
        assertEquals(cover.size.toLong(), artwork.length)
        assertEquals(cover.toList(), file.copyOfRange(artwork.offset.toInt(), (artwork.offset + artwork.length).toInt()).toList())
        assertTrue(result.bytesRead < file.size, "artwork bytes must not be read during the scan")
    }

    @Test
    fun id3v23Utf16AndId3v22() {
        val v23 = id3Tag(3, listOf(id3Frame(3, "TIT2", id3Text("été 音楽", encoding = 1))))
        assertEquals("été 音楽", read(v23 + mpegXingFrame(10)).tags.title)
        val v22 = id3Tag(2, listOf(id3Frame(2, "TT2", id3Text("Old", 0)), id3Frame(2, "TP1", id3Text("Writer", 0))))
        val tags = read(v22 + mpegXingFrame(10)).tags
        assertEquals("Old", tags.title)
        assertEquals(listOf("Writer"), tags.artists)
    }

    @Test
    fun mp3gainApeTagAndId3v1Fallback() {
        val id3v1 = Bytes().ascii("TAG").ascii("Legacy".padEnd(30, '\u0000')).ascii("Old Artist".padEnd(30, '\u0000'))
            .ascii("".padEnd(30, '\u0000')).ascii("1984").zeros(28).u8(0).u8(7).u8(8).build()
        val file = mpegXingFrame(50) + apeTag(listOf("REPLAYGAIN_TRACK_GAIN" to "-3.21 dB", "REPLAYGAIN_ALBUM_GAIN" to "-4 dB")) + id3v1
        val tags = read(file).tags
        assertEquals("Legacy", tags.title)
        assertEquals(listOf("Old Artist"), tags.artists)
        assertEquals(7, tags.trackNumber)
        assertEquals(listOf("Jazz"), tags.genres)
        assertEquals(-3.21, tags.replayGain.trackGainDb)
        assertEquals(-4.0, tags.replayGain.albumGainDb)
    }

    @Test
    fun monkeysAudioWithApeTag() {
        val descriptor = Bytes().ascii("MAC ").u16le(3990).u16le(0).u32le(52).u32le(24).zeros(52 - 16).build()
        val header = Bytes().u16le(2000).u16le(0).u32le(73728).u32le(1000).u32le(11).u16le(16).u16le(2).u32le(44100).build()
        val file = descriptor + header + ByteArray(1000) + apeTag(listOf("Title" to "Lossless", "Artist" to "Band", "Track" to "4"))
        val tags = read(file).tags
        assertEquals(AudioContainer.MonkeysAudio, tags.container)
        assertEquals("Lossless", tags.title)
        assertEquals(4, tags.trackNumber)
        assertEquals((10L * 73728 + 1000) * 1000 / 44100, tags.durationMillis)
    }

    @Test
    fun mp4FreeformReplayGainAndCover() {
        val cover = ByteArray(3000) { 7 }
        val ilst = mp4Atom("ilst",
            mp4Text("©nam", "Harbor"), mp4Text("©ART", "Tide"), mp4Text("©day", "2004-01-01"),
            mp4Atom("trkn", mp4Data(0, Bytes().u16be(0).u16be(5).u16be(9).u16be(0).build())),
            mp4Atom("----", mp4FullBox("mean", "com.apple.iTunes".encodeToByteArray()),
                mp4FullBox("name", "replaygain_track_gain".encodeToByteArray()), mp4Data(1, "-2.5 dB".encodeToByteArray())),
            mp4Atom("covr", mp4Data(13, cover)),
        )
        val mvhd = mp4FullBox("mvhd", Bytes().u32be(0).u32be(0).u32be(1000).u32be(185_000).zeros(80).build())
        val moov = mp4Atom("moov", mvhd, mp4Atom("udta", mp4FullBox("meta", mp4FullBox("hdlr", ByteArray(20)) + ilst)))
        val ftyp = mp4Atom("ftyp", "M4A ".encodeToByteArray() + ByteArray(4))
        val mdat = mp4Atom("mdat", ByteArray(200_000))
        val file = ftyp + mdat + moov
        val result = read(file)
        val tags = result.tags
        assertEquals("Harbor", tags.title)
        assertEquals(5 to 9, tags.trackNumber to tags.trackTotal)
        assertEquals(2004, tags.year)
        assertEquals(-2.5, tags.replayGain.trackGainDb)
        assertEquals(185_000L, tags.durationMillis)
        assertEquals(cover.size.toLong(), tags.artwork?.length)
        assertTrue(result.bytesRead < 100_000, "a trailing moov must not read mdat: ${result.bytesRead}")
    }

    @Test
    fun flacWithLeadingId3UsesNativeComments() {
        val comment = Bytes().u32le(4).ascii("test").u32le(2)
            .u32le("TITLE=Native".length.toLong()).ascii("TITLE=Native")
            .u32le("ARTIST=Flac".length.toLong()).ascii("ARTIST=Flac").build()
        val info = Bytes().zeros(10).u32be((44100L shl 12) or (1L shl 9) or (15L shl 4)).u32be(88200).zeros(16).build()
        val flac = Bytes().ascii("fLaC").u8(0).u24be(34).bytes(info).u8(0x84).u24be(comment.size).bytes(comment).build()
        val file = id3Tag(3, listOf(id3Frame(3, "TIT2", id3Text("Prefixed")))) + flac
        val tags = read(file).tags
        assertEquals(AudioContainer.Flac, tags.container)
        assertEquals("Native", tags.title)
        assertEquals(listOf("Flac"), tags.artists)
        assertEquals(44100, tags.sampleRateHz)
        assertEquals(2, tags.channels)
        assertEquals(16, tags.bitsPerSample)
    }

    @Test
    fun unknownBytesAreNotFatal() {
        val tags = read(ByteArray(4096) { 0x55 }).tags
        assertEquals(AudioContainer.Unknown, tags.container)
        assertNull(tags.title)
    }
}
