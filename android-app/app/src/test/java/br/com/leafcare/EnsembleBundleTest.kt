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
class EnsembleBundleTest {
    @Test fun defaultBundleContainsThreeCalibratedMembersInOneModel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metadata = JSONObject(context.assets.open("model_metadata.json").bufferedReader().use { it.readText() })
        assertEquals("MobileNetV3Ensemble", metadata.getString("architecture"))
        assertEquals("mean_probabilities", metadata.getString("aggregation"))
        assertEquals("embedded_temperature_scaling", metadata.getString("probability_calibration"))
        assertTrue(metadata.getBoolean("threshold_calibrated"))
        val members = metadata.getJSONArray("members")
        assertEquals(3, members.length())
        val architectures = (0 until members.length()).map { members.getJSONObject(it).getString("architecture") }
        assertEquals(2, architectures.count { it == "MobileNetV3Small" })
        assertEquals(1, architectures.count { it == "MobileNetV3Large" })
        assertTrue(metadata.getDouble("temperature").let { it.isFinite() && it > 0.0 })
        assertTrue(metadata.getDouble("confidence_threshold") in 0.0..1.0)
        assertEquals("center_crop_bilinear_integer_v1", metadata.getString("resize"))
        val classes = JSONArray(context.assets.open("classes.json").bufferedReader().use { it.readText() })
        assertEquals(16, classes.length())
        assertEquals(classes.toString(), metadata.getJSONArray("classes").toString())
        val bytes = context.assets.open("leafcare.tflite").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(metadata.getString("model_sha256"), hash)
        assertTrue(metadata.getJSONObject("conversion_parity").getBoolean("passed"))
    }
}
