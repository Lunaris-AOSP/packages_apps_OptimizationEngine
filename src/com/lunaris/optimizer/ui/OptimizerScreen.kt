/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunaris.optimizer.OptimizerUiState
import com.lunaris.optimizer.OptimizerViewModel
import com.lunaris.optimizer.R
import com.lunaris.optimizer.model.*

private enum class Screen { MAIN, APP_CACHE, DATA_CACHE, RARE_APPS, RESOURCES, DEXOPT }

@Composable
fun OptimizerScreen(viewModel: OptimizerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    val navigateBack = { if (screen == Screen.MAIN) onBack() else screen = Screen.MAIN }

    when (screen) {
        Screen.MAIN -> MainScreen(state, viewModel, onBack) { screen = it }
        Screen.APP_CACHE -> AppCacheScreen(state, viewModel, navigateBack)
        Screen.DATA_CACHE -> DataCacheScreen(state, viewModel, navigateBack)
        Screen.RARE_APPS -> RareAppsScreen(state, viewModel, navigateBack)
        Screen.RESOURCES -> ResourceScreen(state, viewModel, navigateBack)
        Screen.DEXOPT -> DexoptAppsScreen(state.run, navigateBack)
    }

    val toolError = state.error
    if (screen != Screen.MAIN && toolError != null) AlertDialog(
        onDismissRequest = viewModel::dismissError,
        title = { Text(stringResource(R.string.attention)) },
        text = { Text(toolError) },
        confirmButton = { TextButton(onClick = { viewModel.dismissError(); viewModel.refreshMetrics() }) {
            Text(stringResource(R.string.retry))
        } },
        dismissButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.dismiss)) } },
    )

    if (state.restartPrompt) AlertDialog(
        onDismissRequest = viewModel::dismissRestart,
        title = { Text(stringResource(R.string.restart_title)) },
        text = { Text(stringResource(R.string.restart_detail)) },
        confirmButton = { TextButton(onClick = viewModel::restartDevice) { Text(stringResource(R.string.restart_now)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissRestart) { Text(stringResource(R.string.restart_later)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(state: OptimizerUiState, viewModel: OptimizerViewModel, onBack: () -> Unit,
    navigate: (Screen) -> Unit) {
    var history by rememberSaveable { mutableStateOf(false) }
    val cacheSelectionValid = Action.CACHE !in state.plan.selected || state.plan.cachePackages.isNotEmpty()
    Scaffold(
        topBar = { TopAppBar(
            title = {},
            navigationIcon = { FilledTonalIconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
            } },
            actions = { IconButton(onClick = viewModel::refreshMetrics, enabled = !state.busy) {
                Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
            } },
        ) },
        bottomBar = { Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                onClick = { if (state.run.active) viewModel.cancel() else viewModel.requestStart() },
                enabled = state.connected && !state.busy && state.run.status != "cancelling" &&
                    (state.run.active || state.plan.selected.isNotEmpty() && cacheSelectionValid),
                modifier = Modifier.navigationBarsPadding().padding(20.dp, 12.dp)
                    .widthIn(max = 600.dp).fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(if (state.run.active) Icons.Default.Close else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(when {
                    state.run.status == "cancelling" -> R.string.cancelling
                    state.run.active -> R.string.cancel_run
                    else -> R.string.review_plan
                }))
            }
        } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 600.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(vertical = 8.dp).semantics { heading() }) }
                item { StatusPanel(state) }
                state.error?.let { message -> item { ErrorCard(message) { viewModel.dismissError(); viewModel.refreshMetrics() } } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(stringResource(R.string.memory), state.metrics.ramAvailableMb?.let { stringResource(R.string.mb, it) } ?: "-", Modifier.weight(1f))
                    Metric(stringResource(R.string.storage), state.metrics.storageAvailable, Modifier.weight(1f))
                } }
                item {
                    SectionHeading(stringResource(R.string.profiles))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.plan.profile == Profile.QUICK,
                            onClick = { viewModel.profile(Profile.QUICK) },
                            label = { Text(stringResource(R.string.quick)) },
                            leadingIcon = if (state.plan.profile == Profile.QUICK) { { Icon(Icons.Default.Check, null) } } else null,
                            enabled = state.editable,
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = state.plan.profile == Profile.DEEP,
                            onClick = { viewModel.profile(Profile.DEEP) },
                            label = { Text(stringResource(R.string.deep)) },
                            leadingIcon = if (state.plan.profile == Profile.DEEP) { { Icon(Icons.Default.Check, null) } } else null,
                            enabled = state.editable,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(stringResource(when (state.plan.profile) {
                        Profile.QUICK -> R.string.quick_detail
                        Profile.DEEP -> R.string.deep_detail
                        Profile.CUSTOM -> R.string.custom_detail
                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { SectionHeading(stringResource(R.string.main_maintenance)) }
                item { AppCacheMaintenanceCard(
                    selected = Action.CACHE in state.plan.selected,
                    count = state.plan.cachePackages.size,
                    enabled = state.editable,
                    toggle = { viewModel.toggle(Action.CACHE, it) },
                    configure = { navigate(Screen.APP_CACHE) },
                ) }
                items(listOf(Action.TRIM, Action.ZRAM, Action.DEXOPT), key = { it.key }) { action ->
                    ActionCard(action, state.plan, state.editable, { viewModel.toggle(action, it) }, viewModel::edit)
                }
                item { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)) {
                    Box(Modifier.padding(16.dp)) { ToggleRow(
                        stringResource(R.string.continue_errors), stringResource(R.string.continue_detail),
                        state.plan.continueOnError, state.editable,
                    ) { enabled -> viewModel.edit { it.copy(continueOnError = enabled) } } }
                } }
                if (!cacheSelectionValid) item { Text(stringResource(R.string.select_cache_apps_first), color = MaterialTheme.colorScheme.error) }
                item { SectionHeading(stringResource(R.string.other_tools)) }
                item { ToolNavigationCard(Icons.Default.Delete, stringResource(R.string.data_cache_title), stringResource(R.string.data_cache_detail)) { navigate(Screen.DATA_CACHE) } }
                item { ToolNavigationCard(Icons.Default.Close, stringResource(R.string.rare_apps_title), stringResource(R.string.rare_apps_detail)) { navigate(Screen.RARE_APPS) } }
                item { ToolNavigationCard(Icons.Default.Settings, stringResource(R.string.resources_title), stringResource(R.string.resources_detail)) { navigate(Screen.RESOURCES) } }
                item { SectionHeading(stringResource(R.string.recent_dexopt_apps)); TextButton(onClick = { navigate(Screen.DEXOPT) }) {
                    Text(stringResource(R.string.view_all_dexopt_apps))
                } }
                if (state.run.id != 0L) {
                    item { Text(stringResource(R.string.results), style = MaterialTheme.typography.titleLarge) }
                    items(state.run.results, key = { it.action.key }) { ResultCard(it) }
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
            state.plan.selected.forEach { Text("- " + stringResource(actionTitle(it))) }
            if (Action.CACHE in state.plan.selected) Text(stringResource(R.string.cache_selected_count, state.plan.cachePackages.size))
            if (Action.DEXOPT in state.plan.selected && (state.plan.fullCompilation || state.plan.adaptiveCompilation)) Text(stringResource(R.string.deep_warning))
            Text(stringResource(R.string.confirm_detail))
        } },
        confirmButton = { TextButton(onClick = viewModel::start) { Text(stringResource(R.string.start)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text(stringResource(R.string.go_back)) } },
    )
}

@Composable
private fun StatusPanel(state: OptimizerUiState) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.maintenance), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(if (!state.connected) R.string.connecting else statusLabel(state.run.status)), style = MaterialTheme.typography.labelMedium)
            }
            HealthGauge(if (state.run.id == 0L) null else state.run.progress,
                stringResource(if (state.run.id == 0L) R.string.on_your_terms else R.string.run_progress))
            Text(stringResource(R.string.intro), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppCacheScreen(state: OptimizerUiState, viewModel: OptimizerViewModel, onBack: () -> Unit) {
    val tool = state.tools.getValue(Tool.APP_CACHE)
    ToolScaffold(stringResource(R.string.cache_title), onBack, {
        ToolActionButton(tool, stringResource(R.string.scan_apps), state.connected && !state.busy) { viewModel.startTool(Tool.APP_CACHE) }
    }) {
        item { Text(stringResource(R.string.app_cache_screen_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (tool.detail.isNotBlank()) item { Text(tool.detail) }
        items(tool.rows, key = { it.id }) { row ->
            SelectableToolRow(row, row.id in state.plan.cachePackages, !state.run.active && !tool.active) { viewModel.selectAppCache(row.id, it) }
        }
    }
}

@Composable
private fun DataCacheScreen(state: OptimizerUiState, viewModel: OptimizerViewModel, onBack: () -> Unit) {
    val tool = state.tools.getValue(Tool.DATA_CACHE)
    ToolScaffold(stringResource(R.string.data_cache_title), onBack, {
        ToolActionButton(tool, stringResource(R.string.clean_data_cache), state.connected && !state.busy) { viewModel.startTool(Tool.DATA_CACHE) }
    }) {
        item { Text(stringResource(R.string.data_cache_paths), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (tool.detail.isNotBlank()) item { Text(tool.detail) }
    }
}

@Composable
private fun RareAppsScreen(state: OptimizerUiState, viewModel: OptimizerViewModel, onBack: () -> Unit) {
    val tool = state.tools.getValue(Tool.RARE_APPS)
    ToolScaffold(stringResource(R.string.rare_apps_title), onBack, {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.startTool(Tool.RARE_APPS) }, enabled = state.connected && !state.busy && !tool.active) { Text(stringResource(R.string.scan_apps)) }
            Button(onClick = { viewModel.startTool(Tool.RARE_APPS, state.selectedRareApps) },
                enabled = state.connected && !state.busy && !tool.active && state.selectedRareApps.isNotEmpty()) { Text(stringResource(R.string.stop_selected)) }
        }
    }) {
        item { Text(stringResource(R.string.rare_apps_screen_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (tool.detail.isNotBlank()) item { Text(tool.detail) }
        items(tool.rows, key = { it.id }) { row ->
            SelectableToolRow(row, row.id in state.selectedRareApps, !state.busy && !tool.active && row.selectable) { viewModel.selectRareApp(row.id, it) }
        }
    }
}

@Composable
private fun ResourceScreen(state: OptimizerUiState, viewModel: OptimizerViewModel, onBack: () -> Unit) {
    val tool = state.tools.getValue(Tool.RESOURCES)
    ToolScaffold(stringResource(R.string.resources_title), onBack, {
        ToolActionButton(tool, stringResource(R.string.analyze_now), state.connected && !state.busy) { viewModel.startTool(Tool.RESOURCES) }
    }) {
        item { Text(stringResource(R.string.resources_screen_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (tool.detail.isNotBlank()) item { Text(tool.detail) }
        items(tool.rows, key = { it.id }) { ResourceRow(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolScaffold(title: String, onBack: () -> Unit, actions: @Composable () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
        }) },
        bottomBar = { Surface { Box(Modifier.navigationBarsPadding().fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { actions() } } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.fillMaxSize().widthIn(max = 600.dp), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun ToolActionButton(tool: ToolState, label: String, enabled: Boolean, action: () -> Unit) {
    Button(onClick = action, enabled = enabled && !tool.active, modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
        if (tool.active) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.PlayArrow, null)
        Spacer(Modifier.width(8.dp)); Text(if (tool.active) stringResource(R.string.running) else label)
    }
}

@Composable
private fun SelectableToolRow(row: ToolRow, selected: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) { Row(
        Modifier.fillMaxWidth().toggleable(
            value = selected,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = change,
        ).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(selected, null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall)
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(android.text.format.Formatter.formatShortFileSize(context, row.bytes), style = MaterialTheme.typography.labelLarge)
    } }
}

@Composable
private fun ResourceRow(row: ToolRow) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(row.title, style = MaterialTheme.typography.titleSmall)
        Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.resource_values, row.cpuPercent, row.memoryMb,
            stringResource(if (row.background) R.string.background else R.string.foreground)))
    } }
}

@Composable
private fun AppCacheMaintenanceCard(selected: Boolean, count: Int, enabled: Boolean,
    toggle: (Boolean) -> Unit, configure: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(stringResource(R.string.cache_title), stringResource(R.string.cache_detail), selected, enabled, toggle)
        Text(stringResource(R.string.cache_selected_count, count), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = configure) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.choose_apps)) }
    } }
}

@Composable
private fun ToolNavigationCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, open: () -> Unit) {
    Card(onClick = open, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null)
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodySmall) }
        Icon(Icons.Default.PlayArrow, null)
    } }
}

@Composable
private fun ActionCard(action: Action, plan: Plan, enabled: Boolean, toggle: (Boolean) -> Unit,
    edit: ((Plan) -> Plan) -> Unit) {
    var expanded by rememberSaveable(action.key) { mutableStateOf(false) }
    val selected = action in plan.selected
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(stringResource(actionTitle(action)), stringResource(actionDetail(action)), selected, enabled, toggle)
        if (action == Action.DEXOPT) {
            TextButton(onClick = { expanded = !expanded }, enabled = enabled && selected) { Text(stringResource(R.string.configure)) }
            if (expanded) {
                ToggleRow(stringResource(R.string.adaptive_compile), stringResource(R.string.adaptive_detail),
                    plan.adaptiveCompilation, enabled && selected) { value -> edit { it.copy(adaptiveCompilation = value, fullCompilation = false, profile = Profile.CUSTOM) } }
                ToggleRow(stringResource(R.string.full_compile), stringResource(R.string.deep_warning),
                    plan.fullCompilation, enabled && selected && !plan.adaptiveCompilation) { value -> edit { it.copy(fullCompilation = value, profile = Profile.CUSTOM) } }
            }
        }
    } }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = change,
    ),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, null, enabled = enabled, thumbContent = { Icon(if (checked) Icons.Default.Check else Icons.Default.Close, null, Modifier.size(16.dp)) })
    }
}

@Composable private fun SectionHeading(title: String) = Text(title, style = MaterialTheme.typography.titleLarge,
    modifier = Modifier.padding(top = 10.dp).semantics { heading() })

@Composable private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.titleLarge); Text(label) }
    }
}

@Composable private fun ErrorCard(message: String, retry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) { Text(stringResource(R.string.attention)); Text(message); TextButton(onClick = retry) { Text(stringResource(R.string.retry)) } }
    }
}

@Composable private fun ResultCard(result: ActionResult) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text(stringResource(actionTitle(result.action)), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(statusLabel(result.status)))
        if (result.detail.isNotBlank()) SelectionContainer { Text(result.detail, style = MaterialTheme.typography.bodySmall) }
    } }
}

@Composable
private fun DexoptAppsScreen(run: Run, onBack: () -> Unit) {
    ToolScaffold(stringResource(R.string.package_filters), onBack, {}) {
        item { Text(stringResource(R.string.package_filters_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (run.packagesTruncated) item { Text(stringResource(R.string.package_limit)) }
        if (run.packages.isEmpty()) item { Text(stringResource(R.string.no_dexopt_apps)) }
        items(run.packages.asReversed(), key = { it.packageName }) { row ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(row.packageName, style = MaterialTheme.typography.titleSmall); Text(row.filter); Text(stringResource(statusLabel(row.status)))
            } }
        }
    }
}

private fun actionTitle(action: Action) = when (action) {
    Action.CACHE -> R.string.cache_title; Action.TRIM -> R.string.trim_title
    Action.ZRAM -> R.string.zram_title; Action.DEXOPT -> R.string.dexopt_title
}
private fun actionDetail(action: Action) = when (action) {
    Action.CACHE -> R.string.cache_detail; Action.TRIM -> R.string.trim_detail
    Action.ZRAM -> R.string.zram_detail; Action.DEXOPT -> R.string.dexopt_detail
}
private fun statusLabel(status: String) = when (status) {
    "running" -> R.string.running; "cancelling" -> R.string.cancelling; "cancelled" -> R.string.cancelled
    "completed" -> R.string.completed; "partial" -> R.string.partial; "compiled" -> R.string.compiled
    "success" -> R.string.success; "failed" -> R.string.failed; "unavailable" -> R.string.unavailable
    "skipped" -> R.string.skipped; "queued" -> R.string.queued; else -> R.string.ready
}
