package com.kabus.admin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kabus.admin.KaBusAdminApp

/** Affirmation root destinations of the compact bottom navigation. */
internal enum class RootTab { Dashboard, Trips, Live, Crew, More }

/** A destination shown in the shell: either a bottom-nav root or a More sub-screen. */
internal sealed interface Screen {
    data class Root(val tab: RootTab) : Screen
    data class Sub(val item: MoreNavItem) : Screen
}

/** A row inside the More menu: pushes [content] or jumps to a root tab via [goTo]. */
internal data class MoreNavItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val content: (@Composable (KaBusAdminApp) -> Unit)? = null,
    val goTo: RootTab? = null
)

internal fun RootTab.label(): String = when (this) {
    RootTab.Dashboard -> "Dashboard"
    RootTab.Trips -> "Trips"
    RootTab.Live -> "Live"
    RootTab.Crew -> "Crew"
    RootTab.More -> "More"
}