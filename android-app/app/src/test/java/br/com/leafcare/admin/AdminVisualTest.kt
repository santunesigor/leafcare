package br.com.leafcare.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import br.com.leafcare.R
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.ui.HistoryContent
import br.com.leafcare.ui.LeafCareTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdminVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val classes = listOf("frog_eye", "healthy", "brown_spot")
    private fun actions(load: (String, JSONObject) -> Unit = { _, _ -> }, select: (JSONObject) -> Unit = {}, action: (String, JSONObject) -> Unit = { _, _ -> }, movePhoto: (Int) -> Unit = {}, nextPhoto: () -> Unit = {}) =
        AdminActions(load, {}, {}, select, action, {}, {}, {}, {}, movePhoto = movePhoto, nextPhoto = nextPhoto)
    private fun show(state: AdminState, events: AdminActions = actions()) {
        compose.setContent { LeafCareTheme { AdminContent(state, DiseaseCatalog(compose.activity), classes, events) } }
    }
    private fun photo() = JSONObject("""{"id":"photo-1","class_id":"frog_eye","confidence":0.82,"email":"igor@exemplo.com","created_at":"2026-10-09T14:30:00Z","model_sha256":"modelo-exemplo","review_version":0,"top3":[{"class_id":"frog_eye","confidence":0.82},{"class_id":"brown_spot","confidence":0.12},{"class_id":"healthy","confidence":0.06}]}""")
    private fun user() = JSONObject("""{"id":"user-1","display_name":"Igor Antunes","email":"igor@exemplo.com","created_at":"2026-10-09T14:30:00Z","email_confirmed_at":"2026-10-09T14:30:00Z","last_sign_in_at":"2026-10-09T14:30:00Z","role":"user","uploads_enabled":true,"analyses":12,"photos":8}""")

    @Test fun normalAccountHasCameraWithoutAdministrativeEntry() {
        compose.setContent { LeafCareTheme { HistoryContent(emptyList(), {}, {}, { R.drawable.v3_example_correct }, null) } }
        compose.onNodeWithContentDescription("Abrir câmera").assertIsDisplayed()
        compose.onNodeWithContentDescription("Abrir administração").assertDoesNotExist()
    }
    @Test fun adminEntrySitsBesideCameraAndBothActionsWork() {
        var admin = false; var camera = false
        compose.setContent { LeafCareTheme { HistoryContent(emptyList(), { camera = true }, {}, { R.drawable.v3_example_correct }, null, onAdmin = { admin = true }) } }
        val cameraButton = compose.onNodeWithContentDescription("Abrir câmera")
        val adminButton = compose.onNodeWithContentDescription("Abrir administração")
        cameraButton.assertIsDisplayed(); adminButton.assertIsDisplayed()
        val cameraBounds = cameraButton.fetchSemanticsNode().boundsInRoot
        val adminBounds = adminButton.fetchSemanticsNode().boundsInRoot
        assertTrue(adminBounds.left > cameraBounds.right)
        assertEquals(cameraBounds.center.y, adminBounds.center.y, 1f)
        capture("admin-acesso-inferior")
        adminButton.performClick(); cameraButton.performClick()
        assertTrue(admin && camera)
    }
    @Test fun dashboardOpensPendingQueueAndKeepsNavigation() {
        var section = ""; var filters = JSONObject()
        show(AdminState(allowed = true, dashboard = JSONObject("""{"users":2,"photos":11,"reviewed":3,"paused":0,"classes":{"frog_eye":5,"healthy":2},"models":{"modelo-exemplo":11}}""")),
            actions(load = { s, p -> section = s; filters = p }))
        compose.onNodeWithText("8").assertIsDisplayed()
        capture("admin-resumo")
        compose.onNodeWithText("Revisar fotos").performClick()
        assertEquals("photos", section); assertEquals("pending", filters.getString("status"))
        compose.onNode(hasText("Usuários") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick(); assertEquals("users", section)
    }
    @Test fun photoQueueOpensDirectlyAndFiltersStayInPanel() {
        var skipped = false
        show(AdminState(allowed = true, section = "photos", rows = listOf(photo())), actions(nextPhoto = { skipped = true }))
        compose.onNodeWithText("Olho-de-rã").assertIsDisplayed()
        compose.onNodeWithTag("review-feed").assertIsDisplayed()
        compose.onNodeWithText("Identificador do modelo").assertDoesNotExist()
        compose.onNodeWithText("Pular sem revisar").performClick()
        assertTrue(skipped)
        capture("admin-triagem-fila")
        compose.onNodeWithContentDescription("Filtrar fotos").performClick()
        compose.onNodeWithText("Identificador do modelo").assertExists()
        compose.onNodeWithText("Aplicar filtros").assertIsDisplayed()
    }
    @Test fun verticalSwipeMovesToNextPhotoWithoutSaving() {
        var saved = false
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        val rows = listOf(photo(), photo().put("id", "photo-2"))
        val state = mutableStateOf(AdminState(allowed = true, section = "photos", rows = rows, photoId = "photo-1", bitmap = bitmap))
        compose.setContent { LeafCareTheme { AdminContent(state.value, DiseaseCatalog(compose.activity), classes,
            actions(action = { _, _ -> saved = true }, movePhoto = { index -> state.value = state.value.copy(photoIndex = index, photoId = rows[index].getString("id")) })) } }
        compose.onNodeWithTag("review-feed").performTouchInput { swipeUp() }
        compose.waitUntil { state.value.photoIndex == 1 }
        compose.onNodeWithText("2 de 2 · Deslize para cima").assertIsDisplayed()
        assertFalse(saved)
    }
    @Test fun confirmSubmitsOriginalClassAndVersionFromFeed() {
        var payload: JSONObject? = null
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap),
            actions(action = { op, data -> assertEquals("review", op); payload = data }))
        compose.onNodeWithText("Confirmar e próxima").assertIsDisplayed().performClick()
        assertEquals("confirmed", payload?.getString("status"))
        assertEquals("frog_eye", payload?.getString("class_id"))
        assertEquals(0, payload?.getInt("version"))
    }
    @Test fun usersShowReadableStatusAndOpenDetails() {
        var selected = ""
        show(AdminState(allowed = true, section = "users", rows = listOf(user())), actions(select = { selected = it.getString("id") }))
        compose.onNodeWithText("Envios ativos").assertIsDisplayed()
        capture("admin-usuarios")
        compose.onNodeWithText("igor@exemplo.com").performClick(); assertEquals("user-1", selected)
    }
    @Test fun permissionChangeRequiresConfirmation() {
        var action = ""
        show(AdminState(allowed = true, section = "users", selected = user()), actions(action = { op, _ -> action = op }))
        capture("admin-usuario-detalhe")
        compose.onNodeWithText("Tornar superadmin").performScrollTo().performClick()
        assertEquals("", action)
        compose.onNodeWithText("Alterar permissão?").assertIsDisplayed()
        compose.onNodeWithText("Confirmar").performClick()
        assertEquals("role", action)
    }
    @Test fun uncertainReviewNeedsReasonAndSubmitsOriginalVersion() {
        var payload: JSONObject? = null
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap),
            actions(action = { op, data -> assertEquals("review", op); payload = data }))
        capture("admin-revisao-topo")
        compose.onNodeWithText("Avaliar").performClick()
        compose.onNodeWithText("Duvidosa").performScrollTo().performClick()
        compose.onNodeWithText("Salvar e próxima").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Foto sem nitidez suficiente")
        capture("admin-revisao-avaliacao")
        compose.onNodeWithText("Salvar e próxima").assertIsEnabled().performClick()
        assertEquals("uncertain", payload?.getString("status"))
        assertEquals("Foto sem nitidez suficiente", payload?.getString("note"))
        assertEquals(0, payload?.getInt("version"))
    }
    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun smallScreenKeepsReviewActionsVisibleWhileEditing() {
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap))
        compose.onNodeWithText("Confirmar e próxima").assertIsDisplayed()
        compose.onNodeWithText("Avaliar").performClick()
        compose.onNodeWithText("Rejeitar").performScrollTo().performClick()
        compose.onNodeWithText("Salvar e próxima").assertIsDisplayed().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performScrollTo().performClick().performTextInput("Imagem inadequada")
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        compose.onNodeWithText("Salvar e próxima").assertIsDisplayed().assertIsEnabled()
        capture("admin-revisao-tela-pequena")
    }
    @Test fun correctionNeedsClassAndPreservesOriginalPrediction() {
        var payload: JSONObject? = null
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap),
            actions(action = { _, data -> payload = data }))
        compose.onNodeWithText("Avaliar").performClick()
        compose.onNodeWithText("Corrigir classe").performClick()
        compose.onNodeWithText("Classe revisada").performScrollTo().performClick()
        compose.onNodeWithText("Folha sem alteração aparente").performClick()
        compose.onNodeWithText("Salvar e próxima").performClick()
        assertEquals("corrected", payload?.getString("status"))
        assertEquals("healthy", payload?.getString("class_id"))
    }
    @Test fun savingReviewClosesPanelAndShowsNextPhotoInSameFeed() {
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        val next = photo().put("id", "photo-2").put("class_id", "healthy")
        val state = mutableStateOf(AdminState(allowed = true, section = "photos", rows = listOf(photo(), next), photoId = "photo-1", bitmap = bitmap))
        compose.setContent { LeafCareTheme { AdminContent(state.value, DiseaseCatalog(compose.activity), classes, actions(action = { _, _ ->
            state.value = state.value.copy(rows = listOf(next), photoId = "photo-2", reviewedCount = 1)
        })) } }
        compose.onNodeWithText("Avaliar").performClick()
        compose.onNodeWithText("Salvar e próxima").performClick()
        compose.onNodeWithText("Sua avaliação").assertDoesNotExist()
        compose.onNodeWithText("Folha sem alteração aparente").assertIsDisplayed()
        compose.onNodeWithText("Confirmar e próxima").assertIsDisplayed().assertIsEnabled()
    }
    @Test fun cancellingReviewDiscardsDraftBeforeQuickConfirmation() {
        var payload: JSONObject? = null
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap),
            actions(action = { _, data -> payload = data }))
        compose.onNodeWithText("Avaliar").performClick()
        compose.onNodeWithText("Rejeitar").performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Rascunho descartado")
        compose.onNodeWithText("Cancelar").performClick()
        assertNull(payload)
        compose.onNodeWithText("Confirmar e próxima").performClick()
        assertEquals("confirmed", payload?.getString("status"))
        assertEquals("", payload?.getString("note"))
    }
    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun keyboardInsetsKeepReviewFooterInsideSmallWindow() {
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap))
        compose.onNodeWithText("Avaliar").performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.runOnIdle {
            val decor = ShadowDialog.getLatestDialog().window!!.decorView
            val insets = android.view.WindowInsets.Builder()
                .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, 220))
                .setVisible(android.view.WindowInsets.Type.ime(), true).build()
            decor.dispatchApplyWindowInsets(insets)
        }
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        compose.onNodeWithText("Salvar e próxima").assertIsDisplayed()
        val saveBottom = compose.onNodeWithText("Salvar e próxima").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("O rodapé deve ficar acima do espaço reservado ao teclado", saveBottom < 260f)
        capture("admin-revisao-teclado-simulado")
    }
    @Test fun auditShowsReadableActionWithoutRawJson() {
        show(AdminState(allowed = true, section = "audit", rows = listOf(JSONObject("""{"id":1,"action":"review","created_at":"2026-10-09T14:30:00Z","actor_id":"reviewer-1","target_id":"photo-1","details":{"after":{"status":"corrected","class_id":"healthy","note":"Folha sem lesão visível"}}}"""))))
        compose.onNodeWithText("Revisão registrada").assertIsDisplayed()
        compose.onNodeWithText("Folha sem lesão visível").assertIsDisplayed()
        capture("admin-atividades")
    }
    @Test fun unavailableAccessDoesNotExposeSelectedAdministrativeData() {
        show(AdminState(allowed = false, section = "users", rows = listOf(user()), selected = user()))
        compose.onNodeWithText("Acesso restrito").assertIsDisplayed()
        compose.onNodeWithText("igor@exemplo.com").assertDoesNotExist()
        compose.onNodeWithText("Tornar superadmin").assertDoesNotExist()
    }
    @Test fun accountDeletionRequiresMatchingEmail() {
        var action = ""
        show(AdminState(allowed = true, section = "users", selected = user()), actions(action = { op, payload ->
            action = op; assertEquals("igor@exemplo.com", payload.getString("confirmation"))
        }))
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("Excluir conta e dados"))
        compose.onNodeWithText("Excluir conta e dados").performClick()
        compose.onNodeWithText("Excluir conta").assertIsNotEnabled()
        capture("admin-confirmar-exclusao")
        val email = compose.onNode(hasSetTextAction() and hasText("E-mail da conta"))
        email.performTextInput("errado@exemplo.com")
        compose.onNodeWithText("Excluir conta").assertIsNotEnabled()
        assertEquals("", action)
        email.performTextReplacement("igor@exemplo.com")
        compose.onNodeWithText("Excluir conta").assertIsEnabled().performClick()
        assertEquals("delete", action)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.runOnIdle {
            val view = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        File("build/reports/screenshots/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
