package com.example.core

/**
 * Generador estándar ISO/IEC 18004 de códigos QR (Versiones 1..10, modo Byte UTF-8, corrección de errores M).
 * Permite dibujar códigos QR nítidos y escaneables en Canvas sin depender de red externa.
 */
data class QrMatrix(
    val size: Int,
    val modules: BooleanArray
) {
    fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrMatrix) return false
        return size == other.size && modules.contentEquals(other.modules)
    }

    override fun hashCode(): Int = 31 * size + modules.contentHashCode()
}

object QrCodeEncoder {

    private data class VersionSpec(
        val version: Int,
        val totalDataCodewords: Int,
        val ecPerBlock: Int,
        val numBlocksGroup1: Int,
        val dataPerBlockGroup1: Int,
        val numBlocksGroup2: Int,
        val dataPerBlockGroup2: Int,
        val alignmentCenters: IntArray
    )

    // ISO/IEC 18004 Table 9 - Error Correction Level M (Versions 1..10)
    private val VERSION_SPECS_M = listOf(
        VersionSpec(1, 16, 10, 1, 16, 0, 0, intArrayOf()),
        VersionSpec(2, 28, 16, 1, 28, 0, 0, intArrayOf(6, 18)),
        VersionSpec(3, 44, 26, 1, 44, 0, 0, intArrayOf(6, 22)),
        VersionSpec(4, 64, 18, 2, 32, 0, 0, intArrayOf(6, 26)),
        VersionSpec(5, 86, 24, 2, 43, 0, 0, intArrayOf(6, 30)),
        VersionSpec(6, 108, 16, 4, 27, 0, 0, intArrayOf(6, 34)),
        VersionSpec(7, 124, 18, 4, 31, 0, 0, intArrayOf(6, 22, 38)),
        VersionSpec(8, 154, 22, 2, 38, 2, 39, intArrayOf(6, 24, 42)),
        VersionSpec(9, 182, 22, 3, 36, 2, 37, intArrayOf(6, 26, 46)),
        VersionSpec(10, 216, 26, 4, 43, 1, 44, intArrayOf(6, 28, 50))
    )

    fun encode(text: String): QrMatrix {
        val rawBytes = text.toByteArray(Charsets.UTF_8)
        val spec = VERSION_SPECS_M.firstOrNull { spec ->
            val ccBits = if (spec.version <= 9) 8 else 16
            val neededBits = 4 + ccBits + rawBytes.size * 8
            neededBits <= spec.totalDataCodewords * 8
        } ?: run {
            val fallbackSpec = VERSION_SPECS_M.last()
            val maxBytes = fallbackSpec.totalDataCodewords - 3
            return encodeBytes(rawBytes.copyOf(maxBytes), fallbackSpec)
        }
        return encodeBytes(rawBytes, spec)
    }

    private fun encodeBytes(payload: ByteArray, spec: VersionSpec): QrMatrix {
        val bb = BitBuffer()
        // Mode indicator: Byte (0100)
        bb.appendBits(0b0100, 4)
        // Character count indicator
        val ccBits = if (spec.version <= 9) 8 else 16
        bb.appendBits(payload.size, ccBits)
        for (b in payload) {
            bb.appendBits(b.toInt() and 0xFF, 8)
        }

        val dataCapacityBits = spec.totalDataCodewords * 8
        val terminatorBits = minOf(4, dataCapacityBits - bb.bitLength)
        bb.appendBits(0, terminatorBits)
        bb.appendBits(0, (8 - (bb.bitLength % 8)) % 8)

        var padByte = 0xEC
        while (bb.bitLength < dataCapacityBits) {
            bb.appendBits(padByte, 8)
            padByte = if (padByte == 0xEC) 0x11 else 0xEC
        }

        val dataCodewords = bb.toByteArray()
        val interleaved = addEccAndInterleave(dataCodewords, spec)
        val size = 17 + 4 * spec.version

        var bestMatrix = BooleanArray(size * size)
        var bestPenalty = Int.MAX_VALUE

        for (mask in 0..7) {
            val modules = BooleanArray(size * size)
            val isFunction = BooleanArray(size * size)
            drawFunctionPatterns(spec, size, modules, isFunction)
            drawCodewords(interleaved, size, modules, isFunction)
            applyMask(mask, size, modules, isFunction)
            drawFormatBits(mask, size, modules)
            if (spec.version >= 7) {
                drawVersionBits(spec.version, size, modules)
            }
            val penalty = computePenalty(size, modules)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMatrix = modules
            }
        }
        return QrMatrix(size = size, modules = bestMatrix)
    }

    private fun addEccAndInterleave(data: ByteArray, spec: VersionSpec): ByteArray {
        val totalBlocks = spec.numBlocksGroup1 + spec.numBlocksGroup2
        val dataBlocks = ArrayList<ByteArray>(totalBlocks)
        val ecBlocks = ArrayList<ByteArray>(totalBlocks)
        val rsDiv = reedSolomonComputeDivisor(spec.ecPerBlock)

        var offset = 0
        for (i in 0 until spec.numBlocksGroup1) {
            val block = data.copyOfRange(offset, offset + spec.dataPerBlockGroup1)
            dataBlocks.add(block)
            ecBlocks.add(reedSolomonComputeRemainder(block, rsDiv))
            offset += spec.dataPerBlockGroup1
        }
        for (i in 0 until spec.numBlocksGroup2) {
            val block = data.copyOfRange(offset, offset + spec.dataPerBlockGroup2)
            dataBlocks.add(block)
            ecBlocks.add(reedSolomonComputeRemainder(block, rsDiv))
            offset += spec.dataPerBlockGroup2
        }

        val totalLen = spec.totalDataCodewords + totalBlocks * spec.ecPerBlock
        val result = ByteArray(totalLen)
        var outIdx = 0

        val maxDataLen = maxOf(spec.dataPerBlockGroup1, spec.dataPerBlockGroup2)
        for (i in 0 until maxDataLen) {
            for (b in dataBlocks) {
                if (i < b.size) result[outIdx++] = b[i]
            }
        }
        for (i in 0 until spec.ecPerBlock) {
            for (ec in ecBlocks) {
                result[outIdx++] = ec[i]
            }
        }
        return result
    }

    private fun reedSolomonComputeDivisor(degree: Int): ByteArray {
        val result = ByteArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = reedSolomonMultiply(result[j].toInt() and 0xFF, root).toByte()
                if (j + 1 < degree) {
                    result[j] = (result[j].toInt() xor (result[j + 1].toInt() and 0xFF)).toByte()
                }
            }
            root = reedSolomonMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonComputeRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
        val result = ByteArray(divisor.size)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor (result[0].toInt() and 0xFF)
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in divisor.indices) {
                result[i] = (
                    (result[i].toInt() and 0xFF) xor
                        reedSolomonMultiply(divisor[i].toInt() and 0xFF, factor)
                    ).toByte()
            }
        }
        return result
    }

    private fun reedSolomonMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun setFunctionModule(
        size: Int,
        modules: BooleanArray,
        isFunction: BooleanArray,
        x: Int,
        y: Int,
        isDark: Boolean
    ) {
        val idx = y * size + x
        modules[idx] = isDark
        isFunction[idx] = true
    }

    private fun drawFunctionPatterns(
        spec: VersionSpec,
        size: Int,
        modules: BooleanArray,
        isFunction: BooleanArray
    ) {
        // Timing patterns
        for (i in 0 until size) {
            setFunctionModule(size, modules, isFunction, 6, i, i % 2 == 0)
            setFunctionModule(size, modules, isFunction, i, 6, i % 2 == 0)
        }

        // Three finder patterns + separators
        drawFinderPattern(size, modules, isFunction, 3, 3)
        drawFinderPattern(size, modules, isFunction, size - 4, 3)
        drawFinderPattern(size, modules, isFunction, 3, size - 4)

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (i in centers.indices) {
            for (j in centers.indices) {
                if ((i == 0 && j == 0) ||
                    (i == 0 && j == centers.lastIndex) ||
                    (i == centers.lastIndex && j == 0)
                ) {
                    continue
                }
                drawAlignmentPattern(size, modules, isFunction, centers[i], centers[j])
            }
        }

        // Reserve format info cells (values filled later per mask)
        for (i in 0..5) setFunctionModule(size, modules, isFunction, 8, i, false)
        setFunctionModule(size, modules, isFunction, 8, 7, false)
        setFunctionModule(size, modules, isFunction, 8, 8, false)
        setFunctionModule(size, modules, isFunction, 7, 8, false)
        for (i in 9 until 15) setFunctionModule(size, modules, isFunction, 14 - i, 8, false)
        for (i in 0 until 8) setFunctionModule(size, modules, isFunction, size - 1 - i, 8, false)
        for (i in 8 until 15) setFunctionModule(size, modules, isFunction, 8, size - 15 + i, false)
        // Dark module
        setFunctionModule(size, modules, isFunction, 8, size - 8, true)

        // Reserve version info cells if version >= 7
        if (spec.version >= 7) {
            for (i in 0 until 18) {
                val a = size - 11 + (i % 3)
                val b = i / 3
                setFunctionModule(size, modules, isFunction, a, b, false)
                setFunctionModule(size, modules, isFunction, b, a, false)
            }
        }
    }

    private fun drawFinderPattern(
        size: Int,
        modules: BooleanArray,
        isFunction: BooleanArray,
        cx: Int,
        cy: Int
    ) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) {
                    val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    setFunctionModule(size, modules, isFunction, x, y, dist != 2 && dist != 4)
                }
            }
        }
    }

    private fun drawAlignmentPattern(
        size: Int,
        modules: BooleanArray,
        isFunction: BooleanArray,
        cx: Int,
        cy: Int
    ) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setFunctionModule(size, modules, isFunction, cx + dx, cy + dy, dist != 1)
            }
        }
    }

    private fun drawCodewords(
        data: ByteArray,
        size: Int,
        modules: BooleanArray,
        isFunction: BooleanArray
    ) {
        var bitIdx = 0
        val totalBits = data.size * 8
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    val idx = y * size + x
                    if (!isFunction[idx] && bitIdx < totalBits) {
                        val byteVal = data[bitIdx ushr 3].toInt() and 0xFF
                        val bit = ((byteVal ushr (7 - (bitIdx and 7))) and 1) != 0
                        modules[idx] = bit
                        bitIdx++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(
        mask: Int,
        size: Int,
        modules: BooleanArray,
        isFunction: BooleanArray
    ) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                if (isFunction[idx]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> (x * y) % 2 + (x * y) % 3 == 0
                    6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                    7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
                    else -> false
                }
                if (invert) modules[idx] = !modules[idx]
            }
        }
    }

    private fun drawFormatBits(mask: Int, size: Int, modules: BooleanArray) {
        // Error Correction Level M = 0
        val data = (0 shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) modules[i * size + 8] = ((bits ushr i) and 1) != 0
        modules[7 * size + 8] = ((bits ushr 6) and 1) != 0
        modules[8 * size + 8] = ((bits ushr 7) and 1) != 0
        modules[8 * size + 7] = ((bits ushr 8) and 1) != 0
        for (i in 9..14) modules[8 * size + (14 - i)] = ((bits ushr i) and 1) != 0

        for (i in 0..7) modules[8 * size + (size - 1 - i)] = ((bits ushr i) and 1) != 0
        for (i in 8..14) modules[(size - 15 + i) * size + 8] = ((bits ushr i) and 1) != 0
    }

    private fun drawVersionBits(version: Int, size: Int, modules: BooleanArray) {
        var rem = version
        repeat(12) {
            rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        }
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val bit = ((bits ushr i) and 1) != 0
            val a = size - 11 + (i % 3)
            val b = i / 3
            modules[b * size + a] = bit
            modules[a * size + b] = bit
        }
    }

    private fun computePenalty(size: Int, modules: BooleanArray): Int {
        var result = 0
        // N1: 5+ consecutive same-color modules in rows and columns
        for (y in 0 until size) {
            var runColor = modules[y * size]
            var runLen = 1
            for (x in 1 until size) {
                val c = modules[y * size + x]
                if (c == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) result += 3 + (runLen - 5)
                    runColor = c
                    runLen = 1
                }
            }
            if (runLen >= 5) result += 3 + (runLen - 5)
        }
        for (x in 0 until size) {
            var runColor = modules[x]
            var runLen = 1
            for (y in 1 until size) {
                val c = modules[y * size + x]
                if (c == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) result += 3 + (runLen - 5)
                    runColor = c
                    runLen = 1
                }
            }
            if (runLen >= 5) result += 3 + (runLen - 5)
        }

        // N2: 2x2 blocks of same color
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y * size + x]
                if (c == modules[y * size + x + 1] &&
                    c == modules[(y + 1) * size + x] &&
                    c == modules[(y + 1) * size + x + 1]
                ) {
                    result += 3
                }
            }
        }

        // N4: dark module ratio deviation from 50%
        var darkCount = 0
        for (m in modules) if (m) darkCount++
        val total = size * size
        val k = kotlin.math.abs(darkCount * 20 - total * 10) / total
        result += k * 10

        return result
    }

    private class BitBuffer {
        private val bytes = ArrayList<Byte>()
        var bitLength: Int = 0
            private set

        fun appendBits(valBits: Int, len: Int) {
            for (i in len - 1 downTo 0) {
                val bit = (valBits ushr i) and 1
                if ((bitLength and 7) == 0) {
                    bytes.add(0)
                }
                if (bit == 1) {
                    val idx = bitLength ushr 3
                    bytes[idx] = (bytes[idx].toInt() or (1 shl (7 - (bitLength and 7)))).toByte()
                }
                bitLength++
            }
        }

        fun toByteArray(): ByteArray = bytes.toByteArray()
    }
}
