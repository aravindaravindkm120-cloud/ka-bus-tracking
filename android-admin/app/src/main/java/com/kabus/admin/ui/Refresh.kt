package com.kabus.admin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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

/**
 * Guards a pull-to-refresh so runs never overlap (e.g. a pull triggered while
 * an earlier refresh or the 10s auto-poll is still in flight is ignored).
 */
internal class RefreshController {
    var refreshing by mutableStateOf(false)
        private set

    /** Runs [block] unless a refresh is already in flight. */
    suspend fun run(block: suspend () -> Unit) {
        if (refreshing) return
        refreshing = true
        try {
            block()
        } finally {
            refreshing = false
        }
    }
}

/**
 * Material pull-to-refresh wrapper. Wrap any Compose scrollable (LazyColumn,
 * LazyVerticalGrid) whose content is the direct child; drag-down at the top
 * triggers [onRefresh] and [PullRefreshIndicator] renders at the top.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun RefreshableBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
    Box(
        Modifier
            .then(modifier)
            .pullRefresh(state)
    ) {
        content()
        PullRefreshIndicator(
            refreshing = refreshing,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Pull-to-refresh wrapper for a [LazyColumn]. Drop-in replacement for
 * `LazyColumn(Modifier.fillMaxSize()) { ... }`; the indicator overlays the top.
 */
@Composable
internal fun PullList(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    RefreshableBox(refreshing = refreshing, onRefresh = onRefresh, modifier = modifier) {
        LazyColumn(Modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * Inline error card shown above existing data when a refresh fails. This keeps
 * the previously loaded content on screen while surfacing the failure with an
 * explicit retry (var-generated full-screen errors are unchanged).
 */
@Composable
internal fun RefreshErrorBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * Small always-visible refresh affordance for screens where a raw drag-over
 * gesture is impossible (e.g. the Live map, whose WebView must keep its own
 * pan/zoom/rotate gestures). Provided as an accessibility-friendly fallback
 * alongside the pull-to-refresh indicator.
 */
@Composable
internal fun RefreshAction(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onRefresh,
        enabled = !refreshing,
        modifier = modifier
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = if (refreshing) "Refreshing" else "Refresh",
            modifier = Modifier.size(18.dp)
        )
        Text(
            if (refreshing) "Refreshing…" else "Refresh",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}