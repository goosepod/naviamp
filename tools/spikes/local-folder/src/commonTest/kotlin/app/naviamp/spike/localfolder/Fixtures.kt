package app.naviamp.spike.localfolder

class MemoryByteSource(private val bytes: ByteArray) : LocalByteSource {
    var reads = 0
    override val lengthBytes: Long = bytes.size.toLong()
    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
        reads++
        if (position >= bytes.size) return -1
        val n = minOf(count.toLong(), bytes.size - position).toInt()
        bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + n)
        return n
    }
    override fun close() = Unit
}

class Bytes {
    private val out = mutableListOf<Byte>()
    val size: Int get() = out.size
    fun u8(value: Int) = apply { out += value.toByte() }
    fun bytes(value: ByteArray) = apply { value.forEach { out += it } }
    fun ascii(value: String) = bytes(value.encodeToByteArray())
    fun u16be(value: Int) = u8(value shr 8).u8(value)
    fun u16le(value: Int) = u8(value).u8(value shr 8)
    fun u24be(value: Int) = u8(value shr 16).u8(value shr 8).u8(value)
    fun u32be(value: Long) = u8((value shr 24).toInt()).u8((value shr 16).toInt()).u8((value shr 8).toInt()).u8(value.toInt())
    fun u32le(value: Long) = u8(value.toInt()).u8((value shr 8).toInt()).u8((value shr 16).toInt()).u8((value shr 24).toInt())
    fun syncSafe(value: Int) = u8(value shr 21 and 0x7F).u8(value shr 14 and 0x7F).u8(value shr 7 and 0x7F).u8(value and 0x7F)
    fun zeros(count: Int) = apply { repeat(count) { out += 0 } }
    fun build(): ByteArray = out.toByteArray()
}

fun utf16le(text: String): ByteArray = Bytes().u8(0xFF).u8(0xFE).apply { text.forEach { u16le(it.code) } }.build()

fun id3Frame(version: Int, id: String, body: ByteArray): ByteArray = Bytes().ascii(id).apply {
    when (version) {
        2 -> u24be(body.size)
        3 -> u32be(body.size.toLong()).u16be(0)
        else -> syncSafe(body.size).u16be(0)
    }
}.bytes(body).build()

fun id3Text(text: String, encoding: Int = 3): ByteArray = Bytes().u8(encoding).bytes(
    when (encoding) { 0 -> text.encodeToByteArray(); 1 -> utf16le(text); else -> text.encodeToByteArray() },
).build()

fun id3Tag(version: Int, frames: List<ByteArray>, padding: Int = 64): ByteArray {
    val body = Bytes().apply { frames.forEach(::bytes) }.zeros(padding).build()
    return Bytes().ascii("ID3").u8(version).u8(0).u8(0).syncSafe(body.size).bytes(body).build()
}

/** One MPEG-1 Layer III 128 kbps 44.1 kHz frame carrying a Xing header with [frames] frames. */
fun mpegXingFrame(frames: Long): ByteArray =
    Bytes().u8(0xFF).u8(0xFB).u8(0x90).u8(0x64).zeros(32).ascii("Xing").u32be(1).u32be(frames).zeros(417 - 48).build()

fun apeTag(items: List<Pair<String, String>>): ByteArray {
    val body = Bytes().apply {
        items.forEach { (key, value) ->
            val bytes = value.encodeToByteArray()
            u32le(bytes.size.toLong()).u32le(0).ascii(key).u8(0).bytes(bytes)
        }
    }.build()
    return Bytes().bytes(body).ascii("APETAGEX").u32le(2000).u32le(body.size + 32L).u32le(items.size.toLong())
        .u32le(0).zeros(8).build()
}

fun mp4Atom(type: String, vararg children: ByteArray): ByteArray {
    val body = Bytes().apply { children.forEach(::bytes) }.build()
    return Bytes().u32be(8L + body.size).bytes(ByteArray(4) { type[it].code.toByte() }).bytes(body).build()
}

fun mp4Data(kind: Int, payload: ByteArray) = mp4Atom("data", Bytes().u32be(kind.toLong()).u32be(0).bytes(payload).build())
fun mp4Text(type: String, text: String) = mp4Atom(type, mp4Data(1, text.encodeToByteArray()))
fun mp4FullBox(type: String, body: ByteArray) = mp4Atom(type, Bytes().u32be(0).bytes(body).build())
