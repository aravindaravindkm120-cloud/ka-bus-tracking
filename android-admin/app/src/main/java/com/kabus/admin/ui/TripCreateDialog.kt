package com.kabus.admin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kabus.admin.KaBusAdminApp
import com.kabus.admin.data.AssignCrewRequest
import com.kabus.admin.data.BusItem
import com.kabus.admin.data.CreateTripRequestBody
import com.kabus.admin.data.CrewItem
import com.kabus.admin.data.RouteItem
import com.kabus.admin.data.TripItem
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Creates a trip.
 *
 * The server requires a route *and* a bus, and rejects a bus from a different
 * division than the route. So the UI narrows in the same order the constraint
 * does: choose a division, then a route in it, then a bus in it. Departure and
 * arrival are validated client-side too, since the server also rejects an
 * arrival before the departure and a round trip would otherwise cost an extra
 * round trip to discover that.
 *
 * Crew is intentionally not part of this dialog: crew must belong to the trip
 * bus's depot, and the crew list endpoint does not expose depot, so the list
 * cannot be narrowed here. Crew is assigned from the trip instead.
 */
@Composable
internal fun TripCreateDialog(
    app: KaBusAdminApp,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    var routes by remember { mutableStateOf(listOf<RouteItem>()) }
    var buses by remember { mutableStateOf(listOf<BusItem>()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var routeId by remember { mutableStateOf<Long?>(null) }
    var busId by remember { mutableStateOf<Long?>(null) }
    var tripNumber by remember { mutableStateOf("") }
    var tripDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var departure by remember { mutableStateOf("08:00") }
    var arrival by remember { mutableStateOf("10:00") }
    var direction by remember { mutableStateOf("OUTBOUND") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { app.api.routes(app.auth(), null, 0, 200) }
            .onSuccess { routes = it.content }
            .onFailure { loadError = it.message }
        runCatching { app.api.buses(app.auth(), null, 0, 200) }
            .onSuccess { buses = it.content }
            .onFailure { if (loadError == null) loadError = it.message }
    }

    // The bus must sit in the route's division; filter here rather than let the
    // admin pick a valid-looking bus and hit a server rejection.
    val selectedRoute = routes.firstOrNull { it.id == routeId }
    val eligibleBuses = buses.filter { it.divisionId != null && it.divisionId == selectedRoute?.divisionId }

    val departureTime = parseDateTime(tripDate, departure)
    val arrivalTime = parseDateTime(tripDate, arrival)
    val timesValid = departureTime != null && arrivalTime != null &&
        !arrivalTime.isBefore(departureTime)

    val canSave = routeId != null && busId != null && timesValid && !saving

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New trip") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (loadError != null) {
                    Text(
                        loadError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                PickerDropdownField(
                    label = "Route",
                    options = routes.map { it.id to "${it.code} - ${it.name}" },
                    selectedId = routeId,
                    enabled = routes.isNotEmpty(),
                    emptyMessage = if (routes.isEmpty()) "No routes available in your scope." else null,
                    onSelect = { routeId = it }
                )
                PickerDropdownField(
                    label = "Bus",
                    options = eligibleBuses.map { it.id to "${it.registrationNo}" },
                    selectedId = busId,
                    enabled = selectedRoute != null && eligibleBuses.isNotEmpty(),
                    emptyMessage = when {
                        selectedRoute == null -> "Select a route first"
                        else -> "No buses in this division. Add one under Fleet."
                    },
                    onSelect = { busId = it }
                )
                OutlinedTextField(
                    tripNumber,
                    { tripNumber = it },
                    label = { Text("Trip number (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    "Left blank, the server generates one from the route and date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    tripDate,
                    { tripDate = it },
                    label = { Text("Trip date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = parseLocalDate(tripDate) == null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        departure,
                        { departure = it },
                        label = { Text("Departure") },
                        singleLine = true,
                        isError = parseLocalTime(departure) == null,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        arrival,
                        { arrival = it },
                        label = { Text("Arrival") },
                        singleLine = true,
                        isError = parseLocalTime(arrival) == null || !timesValid,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
                PickerDropdownField(
                    label = "Direction",
                    options = DIRECTIONS.mapIndexed { i, d -> i.toLong() to d },
                    selectedId = DIRECTIONS.indexOf(direction).toLong(),
                    enabled = true,
                    onSelect = { direction = DIRECTIONS.getOrElse(it?.toInt() ?: 0) { DIRECTIONS.first() } }
                )
                if (departureTime != null && arrivalTime != null && !timesValid) {
                    Text(
                        "Arrival cannot be before departure.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                            app.api.createTrip(
                                app.auth(),
                                CreateTripRequestBody(
                                    routeId = routeId!!,
                                    busId = busId!!,
                                    tripNumber = tripNumber.trim().ifBlank { null },
                                    tripDate = parseLocalDate(tripDate)!!.toString(),
                                    scheduledDeparture = departureTime!!.toString(),
                                    scheduledArrival = arrivalTime!!.toString(),
                                    direction = direction,
                                    status = "SCHEDULED"
                                )
                            )
                        }
                            .onSuccess { onCreated() }
                            .onFailure { error = it.message; saving = false }
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") }
        }
    )
}

/** Closed set enforced by the server's `OUTBOUND|INBOUND` pattern. */
private val DIRECTIONS = listOf("OUTBOUND", "INBOUND")

private fun parseLocalDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.trim()) }.getOrNull()

private fun parseLocalTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value.trim()) }.getOrNull()

private fun parseDateTime(date: String, time: String): LocalDateTime? {
    val d = parseLocalDate(date) ?: return null
    val t = parseLocalTime(time) ?: return null
    return LocalDateTime.of(d, t)
}

/**
 * Assigns crew to an existing trip.
 *
 * The server requires the crew's depot to match the trip bus's depot, but the
 * crew list endpoint does not return depot, so the list cannot be narrowed
 * client-side. Rather than hide that, the full scoped list is offered and the
 * server's mismatch message is surfaced verbatim in the dialog.
 */
@Composable
internal fun TripCrewDialog(
    app: KaBusAdminApp,
    trip: TripItem,
    onDismiss: () -> Unit,
    onAssigned: () -> Unit
) {
    var crew by remember { mutableStateOf(listOf<CrewItem>()) }
    var crewId by remember { mutableStateOf<Long?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { app.api.crew(app.auth(), 200) }
            .onSuccess { crew = it }
            .onFailure { loadError = it.message }
    }

    val assignedIds = trip.crewAssignments.map { it.crewId }.toSet()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Assign crew - ${trip.tripNumber}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "${trip.busRegistrationNo ?: "No bus"} at ${trip.depotName ?: "unknown depot"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (trip.crewAssignments.isNotEmpty()) {
                    Text(
                        "Already on: " + trip.crewAssignments.joinToString { it.fullName ?: it.badgeNo ?: "#${it.crewId}" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                PickerDropdownField(
                    label = "Crew",
                    options = crew.map { it.crewId to "${it.fullName ?: "-"} (${it.badgeNo ?: "no badge"})" },
                    selectedId = crewId,
                    enabled = crew.isNotEmpty(),
                    emptyMessage = loadError ?: if (crew.isEmpty()) "No crew available in your scope." else null,
                    onSelect = { crewId = it }
                )
                if (crewId != null && crewId in assignedIds) {
                    Text(
                        "This crew member is already assigned to the trip.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                enabled = crewId != null && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            app.api.assignTripCrew(app.auth(), trip.id, AssignCrewRequest(crewId!!))
                        }
                            .onSuccess { onAssigned() }
                            .onFailure { error = it.message; saving = false }
                    }
                }
            ) { Text("Assign") }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) onDismiss() }) { Text("Cancel") }
        }
    )
}