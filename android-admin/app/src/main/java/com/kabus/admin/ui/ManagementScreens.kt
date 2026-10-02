package com.kabus.admin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.kabus.admin.data.AdminUserItem
import com.kabus.admin.data.AuditItem
import com.kabus.admin.data.BusItem
import com.kabus.admin.data.BusNumberItem
import com.kabus.admin.data.ChangePasswordRequest
import com.kabus.admin.data.ChangeRoleRequest
import com.kabus.admin.data.CreateBusRequest
import com.kabus.admin.data.CreateRouteRequest
import com.kabus.admin.data.CreateStaffRequest
import com.kabus.admin.data.CreateUserRequest
import com.kabus.admin.data.RouteItem
import com.kabus.admin.data.SettingItem
import com.kabus.admin.data.StaffItem
import com.kabus.admin.data.UpdateSettingRequest
import kotlinx.coroutines.launch

/**
 * Operations hub: fleet, staff and routes management for scoped admin roles.
 * Users / audit logs / settings are SUPER_ADMIN-only and must be reached via
 * their own bottom-nav tabs; the backend enforces this regardless.
 */
@Composable
fun ManagementHubScreen(app: KaBusAdminApp) {
    var section by remember { mutableStateOf<ManageSection?>(null) }

    when (section) {
        ManageSection.Fleet -> ListScreenScaffold(title = "Fleet", onBack = { section = null }) {
            FleetScreen(app)
        }
        ManageSection.Staff -> ListScreenScaffold(title = "Staff", onBack = { section = null }) {
            StaffScreen(app)
        }
        ManageSection.Routes -> ListScreenScaffold(title = "Routes", onBack = { section = null }) {
            RoutesScreen(app)
        }
        null -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Manage", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Fleet, staff and route management within your organizational scope.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { section = ManageSection.Fleet }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Directions, contentDescription = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Fleet", style = MaterialTheme.typography.titleMedium)
                        Text("Buses, registration, capacity, GPS devices", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { section = ManageSection.Staff }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Groups, contentDescription = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Staff", style = MaterialTheme.typography.titleMedium)
                        Text("Non-crew staff records attached to depots", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { section = ManageSection.Routes }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Map, contentDescription = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Routes", style = MaterialTheme.typography.titleMedium)
                        Text("Route masters and stop definitions", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private enum class ManageSection { Fleet, Staff, Routes }

@Composable
private fun ListScreenScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            content()
        }
    }
}

/** Full-screen empty/error/loading placeholder shared by every list screen. */
@Composable
internal fun CenterStatus(
    text: String,
    error: Boolean = false,
    onRetry: (() -> Unit)? = null,
    refreshing: Boolean = false
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (onRetry != null) {
                RefreshAction(refreshing = refreshing, onRefresh = onRetry)
            }
        }
    }
}

object ManagementScreens

// ----------------------------------------------------------------------
// Fleet
// ----------------------------------------------------------------------

@Composable
internal fun FleetScreen(app: KaBusAdminApp) {
    var items by remember { mutableStateOf(listOf<BusItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pull = remember { RefreshController() }

    suspend fun refresh() {
        runCatching { app.api.buses(app.auth(), null, 0, 200) }
            .onSuccess { items = it.content; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New bus")
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                error != null && items.isEmpty() -> CenterStatus(error!!, error = true, onRetry = onRefresh, refreshing = pull.refreshing)
                loading -> CircularProgressIndicator(Modifier.padding(top = 32.dp))
                items.isEmpty() -> CenterStatus("No buses yet.", onRetry = onRefresh, refreshing = pull.refreshing)
                else -> PullList(pull.refreshing, onRefresh) {
                    if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
                    items(items) { b ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${b.registrationNo} Â· ${b.busType ?: "â€”"}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${b.depotName ?: "â€”"} / ${b.townName ?: "â€”"} Â· ${b.status}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = b.enabled,
                                    onCheckedChange = { en -> scope.launch {
                                        runCatching { app.api.toggleBus(app.auth(), b.id, en) }
                                            .onSuccess { refresh() }
                                            .onFailure { error = it.message }
                                    } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        FleetCreateDialog(app, onDismiss = { showForm = false }, onCreated = { showForm = false; scope.launch { refresh() } })
    }
}

@Composable
private fun FleetCreateDialog(app: KaBusAdminApp, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var capacity by remember { mutableStateOf("40") }
    var fuel by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var busNumbers by remember { mutableStateOf(listOf<BusNumberItem>()) }
    var busNumberId by remember { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val org = rememberOrgPicker(app)

    // A vehicle always runs on an existing bus number of its own depot.
    LaunchedEffect(org.selectedDepotId) {
        val depotId = org.selectedDepotId
        busNumberId = null
        busNumbers = if (depotId == null) emptyList() else runCatching {
            app.api.busNumbersForDepot(app.auth(), depotId)
        }.getOrElse {
            error = it.message
            emptyList()
        }
    }

    val canSave = busNumberId != null &&
        capacity.toIntOrNull() != null &&
        org.selectedDepotId != null && org.selectedTownId != null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New bus") },
        text = {
            PickerDialogBody {
                OrgPickerFields(org)
                Spacer(Modifier.height(8.dp))
                PickerDropdownField(
                    label = "Bus number",
                    options = busNumbers.map { it.id to "${it.busNumber} - ${it.busType}" },
                    selectedId = busNumberId,
                    enabled = org.selectedDepotId != null,
                    emptyMessage = "This depot has no bus numbers yet. Add one under Fleet > Bus Numbers.",
                    onSelect = { busNumberId = it }
                )
                OutlinedTextField(capacity, { capacity = it }, label = { Text("Capacity") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(fuel, { fuel = it }, label = { Text("Fuel type (optional)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(make, { make = it }, label = { Text("Make/model (optional)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            app.api.createBus(
                                app.auth(),
                                CreateBusRequest(
                                    busNumberId = busNumberId!!,
                                    capacity = capacity.toInt(),
                                    fuelType = fuel.trim().ifBlank { null },
                                    makeModel = make.trim().ifBlank { null },
                                    manufactureYear = null,
                                    gpsDeviceId = null,
                                    gpsEnabled = null,
                                    status = null,
                                    depotId = org.selectedDepotId!!,
                                    townId = org.selectedTownId!!
                                )
                            )
                        }
                            .onSuccess { onCreated() }
                            .onFailure { error = it.message; saving = false }
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") } }
    )
}

// ----------------------------------------------------------------------
// Staff
// ----------------------------------------------------------------------

@Composable
internal fun StaffScreen(app: KaBusAdminApp) {
    var items by remember { mutableStateOf(listOf<StaffItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pull = remember { RefreshController() }

    suspend fun refresh() {
        runCatching { app.api.staff(app.auth(), null, 0, 200) }
            .onSuccess { items = it.content; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New staff")
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                error != null && items.isEmpty() -> CenterStatus(error!!, error = true, onRetry = onRefresh, refreshing = pull.refreshing)
                loading -> CircularProgressIndicator(Modifier.padding(top = 32.dp))
                items.isEmpty() -> CenterStatus("No staff yet.", onRetry = onRefresh, refreshing = pull.refreshing)
                else -> PullList(pull.refreshing, onRefresh) {
                    if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
                    items(items) { s ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.fullName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${s.designation ?: "â€”"} Â· ${s.empCode ?: "â€”"} Â· ${s.status}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(s.depotName ?: "", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        StaffCreateDialog(app, onDismiss = { showForm = false }, onCreated = { showForm = false; scope.launch { refresh() } })
    }
}

@Composable
private fun StaffCreateDialog(app: KaBusAdminApp, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var empCode by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val org = rememberOrgPicker(app)

    val canSave = name.isNotBlank() && org.selectedDepotId != null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New staff") },
        text = {
            PickerDialogBody {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(empCode, { empCode = it }, label = { Text("Employee code (optional)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(designation, { designation = it }, label = { Text("Designation (optional)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OrgPickerFields(org, requireTown = false)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            app.api.createStaff(
                                app.auth(),
                                CreateStaffRequest(
                                    fullName = name.trim(),
                                    phone = phone.trim().ifBlank { null },
                                    empCode = empCode.trim().ifBlank { null },
                                    designation = designation.trim().ifBlank { null },
                                    status = "ACTIVE",
                                    depotId = org.selectedDepotId!!,
                                    townId = org.selectedTownId,
                                    userId = null
                                )
                            )
                        }
                            .onSuccess { onCreated() }
                            .onFailure { error = it.message; saving = false }
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") } }
    )
}

// ----------------------------------------------------------------------
// Routes
// ----------------------------------------------------------------------

@Composable
internal fun RoutesScreen(app: KaBusAdminApp, canManage: Boolean = true) {
    var items by remember { mutableStateOf(listOf<RouteItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var editStops by remember { mutableStateOf<RouteItem?>(null) }
    val scope = rememberCoroutineScope()
    val pull = remember { RefreshController() }

    suspend fun refresh() {
        runCatching { app.api.routes(app.auth(), null, 0, 200) }
            .onSuccess { items = it.content; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    Scaffold(
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(onClick = { showForm = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New route")
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                error != null && items.isEmpty() -> CenterStatus(error!!, error = true, onRetry = onRefresh, refreshing = pull.refreshing)
                loading -> CircularProgressIndicator(Modifier.padding(top = 32.dp))
                items.isEmpty() -> CenterStatus("No routes yet.", onRetry = onRefresh, refreshing = pull.refreshing)
                else -> PullList(pull.refreshing, onRefresh) {
                    if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
                    items(items) { r ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${r.code} Â· ${r.name}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${r.origin} â†’ ${r.destination} Â· ${r.stopCount} stops Â· ${r.distanceKm ?: "â€”"} km",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (canManage) {
                                    IconButton(onClick = { editStops = r }) {
                                        Icon(
                                            Icons.Filled.List,
                                            contentDescription = "Edit stops for ${r.code}"
                                        )
                                    }
                                    Switch(
                                        checked = r.enabled,
                                        onCheckedChange = { en -> scope.launch {
                                            runCatching { app.api.toggleRoute(app.auth(), r.id, en) }
                                                .onSuccess { refresh() }
                                                .onFailure { error = it.message }
                                        } }
                                    )
                                } else {
                                    Text(
                                        if (r.enabled) "Enabled" else "Disabled",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        RouteCreateDialog(app, onDismiss = { showForm = false }, onCreated = { showForm = false; scope.launch { refresh() } })
    }

    editStops?.let { target ->
        RouteStopsDialog(
            app = app,
            route = target,
            onDismiss = { editStops = null },
            onSaved = { editStops = null; scope.launch { refresh() } }
        )
    }
}

@Composable
private fun RouteCreateDialog(app: KaBusAdminApp, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var distanceKm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val org = rememberOrgPicker(app)

    val canSave = code.isNotBlank() && name.isNotBlank() && origin.isNotBlank() &&
        destination.isNotBlank() && org.selectedDivisionId != null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New route") },
        text = {
            PickerDialogBody {
                OutlinedTextField(code, { code = it }, label = { Text("Route code") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Route name") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(origin, { origin = it }, label = { Text("Origin") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(destination, { destination = it }, label = { Text("Destination") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                // Routes are division-level: no depot/town involved.
                OrgPickerFields(org, includeDepot = false)
                OutlinedTextField(distanceKm, { distanceKm = it }, label = { Text("Distance km (optional)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            app.api.createRoute(
                                app.auth(),
                                CreateRouteRequest(
                                    divisionId = org.selectedDivisionId,
                                    code = code.trim(),
                                    name = name.trim(),
                                    origin = origin.trim(),
                                    destination = destination.trim(),
                                    distanceKm = distanceKm.toDoubleOrNull(),
                                    estDurationMin = null,
                                    status = null
                                )
                            )
                        }
                            .onSuccess { onCreated() }
                            .onFailure { error = it.message; saving = false }
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") } }
    )
}

// ----------------------------------------------------------------------
// Admin users (SUPER_ADMIN)
// ----------------------------------------------------------------------

@Composable
fun UsersScreen(app: KaBusAdminApp) {
    var items by remember { mutableStateOf(listOf<AdminUserItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pull = remember { RefreshController() }

    suspend fun refresh() {
        runCatching { app.api.adminUsers(app.auth(), null, 0, 100) }
            .onSuccess { items = it.content; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New user")
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                error != null && items.isEmpty() -> CenterStatus(error!!, error = true, onRetry = onRefresh, refreshing = pull.refreshing)
                loading -> CircularProgressIndicator(Modifier.padding(top = 32.dp))
                items.isEmpty() -> CenterStatus("No managed users yet.", onRetry = onRefresh, refreshing = pull.refreshing)
                else -> PullList(pull.refreshing, onRefresh) {
                    if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
                    items(items) { u ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("@${u.username}", style = MaterialTheme.typography.titleSmall)
                                    Text("${u.role ?: "â€”"} Â· ${u.fullName}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        listOfNotNull(u.divisionName, u.depotName, u.townName).joinToString(" / "),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Switch(
                                    checked = u.enabled,
                                    onCheckedChange = { en -> scope.launch {
                                        runCatching { app.api.toggleUser(app.auth(), u.id, en) }
                                            .onSuccess { refresh() }
                                            .onFailure { error = it.message }
                                    } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        UserCreateDialog(app, onDismiss = { showForm = false }, onCreated = { showForm = false; scope.launch { refresh() } })
    }
}

@Composable
private fun UserCreateDialog(app: KaBusAdminApp, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("DIVISION_ADMIN") }
    var divisionId by remember { mutableStateOf("") }
    var depotId by remember { mutableStateOf("") }
    var townId by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val roles = listOf("DIVISION_ADMIN", "DIVISION_MANAGER", "DEPOT_HEAD", "TOWN_MANAGER")
    val canSave = username.isNotBlank() && fullName.isNotBlank() && password.length >= 8

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New admin user") },
        text = {
            Column {
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password (min 8)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                roles.forEach { r ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == r, onClick = { role = r })
                        Text(r, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                OutlinedTextField(divisionId, { divisionId = it }, label = { Text("Division ID (required for DIVISION roles)") }, singleLine = true, modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(depotId, { depotId = it }, label = { Text("Depot ID (required for DEPOT_HEAD)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(townId, { townId = it }, label = { Text("Town ID (required for TOWN_MANAGER)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            app.api.createAdminUser(
                                app.auth(),
                                CreateUserRequest(
                                    username = username.trim(),
                                    fullName = fullName.trim(),
                                    email = email.trim().ifBlank { null },
                                    phone = phone.trim().ifBlank { null },
                                    password = password,
                                    role = role,
                                    divisionId = divisionId.toLongOrNull(),
                                    depotId = depotId.toLongOrNull(),
                                    townId = townId.toLongOrNull(),
                                    enabled = true
                                )
                            )
                        }
                            .onSuccess { onCreated() }
                            .onFailure { error = it.message; saving = false }
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") } }
    )
}

// ----------------------------------------------------------------------
// Audit logs (SUPER_ADMIN)
// ----------------------------------------------------------------------

@Composable
fun AuditLogsScreen(app: KaBusAdminApp) {
    var items by remember { mutableStateOf(listOf<AuditItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val pull = remember { RefreshController() }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        runCatching { app.api.auditLogs(app.auth(), null, null, 0, 100) }
            .onSuccess { items = it.content; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    when {
        error != null && items.isEmpty() -> CenterStatus(error!!, error = true, onRetry = onRefresh, refreshing = pull.refreshing)
        loading -> CenterStatus("Loadingâ€¦")
        items.isEmpty() -> CenterStatus("No audit entries.", onRetry = onRefresh, refreshing = pull.refreshing)
        else -> PullList(pull.refreshing, onRefresh) {
            if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
            items(items) { a ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${a.action ?: "â€”"} Â· ${a.resourceType ?: "â€”"}#${a.resourceId ?: "â€”"}", style = MaterialTheme.typography.titleSmall)
                        Text("by ${a.username ?: "â€”"} from ${a.ipAddress ?: "â€”"}", style = MaterialTheme.typography.bodySmall)
                        Text(a.createdAt ?: "", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Settings (SUPER_ADMIN)
// ----------------------------------------------------------------------

@Composable
fun SettingsScreen(app: KaBusAdminApp) {
    var items by remember { mutableStateOf(listOf<SettingItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<SettingItem?>(null) }
    val scope = rememberCoroutineScope()
    val pull = remember { RefreshController() }

    suspend fun refresh() {
        runCatching { app.api.settings(app.auth()) }
            .onSuccess { items = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    when {
        error != null && items.isEmpty() -> CenterStatus(error!!, error = true, onRetry = onRefresh, refreshing = pull.refreshing)
        loading -> CenterStatus("Loadingâ€¦")
        items.isEmpty() -> CenterStatus("No settings.", onRetry = onRefresh, refreshing = pull.refreshing)
        else -> PullList(pull.refreshing, onRefresh) {
            if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
            items(items) { s ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).clickable { editing = s }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.key, style = MaterialTheme.typography.titleSmall)
                        Text(s.description ?: "", style = MaterialTheme.typography.bodySmall)
                        Text("Value: ${s.value ?: "(unset)"}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    editing?.let { s ->
        var value by remember { mutableStateOf(s.value ?: "") }
        var saving by remember { mutableStateOf(false) }
        var saveError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!saving) editing = null },
            title = { Text(s.key) },
            text = {
                Column {
                    Text(s.description ?: "", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value, { value = it }, label = { Text("Value (${s.type ?: "string"})") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            runCatching { app.api.updateSetting(app.auth(), s.key, UpdateSettingRequest(value.trim())) }
                                .onSuccess { editing = null; refresh() }
                                .onFailure { saveError = it.message; saving = false }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { if (!saving) editing = null }) { Text("Cancel") } }
        )
    }
}