package com.kabus.admin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kabus.admin.KaBusAdminApp
import com.kabus.admin.data.CorporationOption
import com.kabus.admin.data.DepotOption
import com.kabus.admin.data.DivisionOption
import com.kabus.admin.data.TownOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Cascading Corporation -> Division -> Depot -> Town selection, backed by the
 * scope-aware read-only `/org-picker` endpoints.
 *
 * Two rules drive the whole design:
 *
 *  1. **Children are never guessed.** Each level is only offered once its
 *     parent is chosen, so an admin can never submit a Depot/Town pair that
 *     sits under a Division they did not select.
 *  2. **Changing a parent clears its descendants.** Otherwise a stale
 *     townId could survive under a new depot and be submitted as a mismatch.
 *
 * The state is exposed as a plain holder so a dialog can read
 * [selectedDepotId] / [selectedTownId] when it builds its request body.
 */
internal class OrgPickerState(
    val scope: CoroutineScope,
    private val app: KaBusAdminApp
) {
    var corporations by mutableStateOf<List<CorporationOption>>(emptyList())
        private set
    var divisions by mutableStateOf<List<DivisionOption>>(emptyList())
        private set
    var depots by mutableStateOf<List<DepotOption>>(emptyList())
        private set
    var towns by mutableStateOf<List<TownOption>>(emptyList())
        private set

    var selectedCorporationId by mutableStateOf<Long?>(null)
        private set
    var selectedDivisionId by mutableStateOf<Long?>(null)
        private set
    var selectedDepotId by mutableStateOf<Long?>(null)
        private set
    var selectedTownId by mutableStateOf<Long?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private fun fail(t: Throwable) {
        error = t.message
    }

    /** Loads the top level. Safe to call again to retry after a failure. */
    fun loadCorporations() {
        scope.launch {
            runCatching { app.api.pickerCorporations(app.auth()) }
                .onSuccess { corporations = it; error = null }
                .onFailure { corporations = emptyList(); fail(it) }
        }
    }

    fun selectCorporation(id: Long?) {
        selectedCorporationId = id
        // Rule 2: drop everything below.
        selectedDivisionId = null
        selectedDepotId = null
        selectedTownId = null
        divisions = emptyList()
        depots = emptyList()
        towns = emptyList()
        if (id == null) return
        scope.launch {
            runCatching { app.api.pickerDivisions(app.auth(), id) }
                .onSuccess { divisions = it; error = null }
                .onFailure { divisions = emptyList(); fail(it) }
        }
    }

    fun selectDivision(id: Long?) {
        selectedDivisionId = id
        selectedDepotId = null
        selectedTownId = null
        depots = emptyList()
        towns = emptyList()
        if (id == null) return
        scope.launch {
            runCatching { app.api.pickerDepots(app.auth(), id) }
                .onSuccess { depots = it; error = null }
                .onFailure { depots = emptyList(); fail(it) }
        }
    }

    fun selectDepot(id: Long?) {
        selectedDepotId = id
        selectedTownId = null
        towns = emptyList()
        if (id == null) return
        scope.launch {
            runCatching { app.api.pickerTowns(app.auth(), id) }
                .onSuccess { towns = it; error = null }
                .onFailure { towns = emptyList(); fail(it) }
        }
    }

    fun selectTown(id: Long?) {
        selectedTownId = id
    }
}

/** Creates an [OrgPickerState] and kicks off the first level's load. */
@Composable
internal fun rememberOrgPicker(app: KaBusAdminApp): OrgPickerState {
    val scope = rememberCoroutineScope()
    val state = remember { OrgPickerState(scope, app) }
    androidx.compose.runtime.LaunchedEffect(Unit) { state.loadCorporations() }
    return state
}

/**
 * Renders the four cascading dropdowns. [includeDivision] is false for forms
 * that hang off a fixed division (e.g. routes, which are division-level).
 */
@Composable
internal fun OrgPickerFields(
    state: OrgPickerState,
    modifier: Modifier = Modifier,
    includeCorporation: Boolean = true,
    includeDivision: Boolean = true,
    includeDepot: Boolean = true,
    requireTown: Boolean = true
) {
    Column(modifier) {
        if (includeCorporation) {
            PickerDropdownField(
                label = "Corporation",
                options = state.corporations.map { it.id to "${it.code} — ${it.name}" },
                selectedId = state.selectedCorporationId,
                enabled = true,
                onSelect = state::selectCorporation
            )
        }
        if (includeDivision) {
            PickerDropdownField(
                label = "Division",
                options = state.divisions.map { it.id to "${it.code} — ${it.name}" },
                selectedId = state.selectedDivisionId,
                // Rule 1: only offered under a chosen corporation.
                enabled = !includeCorporation || state.selectedCorporationId != null,
                onSelect = state::selectDivision
            )
        }
        if (includeDepot) {
            PickerDropdownField(
                label = "Depot",
                options = state.depots.map { it.id to "${it.code} — ${it.name}" },
                selectedId = state.selectedDepotId,
                enabled = if (includeDivision) state.selectedDivisionId != null else state.selectedCorporationId != null,
                onSelect = state::selectDepot
            )
            PickerDropdownField(
                label = if (requireTown) "Town" else "Town (optional)",
                options = state.towns.map { it.id to "${it.code} — ${it.name}" },
                selectedId = state.selectedTownId,
                enabled = state.selectedDepotId != null,
                onSelect = state::selectTown
            )
        }
    }
}

/**
 * Read-only text field that behaves as a dropdown, matching the role selector
 * on the login screen. [enabled] false greys the field and swallows taps, which
 * is how a child level signals "choose a parent first".
 */
@Composable
internal fun PickerDropdownField(
    label: String,
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    enabled: Boolean,
    onSelect: (Long?) -> Unit,
    emptyMessage: String? = null
) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.first == selectedId }?.second ?: ""
    val colors = MaterialTheme.colorScheme

    OutlinedTextField(
        value = if (enabled) selected else "",
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose $label") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )

    if (!enabled) {
        // Explain the block rather than leaving a dead field.
        Text(
            "Select ${parentLabelOf(label)} first",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
        )
        return
    }

    if (options.isEmpty()) {
        // Enabled but nothing to choose: say why, so it is not read as a broken
        // control. This is the normal state for a depot with no bus numbers yet.
        Text(
            emptyMessage ?: "No ${label.lowercase()}s available",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
        )
        return
    }

    Box(Modifier.fillMaxWidth()) {
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = { onSelect(id); open = false }
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(56.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { open = true }
                )
        )
    }
}

private fun parentLabelOf(label: String): String = when (label) {
    "Division" -> "a corporation"
    "Depot" -> "a division"
    "Town", "Town (optional)" -> "a depot"
    else -> "a parent"
}

/** Scrollable body wrapper for dialogs whose content is an [OrgPickerFields]. */
@Composable
internal fun PickerDialogBody(content: @Composable () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) { content() }
}
