package com.kabus.admin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kabus.admin.KaBusAdminApp

/**
 * The More hub. Presentation-only routing: it never changes what the server
 * authorizes â€” scoped/SUPER_ADMIN-only entries are filtered by the cached role.
 */
@Composable
internal fun MoreScreen(
    app: KaBusAdminApp,
    role: String?,
    onNavigate: (MoreNavItem) -> Unit,
    onSignOut: () -> Unit
) {
    val groups = remember(role) { moreGroups(role) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
        )
    ) {
        groups.forEach { (section, rows) ->
            item(key = "h-$section") {
                SectionLabel(section)
            }
            items(rows, key = { it.title }) { row ->
                MoreRowCard(row = row, onClick = { onNavigate(row) })
            }
        }

        item(key = "h-Account") {
            SectionLabel("Account")
        }
        item(key = "signout") {
            MoreRowCard(
                row = MoreNavItem(
                    title = "Sign out",
                    description = "Clear the local session and revoke the refresh token",
                    icon = Icons.Filled.Logout
                ),
                danger = true,
                onClick = { confirmSignOut = true }
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your refresh token will be revoked, and no role data will be cached on this device.") },
            confirmButton = {
                TextButton(onClick = onSignOut) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun MoreRowCard(row: MoreNavItem, onClick: () -> Unit, danger: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (danger) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.description.isNotEmpty()) {
                    Text(
                        text = row.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun canManageRoutes(role: String?): Boolean =
    role == "SUPER_ADMIN" || role == "DIVISION_ADMIN" || role == "DIVISION_MANAGER"

private fun moreGroups(role: String?): List<Pair<String, List<MoreNavItem>>> {
    val administration = buildList {
        if (role == "SUPER_ADMIN") {
            add(MoreNavItem("Organization", "Corporations, divisions, depots, towns", Icons.Filled.Business, content = { OrganizationScreen(it) }))
            add(MoreNavItem("Users", "Admin accounts and roles", Icons.Filled.ManageAccounts, content = { UsersScreen(it) }))
        }
        add(MoreNavItem("Bus Numbers", "Service numbers run by each depot", Icons.Filled.Label, content = { BusNumbersScreen(it) }))
        add(MoreNavItem("Fleet", "Vehicles running on a bus number", Icons.Filled.DirectionsBus, content = { FleetScreen(it) }))
        add(MoreNavItem("Staff", "Depot-attached staff records", Icons.Filled.Group, content = { StaffScreen(it) }))
        add(MoreNavItem("Routes", "Route masters and stops", Icons.Filled.TripOrigin, content = { RoutesScreen(it, canManage = canManageRoutes(role)) }))
    }
    val monitoring = buildList {
        add(MoreNavItem("Live Tracking", "Open the live bus map", Icons.Filled.LocationOn, goTo = RootTab.Live))
        if (role == "SUPER_ADMIN" || role == "DIVISION_ADMIN") {
            add(MoreNavItem("Crew", "Drivers and conductors", Icons.Filled.Group, content = { CrewScreen(it) }))
        }
        add(MoreNavItem("Notifications", "Alerts and system messages", Icons.Filled.Notifications, content = { NotificationsScreen(it) }))
        if (role == "SUPER_ADMIN") {
            add(MoreNavItem("Audit Logs", "Who did what, when", Icons.Filled.History, content = { AuditLogsScreen(it) }))
        }
    }
    val system = if (role == "SUPER_ADMIN") {
        listOf(MoreNavItem("Settings", "System-wide runtime settings", Icons.Filled.Settings, content = { SettingsScreen(it) }))
    } else {
        emptyList()
    }

    return buildList {
        add("Administration" to administration)
        add("Monitoring" to monitoring)
        if (system.isNotEmpty()) add("System" to system)
    }
}