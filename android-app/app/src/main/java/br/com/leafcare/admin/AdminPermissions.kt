package br.com.leafcare.admin

import org.json.JSONObject

internal fun roleLabel(role: String) = when (role) {
    "owner" -> "Proprietário"; "superadmin" -> "Superadmin"; "admin" -> "Admin"; else -> "Usuário"
}

internal fun canManageAccount(state: AdminState, row: JSONObject): Boolean {
    if (state.hierarchyVersion < 1 || !state.managesAccounts || row.optString("id") == state.accountId) return false
    val target = row.optString("role")
    return row.optBoolean("can_manage", true) && target in (if (state.role == "owner") setOf("user", "admin", "superadmin") else setOf("user", "admin"))
}

internal fun roleChoices(role: String): Map<String, String> =
    linkedMapOf("user" to "Usuário", "admin" to "Admin · revisa fotos") +
        if (role == "owner") mapOf("superadmin" to "Superadmin · gerencia contas") else emptyMap()
