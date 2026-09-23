package com.kabus.admin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kabus.admin.KaBusAdminApp
import com.kabus.admin.data.CorporationResponse
import com.kabus.admin.data.DivisionResponse
import com.kabus.admin.data.DepotResponse
import com.kabus.admin.data.TownResponse
import kotlinx.coroutines.launch

/**
 * Organization hierarchy management (SUPER_ADMIN gated by backend).
 * Drill-down: Corporation -> Division -> Depot -> Town.
 * Surface is create + enable/disable only (no update endpoint in ApiService).
 */
@Composable
fun OrganizationScreen(app: KaBusAdminApp) {
    var level by remember { mutableStateOf(OrgLevel.Corporations) }
    var corpId by remember { mutableStateOf<Long?>(null) }
    var divId by remember { mutableStateOf<Long?>(null) }
    var depotId by remember { mutableStateOf<Long?>(null) }

    when (level) {
        OrgLevel.Corporations -> CorporationListScreen(
            app,
            onOpen = { corpId = it; level = OrgLevel.Divisions }
        )
        OrgLevel.Divisions -> DivisionListScreen(
            app, corpId!!,
            onOpen = { divId = it; level = OrgLevel.Depots },
            onBack = { level = OrgLevel.Corporations }
        )
        OrgLevel.Depots -> DepotListScreen(
            app, divId!!,
            onOpen = { depotId = it; level = OrgLevel.Towns },
            onBack = { level = OrgLevel.Divisions }
        )
        OrgLevel.Towns -> TownListScreen(
            app, depotId!!,
            onBack = { level = OrgLevel.Depots }
        )
    }
}

private val OrgLevel.label: String
    get() = when (this) {
        OrgLevel.Corporations -> "Corporation"
        OrgLevel.Divisions -> "Division"
        OrgLevel.Depots -> "Depot"
        OrgLevel.Towns -> "Town"
    }

enum class OrgLevel { Corporations, Divisions, Depots, Towns }

// ---------------------------------------------------------------------
// Corporation
// ---------------------------------------------------------------------

@Composable
private fun CorporationListScreen(
    app: KaBusAdminApp,
    onOpen: (Long) -> Unit
) {
    var items by remember { mutableStateOf(listOf<CorporationResponse>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<CorporationResponse?>(null) }
    val scope = rememberCoroutineScope()
    val pull = remember { RefreshController() }

    suspend fun refresh() {
        runCatching { app.api.corporations(app.auth()) }
            .onSuccess { items = it; error = null }
            .onFailure { error = apiMessage(it) }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    // ---- row + toggle + fab + form ----
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New corporation")
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                error != null && items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    RefreshAction(refreshing = pull.refreshing, onRefresh = onRefresh)
                }
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No corporations yet.")
                    RefreshAction(refreshing = pull.refreshing, onRefresh = onRefresh)
                }
                else -> PullList(pull.refreshing, onRefresh) {
                    if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
                    items(items) { c ->
                        OrgCard(
                            title = "${c.code} · ${c.name}",
                            subtitle = if (c.enabled) "ACTIVE" else "INACTIVE",
                            enabled = c.enabled,
                            onClick = { onOpen(c.id) },
                            onToggle = { en -> scope.launch {
                                runCatching { app.api.toggleCorporation(app.auth(), c.id, en) }
                                    .onSuccess { refresh() }
                                    .onFailure { error = apiMessage(it) }
                            } },
                            onEdit = { editTarget = c }
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        CorporationCreateDialog(
            app = app,
            onDismiss = { showForm = false },
            onCreated = {
                showForm = false
                scope.launch { refresh() }
            }
        )
    }

    editTarget?.let { target ->
        CorporationEditDialog(
            app = app,
            corporation = target,
            onDismiss = { editTarget = null },
            onSaved = {
                editTarget = null
                scope.launch { refresh() }
            }
        )
    }
}

/**
 * New corporation flow: the SUPER_ADMIN enters code/name/status directly; the
 * corporation is created against the backend POST /corporations endpoint. The
 * backend owns the unique-code rule (a duplicate returns HTTP 409 and its safe
 * message is shown in the dialog). No hardcoded catalog is used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorporationCreateDialog(
    app: KaBusAdminApp,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New Corporation") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    singleLine = true,
                    label = { Text("Corporation code") },
                    placeholder = { Text("KSRTC") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Corporation name") },
                    placeholder = { Text("Karnataka State Road Transport Corporation") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = "Active",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    singleLine = true,
                    label = { Text("Status") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && code.isNotBlank() && name.isNotBlank(),
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            app.api.createCorporation(
                                app.auth(),
                                com.kabus.admin.data.CorporationRequest(
                                    code, name, null, null, null, null, null
                                )
                            )
                        }
                            .onSuccess { onCreated() }
                            .onFailure {
                                error = apiMessage(it) ?: "Could not create corporation."
                                saving = false
                            }
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorporationEditDialog(
    app: KaBusAdminApp,
    corporation: CorporationResponse,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var code by remember { mutableStateOf(corporation.code) }
    var name by remember { mutableStateOf(corporation.name) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit corporation") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Corporation name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    singleLine = true,
                    label = { Text("Corporation code") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && code.isNotBlank() && name.isNotBlank(),
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            app.api.updateCorporation(
                                app.auth(),
                                corporation.id,
                                com.kabus.admin.data.CorporationRequest(
                                    code, name, corporation.address, corporation.city,
                                    corporation.state, corporation.contactEmail, corporation.contactPhone
                                )
                            )
                        }
                            .onSuccess { onSaved() }
                            .onFailure {
                                error = apiMessage(it) ?: "Could not update corporation."
                                saving = false
                            }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") }
        }
    )
}

// ---------------------------------------------------------------------
// Division
// ---------------------------------------------------------------------

private fun divisionDetail(d: DivisionResponse): String = buildString {
    if (d.headOffice != null && d.headOffice.isNotBlank()) append(d.headOffice)
    if (d.adminName != null) {
        if (isNotEmpty()) append('\n')
        append("Admin: ").append(d.adminName)
        if (d.adminEmail != null && d.adminEmail.isNotBlank()) append('\n').append(d.adminEmail)
        append('\n').append("DIVISION_ADMIN · ")
            .append(if (d.adminStatus == "ACTIVE") "Active" else "Inactive")
    }
}.ifEmpty { "No admin assigned" }

@Composable
private fun DivisionListScreen(
    app: KaBusAdminApp,
    corporationId: Long,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit
) {
    var items by remember { mutableStateOf(listOf<DivisionResponse>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val pull = remember { RefreshController() }

    suspend fun refresh() {
        runCatching { app.api.divisions(app.auth(), corporationId) }
            .onSuccess { items = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    Scaffold(
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New division")
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                error != null && items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    RefreshAction(refreshing = pull.refreshing, onRefresh = onRefresh)
                }
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No divisions under this corporation.")
                    RefreshAction(refreshing = pull.refreshing, onRefresh = onRefresh)
                }
                else -> PullList(pull.refreshing, onRefresh) {
                    if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
                    items(items) { d ->
                        OrgCard(
                            title = "${d.code} · ${d.name}",
                            subtitle = divisionDetail(d),
                            enabled = d.enabled,
                            onClick = { onOpen(d.id) },
                            onToggle = { en -> scope.launch {
                                runCatching { app.api.toggleDivision(app.auth(), d.id, en) }
                                    .onSuccess { refresh() }
                                    .onFailure { error = it.message }
                            } }
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        OrgCreateDialog(
            kind = "Division",
            code = code, name = name,
            onCodeChange = { code = it },
            onNameChange = { name = it },
            onDismiss = { showForm = false },
            onSave = {
                showForm = false
                scope.launch {
                    runCatching {
                        app.api.createDivision(
                            app.auth(),
                            com.kabus.admin.data.DivisionRequest(corporationId, code, name, null)
                        )
                    }
                        .onSuccess { refresh() }
                        .onFailure { error = it.message }
                }
            }
        )
    }
}

// ---------------------------------------------------------------------
// Depot
// ---------------------------------------------------------------------

@Composable
private fun DepotListScreen(
    app: KaBusAdminApp,
    divisionId: Long,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit
) {
    var items by remember { mutableStateOf(listOf<DepotResponse>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val pull = remember { RefreshController() }

    suspend fun refresh() {
        runCatching { app.api.depots(app.auth(), divisionId) }
            .onSuccess { items = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    Scaffold(
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New depot")
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                error != null && items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    RefreshAction(refreshing = pull.refreshing, onRefresh = onRefresh)
                }
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No depots under this division.")
                    RefreshAction(refreshing = pull.refreshing, onRefresh = onRefresh)
                }
                else -> PullList(pull.refreshing, onRefresh) {
                    if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
                    items(items) { d ->
                        OrgCard(
                            title = "${d.code} · ${d.name}",
                            subtitle = d.address,
                            enabled = d.enabled,
                            onClick = { onOpen(d.id) },
                            onToggle = { en -> scope.launch {
                                runCatching { app.api.toggleDepot(app.auth(), d.id, en) }
                                    .onSuccess { refresh() }
                                    .onFailure { error = it.message }
                            } }
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        OrgCreateDialog(
            kind = "Depot",
            code = code, name = name,
            onCodeChange = { code = it },
            onNameChange = { name = it },
            onDismiss = { showForm = false },
            onSave = {
                showForm = false
                scope.launch {
                    runCatching {
                        app.api.createDepot(
                            app.auth(),
                            com.kabus.admin.data.DepotRequest(divisionId, code, name, null, null)
                        )
                    }
                        .onSuccess { refresh() }
                        .onFailure { error = it.message }
                }
            }
        )
    }
}

// ---------------------------------------------------------------------
// Town (leaf)
// ---------------------------------------------------------------------

@Composable
private fun TownListScreen(
    app: KaBusAdminApp,
    depotId: Long,
    onBack: () -> Unit
) {
    var items by remember { mutableStateOf(listOf<TownResponse>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val pull = remember { RefreshController() }

    suspend fun refresh() {
        runCatching { app.api.towns(app.auth(), depotId) }
            .onSuccess { items = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    Scaffold(
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New town")
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                error != null && items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    RefreshAction(refreshing = pull.refreshing, onRefresh = onRefresh)
                }
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                items.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No towns under this depot.")
                    RefreshAction(refreshing = pull.refreshing, onRefresh = onRefresh)
                }
                else -> PullList(pull.refreshing, onRefresh) {
                    if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
                    items(items) { t ->
                        OrgCard(
                            title = "${t.code} · ${t.name}",
                            subtitle = null,
                            enabled = t.enabled,
                            onClick = { /* leaf */ },
                            onToggle = { en -> scope.launch {
                                runCatching { app.api.toggleTown(app.auth(), t.id, en) }
                                    .onSuccess { refresh() }
                                    .onFailure { error = it.message }
                            } }
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        OrgCreateDialog(
            kind = "Town",
            code = code, name = name,
            onCodeChange = { code = it },
            onNameChange = { name = it },
            onDismiss = { showForm = false },
            onSave = {
                showForm = false
                scope.launch {
                    runCatching {
                        app.api.createTown(
                            app.auth(),
                            com.kabus.admin.data.TownRequest(depotId, code, name)
                        )
                    }
                        .onSuccess { refresh() }
                        .onFailure { error = it.message }
                }
            }
        )
    }
}

// ---------------------------------------------------------------------
// Shared row card + create dialog
// ---------------------------------------------------------------------

@Composable
private fun OrgCard(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: (() -> Unit)? = null
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun OrgCreateDialog(
    kind: String,
    code: String,
    name: String,
    onCodeChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New $kind") },
        text = {
            Column {
                OutlinedTextField(code, onCodeChange, label = { Text("Code") }, singleLine = true)
                OutlinedTextField(
                    name, onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = code.isNotBlank() && name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
