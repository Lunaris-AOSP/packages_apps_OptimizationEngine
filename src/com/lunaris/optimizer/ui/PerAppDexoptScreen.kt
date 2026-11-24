/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.lunaris.optimizer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lunaris.optimizer.OptimizerUiState
import com.lunaris.optimizer.OptimizerViewModel
import com.lunaris.optimizer.R
import com.lunaris.optimizer.model.DexoptApp

private enum class AppKind { ALL, USER, SYSTEM }

@Composable
internal fun PerAppDexoptScreen(state: OptimizerUiState, viewModel: OptimizerViewModel, onBack: () -> Unit) {
    val dexopt = state.perAppDexopt
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(AppKind.ALL) }
    val visible = remember(dexopt.apps, query, kind) {
        val search = query.trim()
        dexopt.apps.filter { app ->
            (kind == AppKind.ALL || app.system == (kind == AppKind.SYSTEM)) &&
                (search.isEmpty() || app.title.contains(search, ignoreCase = true)
                    || app.packageName.contains(search, ignoreCase = true))
        }
    }
    val editable = state.editable && state.connected
    val motion = MaterialTheme.motionScheme
    val actionContainerColor by animateColorAsState(
        targetValue = if (dexopt.compiling) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primary,
        animationSpec = motion.fastEffectsSpec(),
        label = "dexoptActionContainer",
    )
    val actionContentColor by animateColorAsState(
        targetValue = if (dexopt.compiling) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimary,
        animationSpec = motion.fastEffectsSpec(),
        label = "dexoptActionContent",
    )
    val onCancelClick = hapticReject(viewModel::cancelPerAppDexopt)
    val onStartClick = hapticConfirm(viewModel::startPerAppDexopt)
    ToolScaffold(stringResource(R.string.per_app_dexopt), onBack, actions = {
        PrimaryActionButton(
            state = dexopt.compiling,
            label = if (dexopt.compiling) {
                stringResource(if (dexopt.status == "cancelling") R.string.cancelling else R.string.stop_compilation)
            } else {
                stringResource(R.string.compile_selected, state.selectedDexoptApps.size)
            },
            onClick = if (dexopt.compiling) onCancelClick else onStartClick,
            enabled = if (dexopt.compiling) {
                !state.busy && dexopt.status != "cancelling"
            } else {
                editable && dexopt.loaded && state.selectedDexoptApps.isNotEmpty() && state.dexoptFilter in dexopt.filters
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = actionContainerColor,
                contentColor = actionContentColor,
            ),
        ) { compiling ->
            Icon(
                if (compiling) Icons.Default.Close else Icons.Default.Build,
                null,
                Modifier.fillMaxSize(),
            )
        }
    }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "dexopt:description") {
            Text(stringResource(R.string.per_app_dexopt_screen_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item(key = "dexopt:progress") {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceBright) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(dexoptStatusLabel(dexopt.status)),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                        IconButton(onClick = hapticClick(viewModel::refreshDexoptApps), enabled = editable) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.refresh_dexopt_apps))
                        }
                    }
                    if (dexopt.status == "loading") LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (dexopt.selectedCount > 0) {
                        LinearProgressIndicator(progress = { dexopt.progress }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.dexopt_progress, dexopt.done, dexopt.selectedCount),
                            style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                    if (dexopt.detail.isNotBlank()) SelectionContainer {
                        Text(dexopt.detail, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(R.string.cache_selected_count, state.selectedDexoptApps.size),
                        style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                    if (state.run.active) Text(stringResource(R.string.dexopt_maintenance_active),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "dexopt:compiler") {
            CompilerFilterPicker(state.dexoptFilter, dexopt.filters, editable, viewModel::setDexoptFilter)
        }
        if (state.dexoptFilter == "speed" || state.dexoptFilter == "everything") item(key = "dexopt:power") {
            Text(stringResource(R.string.deep_warning), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item(key = "dexopt:search") {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_apps)) }, singleLine = true,
                shape = MaterialTheme.shapes.large,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = hapticClick { query = "" }) {
                    Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                } })
        }
        item(key = "dexopt:types") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppKind.entries.forEach { option ->
                    FilterChip(selected = option == kind, onClick = hapticClick { kind = option },
                        label = { Text(stringResource(when (option) {
                            AppKind.ALL -> R.string.all_apps
                            AppKind.USER -> R.string.user_apps
                            AppKind.SYSTEM -> R.string.system_apps
                        })) })
                }
            }
        }
        if (visible.isNotEmpty()) item(key = "dexopt:selection") {
            val names = visible.map { it.packageName }.toSet()
            val allSelected = state.selectedDexoptApps.containsAll(names)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dexopt_visible_count, visible.size), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = hapticClick { viewModel.selectDexoptApps(names, !allSelected) }, enabled = editable) {
                    Text(stringResource(if (allSelected) R.string.deselect_visible else R.string.select_visible))
                }
            }
        }
        if (visible.isEmpty() && !dexopt.active) item(key = "dexopt:empty") {
            Text(stringResource(if (dexopt.loaded) R.string.no_matching_apps else R.string.dexopt_scan_required),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(visible, key = { _, app -> app.packageName }) { index, app ->
            DexoptAppRow(app, app.packageName in state.selectedDexoptApps, editable,
                first = index == 0, last = index == visible.lastIndex) {
                viewModel.selectDexoptApps(setOf(app.packageName), it)
            }
        }
    }
}

@Composable
private fun CompilerFilterPicker(filter: String, filters: List<String>, enabled: Boolean, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.compilation_filter), style = MaterialTheme.typography.titleMediumEmphasized)
        Box {
            OutlinedButton(onClick = hapticClick { expanded = true }, enabled = enabled && filters.isNotEmpty()) {
                Text(filter)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filters.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, enabled = enabled,
                        onClick = hapticClick { change(option); expanded = false })
                }
            }
        }
        Text(stringResource(R.string.dexopt_filter_detail), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DexoptAppRow(app: DexoptApp, selected: Boolean, enabled: Boolean,
    first: Boolean, last: Boolean, change: (Boolean) -> Unit) {
    var expanded by rememberSaveable(app.packageName) { mutableStateOf(false) }
    Surface(shape = groupedCardShape(first, last),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceBright,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().toggleable(value = selected, enabled = enabled, role = Role.Checkbox,
                onValueChange = hapticToggle(change)).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCacheIcon(app.packageName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(app.title, style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(if (app.system) R.string.system_apps else R.string.user_apps),
                        style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.current_compilation,
                        stringResource(currentStatusLabel(app.currentStatus)), app.currentFilter.ifEmpty { "—" }),
                        style = MaterialTheme.typography.bodySmall)
                    if (app.status != "ready") Text(stringResource(R.string.requested_compilation,
                        app.requestedFilter, stringResource(dexoptStatusLabel(app.status))),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = if (app.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                RadioButton(selected = selected, onClick = null, enabled = enabled)
            }
            if (app.detail.isNotBlank()) SelectionContainer {
                Text(app.detail, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.status == "failed") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (app.currentDetail.isNotBlank()) {
                TextButton(onClick = hapticClick { expanded = !expanded }, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(stringResource(if (expanded) R.string.hide_options else R.string.compilation_details))
                }
                if (expanded) SelectionContainer {
                    Text(app.currentDetail, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun currentStatusLabel(status: String) = when (status) {
    "compiled" -> R.string.compiled
    "mixed" -> R.string.mixed_compilation
    "not_compiled" -> R.string.not_compiled
    else -> R.string.unavailable
}

private fun dexoptStatusLabel(status: String) = when (status) {
    "loading" -> R.string.reading_compilation
    "running" -> R.string.running
    "cancelling" -> R.string.cancelling
    "compiled" -> R.string.compiled
    "queued" -> R.string.queued
    "skipped" -> R.string.skipped
    "failed" -> R.string.failed
    "completed" -> R.string.completed
    "partial" -> R.string.partial
    "cancelled" -> R.string.cancelled
    else -> R.string.ready
}
