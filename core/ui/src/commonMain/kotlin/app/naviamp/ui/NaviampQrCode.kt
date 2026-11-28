package app.naviamp.ui

/**
 * A QR Code Model 2 symbol (ISO/IEC 18004) in byte mode with error-correction level M (#715).
 * Pure shared code, so every platform renders the same Connect link without a native dependency.
 */
class NaviampQrCode private constructor(
    /** Modules per side, excluding the quiet zone. */
    val size: Int,
    private val modules: BooleanArray,
) {
    /** True for a dark module at column [x], row [y]. */
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    companion object {
        /** Encodes [text] as UTF-8, choosing the smallest version that holds it; null when too long. */
        fun encode(text: String): NaviampQrCode? {
            val data = text.encodeToByteArray()
            val version = (1..40).firstOrNull { dataCapacityBits(it) >= segmentBits(it, data.size) } ?: return null
            val codewords = dataCodewords(version, data)
            val builder = Builder(version)
            builder.drawFunctionPatterns()
            builder.drawCodewords(addErrorCorrectionAndInterleave(version, codewords))
            val mask = (0 until 8).minBy { candidate ->
                builder.applyMask(candidate)
                builder.drawFormatBits(candidate)
                builder.penalty().also { builder.applyMask(candidate) }
            }
            builder.applyMask(mask)
            builder.drawFormatBits(mask)
            return NaviampQrCode(builder.size, builder.modules)
        }

        private fun characterCountBits(version: Int) = if (version <= 9) 8 else 16

        private fun segmentBits(version: Int, bytes: Int): Int =
            if (bytes >= (1 shl characterCountBits(version))) Int.MAX_VALUE else 4 + characterCountBits(version) + bytes * 8

        private fun dataCapacityBits(version: Int): Int = dataCodewordCount(version) * 8

        private fun dataCodewordCount(version: Int): Int =
            rawDataModules(version) / 8 - EccCodewordsPerBlockM[version] * EccBlocksM[version]

        private fun rawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val alignments = version / 7 + 2
                result -= (25 * alignments - 10) * alignments - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        private fun dataCodewords(version: Int, data: ByteArray): IntArray {
            val bits = BitBuffer()
            bits.append(0b0100, 4)
            bits.append(data.size, characterCountBits(version))
            data.forEach { bits.append(it.toInt() and 0xff, 8) }
            val capacity = dataCapacityBits(version)
            bits.append(0, minOf(4, capacity - bits.size))
            bits.append(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capacity) {
                bits.append(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }
            return IntArray(bits.size / 8) { index ->
                (0 until 8).fold(0) { byte, bit -> (byte shl 1) or if (bits[index * 8 + bit]) 1 else 0 }
            }
        }

        private fun addErrorCorrectionAndInterleave(version: Int, data: IntArray): IntArray {
            val blockCount = EccBlocksM[version]
            val eccLength = EccCodewordsPerBlockM[version]
            val rawCodewords = rawDataModules(version) / 8
            val shortBlocks = blockCount - rawCodewords % blockCount
            val shortBlockLength = rawCodewords / blockCount
            val divisor = reedSolomonDivisor(eccLength)
            val blocks = ArrayList<IntArray>(blockCount)
            var offset = 0
            for (index in 0 until blockCount) {
                val dataLength = shortBlockLength - eccLength + if (index < shortBlocks) 0 else 1
                val block = data.copyOfRange(offset, offset + dataLength)
                offset += dataLength
                val ecc = reedSolomonRemainder(block, divisor)
                // Short blocks carry a placeholder so every block indexes alike while interleaving.
                val padded = if (index < shortBlocks) block + intArrayOf(0) else block
                blocks += padded + ecc
            }
            val result = IntArray(rawCodewords)
            var next = 0
            for (column in blocks[0].indices) {
                blocks.forEachIndexed { index, block ->
                    if (column != shortBlockLength - eccLength || index >= shortBlocks) result[next++] = block[column]
                }
            }
            return result
        }

        private fun reedSolomonDivisor(degree: Int): IntArray {
            val result = IntArray(degree).also { it[degree - 1] = 1 }
            var root = 1
            repeat(degree) {
                for (j in result.indices) {
                    result[j] = gfMultiply(result[j], root)
                    if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
                }
                root = gfMultiply(root, 0x02)
            }
            return result
        }

        private fun reedSolomonRemainder(data: IntArray, divisor: IntArray): IntArray {
            val result = IntArray(divisor.size)
            for (value in data) {
                val factor = value xor result[0]
                result.copyInto(result, 0, 1)
                result[result.size - 1] = 0
                for (index in result.indices) result[index] = result[index] xor gfMultiply(divisor[index], factor)
            }
            return result
        }

        private fun gfMultiply(x: Int, y: Int): Int {
            var z = 0
            for (bit in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr bit) and 1) * x)
            }
            return z
        }

        private val EccCodewordsPerBlockM = intArrayOf(
            -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
            26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
        )
        private val EccBlocksM = intArrayOf(
            -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
            17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49,
        )
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val size: Int get() = bits.size
        operator fun get(index: Int) = bits[index]
        fun append(value: Int, length: Int) {
            for (bit in length - 1 downTo 0) bits += ((value ushr bit) and 1) != 0
        }
    }

    private class Builder(private val version: Int) {
        val size = version * 4 + 17
        val modules = BooleanArray(size * size)
        private val function = BooleanArray(size * size)

        private fun set(x: Int, y: Int, dark: Boolean) { modules[y * size + x] = dark }
        private fun setFunction(x: Int, y: Int, dark: Boolean) {
            set(x, y, dark)
            function[y * size + x] = true
        }

        fun drawFunctionPatterns() {
            for (index in 0 until size) {
                setFunction(6, index, index % 2 == 0)
                setFunction(index, 6, index % 2 == 0)
            }
            drawFinder(3, 3)
            drawFinder(size - 4, 3)
            drawFinder(3, size - 4)
            val positions = alignmentPositions()
            val last = positions.size - 1
            for (i in positions.indices) for (j in positions.indices) {
                if (i == 0 && j == 0 || i == 0 && j == last || i == last && j == 0) continue
                drawAlignment(positions[i], positions[j])
            }
            drawFormatBits(0)
            drawVersion()
        }

        private fun drawFinder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) setFunction(x, y, distance != 2 && distance != 4)
            }
        }

        private fun drawAlignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) {
                setFunction(cx + dx, cy + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = if (version == 32) 26 else (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
            val result = IntArray(count)
            result[0] = 6
            var position = size - 7
            for (index in count - 1 downTo 1) {
                result[index] = position
                position -= step
            }
            return result
        }

        /** Level M's format indicator is 0b00. */
        fun drawFormatBits(mask: Int) {
            val data = mask
            var remainder = data
            repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
            val bits = ((data shl 10) or remainder) xor 0x5412
            fun bit(index: Int) = ((bits ushr index) and 1) != 0
            for (index in 0..5) setFunction(8, index, bit(index))
            setFunction(8, 7, bit(6))
            setFunction(8, 8, bit(7))
            setFunction(7, 8, bit(8))
            for (index in 9 until 15) setFunction(14 - index, 8, bit(index))
            for (index in 0 until 8) setFunction(size - 1 - index, 8, bit(index))
            for (index in 8 until 15) setFunction(8, size - 15 + index, bit(index))
            setFunction(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var remainder = version
            repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
            val bits = (version shl 12) or remainder
            for (index in 0 until 18) {
                val dark = ((bits ushr index) and 1) != 0
                val a = size - 11 + index % 3
                val b = index / 3
                setFunction(a, b, dark)
                setFunction(b, a, dark)
            }
        }

        fun drawCodewords(data: IntArray) {
            var index = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vertical in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vertical else vertical
                        if (!function[y * size + x] && index < data.size * 8) {
                            set(x, y, ((data[index ushr 3] ushr (7 - (index and 7))) and 1) != 0)
                            index++
                        }
                    }
                }
                right -= 2
            }
        }

        /** Applying the same mask twice restores the symbol. */
        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                val cell = y * size + x
                if (invert && !function[cell]) modules[cell] = !modules[cell]
            }
        }

        fun penalty(): Int {
            var result = 0
            fun dark(x: Int, y: Int) = modules[y * size + x]
            for (line in 0 until size) {
                result += runPenalty { dark(it, line) } + runPenalty { dark(line, it) }
                result += finderLikePenalty { dark(it, line) } + finderLikePenalty { dark(line, it) }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val color = dark(x, y)
                if (color == dark(x + 1, y) && color == dark(x, y + 1) && color == dark(x + 1, y + 1)) result += 3
            }
            val total = size * size
            val darkCount = modules.count { it }
            val k = (kotlin.math.abs(darkCount * 20 - total * 10) + total - 1) / total - 1
            return result + k * 10
        }

        private inline fun runPenalty(at: (Int) -> Boolean): Int {
            var result = 0
            var run = 1
            for (index in 1 until size) {
                if (at(index) == at(index - 1)) {
                    run++
                } else {
                    if (run >= 5) result += 3 + run - 5
                    run = 1
                }
            }
            if (run >= 5) result += 3 + run - 5
            return result
        }

        /** A 1:1:3:1:1 finder-like run with four light modules on one side (outside counts as light). */
        private inline fun finderLikePenalty(at: (Int) -> Boolean): Int {
            var result = 0
            for (start in -4 until size) {
                val matches = FinderLike.any { pattern ->
                    pattern.indices.all { offset ->
                        val index = start + offset
                        (index in 0 until size && at(index)) == pattern[offset]
                    }
                }
                if (matches) result += 40
            }
            return result
        }
    }
}

private val FinderLike = listOf(
    booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false),
    booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true),
)
