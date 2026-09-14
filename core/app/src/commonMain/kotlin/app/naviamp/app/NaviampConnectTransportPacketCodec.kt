package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectEnvelope
import app.naviamp.domain.connect.NaviampConnectWireCodec

sealed interface NaviampConnectTransportPacket {
    data class Plaintext(val envelope: NaviampConnectEnvelope) : NaviampConnectTransportPacket
    data class Encrypted(val frame: NaviampConnectEncryptedFrame) : NaviampConnectTransportPacket
}

/** Shared binary packet format layered inside the socket's bounded length-prefixed frames. */
object NaviampConnectTransportPacketCodec {
    fun encode(packet: NaviampConnectTransportPacket): ByteArray = when (packet) {
        is NaviampConnectTransportPacket.Plaintext -> {
            val payload = NaviampConnectWireCodec.encode(packet.envelope).encodeToByteArray()
            byteArrayOf(PlaintextPacket) + payload
        }
        is NaviampConnectTransportPacket.Encrypted -> encodeEncrypted(packet.frame)
    }

    fun decode(bytes: ByteArray): NaviampConnectTransportPacket {
        require(bytes.isNotEmpty()) { "A Connect transport packet cannot be empty." }
        return when (bytes[0]) {
            PlaintextPacket -> {
                require(bytes.size > 1) { "A plaintext packet requires an envelope." }
                val json = bytes.copyOfRange(1, bytes.size)
                    .decodeToString(throwOnInvalidSequence = true)
                NaviampConnectTransportPacket.Plaintext(NaviampConnectWireCodec.decode(json))
            }
            EncryptedPacket -> NaviampConnectTransportPacket.Encrypted(decodeEncrypted(bytes))
            else -> throw IllegalArgumentException("Unknown Naviamp Connect transport packet type.")
        }
    }

    private fun encodeEncrypted(frame: NaviampConnectEncryptedFrame): ByteArray {
        val session = frame.sessionId.encodeToByteArray()
        require(session.size <= MaximumSessionIdBytes) { "The encrypted session ID is too large." }
        val result = ByteArray(1 + IntBytes + ShortBytes + session.size + LongBytes + frame.ciphertext.size)
        var offset = 0
        result[offset++] = EncryptedPacket
        result.writeInt(offset, frame.protocolVersion)
        offset += IntBytes
        result.writeUnsignedShort(offset, session.size)
        offset += ShortBytes
        session.copyInto(result, offset)
        offset += session.size
        result.writeLong(offset, frame.sequence)
        offset += LongBytes
        frame.ciphertext.copyInto(result, offset)
        session.fill(0)
        return result
    }

    private fun decodeEncrypted(bytes: ByteArray): NaviampConnectEncryptedFrame {
        require(bytes.size >= 1 + IntBytes + ShortBytes + LongBytes + 1) {
            "The encrypted packet is truncated."
        }
        var offset = 1
        val protocolVersion = bytes.readInt(offset)
        offset += IntBytes
        val sessionSize = bytes.readUnsignedShort(offset)
        offset += ShortBytes
        require(sessionSize in 1..MaximumSessionIdBytes) { "The encrypted session ID is invalid." }
        require(bytes.size >= offset + sessionSize + LongBytes + 1) { "The encrypted packet is truncated." }
        val sessionId = bytes.copyOfRange(offset, offset + sessionSize)
            .decodeToString(throwOnInvalidSequence = true)
        offset += sessionSize
        val sequence = bytes.readLong(offset)
        offset += LongBytes
        return NaviampConnectEncryptedFrame(
            protocolVersion = protocolVersion,
            sessionId = sessionId,
            sequence = sequence,
            ciphertext = bytes.copyOfRange(offset, bytes.size),
        )
    }

    private fun ByteArray.writeUnsignedShort(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun ByteArray.readUnsignedShort(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)

    private fun ByteArray.writeInt(offset: Int, value: Int) {
        for (index in 0 until IntBytes) this[offset + index] = (value ushr (24 - index * 8)).toByte()
    }

    private fun ByteArray.readInt(offset: Int): Int {
        var result = 0
        for (index in 0 until IntBytes) result = (result shl 8) or (this[offset + index].toInt() and 0xff)
        return result
    }

    private fun ByteArray.writeLong(offset: Int, value: Long) {
        for (index in 0 until LongBytes) this[offset + index] = (value ushr (56 - index * 8)).toByte()
    }

    private fun ByteArray.readLong(offset: Int): Long {
        var result = 0L
        for (index in 0 until LongBytes) result = (result shl 8) or (this[offset + index].toLong() and 0xff)
        return result
    }

    private const val PlaintextPacket: Byte = 1
    private const val EncryptedPacket: Byte = 2
    private const val IntBytes = 4
    private const val ShortBytes = 2
    private const val LongBytes = 8
    private const val MaximumSessionIdBytes = 256
}
