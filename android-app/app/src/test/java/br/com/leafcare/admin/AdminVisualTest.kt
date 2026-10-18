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
    private fun actions(load: (String, JSONObject) -> Unit = { _, _ -> }, select: (JSONObject) -> Unit = {}, action: (String, JSONObject) -> Unit = { _, _ -> }, movePhoto: (Int) -> Unit = {}, nextPhoto: () -> Unit = {}, thumbnail: suspend (String) -> Bitmap? = { null }) =
        AdminActions(load, {}, {}, select, action, {}, {}, {}, {}, movePhoto = movePhoto, nextPhoto = nextPhoto, thumbnail = thumbnail)
    private fun show(state: AdminState, events: AdminActions = actions()) {
        compose.setContent { LeafCareTheme { AdminContent(state, DiseaseCatalog(compose.activity), classes, events) } }
    }
    private fun photo() = JSONObject("""{"id":"photo-1","class_id":"frog_eye","confidence":0.82,"email":"igor@exemplo.com","created_at":"2026-10-09T14:30:00Z","model_sha256":"modelo-exemplo","review_version":0,"top3":[{"class_id":"frog_eye","confidence":0.82},{"class_id":"brown_spot","confidence":0.12},{"class_id":"healthy","confidence":0.06}]}""")
    private fun user() = JSONObject("""{"id":"user-1","display_name":"Igor Antunes","email":"igor@exemplo.com","created_at":"2026-10-09T14:30:00Z","email_confirmed_at":"2026-10-09T14:30:00Z","last_sign_in_at":"2026-10-09T14:30:00Z","role":"user","uploads_enabled":true,"analyses":12,"photos":8}""")

    @Test fun galleryHasTwoColumnsAndDoesNotShowTriageActions() {
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        val rows = listOf(photo().put("review_status", "confirmed"), photo().put("id", "photo-2").put("review_status", "confirmed"), photo().put("id", "photo-3").put("review_status", "confirmed"))
        var selected = ""
        show(AdminState(allowed = true, section = "gallery", rows = rows, photoFilterStatus = "confirmed"), actions(select = { selected = it.getString("id") }, thumbnail = { bitmap }))
        val first = compose.onNodeWithTag("gallery-photo-1").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("gallery-photo-2").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(first.right < second.left)
        compose.onNodeWithText("Confirmar").assertDoesNotExist()
        compose.onNodeWithText("Pular").assertDoesNotExist()
        compose.onNodeWithTag("gallery-photo-2").performClick()
        assertEquals("photo-2", selected)
        capture("admin-galeria-duas-colunas")
    }
    @Test fun galleryDetailShowsHumanReviewWithoutSaveButtons() {
        val row = photo().put("review_status", "corrected").put("reviewed_class", "healthy").put("note", "Folha conferida")
        show(AdminState(allowed = true, section = "gallery", rows = listOf(row), selected = row))
        compose.onNodeWithText("Consulta da foto").assertIsDisplayed()
        compose.onNodeWithText("Confirmar").assertDoesNotExist()
        compose.onNodeWithText("Salvar e próxima").assertDoesNotExist()
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("Avaliação humana"))
        compose.onNodeWithText("Folha sem alteração aparente").assertIsDisplayed()
        capture("admin-galeria-detalhe")
    }
    @Test fun adminNavigationHidesAccountManagement() {
        show(AdminState(allowed = true, role = "admin", dashboard = JSONObject("""{"photos":1,"reviewed":0,"statuses":{}}""")))
        compose.onNode(hasText("Usuários") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertDoesNotExist()
        compose.onNodeWithText("Admin").assertIsDisplayed()
        compose.onNodeWithText("Gerenciar usuários").assertDoesNotExist()
    }
    @Test fun superadminCannotChangeOwnerOrOfferPeerPromotion() {
        val row = user().put("role", "owner")
        show(AdminState(allowed = true, role = "superadmin", hierarchyVersion = 1, section = "users", selected = row))
        compose.onNodeWithText("Alterar papel").performScrollTo().performClick()
        compose.onNodeWithText("Alterar permissão?").assertDoesNotExist()
        assertFalse(canManageAccount(AdminState(role = "superadmin", hierarchyVersion = 1), row))
        assertFalse(roleChoices("superadmin").containsKey("superadmin"))
        assertTrue(roleChoices("owner").containsKey("superadmin"))
    }
    @Test fun modelOriginKeepsIdentifierShortWithoutExpansion() {
        val hash = "0123456789abcdef".repeat(4)
        show(AdminState(allowed = true, role = "owner", dashboard = JSONObject().put("photos", 1).put("reviewed", 0).put("models", JSONObject().put(hash, 1))))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Modelo ${shortId(hash)}"))
        compose.onNodeWithText("Modelo ${shortId(hash)}").assertIsDisplayed().performClick()
        compose.onNodeWithText(hash).assertDoesNotExist()
    }

    @Test fun normalAccountHasCameraWithoutAdministrativeEntry() {
        compose.setContent { LeafCareTheme { HistoryContent(emptyList(), {}, {}, { R.drawable.v3_example_correct }, null) } }
        compose.onNodeWithContentDescription("Abrir câmera").assertIsDisplayed()
        compose.onNodeWithContentDescription("Abrir administração").assertDoesNotExist()
    }
    @Test fun adminEntrySitsBesideProfileAndBothActionsWork() {
        var admin = false; var camera = false
        compose.setContent { LeafCareTheme { HistoryContent(emptyList(), { camera = true }, {}, { R.drawable.v3_example_correct }, null, onAdmin = { admin = true }) } }
        val cameraButton = compose.onNodeWithContentDescription("Abrir câmera")
        val adminButton = compose.onNodeWithContentDescription("Abrir administração")
        cameraButton.assertIsDisplayed(); adminButton.assertIsDisplayed()
        val cameraBounds = cameraButton.fetchSemanticsNode().boundsInRoot
        val adminBounds = adminButton.fetchSemanticsNode().boundsInRoot
        val profileBounds = compose.onNodeWithContentDescription("Abrir perfil").fetchSemanticsNode().boundsInRoot
        assertTrue(adminBounds.right < profileBounds.left)
        assertEquals(profileBounds.center.y, adminBounds.center.y, 1f)
        assertTrue(adminBounds.bottom < cameraBounds.top)
        capture("admin-acesso-superior")
        adminButton.performClick(); cameraButton.performClick()
        assertTrue(admin && camera)
    }
    @Test fun dashboardOpensTriagePanelAndKeepsNavigation() {
        var section = ""; var filters = JSONObject()
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, dashboard = JSONObject("""{"users":2,"photos":11,"reviewed":3,"paused":0,"statuses":{"pending":8,"confirmed":2,"corrected":1},"classes":{"frog_eye":5,"healthy":2},"models":{"modelo-exemplo":11}}""")),
            actions(load = { s, p -> section = s; filters = p }))
        compose.onNodeWithText("8").assertIsDisplayed()
        capture("admin-resumo")
        compose.onNodeWithText("Abrir painel de triagem").performClick()
        assertEquals("triage", section); assertFalse(filters.has("status"))
        compose.onNode(hasText("Usuários") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick(); assertEquals("users", section)
    }
    @Test fun photoQueueOpensDirectlyAndFiltersStayInPanel() {
        var skipped = false
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo())), actions(nextPhoto = { skipped = true }))
        compose.onNodeWithText("Olho-de-rã").assertIsDisplayed()
        compose.onNodeWithTag("review-photo").assertIsDisplayed()
        compose.onNodeWithText("Identificador do modelo").assertDoesNotExist()
        compose.onNodeWithText("Pular").performClick()
        assertTrue(skipped)
        capture("admin-triagem-fila")
        compose.onNodeWithContentDescription("Filtrar fotos").performClick()
        compose.onNodeWithText("Identificador do modelo").assertDoesNotExist()
        compose.onNodeWithText("Mais filtros").performScrollTo().performClick()
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("Identificador do modelo"))
        compose.onNodeWithText("Identificador do modelo").assertExists()
        compose.onNodeWithText("Aplicar filtros").assertIsDisplayed()
    }
    @Test fun inspectionSwipeDoesNotChangePhotoAndButtonsNavigateWithoutSaving() {
        var saved = false
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        val rows = listOf(photo(), photo().put("id", "photo-2"))
        val state = mutableStateOf(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = rows, photoId = "photo-1", bitmap = bitmap))
        val move: (Int) -> Unit = { index -> state.value = state.value.copy(photoIndex = index, photoId = rows[index].getString("id")) }
        compose.setContent { LeafCareTheme { AdminContent(state.value, DiseaseCatalog(compose.activity), classes,
            actions(action = { _, _ -> saved = true }, movePhoto = move, nextPhoto = { move(1) })) } }
        compose.onNodeWithContentDescription("Foto da análise").performTouchInput { swipeUp() }
        compose.onNodeWithText("Foto 1 de 2").assertIsDisplayed()
        compose.onNodeWithText("Anterior").assertIsNotEnabled()
        compose.onNodeWithText("Pular").performClick()
        compose.onNodeWithText("Foto 2 de 2").assertIsDisplayed()
        compose.onNodeWithText("Anterior").performClick()
        compose.onNodeWithText("Foto 1 de 2").assertIsDisplayed()
        assertFalse(saved)
    }
    @Test fun triagePanelShowsRemoteTotalsAndOpensSelectedStatus() {
        var section = ""; var query = JSONObject()
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "triage", dashboard = JSONObject("""{"photos":11,"reviewed":3,"classes":{"frog_eye":5}}""")),
            actions(load = { s, p -> section = s; query = p }))
        compose.onNodeWithText("Painel de triagens").assertIsDisplayed()
        compose.onNodeWithText("3 de 11 fotos revisadas").assertIsDisplayed()
        capture("admin-painel-triagens")
        compose.onNodeWithText("Avaliar pendentes").performClick()
        assertEquals("photos", section); assertEquals("pending", query.getString("status"))
        compose.onNodeWithText("Duvidosas").performScrollTo().performClick()
        assertEquals("gallery", section); assertEquals("uncertain", query.getString("status"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Olho-de-rã"))
        compose.onNodeWithText("Olho-de-rã").performClick()
        assertEquals("frog_eye", query.getString("class_id")); assertEquals("", query.getString("status"))
    }
    @Test fun missingDashboardDoesNotPresentFalseZeroTotals() {
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "triage", error = "Sem conexão"))
        compose.onNodeWithText("Sem conexão").assertIsDisplayed()
        compose.onNodeWithText("Painel indisponível").assertIsDisplayed()
        compose.onNodeWithText("Tentar novamente").assertIsDisplayed()
        compose.onNodeWithText("0").assertDoesNotExist()
        compose.onNodeWithText("Nenhuma pendência").assertDoesNotExist()
    }
    @Test fun queueReturnsToTriagePanel() {
        var section = ""
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos"), actions(load = { s, _ -> section = s }))
        compose.onNodeWithContentDescription("Voltar ao painel de triagens").performClick()
        assertEquals("triage", section)
    }
    @Test fun completedPanelDisablesPendingActionButAllowsConsultation() {
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "triage", dashboard = JSONObject("""{"photos":11,"reviewed":11}""")))
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("Nenhuma pendência").assertIsNotEnabled()
        compose.onNodeWithText("Ver todas as fotos").performScrollTo().assertIsEnabled()
    }
    @Test fun dashboardLoadingDoesNotLookLikeAnEmptyQueue() {
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "triage", busy = true))
        compose.onNodeWithText("Carregando painel…").assertIsDisplayed()
        compose.onNodeWithText("Nenhuma pendência").assertDoesNotExist()
    }
    @Test fun classShortcutRemainsSelectedWhenOpeningFilters() {
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo()), photoFilterStatus = "", photoQuery = """{"status":"","class_id":"frog_eye"}"""))
        compose.onNodeWithContentDescription("Filtrar fotos").performClick()
        compose.onNode(hasText("Olho-de-rã") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("Aplicar filtros").assertIsDisplayed()
    }
    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun enlargedFontKeepsPrimaryReviewActionsVisible() {
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 1.3f)) {
                LeafCareTheme { AdminContent(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap), DiseaseCatalog(compose.activity), classes, actions()) }
            }
        }
        listOf("Confirmar", "Corrigir", "Duvidosa", "Rejeitar").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        val confirmBounds = compose.onNodeWithText("Confirmar").fetchSemanticsNode().boundsInRoot
        listOf("Corrigir", "Duvidosa", "Rejeitar").forEach {
            val bounds = compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot
            assertEquals(confirmBounds.width, bounds.width, 1f)
            assertEquals(confirmBounds.height, bounds.height, 1f)
        }
        compose.onNodeWithText("Pular").assertIsDisplayed()
        capture("admin-revisao-fonte-ampliada")
    }
    @Test fun confirmSubmitsOriginalClassAndVersionFromFeed() {
        var payload: JSONObject? = null
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap),
            actions(action = { op, data -> assertEquals("review", op); payload = data }))
        compose.onNodeWithText("Confirmar").assertIsDisplayed().performClick()
        assertEquals("confirmed", payload?.getString("status"))
        assertEquals("frog_eye", payload?.getString("class_id"))
        assertEquals(0, payload?.getInt("version"))
    }
    @Test fun usersShowReadableStatusAndOpenDetails() {
        var selected = ""
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "users", rows = listOf(user())), actions(select = { selected = it.getString("id") }))
        compose.onNodeWithText("Envios ativos").assertIsDisplayed()
        capture("admin-usuarios")
        compose.onNodeWithText("igor@exemplo.com").performClick(); assertEquals("user-1", selected)
    }
    @Test fun invitationCanBeSharedThroughAppsAfterEnteringRecipientEmail() {
        var operation = ""; var email = ""
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "users"), actions(action = { op, data -> operation = op; email = data.getString("email") }))
        compose.onNodeWithText("Convidar usuário").performClick()
        compose.onNodeWithText("Compartilhar convite").assertIsNotEnabled()
        compose.onNodeWithText("Enviar por e-mail").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("E-mail da pessoa")).performTextInput("pessoa@exemplo.com")
        capture("admin-convite-compartilhar")
        compose.onNodeWithText("Compartilhar convite").assertIsEnabled().performClick()
        assertEquals("invite_link", operation); assertEquals("pessoa@exemplo.com", email)
    }
    @Test fun emailInvitationRemainsAvailable() {
        var operation = ""
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "users"), actions(action = { op, _ -> operation = op }))
        compose.onNodeWithText("Convidar usuário").performClick()
        compose.onNode(hasSetTextAction() and hasText("E-mail da pessoa")).performTextInput("pessoa@exemplo.com")
        compose.onNodeWithText("Enviar por e-mail").performClick()
        assertEquals("invite", operation)
    }
    @Test fun invitationOpensNativeChooserWithTextLinkAndNoFixedApp() {
        val chooser = invitationShareIntent("https://auth.example.test/invite")
        assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
        val intent = chooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)!!
        assertEquals(android.content.Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertTrue(intent.getStringExtra(android.content.Intent.EXTRA_TEXT)!!.contains("https://auth.example.test/invite"))
        assertNull(intent.component); assertNull(intent.`package`)
    }
    @Test fun rejectionWithoutNoteKeepsOriginalVersionAndAdvancesOnlyThroughSave() {
        var payload: JSONObject? = null
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap),
            actions(action = { _, data -> payload = data }))
        compose.onNodeWithText("Rejeitar").performClick()
        assertNull(payload)
        compose.onNodeWithText("Salvar e próxima").assertIsEnabled().performClick()
        assertEquals("rejected", payload?.getString("status"))
        assertEquals("", payload?.getString("note"))
        assertEquals(0, payload?.getInt("version"))
    }
    @Test fun permissionChangeRequiresConfirmation() {
        var action = ""
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "users", selected = user()), actions(action = { op, _ -> action = op }))
        capture("admin-usuario-detalhe")
        compose.onNodeWithText("Alterar papel").performScrollTo().performClick()
        assertEquals("", action)
        compose.onNodeWithText("Alterar permissão?").assertIsDisplayed()
        compose.onNodeWithText("Confirmar").assertIsNotEnabled()
        compose.onNodeWithText("Novo papel").performClick()
        compose.onNodeWithText("Admin · revisa fotos").performClick()
        compose.onNodeWithText("Confirmar").performClick()
        assertEquals("role", action)
    }
    @Test fun uncertainReviewAcceptsEmptyNoteAndSubmitsOriginalVersion() {
        var payload: JSONObject? = null
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap),
            actions(action = { op, data -> assertEquals("review", op); payload = data }))
        capture("admin-revisao-topo")
        compose.onNodeWithText("Duvidosa").performClick()
        compose.onNodeWithText("Observação (opcional)").assertExists()
        capture("admin-revisao-avaliacao")
        compose.onNodeWithText("Salvar e próxima").assertIsEnabled().performClick()
        assertEquals("uncertain", payload?.getString("status"))
        assertEquals("", payload?.getString("note"))
        assertEquals(0, payload?.getInt("version"))
    }
    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun smallScreenKeepsReviewActionsVisibleWhileEditing() {
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap))
        listOf("Confirmar", "Corrigir", "Duvidosa", "Rejeitar").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        capture("admin-revisao-acoes-tela-pequena")
        compose.onNodeWithText("Rejeitar").performClick()
        compose.onNodeWithText("Salvar e próxima").assertIsDisplayed().assertIsEnabled()
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performClick().performTextInput("Imagem inadequada")
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        compose.onNodeWithText("Salvar e próxima").assertIsDisplayed().assertIsEnabled()
        capture("admin-revisao-tela-pequena")
    }
    @Test fun correctionNeedsClassAndPreservesOriginalPrediction() {
        var payload: JSONObject? = null
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap),
            actions(action = { _, data -> payload = data }))
        compose.onNodeWithText("Corrigir").performClick()
        compose.onNodeWithText("Classe revisada").performScrollTo().performClick()
        compose.onNodeWithText("Folha sem alteração aparente").performClick()
        compose.onNodeWithText("Salvar e próxima").performClick()
        assertEquals("corrected", payload?.getString("status"))
        assertEquals("healthy", payload?.getString("class_id"))
    }
    @Test fun savingReviewClosesPanelAndShowsNextPhotoInSameFeed() {
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        val next = photo().put("id", "photo-2").put("class_id", "healthy")
        val state = mutableStateOf(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo(), next), photoId = "photo-1", bitmap = bitmap))
        compose.setContent { LeafCareTheme { AdminContent(state.value, DiseaseCatalog(compose.activity), classes, actions(action = { _, _ ->
            state.value = state.value.copy(rows = listOf(next), photoId = "photo-2", reviewedCount = 1)
        })) } }
        compose.onNodeWithText("Duvidosa").performClick()
        compose.onNodeWithText("Salvar e próxima").performClick()
        compose.onNodeWithText("Sua avaliação").assertDoesNotExist()
        compose.onNodeWithText("Folha sem alteração aparente").assertIsDisplayed()
        compose.onNodeWithText("Confirmar").assertIsDisplayed().assertIsEnabled()
    }
    @Test fun cancellingReviewDiscardsDraftBeforeQuickConfirmation() {
        var payload: JSONObject? = null
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap),
            actions(action = { _, data -> payload = data }))
        compose.onNodeWithText("Rejeitar").performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Rascunho descartado")
        compose.onNodeWithText("Cancelar").performClick()
        assertNull(payload)
        compose.onNodeWithText("Confirmar").performClick()
        assertEquals("confirmed", payload?.getString("status"))
        assertEquals("", payload?.getString("note"))
    }
    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun keyboardInsetsKeepReviewFooterInsideSmallWindow() {
        val bitmap = BitmapFactory.decodeResource(compose.activity.resources, R.drawable.v3_example_correct)
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "photos", rows = listOf(photo()), photoId = "photo-1", bitmap = bitmap))
        compose.onNodeWithText("Duvidosa").performClick()
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performClick()
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
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "audit", rows = listOf(JSONObject("""{"id":1,"action":"review","created_at":"2026-10-09T14:30:00Z","actor_id":"reviewer-1","target_id":"photo-1","details":{"after":{"status":"corrected","class_id":"healthy","note":"Folha sem lesão visível"}}}"""))))
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
        show(AdminState(allowed = true, role = "owner", hierarchyVersion = 1, section = "users", selected = user()), actions(action = { op, payload ->
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
