package br.com.leafcare.admin

import android.content.Intent
import android.util.Patterns
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.ui.LeafButton
import br.com.leafcare.ui.LeafColors
import org.json.JSONArray
import org.json.JSONObject

internal val reviewStatuses = linkedMapOf("pending" to "Pendentes", "confirmed" to "Confirmadas", "corrected" to "Corrigidas", "uncertain" to "Duvidosas", "rejected" to "Rejeitadas", "" to "Todas")
internal fun JSONObject.label(key: String) = if (isNull(key)) "—" else optString(key, "—")

/** UI events kept separate from authenticated requests, also used by local previews. */
internal data class AdminActions(
    val load: (String, JSONObject) -> Unit,
    val refresh: () -> Unit,
    val page: (Int) -> Unit,
    val select: (JSONObject) -> Unit,
    val action: (String, JSONObject) -> Unit,
    val history: (String) -> Unit,
    val closeDetails: () -> Unit,
    val checkAccess: () -> Unit,
    val back: () -> Unit,
    val movePhoto: (Int) -> Unit = {},
    val nextPhoto: () -> Unit = {},
    val retryPhoto: () -> Unit = {},
    val thumbnail: suspend (String) -> android.graphics.Bitmap? = { null },
)

@Composable
internal fun AdminScreen(vm: AdminViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val catalog = remember { DiseaseCatalog(context) }
    val classes = remember { context.assets.open("classes.json").bufferedReader().use { JSONArray(it.readText()) }.let { a -> List(a.length()) { a.getString(it) } } }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.invitationLinks.collect { link ->
                try { context.startActivity(invitationShareIntent(link)) }
                catch (_: android.content.ActivityNotFoundException) { vm.shareFailed() }
            }
        }
    }
    AdminContent(state, catalog, classes, AdminActions(
        load = { section, query -> vm.load(section, query = query) }, refresh = vm::reload,
        page = vm::page, select = vm::select, action = vm::action, history = vm::history,
        closeDetails = vm::closeDetails, checkAccess = vm::checkAccess, back = onBack,
        movePhoto = vm::movePhoto, nextPhoto = vm::nextPhoto, retryPhoto = vm::retryPhoto,
        thumbnail = vm::thumbnail,
    ))
}

@Composable
internal fun AdminContent(
    state: AdminState, catalog: DiseaseCatalog, classes: List<String>, actions: AdminActions,
) {
    var search by remember { mutableStateOf("") }
    var invite by rememberSaveable { mutableStateOf(false) }
    var inviteEmail by rememberSaveable { mutableStateOf("") }
    val validInviteEmail = Patterns.EMAIL_ADDRESS.matcher(inviteEmail.trim()).matches()
    val openGallery: (JSONObject) -> Unit = { actions.load("gallery", it) }
    if (state.allowed && state.section == "gallery") {
        PhotoGallery(state, actions, classes, catalog)
        return
    }
    if (state.allowed && state.section == "photos") {
        ReviewQueue(state, actions, classes, catalog)
        return
    }
    Column(Modifier.fillMaxSize().background(LeafColors.Pale), horizontalAlignment = Alignment.CenterHorizontally) {
        AdminTopBar("Administração", roleLabel(state.role).takeIf { state.allowed }, actions.back, "Voltar", state.busy) {
            if (state.allowed) IconButton(onClick = actions.refresh, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Atualizar administração", tint = LeafColors.Green) }
        }
        if (!state.allowed) {
            AdminEmpty("Acesso restrito", state.error ?: "Conecte-se para verificar sua permissão de administrador.", Icons.Outlined.AdminPanelSettings)
            LeafButton("Verificar acesso", actions.checkAccess, modifier = Modifier.padding(horizontal = 24.dp))
            return@Column
        }
        val tabs = listOf("dashboard" to "Resumo", "triage" to "Triagem") + (if (state.managesAccounts) listOf("users" to "Usuários") else emptyList()) + listOf("audit" to "Atividades")
        Spacer(Modifier.height(6.dp))
        AdminTabs(tabs, state.section, !state.busy) { actions.load(it, JSONObject()) }
        LazyColumn(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.error?.let { item { AdminNotice(it, error = true) } }
            state.message?.let { item { AdminNotice(it) } }
            if (state.section in listOf("dashboard", "triage")) {
                if (!state.dashboard.has("photos")) item {
                    Column {
                        AdminEmpty(if (state.busy) "Carregando painel…" else "Painel indisponível", if (state.busy) "Buscando os totais de fotos e revisões." else "Confira sua conexão e atualize para tentar novamente.", Icons.Outlined.CloudSync)
                        if (!state.busy) LeafButton("Tentar novamente", actions.refresh, secondary = true)
                    }
                } else if (state.section == "triage") {
                    triageDashboard(state.dashboard, catalog, state.busy,
                        evaluate = { actions.load("photos", JSONObject().put("status", "pending")) }, open = openGallery)
                } else item {
                    AdminDashboard(state.dashboard, state.busy, onReview = { actions.load("photos", JSONObject().put("status", "pending")) },
                        browse = openGallery, managesAccounts = state.managesAccounts,
                        onReviewTraining = { actions.load("photos", JSONObject().put("status", "pending").put("training", "true")) }) { actions.load("users", JSONObject()) }
                }
            } else {
                if (state.section == "users") item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val runSearch = { actions.load("users", JSONObject().put("search", search)) }
                        OutlinedTextField(search, { search = it }, placeholder = { Text("Buscar nome ou e-mail") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), colors = leafFieldColors(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { if (!state.busy) runSearch() }),
                            trailingIcon = { IconButton(onClick = runSearch, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Buscar usuários") } })
                        AdminSectionHeader("Contas", "Convidar", enabled = !state.busy) { invite = true }
                    }
                }
                if (state.rows.isNotEmpty()) item {
                    AdminGroup {
                        state.rows.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(color = LeafColors.Border)
                            when (state.section) {
                                "users" -> AdminUserRow(row, !state.busy) { actions.select(row) }
                                "audit" -> AdminAuditRow(row, catalog)
                            }
                        }
                    }
                } else item {
                    AdminEmpty(if (state.busy) "Carregando…" else when (state.section) { "users" -> "Nenhum usuário encontrado"; else -> "Nenhuma atividade encontrada" }, if (state.busy) "Buscando os registros." else "Nenhum registro encontrado para esta seleção.", Icons.Outlined.TaskAlt)
                }
                item { AdminPager(state.page, state.rows.size == 20, state.busy, actions.page) }
            }
        }
    }
    if (state.allowed && invite) AdminSheet("Convidar usuário", { invite = false }, dismissEnabled = !state.busy) {
        item {
            Text("Informe o e-mail da pessoa. Depois, escolha um aplicativo para compartilhar o link de acesso ou envie o convite por e-mail.", color = LeafColors.Muted)
        }
        item {
            OutlinedTextField(inviteEmail, { inviteEmail = it }, label = { Text("E-mail da pessoa") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), colors = leafFieldColors())
        }
        item {
            Button(onClick = { actions.action("invite_link", JSONObject().put("email", inviteEmail.trim())); invite = false },
                enabled = validInviteEmail && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Share, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Compartilhar convite")
            }
        }
        item {
            OutlinedButton(onClick = { actions.action("invite", JSONObject().put("email", inviteEmail.trim())); invite = false },
                enabled = validInviteEmail && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.MailOutline, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Enviar por e-mail")
            }
        }
    }
    if (state.allowed) state.selected?.let { row ->
        if (state.section == "users") UserDetails(actions, row, state)
    }
}

internal fun invitationShareIntent(link: String): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Convite para o LeafCare")
        putExtra(Intent.EXTRA_TEXT, "Você recebeu um convite para o LeafCare! Com o aplicativo instalado, abra este link para definir sua senha e acessar:\n$link")
    }, "Compartilhar convite",
)
