/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunaris.optimizer.OptimizerViewModel
import com.lunaris.optimizer.R
import com.lunaris.optimizer.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizerScreen(viewModel: OptimizerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var history by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(
            title = {},
            navigationIcon = { FilledTonalIconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) } },
            actions = { IconButton(onClick = { viewModel.refreshMetrics() }, enabled = !state.busy) {
                Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
            } },
        ) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { if (state.run.active) viewModel.cancel() else viewModel.requestStart() },
                        enabled = state.connected && !state.busy && state.run.status != "cancelling" &&
                            (state.run.active || state.plan.selected.isNotEmpty()),
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(min = 64.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(if (state.run.active) Icons.Default.Close else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(when {
                        state.run.status == "cancelling" -> R.string.cancelling
                        state.run.active -> R.string.cancel_run
                        else -> R.string.review_plan
                    }), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp).semantics { heading() })
                }
                item {
                    Surface(shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.maintenance), style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(stringResource(if (!state.connected) R.string.connecting else statusLabel(state.run.status)),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            HealthGauge(if (state.run.id == 0L) null else state.run.progress,
                                stringResource(if (state.run.id == 0L) R.string.on_your_terms else R.string.run_progress))
                            Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.error?.let { error -> item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.attention), style = MaterialTheme.typography.titleMedium)
                            Text(error)
                            TextButton(onClick = { viewModel.dismissError(); viewModel.refreshMetrics() }) { Text(stringResource(R.string.retry)) }
                        }
                    }
                } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric(stringResource(R.string.memory), state.metrics.ramAvailableMb?.let { stringResource(R.string.mb, it) } ?: "—", Modifier.weight(1f))
                        Metric(stringResource(R.string.storage), state.metrics.storageAvailable, Modifier.weight(1f))
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeading(stringResource(R.string.profiles))
                        BoxWithConstraints {
                            val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
                            if (stacked) {
                                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ProfileOption(Profile.QUICK, state.plan.profile, state.editable, Modifier.fillMaxWidth()) { viewModel.profile(Profile.QUICK) }
                                    ProfileOption(Profile.DEEP, state.plan.profile, state.editable, Modifier.fillMaxWidth()) { viewModel.profile(Profile.DEEP) }
                                }
                            } else {
                                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ProfileOption(Profile.QUICK, state.plan.profile, state.editable, Modifier.weight(1f)) { viewModel.profile(Profile.QUICK) }
                                    ProfileOption(Profile.DEEP, state.plan.profile, state.editable, Modifier.weight(1f)) { viewModel.profile(Profile.DEEP) }
                                }
                            }
                        }
                        Text(stringResource(when (state.plan.profile) {
                            Profile.QUICK -> R.string.quick_detail
                            Profile.DEEP -> R.string.deep_detail
                            Profile.CUSTOM -> R.string.custom_detail
                        }), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                item { SectionHeading(stringResource(R.string.actions)) }
                items(Action.entries, key = { it.key }) { action ->
                    ActionCard(action, state.plan, state.editable, { viewModel.toggle(action, it) }, viewModel::edit)
                }
                item {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)) {
                    Box(Modifier.padding(20.dp)) {
                    ToggleRow(stringResource(R.string.continue_errors), stringResource(R.string.continue_detail),
                        state.plan.continueOnError, state.editable) { enabled -> viewModel.edit { it.copy(continueOnError = enabled) } }
                    } }
                }
                if (state.run.id != 0L) {
                    item {
                        Text(stringResource(R.string.results), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.elapsed, state.elapsedSeconds / 60, state.elapsedSeconds % 60))
                        if (state.run.active) Text(stringResource(R.string.cancel_detail), style = MaterialTheme.typography.bodySmall)
                    }
                    items(state.run.results, key = { "result_" + it.action.key }) { result -> ResultCard(result) }
                    if (state.run.packages.isNotEmpty()) item {
                        SectionHeading(stringResource(R.string.package_filters))
                        Text(stringResource(R.string.package_filters_detail), style = MaterialTheme.typography.bodySmall)
                        if (state.run.packagesTruncated) Text(stringResource(R.string.package_limit))
                    }
                    items(state.run.packages, key = { "package_" + it.packageName }) { result ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(result.packageName, style = MaterialTheme.typography.titleSmall)
                                Text(result.filter + " · " + stringResource(statusLabel(result.status)))
                            }
                        }
                    }
                }
                if (state.lastReport.isNotBlank()) item {
                    TextButton(onClick = { history = !history }) { Text(stringResource(R.string.last_report)) }
                    if (history) SelectionContainer { Text(state.lastReport, style = MaterialTheme.typography.bodySmall) }
                }
                item { Text(stringResource(R.string.scope_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (state.confirm) AlertDialog(
        onDismissRequest = viewModel::dismissConfirmation,
        title = { Text(stringResource(R.string.review_plan)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.plan.selected.forEach { Text("• " + stringResource(actionTitle(it))) }
            if (Action.CACHE in state.plan.selected) Text(stringResource(R.string.cache_target, state.plan.cacheTargetGiB))
            if (Action.DEXOPT in state.plan.selected && (state.plan.fullCompilation || state.plan.adaptiveCompilation)) Text(stringResource(R.string.deep_warning))
            if (Action.DEXOPT in state.plan.selected && state.plan.adaptiveCompilation) Text(stringResource(R.string.adaptive_detail))
            Text(stringResource(R.string.confirm_detail))
        } },
        confirmButton = { TextButton(onClick = viewModel::start) { Text(stringResource(R.string.start)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text(stringResource(R.string.go_back)) } },
    )
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun ProfileOption(profile: Profile, current: Profile, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    val selected = profile == current
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp), label = "profileColor")
    Surface(modifier = modifier, shape = if (selected) RoundedCornerShape(32.dp)
        else if (profile == Profile.QUICK) RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp, topEnd = 12.dp, bottomEnd = 12.dp)
        else RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 32.dp, bottomEnd = 32.dp),
        color = container, contentColor = (if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = if (enabled) 1f else 0.38f)) {
        Column(Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton,
            onClick = onClick).padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (selected) Icons.Default.Check else if (profile == Profile.QUICK) Icons.Default.PlayArrow else Icons.Default.Build,
                contentDescription = null, modifier = Modifier.size(28.dp))
            Text(stringResource(if (profile == Profile.QUICK) R.string.quick else R.string.deep),
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ActionCard(action: Action, plan: Plan, enabled: Boolean, toggle: (Boolean) -> Unit,
    edit: ((Plan) -> Plan) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = action in plan.selected
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp)
        else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp), label = "actionColor")
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                Icon(actionIcon(action), null, modifier = Modifier.padding(12.dp).size(24.dp))
            }
            ToggleRow(stringResource(actionTitle(action)), stringResource(actionDetail(action)), action in plan.selected, enabled, toggle)
            if (action == Action.CACHE || action == Action.DEXOPT) {
                TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.hide_options else R.string.configure)) }
                if (expanded && action == Action.CACHE) {
                    Text(stringResource(R.string.cache_target, plan.cacheTargetGiB))
                    Slider(value = plan.cacheTargetGiB.toFloat(), onValueChange = { value ->
                        edit { it.copy(cacheTargetGiB = value.toInt(), profile = Profile.CUSTOM) }
                    }, enabled = enabled && action in plan.selected, valueRange = 1f..16f, steps = 14)
                    Text(stringResource(R.string.cache_help), style = MaterialTheme.typography.bodySmall)
                }
                if (expanded && action == Action.DEXOPT) {
                    ToggleRow(stringResource(R.string.adaptive_compile), stringResource(R.string.adaptive_detail),
                        plan.adaptiveCompilation, enabled && selected) { adaptive ->
                        edit { it.copy(adaptiveCompilation = adaptive, fullCompilation = false, profile = Profile.CUSTOM) }
                    }
                    ToggleRow(stringResource(R.string.full_compile),
                    stringResource(R.string.deep_warning), plan.fullCompilation, enabled && selected && !plan.adaptiveCompilation) { full ->
                        edit { it.copy(fullCompilation = full, profile = Profile.CUSTOM) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
        value = checked, enabled = enabled, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled,
            thumbContent = { Icon(if (checked) Icons.Default.Check else Icons.Default.Close, null, Modifier.size(16.dp)) })
    }
}

@Composable
private fun ResultCard(result: ActionResult) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(actionTitle(result.action)), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(statusLabel(result.status)), color = if (result.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (result.detail.isNotBlank()) SelectionContainer { Text(result.detail, style = MaterialTheme.typography.bodySmall) }
        if (result.status == "running") LinearProgressIndicator(Modifier.fillMaxWidth())
    } }
}

private fun actionIcon(action: Action): ImageVector = when (action) {
    Action.CACHE -> Icons.Default.Delete
    Action.TRIM -> Icons.Default.Build
    Action.ZRAM -> Icons.Default.Refresh
    Action.DEXOPT -> Icons.Default.Settings
}

private fun actionTitle(action: Action) = when (action) {
    Action.CACHE -> R.string.cache_title
    Action.TRIM -> R.string.trim_title
    Action.ZRAM -> R.string.zram_title
    Action.DEXOPT -> R.string.dexopt_title
}
private fun actionDetail(action: Action) = when (action) {
    Action.CACHE -> R.string.cache_detail
    Action.TRIM -> R.string.trim_detail
    Action.ZRAM -> R.string.zram_detail
    Action.DEXOPT -> R.string.dexopt_detail
}
private fun statusLabel(status: String) = when (status) {
    "running" -> R.string.running
    "cancelling" -> R.string.cancelling
    "cancelled" -> R.string.cancelled
    "completed" -> R.string.completed
    "partial" -> R.string.partial
    "compiled" -> R.string.compiled
    "success" -> R.string.success
    "failed" -> R.string.failed
    "unavailable" -> R.string.unavailable
    "skipped" -> R.string.skipped
    "queued" -> R.string.queued
    else -> R.string.ready
}
