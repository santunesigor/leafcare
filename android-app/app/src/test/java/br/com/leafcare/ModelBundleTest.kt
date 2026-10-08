package br.com.leafcare

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ModelBundleTest {
    @Test fun defaultBundleContainsCalibratedDistilledModel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metadata = JSONObject(context.assets.open("model_metadata.json").bufferedReader().use { it.readText() })
        assertEquals("MobileNetV4SmallDistilled", metadata.getString("architecture"))
        br.com.leafcare.ml.validateModelMetadata(metadata)
        assertEquals("embedded_temperature_scaling", metadata.getString("probability_calibration"))
        assertTrue(metadata.getBoolean("threshold_calibrated"))
        assertTrue(metadata.getDouble("temperature").let { it.isFinite() && it > 0.0 })
        assertTrue(metadata.getDouble("confidence_threshold") in 0.0..1.0)
        assertEquals("resize_shorter_256_bicubic_center_crop_224_v1", metadata.getString("resize"))
        val classes = JSONArray(context.assets.open("classes.json").bufferedReader().use { it.readText() })
        assertEquals(16, classes.length())
        assertEquals(classes.toString(), metadata.getJSONArray("classes").toString())
        val bytes = context.assets.open("leafcare.tflite").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(metadata.getString("model_sha256"), hash)
        assertTrue(metadata.getJSONObject("conversion_parity").getBoolean("passed"))
    }
    @Test fun incompatibleMetadataIsRejected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = context.assets.open("model_metadata.json").bufferedReader().use { it.readText() }
        for ((key, invalid) in listOf("schema_version" to 1, "architecture" to "MobileNetV3Ensemble",
            "resize" to "center_crop_bilinear_integer_v1", "temperature" to 0.0,
            "confidence_threshold" to 2.0, "normalization" to "wrong", "threshold_calibrated" to false)) {
            val metadata = JSONObject(original).put(key, invalid)
            assertThrows(IllegalArgumentException::class.java) { br.com.leafcare.ml.validateModelMetadata(metadata) }
        }
    }

}
