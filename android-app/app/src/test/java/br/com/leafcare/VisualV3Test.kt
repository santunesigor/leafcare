package br.com.leafcare

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import br.com.leafcare.ui.*
import br.com.leafcare.data.*
import coil.imageLoader
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w420dp-h865dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualV3Test {
    @Test fun historySearchAndNavigation() {
        var selected = false; var opened = false
        compose.activity.imageLoader.memoryCache?.clear()
        val row = AnalysisEntity("fixture", "fixture.jpg", 1788134400000, "frog_eye", "Olho-de-rã", "Cercospora nicotianae", .82f, "[]", false, .7f, 30.0, "fixture")
        compose.setContent { LeafCareTheme { HistoryContent(listOf(row), .7f, { opened = true }, { selected = true }, { R.drawable.v3_example_correct }, null) } }
        compose.onNodeWithText("Olá").assertExists()
        awaitPhoto()
        capture("historico-v3")
        compose.onNodeWithText("Olho-de-rã").performClick()
        compose.onNodeWithContentDescription("Abrir câmera").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("ausente")
        compose.onNodeWithText("Nenhuma análise encontrada").assertExists()
        assertTrue(selected && opened)
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun awaitPhoto() {
        compose.waitUntil(5_000) { compose.activity.imageLoader.memoryCache?.keys?.isNotEmpty() == true }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        // Draw the actual activity hierarchy: PixelCopy/forceRedraw requires a
        // hardware window and times out in this headless Robolectric runtime.
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        }
        val file = File("build/reports/screenshots/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.width >= 300)
    }
    @Test fun helpExamplesAndDismiss() {
        var dismissed = false
        compose.setContent { LeafCareTheme { Surface { HelpContent { dismissed = true } } } }
        listOf("Correta", "Desfocada", "Distante", "Pouca luz").forEach { compose.onNodeWithContentDescription("Exemplo: $it").assertExists() }
        capture("ajuda-v3")
        compose.onNodeWithText("Entendi").performClick()
        assertTrue(dismissed)
    }
    @Test fun cameraControls() {
        var captured = false; var helped = false; var gallery = false
        compose.setContent { LeafCareTheme { CameraContent({}, { helped = true }, { gallery = true }, { captured = true }, {}, {}, true, true, true, true, false, true) {
            Image(painterResource(R.drawable.v3_example_correct), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } } }
        capture("camera-v3")
        compose.onNodeWithContentDescription("Como tirar a foto").performClick()
        compose.onNodeWithContentDescription("Abrir galeria").performClick()
        compose.onNodeWithContentDescription("Fotografar").performClick()
        assertTrue(helped && gallery && captured)
    }
    @Test fun resultDetailsAndNavigation() {
        var navigated = false
        compose.activity.imageLoader.memoryCache?.clear()
        val row = AnalysisEntity("fixture", "fixture.jpg", 1788134400000, "frog_eye", "Olho-de-rã", "Cercospora nicotianae", .82f,
            """[{"name":"Olho-de-rã","confidence":0.82},{"name":"Mancha-marrom","confidence":0.12},{"name":"Antracnose","confidence":0.06}]""", false, .7f, 30.0, "fixture")
        val disease = DiseaseInfo("Olho-de-rã", row.scientificName, "Doença fúngica que provoca pequenas manchas circulares nas folhas do fumo.", listOf("Manchas de centro claro e bordas escuras."), "Umidade elevada e períodos chuvosos.", "Observe outras plantas e procure um técnico para confirmar a causa.", false,
            appearance = "Manchas pequenas e circulares, com centro claro e bordas escuras.",
            affectedRegion = "Observe as áreas alteradas da folha e compare com outras plantas.",
            symptomEvolution = "Registre novas fotos para acompanhar as alterações.")
        compose.setContent { LeafCareTheme { ResultContent(row, disease, R.drawable.v3_example_correct, { navigated = true }, {}, {}) } }
        awaitPhoto()
        capture("resultado-v3-topo")
        compose.onNodeWithText("Ver as 3 possibilidades").performClick()
        compose.onNodeWithText("Mancha-marrom").assertExists()
        compose.onNodeWithText("Voltar ao histórico").performScrollTo()
        capture("resultado-v3-final")
        compose.onNodeWithText("Voltar ao histórico").performClick()
        assertTrue(navigated)
    }
}
