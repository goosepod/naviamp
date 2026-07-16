@file:OptIn(ExperimentalUnsignedTypes::class)

package app.naviamp.provider.navidrome

private val Md5ShiftAmounts = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
)

private val Md5Constants = uintArrayOf(
    0xd76aa478u, 0xe8c7b756u, 0x242070dbu, 0xc1bdceeeu,
    0xf57c0fafu, 0x4787c62au, 0xa8304613u, 0xfd469501u,
    0x698098d8u, 0x8b44f7afu, 0xffff5bb1u, 0x895cd7beu,
    0x6b901122u, 0xfd987193u, 0xa679438eu, 0x49b40821u,
    0xf61e2562u, 0xc040b340u, 0x265e5a51u, 0xe9b6c7aau,
    0xd62f105du, 0x02441453u, 0xd8a1e681u, 0xe7d3fbc8u,
    0x21e1cde6u, 0xc33707d6u, 0xf4d50d87u, 0x455a14edu,
    0xa9e3e905u, 0xfcefa3f8u, 0x676f02d9u, 0x8d2a4c8au,
    0xfffa3942u, 0x8771f681u, 0x6d9d6122u, 0xfde5380cu,
    0xa4beea44u, 0x4bdecfa9u, 0xf6bb4b60u, 0xbebfbc70u,
    0x289b7ec6u, 0xeaa127fau, 0xd4ef3085u, 0x04881d05u,
    0xd9d4d039u, 0xe6db99e5u, 0x1fa27cf8u, 0xc4ac5665u,
    0xf4292244u, 0x432aff97u, 0xab9423a7u, 0xfc93a039u,
    0x655b59c3u, 0x8f0ccc92u, 0xffeff47du, 0x85845dd1u,
    0x6fa87e4fu, 0xfe2ce6e0u, 0xa3014314u, 0x4e0811a1u,
    0xf7537e82u, 0xbd3af235u, 0x2ad7d2bbu, 0xeb86d391u,
)

internal fun md5Hex(input: ByteArray): String {
    val paddedSize = ((input.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedSize)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    val bitLength = input.size.toULong() * 8u
    repeat(8) { index ->
        padded[paddedSize - 8 + index] = (bitLength shr (index * 8)).toByte()
    }

    var a0 = 0x67452301u
    var b0 = 0xefcdab89u
    var c0 = 0x98badcfeu
    var d0 = 0x10325476u
    val words = UIntArray(16)

    for (offset in padded.indices step 64) {
        for (index in words.indices) {
            val byteOffset = offset + index * 4
            words[index] =
                padded[byteOffset].toUByte().toUInt() or
                    (padded[byteOffset + 1].toUByte().toUInt() shl 8) or
                    (padded[byteOffset + 2].toUByte().toUInt() shl 16) or
                    (padded[byteOffset + 3].toUByte().toUInt() shl 24)
        }

        var a = a0
        var b = b0
        var c = c0
        var d = d0
        repeat(64) { index ->
            val (mixed, wordIndex) = when (index) {
                in 0..15 -> ((b and c) or (b.inv() and d)) to index
                in 16..31 -> ((d and b) or (d.inv() and c)) to ((5 * index + 1) % 16)
                in 32..47 -> (b xor c xor d) to ((3 * index + 5) % 16)
                else -> (c xor (b or d.inv())) to ((7 * index) % 16)
            }
            val previousD = d
            d = c
            c = b
            b += (a + mixed + Md5Constants[index] + words[wordIndex]).rotateLeft(Md5ShiftAmounts[index])
            a = previousD
        }
        a0 += a
        b0 += b
        c0 += c
        d0 += d
    }

    val hex = "0123456789abcdef"
    return buildString(32) {
        listOf(a0, b0, c0, d0).forEach { word ->
            repeat(4) { byteIndex ->
                val value = ((word shr (byteIndex * 8)) and 0xffu).toInt()
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}

private fun UInt.rotateLeft(bitCount: Int): UInt =
    (this shl bitCount) or (this shr (32 - bitCount))
