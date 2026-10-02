package com.kabus.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kabus.admin.KaBusAdminApp
import com.kabus.admin.data.ReplaceStopsRequest
import com.kabus.admin.data.RouteItem
import com.kabus.admin.data.RouteStopInput
import kotlinx.coroutines.launch

/**
 * Editable row for one stop. [stopOrder] is derived from list position on save,
 * so the admin never types an order number and cannot create a duplicate.
 */
private class StopDraft(
    name: String,
    latitude: String,
    longitude: String,
    distance: String
) {
    var name by mutableStateOf(name)
    var latitude by mutableStateOf(latitude)
    var longitude by mutableStateOf(longitude)
    var distance by mutableStateOf(distance)

    val valid: Boolean
        get() = name.isNotBlank() &&
            latitude.toDoubleOrNull() != null &&
            longitude.toDoubleOrNull() != null
}

private fun StopDraft.toInput(order: Int): RouteStopInput = RouteStopInput(
    stopOrder = order,
    stopName = name.trim(),
    latitude = latitude.toDouble(),
    longitude = longitude.toDouble(),
    distanceFromStart = distance.toDoubleOrNull()
)

/**
 * Whole-list stop editor for one route.
 *
 * The API replaces every stop at once (`PUT /api/admin/routes/{id}/stops`), so
 * this screen edits a local draft and saves once. Reordering renumbers stops
 * from 1; the server rejects duplicate orders, and this UI cannot produce them.
 *
 * A route needs no stops to exist, so an empty list is a valid save (it clears
 * them).
 */
@Composable
internal fun RouteStopsDialog(
    app: KaBusAdminApp,
    route: RouteItem,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val drafts = remember(route.id) {
        mutableStateListOf<StopDraft>().apply {
            route.stops.sortedBy { it.stopOrder }.forEach {
                add(
                    StopDraft(
                        name = it.stopName,
                        latitude = it.latitude?.toString() ?: "",
                        longitude = it.longitude?.toString() ?: "",
                        distance = it.distanceFromStart?.toString() ?: ""
                    )
                )
            }
        }
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val invalid = drafts.any { !it.valid }
    val canSave = !invalid && !saving

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Stops - ${route.code}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${route.origin} to ${route.destination}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (drafts.isEmpty()) {
                    Text(
                        "No stops yet. Add the places this route calls at, in order.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                drafts.forEachIndexed { index, draft ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Stop ${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        drafts.removeAt(index)
                                        drafts.add(index - 1, draft)
                                    }
                                ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move stop ${index + 1} up") }
                                IconButton(
                                    enabled = index < drafts.lastIndex,
                                    onClick = {
                                        drafts.removeAt(index)
                                        drafts.add(index + 1, draft)
                                    }
                                ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move stop ${index + 1} down") }
                                IconButton(onClick = { drafts.remove(draft) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove stop ${index + 1}")
                                }
                            }
                            OutlinedTextField(
                                draft.name,
                                { draft.name = it },
                                label = { Text("Stop name") },
                                singleLine = true
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    draft.latitude,
                                    { draft.latitude = it },
                                    label = { Text("Latitude") },
                                    singleLine = true,
                                    isError = draft.latitude.isNotBlank() && draft.latitude.toDoubleOrNull() == null,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    draft.longitude,
                                    { draft.longitude = it },
                                    label = { Text("Longitude") },
                                    singleLine = true,
                                    isError = draft.longitude.isNotBlank() && draft.longitude.toDoubleOrNull() == null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                draft.distance,
                                { draft.distance = it },
                                label = { Text("Distance from start km (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        drafts.add(
                            StopDraft(
                                name = "",
                                latitude = "",
                                longitude = "",
                                distance = ""
                            )
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Add stop", modifier = Modifier.padding(start = 4.dp))
                }
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
                enabled = canSave,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            app.api.replaceRouteStops(
                                app.auth(),
                                route.id,
                                ReplaceStopsRequest(
                                    stops = drafts.mapIndexed { i, d -> d.toInput(i + 1) }
                                )
                            )
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