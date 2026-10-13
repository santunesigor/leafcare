package br.com.leafcare.admin

import android.content.Intent
import android.util.Patterns
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import br.com.leafcare.data.DiseaseCatalog
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
    val openTriage = { actions.load("triage", JSONObject()) }
    if (state.allowed && state.section == "photos") {
        ReviewQueue(state, actions, classes, catalog)
        return
    }
    Column(Modifier.fillMaxSize().background(LeafColors.Pale), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.back, modifier = Modifier.size(44.dp).background(Color.White, CircleShape).border(1.dp, LeafColors.Border, CircleShape)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", tint = LeafColors.Text)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("LEAFCARE", color = LeafColors.Green, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text("Administração", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Contas, fotos e revisões", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            }
            IconButton(onClick = actions.refresh, enabled = state.allowed && !state.busy) { Icon(Icons.Outlined.Refresh, "Atualizar administração", tint = LeafColors.Green) }
        }
        if (!state.allowed) {
            AdminEmpty("Acesso restrito", state.error ?: "Conecte-se para verificar sua permissão de administrador.", Icons.Outlined.AdminPanelSettings)
            Button(onClick = actions.checkAccess, modifier = Modifier.align(Alignment.CenterHorizontally), shape = RoundedCornerShape(14.dp)) { Text("Verificar acesso") }
            return@Column
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("dashboard" to "Resumo", "users" to "Usuários", "triage" to "Triagem", "audit" to "Atividades").forEach { (id, title) ->
                val selected = state.section == id
                val icon = when (id) { "dashboard" -> Icons.Outlined.Dashboard; "users" -> Icons.Outlined.PeopleAlt; "triage" -> Icons.Outlined.Eco; else -> Icons.Outlined.History }
                Surface(onClick = { actions.load(id, JSONObject()) }, enabled = !state.busy,
                    shape = RoundedCornerShape(16.dp), color = if (selected) LeafColors.Green else Color.White,
                    border = if (selected) null else BorderStroke(1.dp, LeafColors.Border), modifier = Modifier.weight(1f).semantics { role = Role.Tab; this.selected = selected }) {
                    Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(icon, null, Modifier.size(22.dp), tint = if (selected) Color.White else LeafColors.Muted)
                        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else LeafColors.Text)
                    }
                }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = LeafColors.Green, trackColor = LeafColors.Border)
        LazyColumn(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth(), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            state.error?.let { item { AdminNotice(it, error = true) } }
            state.message?.let { item { AdminNotice(it) } }
            if (state.section in listOf("dashboard", "triage")) {
                if (!state.dashboard.has("photos")) item {
                    AdminEmpty(if (state.busy) "Carregando painel…" else "Painel indisponível", if (state.busy) "Buscando os totais de fotos e revisões." else "Confira sua conexão e atualize para tentar novamente.", Icons.Outlined.CloudSync)
                    if (!state.busy) OutlinedButton(onClick = actions.refresh, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Tentar novamente") }
                } else if (state.section == "triage") {
                    triageDashboard(state.dashboard, catalog, state.busy) { query -> actions.load("photos", query) }
                } else item { AdminDashboard(state.dashboard, catalog, state.busy, openTriage) }
            } else {
                item {
                    if (state.section == "users") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AdminSectionTitle("Usuários", "Gerencie acessos e envios")
                            OutlinedTextField(search, { search = it }, placeholder = { Text("Buscar nome ou e-mail") }, singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                                trailingIcon = { IconButton(onClick = { actions.load("users", JSONObject().put("search", search)) }, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Buscar usuários") } })
                            OutlinedButton(onClick = { invite = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, LeafColors.Border)) {
                                Icon(Icons.Outlined.PersonAdd, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Convidar usuário")
                            }
                        }
                    }
                    if (state.section == "audit") AdminSectionTitle("Atividades", "Histórico das ações administrativas")
                }
                items(state.rows, key = { it.get("id").toString() }) { row ->
                    when (state.section) {
                        "users" -> AdminUserCard(row, !state.busy) { actions.select(row) }
                        "audit" -> AdminAuditCard(row, catalog)
                    }
                }
                if (state.rows.isEmpty()) item {
                    AdminEmpty(if (state.busy) "Carregando…" else when (state.section) { "photos" -> "Sem fotos nesta seleção"; "users" -> "Nenhum usuário encontrado"; else -> "Nenhuma atividade encontrada" }, if (state.busy) "Buscando os registros." else "Nenhum registro encontrado para esta seleção.", Icons.Outlined.TaskAlt)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { actions.page(-1) }, enabled = state.page > 0 && !state.busy) { Text("Anterior") }
                        Text("Página ${state.page + 1}", color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { actions.page(1) }, enabled = state.rows.size == 20 && !state.busy) { Text("Próxima") }
                    }
                }
            }
        }
    }
    if (state.allowed && invite) AdminSheet("Convidar usuário", { invite = false }, dismissEnabled = !state.busy, heightFraction = .68f,
        footer = { TextButton(onClick = { invite = false }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Cancelar") } }) {
        item {
            Text("Informe o e-mail da pessoa. Depois, escolha um aplicativo para compartilhar o link de acesso ou envie o convite por e-mail.", color = LeafColors.Muted)
        }
        item {
            OutlinedTextField(inviteEmail, { inviteEmail = it }, label = { Text("E-mail da pessoa") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        }
        item {
            Button(onClick = { actions.action("invite_link", JSONObject().put("email", inviteEmail.trim())); invite = false },
                enabled = validInviteEmail && !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Share, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Compartilhar convite")
            }
        }
        item {
            OutlinedButton(onClick = { actions.action("invite", JSONObject().put("email", inviteEmail.trim())); invite = false },
                enabled = validInviteEmail && !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.MailOutline, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Enviar por e-mail")
            }
        }
    }
    if (state.allowed) state.selected?.let { row ->
        if (state.section == "users") UserDetails(actions, row, state.busy, state.error)
    }
}

internal fun invitationShareIntent(link: String): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Convite para o LeafCare")
        putExtra(Intent.EXTRA_TEXT, "Você recebeu um convite para o LeafCare! Com o aplicativo instalado, abra este link para definir sua senha e acessar:\n$link")
    }, "Compartilhar convite",
)
