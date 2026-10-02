package com.kabus.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.kabus.admin.data.RefreshRequest
import com.kabus.admin.ui.CrewScreen
import com.kabus.admin.ui.DashboardScreen
import com.kabus.admin.ui.LiveMapScreen
import com.kabus.admin.ui.LoginScreen
import com.kabus.admin.ui.MoreNavItem
import com.kabus.admin.ui.MoreScreen
import com.kabus.admin.ui.RootTab
import com.kabus.admin.ui.TripsScreen
import com.kabus.admin.ui.auth
import com.kabus.admin.ui.label
import com.kabus.admin.ui.theme.KaBusAdminTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app: KaBusAdminApp by lazy { application as KaBusAdminApp }
    private val scope: CoroutineScope
        get() = kotlinx.coroutines.MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaBusAdminTheme {
                var loggedIn by remember { mutableStateOf(app.session.accessToken != null) }

                if (!loggedIn) {
                    LoginScreen(app) { loggedIn = true }
                } else {
                    AppShell(
                        app = app,
                        onSignOut = {
                            scope.launch {
                                runCatching {
                                    val rt = app.session.refreshToken
                                    if (rt != null) app.api.logout(RefreshRequest(rt))
                                }
                                app.session.clear()
                                loggedIn = false
                            }
                        }
                    )
                }
            }
        }
    }
}

private const val SUBTITLE_UNKNOWN = "Admin"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AppShell(app: KaBusAdminApp, onSignOut: () -> Unit) {
    val role = app.session.cachedRole
    val tabs = remember(role) { bottomTabsForRole(role) }
    val scope = rememberCoroutineScope()

    // The pager is the single source of truth for the selected root tab.
    val pagerState = rememberPagerState { tabs.size }

    // More sub-screens own the whole content area; while one is open the pager
    // is not composed at all, so forms/dialogs can never swipe.
    var activeSub by remember { mutableStateOf<MoreNavItem?>(null) }

    // System back returns from a More sub-screen to the More root.
    BackHandler(enabled = activeSub != null) {
        activeSub = null
    }

    // One lightweight dashboard fetch drives the top-bar scope subtitle.
    var roleText by remember(role) { mutableStateOf(role?.replace('_', ' ') ?: SUBTITLE_UNKNOWN) }
    var scopeText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(role) {
        runCatching { app.api.dashboard(app.auth()) }.onSuccess { d ->
            roleText = d.role.replace('_', ' ')
            val s = d.scope
            scopeText = when {
                s == null -> null
                s.wholeSystem -> "Whole system"
                s.divisionIds != null -> "Division(s) scope"
                s.depotIds != null -> "Depot(s) scope"
                s.townIds != null -> "Town(s) scope"
                s.depotId != null -> "Depot scope"
                s.divisionId != null -> "Division scope"
                else -> null
            }
        }
    }

    val currentTab = tabs.getOrNull(pagerState.currentPage)?.first ?: RootTab.Dashboard

    Scaffold(
        topBar = {
            DashTopBar(
                sub = activeSub,
                tab = currentTab,
                roleText = roleText,
                scopeText = scopeText,
                onBack = { activeSub = null }
            )
        },
        bottomBar = {
            if (activeSub == null) {
                NavigationBar {
                    tabs.forEachIndexed { index, t ->
                        val (tab, label, icon) = t
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = {
                                Text(
                                    label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val sub = activeSub
            if (sub != null) {
                sub.content?.invoke(app)
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { page -> tabs[page].first.name }
                ) { page ->
                    when (tabs[page].first) {
                        RootTab.Dashboard -> DashboardScreen(app)
                        RootTab.Trips -> TripsScreen(app, canManage = canManageTrips(role))
                        RootTab.Live -> LiveMapScreen(app)
                        RootTab.Crew -> CrewScreen(app)
                        RootTab.More -> MoreScreen(
                            app = app,
                            role = role,
                            onNavigate = { row ->
                                when {
                                    row.content != null -> activeSub = row
                                    row.goTo != null -> {
                                        val index = tabs.indexOfFirst { it.first == row.goTo }
                                        if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                }
                            },
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashTopBar(
    sub: MoreNavItem?,
    tab: RootTab,
    roleText: String,
    scopeText: String?,
    onBack: () -> Unit
) {
    if (sub != null) {
        TopAppBar(
            title = { Text(sub.title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        return
    }
    when (tab) {
        RootTab.Dashboard ->
            TopAppBar(
                title = {
                    Column {
                        Text("KA Bus Tracking", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(roleText, scopeText).joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        else ->
            TopAppBar(
                title = {
                    Text(
                        tab.label(),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
    }
}

/**
 * Whether to offer trip creation and crew assignment.
 *
 * Mirrors the server: any administrative scope may create a trip, provided the
 * route, bus depot and bus town all fall inside it (see ScopeGuard). So this is
 * true for every admin role, and hidden only for a role with no admin scope.
 */
private fun canManageTrips(role: String?): Boolean = when (role) {
    "SUPER_ADMIN", "DIVISION_ADMIN", "DIVISION_MANAGER", "DEPOT_HEAD", "TOWN_MANAGER" -> true
    else -> false
}

/**
 * Compact, phone-first bottom navigation: never more than four labelled items.
 * Server-side authorization is unchanged — these tabs are UI presentation only.
 */
private fun bottomTabsForRole(role: String?): List<Triple<RootTab, String, ImageVector>> {
    val tabs = when (role) {
        "SUPER_ADMIN", "DIVISION_ADMIN" -> listOf(
            Triple(RootTab.Dashboard, "Dashboard", Icons.Filled.Dashboard),
            Triple(RootTab.Trips, "Trips", Icons.Filled.TripOrigin),
            Triple(RootTab.Live, "Live", Icons.Filled.LocationOn)
        )
        "DIVISION_MANAGER", "DEPOT_HEAD" -> listOf(
            Triple(RootTab.Trips, "Trips", Icons.Filled.TripOrigin),
            Triple(RootTab.Live, "Live", Icons.Filled.LocationOn),
            Triple(RootTab.Crew, "Crew", Icons.Filled.Group)
        )
        "TOWN_MANAGER" -> listOf(
            Triple(RootTab.Trips, "Trips", Icons.Filled.TripOrigin),
            Triple(RootTab.Live, "Live", Icons.Filled.LocationOn)
        )
        else -> listOf(
            Triple(RootTab.Trips, "Trips", Icons.Filled.TripOrigin),
            Triple(RootTab.Live, "Live", Icons.Filled.LocationOn)
        )
    }
    return tabs + Triple(RootTab.More, "More", Icons.Filled.MoreHoriz)
}