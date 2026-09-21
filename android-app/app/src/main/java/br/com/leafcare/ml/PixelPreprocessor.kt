package br.com.leafcare.ml

/** Bilinear half-pixel, aritmética inteira; contrato compartilhado com Python. */
object PixelPreprocessor {
    fun toRgb(pixels: IntArray, width: Int, height: Int, size: Int = 224): FloatArray {
        require(width > 0 && height > 0 && pixels.size == width * height && size > 0)
        val side = minOf(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        val denominator = 2L * size
        val divisor = denominator * denominator
        val positions = LongArray(size) { x ->
            ((2L * x + 1) * side - size).coerceIn(0, (side - 1) * denominator)
        }
        fun channel(x: Int, y: Int, shift: Int): Long {
            val pixel = pixels[(top + y) * width + left + x]
            val alpha = (pixel ushr 24) and 255
            val component = (pixel ushr shift) and 255
            return (component * alpha + 255 * (255 - alpha) + 127L) / 255
        }
        val result = FloatArray(size * size * 3)
        for (y in 0 until size) {
            val y0 = (positions[y] / denominator).toInt()
            val y1 = minOf(y0 + 1, side - 1)
            val fy = positions[y] % denominator
            for (x in 0 until size) {
                val x0 = (positions[x] / denominator).toInt()
                val x1 = minOf(x0 + 1, side - 1)
                val fx = positions[x] % denominator
                for (c in 0..2) {
                    val shift = (2 - c) * 8
                    val upper = channel(x0, y0, shift) * (denominator - fx) + channel(x1, y0, shift) * fx
                    val lower = channel(x0, y1, shift) * (denominator - fx) + channel(x1, y1, shift) * fx
                    result[(y * size + x) * 3 + c] = ((upper * (denominator - fy) + lower * fy + divisor / 2) / divisor).toFloat()
                }
            }
        }
        return result
    }
}
