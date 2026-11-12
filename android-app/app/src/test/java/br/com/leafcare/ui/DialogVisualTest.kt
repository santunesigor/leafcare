package br.com.leafcare.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import br.com.leafcare.ui.auth.unsyncedSignOutMessage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Renders every app dialog in a real dialog window, over a dimmed screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialogVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(name: String, title: String, dialog: @Composable () -> Unit) {
        compose.setContent { LeafCareTheme { Surface(Modifier.fillMaxSize(), color = LeafColors.Pale) { Box(Modifier.fillMaxSize()) }; dialog() } }
        compose.onNodeWithText(title).assertIsDisplayed()
        capture(name)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            root.draw(canvas)
            canvas.drawColor(0x99000000.toInt())
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let { view ->
                canvas.save()
                canvas.translate((root.width - view.width) / 2f, (root.height - view.height) / 2f)
                view.draw(canvas)
                canvas.restore()
            }
            bitmap
        }
        File("build/reports/screenshots/modal-$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun deleteAnalysis() = show("excluir-analise", "Excluir esta análise?") {
        LeafDialog("Excluir esta análise?", {}, message = "A análise e a foto serão removidas deste aparelho e da sua conta.", actions = {
            LeafButton("Excluir", {}, danger = true); LeafButton("Cancelar", {}, secondary = true)
        })
    }

    @Test fun error() = show("erro", "Não foi possível concluir") {
        LeafDialog("Não foi possível concluir", {}, message = "Não foi possível ler a imagem. Tente outra foto.", actions = { LeafButton("Entendi", {}) })
    }

    @Test fun analyzing() = show("analisando", "Analisando no aparelho") {
        LeafProgressDialog("Analisando no aparelho", "Aguarde. A análise é feita neste aparelho.")
    }

    @Test fun signOut() = show("sair-pendentes", "Sair com análises não enviadas?") {
        LeafDialog("Sair com análises não enviadas?", {}, message = unsyncedSignOutMessage(3), actions = {
            LeafButton("Sair", {}, danger = true); LeafButton("Cancelar", {}, secondary = true)
        })
    }

    @Test fun revoke() = show("revogar", "Revogar autorização?") {
        LeafDialog("Revogar autorização?", {}, message = "Novos envios param e as cópias de treinamento serão excluídas. O LeafCare só poderá ser usado novamente depois de aceitar os termos outra vez.", actions = {
            LeafButton("Revogar", {}, danger = true); LeafButton("Cancelar", {}, secondary = true)
        })
    }

    @Test fun longLabelsStack() = show("rotulos-longos", "Cancelar recuperação?") {
        LeafDialog("Cancelar recuperação?", {}, message = "Se você sair agora, será necessário solicitar um novo link de recuperação por e-mail.", actions = {
            LeafButton("Continuar recuperação de senha", {}); LeafButton("Sair", {}, secondary = true, danger = true)
        })
    }

    @Test fun help() = show("ajuda", "Como tirar a foto") { HelpSheet {} }
}
