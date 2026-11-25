package app.naviamp.spike.localfolder.tags

import app.naviamp.spike.localfolder.LocalByteSource
import app.naviamp.spike.localfolder.readFully

/**
 * A small read-through cache over a [LocalByteSource]. Tag parsers alternate between the head and
 * the tail of a file, so a few aligned chunks keep native read calls (binder or syscall round trips)
 * and repeated bytes low.
 */
class ByteWindow(private val source: LocalByteSource, private val chunkSize: Int = 32 * 1024, slots: Int = 3) {
    val length: Long = source.lengthBytes
    private class Chunk(val bytes: ByteArray, var start: Long = -1, var length: Int = 0, var used: Long = 0)
    private val chunks = List(slots) { Chunk(ByteArray(chunkSize)) }
    private var clock = 0L
    var nativeReads = 0
        private set
    var bytesRead = 0L
        private set

    fun bytes(position: Long, count: Int): ByteArray {
        if (position < 0 || position >= length || count <= 0) return ByteArray(0)
        val available = minOf(count.toLong(), length - position).toInt()
        val result = ByteArray(available)
        if (available > chunkSize) {
            nativeRead(position, result, available)
            return result
        }
        var copied = 0
        while (copied < available) {
            val at = position + copied
            val chunk = chunks.firstOrNull { it.start >= 0 && at >= it.start && at < it.start + it.length }
                ?: chunks.minBy(Chunk::used).also { victim ->
                    victim.start = at
                    victim.length = nativeRead(at, victim.bytes, minOf(chunkSize.toLong(), length - at).toInt())
                    if (victim.length <= 0) victim.start = -1
                }
            if (chunk.start < 0) break
            chunk.used = ++clock
            val from = (at - chunk.start).toInt()
            val run = minOf(available - copied, chunk.length - from)
            chunk.bytes.copyInto(result, copied, from, from + run)
            copied += run
        }
        return if (copied == available) result else result.copyOf(copied)
    }

    fun u8(position: Long): Int = bytes(position, 1).firstOrNull()?.toInt()?.and(0xFF) ?: -1
    fun u32be(position: Long): Long = bytes(position, 4).u32be(0)
    fun u32le(position: Long): Long = bytes(position, 4).u32le(0)
    fun ascii(position: Long, count: Int): String = bytes(position, count).latin1(0, count)

    private fun nativeRead(position: Long, into: ByteArray, count: Int): Int {
        nativeReads++
        return source.readFully(position, into, 0, count).also { bytesRead += it }
    }
}

internal fun ByteArray.u8(at: Int): Int = if (at in indices) this[at].toInt() and 0xFF else 0
internal fun ByteArray.u16be(at: Int): Int = (u8(at) shl 8) or u8(at + 1)
internal fun ByteArray.u16le(at: Int): Int = u8(at) or (u8(at + 1) shl 8)
internal fun ByteArray.u24be(at: Int): Int = (u8(at) shl 16) or (u8(at + 1) shl 8) or u8(at + 2)
internal fun ByteArray.u32be(at: Int): Long =
    (u8(at).toLong() shl 24) or (u8(at + 1).toLong() shl 16) or (u8(at + 2).toLong() shl 8) or u8(at + 3).toLong()
internal fun ByteArray.u32le(at: Int): Long =
    u8(at).toLong() or (u8(at + 1).toLong() shl 8) or (u8(at + 2).toLong() shl 16) or (u8(at + 3).toLong() shl 24)
internal fun ByteArray.u64be(at: Int): Long = (u32be(at) shl 32) or u32be(at + 4)
internal fun ByteArray.u64le(at: Int): Long = u32le(at) or (u32le(at + 4) shl 32)
internal fun ByteArray.syncSafe(at: Int): Int =
    (u8(at) and 0x7F shl 21) or (u8(at + 1) and 0x7F shl 14) or (u8(at + 2) and 0x7F shl 7) or (u8(at + 3) and 0x7F)

internal fun ByteArray.latin1(from: Int, count: Int): String {
    val end = minOf(size, from + count)
    if (from >= end) return ""
    return CharArray(end - from) { (this[from + it].toInt() and 0xFF).toChar() }.concatToString()
}

internal fun ByteArray.utf8(from: Int = 0, count: Int = size - from): String {
    val end = minOf(size, from + count)
    return if (from >= end) "" else decodeToString(from, end)
}

internal fun ByteArray.utf16(from: Int, count: Int, bigEndianDefault: Boolean): String {
    var at = from
    val end = minOf(size, from + count)
    var bigEndian = bigEndianDefault
    if (end - at >= 2) {
        val bom = u16be(at)
        if (bom == 0xFEFF) { bigEndian = true; at += 2 } else if (bom == 0xFFFE) { bigEndian = false; at += 2 }
    }
    val builder = StringBuilder((end - at) / 2)
    while (at + 1 < end) {
        builder.append((if (bigEndian) u16be(at) else u16le(at)).toChar())
        at += 2
    }
    return builder.toString()
}
