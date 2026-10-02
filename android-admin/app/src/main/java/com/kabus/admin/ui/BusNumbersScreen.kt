package com.kabus.admin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.kabus.admin.data.BusNumberItem
import com.kabus.admin.data.CreateBusNumberRequest
import com.kabus.admin.data.UpdateBusNumberRequest
import kotlinx.coroutines.launch

/**
 * The bus number master. This is the parent of Fleet: a bus number belongs to a
 * depot and a town, and vehicles run on one. Kept as its own screen so the
 * distinction between "the service" and "the bus" stays obvious.
 *
 * Authorization is entirely server-side; this screen only shows what the
 * scoped `/api/admin/fleet/bus-numbers` endpoints return for the caller.
 */
@Composable
internal fun BusNumbersScreen(app: KaBusAdminApp) {
    var items by remember { mutableStateOf(listOf<BusNumberItem>()) }
    var search by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<BusNumberItem?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pull = remember { RefreshController() }

    suspend fun refresh() {
        runCatching { app.api.busNumbers(app.auth(), search.trim().ifBlank { null }, 0, 200) }
            .onSuccess { items = it.content; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(search) { refresh() }

    val onRefresh: () -> Unit = { scope.launch { pull.run { refresh() } } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New bus number")
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search bus number") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Box(Modifier.fillMaxSize()) {
                    when {
                        error != null && items.isEmpty() ->
                            CenterStatus(error!!, error = true, onRetry = onRefresh, refreshing = pull.refreshing)
                        loading -> CircularProgressIndicator(Modifier.padding(top = 32.dp))
                        items.isEmpty() -> CenterStatus("No bus numbers yet.", onRetry = onRefresh, refreshing = pull.refreshing)
                        else -> PullList(pull.refreshing, onRefresh) {
                            if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
                            items(items) { bn ->
                                BusNumberCard(
                                    item = bn,
                                    onToggle = { en ->
                                        scope.launch {
                                            runCatching { app.api.toggleBusNumber(app.auth(), bn.id, en) }
                                                .onSuccess { refresh() }
                                                .onFailure { error = it.message }
                                        }
                                    },
                                    onEdit = { editing = bn },
                                    onDelete = {
                                        scope.launch {
                                            runCatching { app.api.deleteBusNumber(app.auth(), bn.id) }
                                                .onSuccess { refresh() }
                                                .onFailure { error = it.message }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        BusNumberDialog(
            app = app,
            existing = null,
            onDismiss = { showCreate = false },
            onSaved = { showCreate = false; scope.launch { refresh() } }
        )
    }

    editing?.let { target ->
        BusNumberDialog(
            app = app,
            existing = target,
            onDismiss = { editing = null },
            onSaved = { editing = null; scope.launch { refresh() } }
        )
    }
}

@Composable
private fun BusNumberCard(
    item: BusNumberItem,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${item.busNumber} - ${item.busType}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "${item.depotName ?: "-"} / ${item.townName ?: "-"}" +
                        (item.vehicleCount?.takeIf { it > 0 }?.let { " - $it vehicle(s)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit ${item.busNumber}")
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${item.busNumber}")
            }
            Switch(checked = item.enabled, onCheckedChange = onToggle)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${item.busNumber}?") },
            text = { Text("This cannot be undone. A bus number with vehicles assigned cannot be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

/** Closed set enforced by the server as well; kept here so the UI cannot offer anything else. */
private val BUS_TYPES = listOf("ORDINARY", "EXPRESS", "RAJADHARSHA")

/**
 * Create/edit dialog. On create the full depot -> town hierarchy is required;
 * on edit the depot is fixed (moving a service between depots is a new record),
 * so only the type and the town are editable, with towns limited to the same depot.
 */
@Composable
private fun BusNumberDialog(
    app: KaBusAdminApp,
    existing: BusNumberItem?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var busNumber by remember { mutableStateOf(existing?.busNumber ?: "") }
    var busType by remember { mutableStateOf(existing?.busType ?: BUS_TYPES.first()) }
    var townId by remember { mutableStateOf(existing?.townId) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Edit keeps the recorded depot, so fetch its towns directly instead of
    // making the admin re-pick corporation/division just to change a type.
    val depotId = existing?.depotId
    var towns by remember { mutableStateOf(listOf<com.kabus.admin.data.TownOption>()) }
    val org = rememberOrgPicker(app)

    LaunchedEffect(depotId) {
        towns = if (depotId == null) emptyList() else runCatching {
            app.api.pickerTowns(app.auth(), depotId)
        }.getOrElse { emptyList() }
    }

    val isCreate = existing == null
    val canSave = if (isCreate) {
        busNumber.isNotBlank() && org.selectedDepotId != null && org.selectedTownId != null
    } else {
        townId != null
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (isCreate) "New bus number" else "Edit ${existing?.busNumber}") },
        text = {
            PickerDialogBody {
                if (isCreate) {
                    OutlinedTextField(
                        busNumber,
                        { busNumber = it.uppercase() },
                        label = { Text("Bus number") },
                        singleLine = true
                    )
                    OrgPickerFields(org, requireTown = true)
                } else {
                    Text(
                        "${existing?.depotName ?: "-"}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    PickerDropdownField(
                        label = "Town",
                        options = towns.map { it.id to "${it.code} - ${it.name}" },
                        selectedId = townId,
                        enabled = true,
                        emptyMessage = "This depot has no towns yet.",
                        onSelect = { townId = it }
                    )
                }
                // The dropdown carries Long ids, so index into BUS_TYPES to drive
                // selection rather than duplicating a second dropdown component.
                PickerDropdownField(
                    label = "Bus type",
                    options = BUS_TYPES.mapIndexed { i, t -> i.toLong() to t },
                    selectedId = BUS_TYPES.indexOf(busType).toLong(),
                    enabled = true,
                    onSelect = { busType = BUS_TYPES.getOrElse(it?.toInt() ?: 0) { BUS_TYPES.first() } }
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
                enabled = canSave && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            if (isCreate) {
                                app.api.createBusNumber(
                                    app.auth(),
                                    CreateBusNumberRequest(
                                        busNumber = busNumber.trim(),
                                        busType = busType,
                                        depotId = org.selectedDepotId!!,
                                        townId = org.selectedTownId!!
                                    )
                                )
                            } else {
                                app.api.updateBusNumber(
                                    app.auth(),
                                    existing!!.id,
                                    UpdateBusNumberRequest(busType = busType, townId = townId)
                                )
                            }
                        }
                            .onSuccess { onSaved() }
                            .onFailure { error = it.message; saving = false }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") }
        }
    )
}