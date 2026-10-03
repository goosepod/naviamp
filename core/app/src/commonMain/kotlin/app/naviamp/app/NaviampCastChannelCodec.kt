package app.naviamp.app

import app.naviamp.app.NaviampCastProto.bytes
import app.naviamp.app.NaviampCastProto.number
import app.naviamp.app.NaviampCastProto.string

/** Cast v2.1.0 envelope, inside the transport's four-byte big-endian length prefix. */
data class NaviampCastChannelMessage(
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val text: String? = null,
    val binary: ByteArray? = null,
)

object NaviampCastChannelCodec {
    const val MaximumFrameBytes = 65_536

    fun encode(message: NaviampCastChannelMessage): ByteArray {
        require(message.sourceId.isNotBlank() && message.destinationId.isNotBlank() && message.namespace.isNotBlank())
        require((message.text == null) != (message.binary == null))
        return NaviampCastProto.encode(
            NaviampCastProto.number(1, 0),
            NaviampCastProto.bytes(2, message.sourceId.encodeToByteArray()),
            NaviampCastProto.bytes(3, message.destinationId.encodeToByteArray()),
            NaviampCastProto.bytes(4, message.namespace.encodeToByteArray()),
            NaviampCastProto.number(5, if (message.text != null) 0 else 1),
            NaviampCastProto.bytes(if (message.text != null) 6 else 7,
                message.text?.encodeToByteArray() ?: checkNotNull(message.binary)),
        )
    }

    fun decode(frame: ByteArray): NaviampCastChannelMessage {
        val fields = NaviampCastProto.decode(frame)
        require(fields.number(1) == 0L) { "Unsupported Cast channel version." }
        require(fields.number(8) in listOf(null, 0L)) { "Chunked Cast messages are unsupported." }
        val source = fields.string(2)
        val destination = fields.string(3)
        val namespace = fields.string(4)
        require(source.isNotBlank() && destination.isNotBlank() && namespace.isNotBlank())
        return when (fields.number(5)) {
            0L -> {
                require(fields.bytes(7) == null)
                NaviampCastChannelMessage(source, destination, namespace, text = fields.string(6))
            }
            1L -> {
                require(fields.bytes(6) == null)
                NaviampCastChannelMessage(source, destination, namespace, binary = checkNotNull(fields.bytes(7)))
            }
            else -> error("Unknown Cast payload type.")
        }
    }
}

/** Small bounded protobuf wire reader, shared by channel and device-auth messages. */
internal object NaviampCastProto {
    data class Field(val id: Int, val number: Long? = null, val bytes: ByteArray? = null)
    fun number(id: Int, value: Long) = Field(id, number = value)
    fun bytes(id: Int, value: ByteArray) = Field(id, bytes = value)

    fun encode(vararg fields: Field): ByteArray {
        val output = ArrayList<Byte>()
        fun varint(value: Long) {
            require(value >= 0)
            var remaining = value
            do {
                output += ((remaining and 127) or if (remaining > 127) 128 else 0).toByte()
                remaining = remaining ushr 7
            } while (remaining != 0L)
        }
        fields.forEach { field ->
            require(field.id in 1..0x1fffffff)
            varint((field.id.toLong() shl 3) or if (field.bytes != null) 2 else 0)
            if (field.bytes != null) {
                varint(field.bytes.size.toLong())
                field.bytes.forEach { output += it }
            } else varint(checkNotNull(field.number))
            require(output.size <= NaviampCastChannelCodec.MaximumFrameBytes)
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): List<Field> {
        require(bytes.isNotEmpty() && bytes.size <= NaviampCastChannelCodec.MaximumFrameBytes)
        var offset = 0
        fun varint(): Long {
            var value = 0L
            for (shift in 0..63 step 7) {
                require(offset < bytes.size) { "Truncated protobuf varint." }
                val next = bytes[offset++].toInt() and 255
                require(shift != 63 || next <= 1) { "Overflowing protobuf varint." }
                value = value or ((next and 127).toLong() shl shift)
                if (next < 128) return value
            }
            error("Overflowing protobuf varint.")
        }
        fun take(count: Long): ByteArray {
            require(count >= 0 && count <= bytes.size - offset) { "Truncated protobuf field." }
            val start = offset
            offset += count.toInt()
            return bytes.copyOfRange(start, offset)
        }
        return buildList {
            while (offset < bytes.size) {
                val tag = varint()
                require(tag > 0 && tag ushr 3 <= 0x1fffffff)
                val id = (tag ushr 3).toInt()
                require(id != 0)
                when (tag and 7) {
                    0L -> add(number(id, varint()))
                    2L -> add(bytes(id, take(varint())))
                    1L -> take(8) // Unknown fixed-width fields can be skipped.
                    5L -> take(4)
                    else -> error("Unsupported protobuf wire type.")
                }
            }
        }
    }

    fun List<Field>.bytes(id: Int): ByteArray? {
        val matches = filter { it.id == id }
        require(matches.size <= 1) { "Duplicate singular protobuf field." }
        return matches.singleOrNull()?.let { checkNotNull(it.bytes) }
    }
    fun List<Field>.number(id: Int): Long? {
        val matches = filter { it.id == id }
        require(matches.size <= 1) { "Duplicate singular protobuf field." }
        return matches.singleOrNull()?.let { checkNotNull(it.number) }
    }
    fun List<Field>.string(id: Int): String = checkNotNull(bytes(id)).decodeToString(throwOnInvalidSequence = true)
}
