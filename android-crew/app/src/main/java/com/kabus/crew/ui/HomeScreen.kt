package com.kabus.crew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabus.crew.KaBusCrewApp
import com.kabus.crew.BuildConfig
import com.kabus.crew.data.Assignment
import com.kabus.crew.data.CrewStatus
import com.kabus.crew.data.RefreshRequest
import com.kabus.crew.data.GpsStartRequest
import com.kabus.crew.data.NotificationItem
import com.kabus.crew.service.LocationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(app: KaBusCrewApp, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var assignment by remember { mutableStateOf<Assignment?>(null) }
    var status by remember { mutableStateOf<CrewStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sessionActive by remember { mutableStateOf(app.session.gpsSessionKey != null) }

    val signOut: () -> Unit = {
        scope.launch {
            val refresh = app.session.refreshToken
            if (!refresh.isNullOrBlank()) {
                runCatching { app.api.logout(RefreshRequest(refresh)) }
            }
            val key = app.session.gpsSessionKey
            if (!key.isNullOrBlank()) {
                runCatching { app.api.gpsEnd("Bearer ${app.session.accessToken}", key) }
            }
            LocationService.stop(context)
            app.session.clear()
            onSignOut()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching { app.api.status("Bearer ${app.session.accessToken}") }
                .onSuccess { st ->
                    status = st
                    assignment = st.assignment
                    if (st.gpsStatus.equals("ACTIVE", true)) {
                        sessionActive = true
                    }
                }
                .onFailure { error = it.message }
            loading = false
            delay(10_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Signed in: ${app.session.cachedName ?: "—"}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = signOut,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Sign out")
                }
            }

            Spacer(Modifier.height(10.dp))
            val a = assignment
            if (a?.hasAssignment == true) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Bus ${a.busRegistration} (${a.busType ?: "—"})", style = MaterialTheme.typography.titleMedium)
                        Text("Trip ${a.tripNumber ?: "—"} · ${a.routeName ?: "—"} (${a.routeCode ?: "—"})", style = MaterialTheme.typography.bodyMedium)
                        Text("${a.origin ?: "—"} → ${a.destination ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                        Text("Crew: ${a.crewName ?: "—"} (${a.crewType ?: "—"} · ${a.badgeNo ?: "—"})", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        GpsStatusChip(status)
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("No active assignment", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Your depot has not assigned you a bus/trip yet. GPS Start will be enabled once an assignment exists.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // GPS controls - Start/End decision is always sent to the server;
            // the app only mirrors the server-returned session state.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            error = null
                            runCatching {
                                app.api.gpsStart(
                                    "Bearer ${app.session.accessToken}",
                                    GpsStartRequest(app.session.deviceId, BuildConfig.VERSION_NAME)
                                )
                            }.onSuccess { res ->
                                val key = res.sessionKey
                                if (!key.isNullOrBlank()) {
                                    app.session.gpsSessionKey = key
                                    sessionActive = true
                                    LocationService.start(context, key)
                                } else {
                                    error = "No session key returned"
                                }
                            }.onFailure { error = it.message }
                        }
                    },
                    enabled = !sessionActive && a?.hasAssignment == true,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start live GPS")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            error = null
                            val key = app.session.gpsSessionKey
                            if (!key.isNullOrBlank()) {
                                runCatching {
                                    app.api.gpsEnd("Bearer ${app.session.accessToken}", key)
                                }
                                LocationService.stop(context)
                                app.session.gpsSessionKey = null
                                sessionActive = false
                            }
                        }
                    },
                    enabled = sessionActive,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("End GPS")
                }
            }

            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
            BatteryOptimizationCard(show = sessionActive)

            Spacer(Modifier.height(10.dp))
            Text("Note: Start/End is decided by the server from your assignment. Locations are real GPS fixes only.", style = MaterialTheme.typography.labelSmall)

            Spacer(Modifier.height(12.dp))
            DeviceMapCard()

            AdBannerWithGap()

            Spacer(Modifier.height(10.dp))
            NotificationsSection(app)
        }
    }
}

private @Composable
fun AdBannerWithGap() {
    Spacer(Modifier.height(8.dp))
    AdBanner()
}

@Composable
fun GpsStatusChip(status: CrewStatus?) {
    val gps = status?.gpsStatus
    val color = if (gps.equals("ACTIVE", true)) Color(0xFF16A34A) else Color(0xFF9CA3AF)
    val age = status?.lastUpdateAgeSeconds ?: 0
    Text(
        "GPS: ${gps ?: "UNKNOWN"} · last update ${age}s ago",
        color = color,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
fun NotificationsSection(app: KaBusCrewApp) {
    var items by remember { mutableStateOf(listOf<NotificationItem>()) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { app.api.notifications("Bearer ${app.session.accessToken}", 0, 20) }
                .onSuccess { items = it.content }
            delay(30_000)
        }
    }
    Text("Notifications", style = MaterialTheme.typography.titleSmall)
    items.take(5).forEach { n ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (n.isRead) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            )
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(n.title, style = MaterialTheme.typography.labelLarge)
                n.body?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}