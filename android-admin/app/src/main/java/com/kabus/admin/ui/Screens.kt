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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabus.admin.KaBusAdminApp
import com.kabus.admin.data.DashboardResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface DashCell {
    val key: String
    val fullSpan: Boolean

    data class Header(override val key: String, val title: String) : DashCell {
        override val fullSpan: Boolean get() = true
    }

    data class Stat(
        override val key: String,
        val label: String,
        val value: Long,
        val valueColor: Color = Color.Unspecified
    ) : DashCell {
        override val fullSpan: Boolean get() = false
    }

    data class Ad(override val key: String, val slot: String) : DashCell {
        override val fullSpan: Boolean get() = true
    }
}

private val StatusLive = Color(0xFF16A34A)
private val StatusStale = Color(0xFFF59E0B)
private val StatusOffline = Color(0xFF9CA3AF)

@Composable
fun DashboardScreen(app: KaBusAdminApp) {
    var dash by remember { mutableStateOf<DashboardResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val refresh = remember { RefreshController() }
    val scope = rememberCoroutineScope()

    suspend fun fetchDashboard() {
        runCatching { app.api.dashboard("Bearer ${app.session.accessToken}") }
            .onSuccess { dash = it; error = null }
            .onFailure { error = it.message }
    }

    LaunchedEffect(Unit) {
        while (true) {
            fetchDashboard()
            delay(15_000) // auto-refresh
        }
    }

    val data = dash
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            else CircularProgressIndicator()
        }
        return
    }

    val cells = buildList {
        add(DashCell.Ad("ad-top", "ADMIN_APP_DASHBOARD"))
        add(DashCell.Header("h-live", "Live"))
        add(DashCell.Stat("v-live", "Live", data.busesLive, StatusLive))
        add(DashCell.Stat("v-stale", "Stale", data.busesStale, StatusStale))
        add(DashCell.Stat("v-offline", "Offline", data.busesOffline, StatusOffline))
        add(DashCell.Header("h-operations", "Operations"))
        add(DashCell.Stat("v-trips", "Active trips", data.activeTrips))
        add(DashCell.Stat("v-gps", "GPS sessions", data.activeGpsSessions))
        add(DashCell.Stat("v-buses", "Buses", data.buses))
        add(DashCell.Stat("v-routes", "Routes", data.routes))
        add(DashCell.Header("h-org", "Organization"))
        add(DashCell.Stat("v-corps", "Corporations", data.corporations))
        add(DashCell.Stat("v-divs", "Divisions", data.divisions))
        add(DashCell.Stat("v-depots", "Depots", data.depots))
        add(DashCell.Stat("v-towns", "Towns", data.towns))
        add(DashCell.Header("h-staff", "Staff"))
        add(DashCell.Stat("v-crew", "Crew", data.crew))
        add(DashCell.Stat("v-staff", "Staff", data.staff))
    }

    val onRefresh: () -> Unit = { scope.launch { refresh.run { fetchDashboard() } } }

    RefreshableBox(
        refreshing = refresh.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RefreshErrorBanner(error!!, onRetry = onRefresh)
                }
            }
            itemsIndexed(
                items = cells,
                key = { _, cell -> cell.key },
                span = { _, cell -> if (cell.fullSpan) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
            ) { _, cell ->
                when (cell) {
                    is DashCell.Header -> Text(
                        cell.title.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    is DashCell.Stat -> DashStatCard(cell)
                    is DashCell.Ad -> AdBanner(cell.slot)
                }
            }
        }
    }
}

@Composable
private fun DashStatCard(cell: DashCell.Stat) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                cell.value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontSize = 24.sp,
                color = if (cell.valueColor == Color.Unspecified) MaterialTheme.colorScheme.primary else cell.valueColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                cell.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TripsScreen(app: KaBusAdminApp) {
    var page by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf(listOf<com.kabus.admin.data.TripItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val refresh = remember { RefreshController() }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        runCatching { app.api.trips("Bearer ${app.session.accessToken}", null, null, null, page, 20) }
            .onSuccess { items = it.content; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(page) { load() }

    val onRefresh: () -> Unit = { scope.launch { refresh.run { load() } } }

    RefreshableBox(
        refreshing = refresh.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            if (error != null && items.isNotEmpty()) {
                item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
            }
            if (loading && items.isEmpty()) item { CircularProgressIndicator() }
            if (error != null && items.isEmpty()) {
                item { Text(error!!, color = MaterialTheme.colorScheme.error) }
            }
            items(items.size) { i ->
                val t = items[i]
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${t.tripNumber} · ${t.busRegistrationNo ?: "—"}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${t.routeName ?: "—"} · ${t.status}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Dep: ${t.scheduledDeparture ?: "—"} · Arr: ${t.scheduledArrival ?: "—"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveBusesScreen(app: KaBusAdminApp) {
    var items by remember { mutableStateOf(listOf<com.kabus.admin.data.LiveBusItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val refresh = remember { RefreshController() }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        runCatching { app.api.liveBuses("Bearer ${app.session.accessToken}", 200) }
            .onSuccess { items = it; error = null }
            .onFailure { error = it.message }
    }

    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(10_000)
        }
    }

    val onRefresh: () -> Unit = { scope.launch { refresh.run { load() } } }

    if (error != null && items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp))
                RefreshAction(refreshing = refresh.refreshing, onRefresh = onRefresh)
            }
        }
        return
    }

    RefreshableBox(
        refreshing = refresh.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
            items(items.size) { i ->
                val b = items[i]
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.registrationNo, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${b.routeName ?: "No trip"} · ${b.speedKmh?.let { "%.0f km/h".format(it) } ?: "—"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(b.capturedAt ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusChip(b.status)
                    }
                }
            }
        }
    }
}

@Composable
fun CrewScreen(app: KaBusAdminApp) {
    var items by remember { mutableStateOf(listOf<com.kabus.admin.data.CrewItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val refresh = remember { RefreshController() }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        runCatching { app.api.crew("Bearer ${app.session.accessToken}", 200) }
            .onSuccess { items = it; error = null }
            .onFailure { error = it.message }
    }

    LaunchedEffect(Unit) { load() }

    val onRefresh: () -> Unit = { scope.launch { refresh.run { load() } } }

    if (error != null && items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp))
                RefreshAction(refreshing = refresh.refreshing, onRefresh = onRefresh)
            }
        }
        return
    }

    RefreshableBox(
        refreshing = refresh.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
            items(items.size) { i ->
                val c = items[i]
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.fullName ?: "—", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${c.badgeNo ?: "—"} · ${c.crewType ?: "—"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text("Duty: ${c.dutyStatus ?: "—"}", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusChip(c.status ?: "UNKNOWN", dotOnly = true)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(app: KaBusAdminApp) {
    var items by remember { mutableStateOf(listOf<com.kabus.admin.data.NotificationItem>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val refresh = remember { RefreshController() }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        runCatching { app.api.notifications("Bearer ${app.session.accessToken}", 0, 50) }
            .onSuccess { items = it.content; error = null }
            .onFailure { error = it.message }
    }

    LaunchedEffect(Unit) { load() }

    val onRefresh: () -> Unit = { scope.launch { refresh.run { load() } } }

    if (error != null && items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp))
                RefreshAction(refreshing = refresh.refreshing, onRefresh = onRefresh)
            }
        }
        return
    }

    RefreshableBox(
        refreshing = refresh.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            if (error != null) item { RefreshErrorBanner(error!!, onRetry = onRefresh) }
            items(items.size) { i ->
                val n = items[i]
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            if (!n.isRead) {
                                scope.launch {
                                    runCatching {
                                        app.api.markNotificationRead(
                                            "Bearer ${app.session.accessToken}",
                                            n.id
                                        )
                                    }
                                    items = items.map {
                                        if (it.id == n.id) it.copy(isRead = true) else it
                                    }
                                }
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (n.isRead) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall)
                        n.body?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(n.type, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String, dotOnly: Boolean = false) {
    val color = when {
        status.equals("LIVE", true) -> MaterialTheme.colorScheme.primary
        status.equals("ACTIVE", true) -> androidx.compose.ui.graphics.Color(0xFF16A34A)
        status.equals("STALE", true) -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
        else -> androidx.compose.ui.graphics.Color(0xFF9CA3AF)
    }
    Text(
        if (dotOnly) "● ${status.replace('_', ' ')}" else status.replace('_', ' '),
        color = color,
        style = MaterialTheme.typography.labelMedium
    )
}