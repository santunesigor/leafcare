package br.com.leafcare

import br.com.leafcare.ml.DistilledPreprocessor
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Properties

class DistilledPreprocessorTest {
    private fun hash(values: FloatArray): String = MessageDigest.getInstance("SHA-256")
        .digest(ByteArray(values.size) { values[it].toInt().toByte() })
        .joinToString("") { "%02x".format(it) }

    @Test fun pillowSyntheticGoldenParity() {
        val fixtures = Properties().apply {
            DistilledPreprocessorTest::class.java.classLoader!!.getResourceAsStream("distilled_preprocess_golden.properties")!!.use { load(it) }
        }
        for (name in fixtures.getProperty("cases").split(",")) {
            val (width, height) = fixtures.getProperty("$name.size").split(",").map { it.toInt() }
            val pixels = IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                val alpha = if (name == "alpha") (x * 17 + y * 31) % 256 else 255
                (alpha shl 24) or (((x * 37 + y * 13) % 256) shl 16) or
                    (((x * 11 + y * 43) % 256) shl 8) or ((x * 53 + y * 7) % 256)
            }
            assertEquals(name, fixtures.getProperty("$name.sha256"), hash(DistilledPreprocessor.toRgb(pixels, width, height)))
        }
    }

    @Test fun localValidationPixelReplay() {
        val directory = System.getenv("LEAFCARE_PARITY_DIR") ?: return
        val fixtures = Properties().apply { File(directory, "index.properties").inputStream().use { load(it) } }
        val count = fixtures.getProperty("count").toInt()
        assertEquals(104, count)
        repeat(count) { index ->
            val name = index.toString().padStart(3, '0')
            val width = fixtures.getProperty("$name.width").toInt()
            val height = fixtures.getProperty("$name.height").toInt()
            val bytes = File(directory, "$name.rgb").readBytes()
            val pixels = IntArray(width * height) { i ->
                (255 shl 24) or ((bytes[i * 3].toInt() and 255) shl 16) or
                    ((bytes[i * 3 + 1].toInt() and 255) shl 8) or (bytes[i * 3 + 2].toInt() and 255)
            }
            assertEquals(name, fixtures.getProperty("$name.sha256"), hash(DistilledPreprocessor.toRgb(pixels, width, height)))
        }
    }

    @Test fun rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException::class.java) { DistilledPreprocessor.toRgb(intArrayOf(), 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { DistilledPreprocessor.toRgb(intArrayOf(0), 2, 2) }
    }
}
