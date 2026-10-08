package br.com.leafcare.ml

import kotlin.math.abs
import kotlin.math.max

/** Resize bicúbico antialias uint8 e crop do aluno, equivalente ao Pillow 10.4. */
object DistilledPreprocessor {
    private const val SIZE = 224
    private const val SHORT_SIDE = 256L
    private const val PRECISION = 22
    private const val UNIT = 1 shl PRECISION
    private data class Kernel(val start: Int, val weights: IntArray)

    private fun cubic(value: Double): Double {
        val x = abs(value)
        return when {
            x < 1.0 -> ((1.5 * x - 2.5) * x * x + 1.0)
            x < 2.0 -> (((x - 5.0) * x + 8.0) * x - 4.0) * -0.5
            else -> 0.0
        }
    }

    private fun kernels(input: Int, output: Long, offset: Long): Array<Kernel> {
        val scale = input.toDouble() / output
        val filterScale = max(1.0, scale)
        val support = 2.0 * filterScale
        val inverse = 1.0 / filterScale
        return Array(SIZE) { index ->
            val center = (offset + index + 0.5) * scale
            val start = (center - support + 0.5).toInt().coerceAtLeast(0)
            val end = (center + support + 0.5).toInt().coerceAtMost(input)
            val raw = DoubleArray(end - start) { cubic((it + start - center + 0.5) * inverse) }
            val total = raw.sum()
            val weights = IntArray(raw.size) {
                val scaled = raw[it] / total * UNIT
                (scaled + if (scaled < 0.0) -0.5 else 0.5).toInt()
            }
            Kernel(start, weights)
        }
    }

    fun toRgb(pixels: IntArray, width: Int, height: Int): FloatArray {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height)
        val short = minOf(width, height)
        val resizedWidth = SHORT_SIDE * width / short
        val resizedHeight = SHORT_SIDE * height / short
        // torchvision CenterCrop usa round ties-to-even quando a diferença é ímpar.
        val left = Math.rint((resizedWidth - SIZE) / 2.0).toLong()
        val top = Math.rint((resizedHeight - SIZE) / 2.0).toLong()
        val xKernels = kernels(width, resizedWidth, left)
        val yKernels = kernels(height, resizedHeight, top)
        val firstRow = yKernels.first().start
        val lastRow = yKernels.last().let { it.start + it.weights.size }
        // Apenas a faixa necessária ao crop; evita materializar imagens panorâmicas enormes.
        val horizontal = IntArray((lastRow - firstRow) * SIZE)
        for (y in firstRow until lastRow) {
            for (x in 0 until SIZE) {
                val kernel = xKernels[x]
                var packed = 0
                for (channel in 0..2) {
                    val shift = (2 - channel) * 8
                    var sum = (UNIT / 2).toLong()
                    for (tap in kernel.weights.indices) {
                        val pixel = pixels[y * width + kernel.start + tap]
                        val alpha = (pixel ushr 24) and 255
                        val component = (pixel ushr shift) and 255
                        val rgb = (component * alpha + 255 * (255 - alpha) + 127) / 255
                        sum += rgb.toLong() * kernel.weights[tap]
                    }
                    val value = (sum shr PRECISION).toInt().coerceIn(0, 255)
                    packed = packed or (value shl shift)
                }
                horizontal[(y - firstRow) * SIZE + x] = packed
            }
        }
        val result = FloatArray(SIZE * SIZE * 3)
        for (y in 0 until SIZE) {
            val kernel = yKernels[y]
            for (x in 0 until SIZE) {
                for (channel in 0..2) {
                    val shift = (2 - channel) * 8
                    var sum = (UNIT / 2).toLong()
                    for (tap in kernel.weights.indices) {
                        val pixel = horizontal[(kernel.start + tap - firstRow) * SIZE + x]
                        sum += ((pixel ushr shift) and 255).toLong() * kernel.weights[tap]
                    }
                    result[(y * SIZE + x) * 3 + channel] = (sum shr PRECISION).toInt().coerceIn(0, 255).toFloat()
                }
            }
        }
        return result
    }
}
