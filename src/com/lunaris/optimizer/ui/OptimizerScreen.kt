/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunaris.optimizer.OptimizerUiState
import com.lunaris.optimizer.OptimizerViewModel
import com.lunaris.optimizer.R
import com.lunaris.optimizer.model.*

private enum class Screen { MAIN, TOOLS, RESULTS, APP_CACHE, DATA_CACHE, RARE_APPS, RESOURCES, DEXOPT }

private val Screen.isTopLevel: Boolean get() = this == Screen.MAIN || this == Screen.TOOLS

private val BackStackSaver = listSaver<List<Screen>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names -> names.map { Screen.valueOf(it) } },
)

// Material motion: fade through between peers, shared X axis within a hierarchy.
private val MotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EnterEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
private val ExitEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

private fun pageTransition(forward: Boolean, slideDistance: Int): ContentTransform {
    val offset = if (forward) slideDistance else -slideDistance
    return (slideInHorizontally(tween(300, easing = MotionEasing)) { offset } +
        fadeIn(tween(210, delayMillis = 90, easing = EnterEasing))) togetherWith
        (slideOutHorizontally(tween(300, easing = MotionEasing)) { -offset } +
            fadeOut(tween(90, easing = ExitEasing)))
}

private fun tabTransition(): ContentTransform =
    (fadeIn(tween(195, delayMillis = 105, easing = EnterEasing)) +
        scaleIn(tween(195, delayMillis = 105, easing = EnterEasing), initialScale = 0.92f)) togetherWith
        fadeOut(tween(105, easing = ExitEasing))

@Composable
fun OptimizerScreen(viewModel: OptimizerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var backStack by rememberSaveable(stateSaver = BackStackSaver) { mutableStateOf(listOf(Screen.MAIN)) }
    val screen = backStack.last()
    val navigateBack: () -> Unit = {
        if (backStack.size > 1) backStack = backStack.dropLast(1) else onBack()
    }
    // One handler avoids outgoing pages intercepting Back during a transition.
    BackHandler(enabled = backStack.size > 1, onBack = navigateBack)
    val pageStates = rememberSaveableStateHolder()
    val selectedTab = if (Screen.TOOLS in backStack) 1 else 0
    val navigationSegments = rememberElasticSegments(2, selectedTab)
    val bottomBar: @Composable () -> Unit = {
        FloatingBottomNavigation(selectedTab, navigationSegments) {
            if (backStack.last().isTopLevel) {
                backStack = if (it == 0) listOf(Screen.MAIN) else listOf(Screen.MAIN, Screen.TOOLS)
            }
        }
    }
    val slideDistance = with(LocalDensity.current) { 30.dp.roundToPx() } *
        if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

    AnimatedContent(
        targetState = backStack,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clipToBounds(),
        transitionSpec = { pageTransition(targetState.size > initialState.size, slideDistance).using(sizeTransform = null) },
        // Home and Extras share a shell so their bottom bar stays in place.
        contentKey = { stack -> if (stack.last().isTopLevel) "tabs" else stack.last().name },
        label = "pageNavigation",
    ) { visibleStack ->
        val destination = visibleStack.last()
        // Ignore stale page callbacks while that page is animating out.
        val pageBack: () -> Unit = { if (backStack == visibleStack) navigateBack() }
        val navigate: (Screen) -> Unit = { next ->
            if (backStack == visibleStack && next != destination) backStack = backStack + next
        }
        if (destination.isTopLevel) {
            Scaffold(bottomBar = bottomBar, contentWindowInsets = WindowInsets(0)) { padding ->
                AnimatedContent(
                    targetState = destination,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    transitionSpec = { tabTransition().using(sizeTransform = null) },
                    label = "tabNavigation",
                ) { tab ->
                    pageStates.SaveableStateProvider(tab.name) {
                        val tabBack: () -> Unit = { if (backStack.last() == tab) pageBack() }
                        val tabNavigate: (Screen) -> Unit = { next -> if (backStack.last() == tab) navigate(next) }
                        when (tab) {
                            Screen.MAIN -> MainScreen(state, viewModel, tabBack, padding.calculateBottomPadding(), tabNavigate)
                            Screen.TOOLS -> ToolsScreen(tabBack, padding.calculateBottomPadding(), tabNavigate)
                            else -> Unit
                        }
                    }
                }
            }
        } else {
            pageStates.SaveableStateProvider(destination.name) {
                when (destination) {
                    Screen.RESULTS -> ResultsScreen(state.run, pageBack)
                    Screen.APP_CACHE -> AppCacheScreen(state, viewModel, pageBack)
                    Screen.DATA_CACHE -> DataCacheScreen(state, viewModel, pageBack)
                    Screen.RARE_APPS -> RareAppsScreen(state, viewModel, pageBack)
                    Screen.RESOURCES -> ResourceScreen(state, viewModel, pageBack)
                    Screen.DEXOPT -> DexoptAppsScreen(state.run, pageBack)
                    else -> Unit
                }
            }
        }
    }

    val toolError = state.error
    if (screen != Screen.MAIN && toolError != null) AlertDialog(
        onDismissRequest = viewModel::dismissError,
        title = { Text(stringResource(R.string.attention)) },
        text = { Text(toolError) },
        confirmButton = { TextButton(onClick = hapticClick { viewModel.dismissError(); viewModel.refreshMetrics() }) {
            Text(stringResource(R.string.retry))
        } },
        dismissButton = { TextButton(onClick = hapticClick(viewModel::dismissError)) { Text(stringResource(R.string.dismiss)) } },
    )

    if (state.restartPrompt) AlertDialog(
        onDismissRequest = viewModel::dismissRestart,
        title = { Text(stringResource(R.string.restart_title)) },
        text = { Text(stringResource(R.string.restart_detail)) },
        confirmButton = { TextButton(onClick = hapticClick(viewModel::restartDevice)) { Text(stringResource(R.string.restart_now)) } },
        dismissButton = { TextButton(onClick = hapticClick(viewModel::dismissRestart)) { Text(stringResource(R.string.restart_later)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(state: OptimizerUiState, viewModel: OptimizerViewModel, onBack: () -> Unit,
    bottomPadding: Dp,
    navigate: (Screen) -> Unit) {
    var history by rememberSaveable { mutableStateOf(false) }
    val cacheSelectionValid = Action.CACHE !in state.plan.selected || state.plan.cachePackages.isNotEmpty()
    Scaffold(
        topBar = { TopAppBar(
            title = {},
            navigationIcon = { FilledTonalIconButton(onClick = hapticClick(onBack), modifier = Modifier.padding(start = 12.dp)) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
            } },
            actions = { IconButton(onClick = hapticClick(viewModel::refreshMainScreen), enabled = !state.busy) {
                Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
            } },
        ) },
    ) { padding ->
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 600.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp,
                    bottom = bottomPadding + 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(vertical = 8.dp).semantics { heading() }) }
                item { StatusPanel(state) }
                item {
                    Button(
                        onClick = hapticClick {
                            when {
                                state.run.active -> viewModel.cancel()
                                state.showRunResult -> navigate(Screen.RESULTS)
                                else -> viewModel.requestStart()
                            }
                        },
                        enabled = state.showRunResult || (state.connected && !state.busy &&
                            state.run.status != "cancelling" &&
                            (state.run.active || state.plan.selected.isNotEmpty() && cacheSelectionValid)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Icon(when {
                            state.run.active -> Icons.Default.Close
                            state.showRunResult -> Icons.Default.Check
                            else -> Icons.Default.PlayArrow
                        }, null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(when {
                            state.run.status == "cancelling" -> R.string.cancelling
                            state.run.active -> R.string.cancel_run
                            state.showRunResult -> R.string.run_result
                            else -> R.string.optimize
                        }))
                    }
                }
                state.error?.let { message -> item { ErrorCard(message) { viewModel.dismissError(); viewModel.refreshMetrics() } } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(stringResource(R.string.memory), state.metrics.ramAvailableMb?.let { stringResource(R.string.mb, it) } ?: "-", Modifier.weight(1f))
                    Metric(stringResource(R.string.storage), state.metrics.storageAvailable, Modifier.weight(1f))
                } }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeading(stringResource(R.string.profiles))
                        BoxWithConstraints {
                            val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f
                            val profiles = listOf(Profile.QUICK, Profile.DEEP)
                            val segments = rememberElasticSegments(profiles.size, profiles.indexOf(state.plan.profile), elasticity = 0.45f)
                            if (stacked) {
                                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    profiles.forEachIndexed { index, profile ->
                                        ProfileOption(profile, state.plan.profile, state.editable,
                                            Modifier.fillMaxWidth().graphicsLayer {
                                                scaleX = 0.96f + (segments.weights[index] - 0.70f) * 0.06f
                                            }) { segments.tap(index); viewModel.profile(profile) }
                                    }
                                }
                            } else {
                                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    profiles.forEachIndexed { index, profile ->
                                        ProfileOption(profile, state.plan.profile, state.editable,
                                            Modifier.weight(segments.weights[index])) {
                                            segments.tap(index); viewModel.profile(profile)
                                        }
                                    }
                                }
                            }
                        }
                        Text(stringResource(when (state.plan.profile) {
                            Profile.QUICK -> R.string.quick_detail
                            Profile.DEEP -> R.string.deep_detail
                            Profile.CUSTOM -> R.string.custom_detail
                        }), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { SectionHeading(stringResource(R.string.main_maintenance)) }
                item { AppCacheMaintenanceCard(
                    selected = Action.CACHE in state.plan.selected,
                    count = state.plan.cachePackages.size,
                    enabled = state.editable,
                    toggle = { viewModel.toggle(Action.CACHE, it) },
                    configure = { navigate(Screen.APP_CACHE) },
                ) }
                // Keys must be unique across maintenance cards and results in this LazyColumn.
                items(listOf(Action.TRIM, Action.ZRAM, Action.DEXOPT), key = { "maintenance:${it.key}" }) { action ->
                    ActionCard(action, state.plan, state.editable, { viewModel.toggle(action, it) }, viewModel::edit)
                }
                item { Card(Modifier.fillMaxWidth(), colors = maintenanceCardColors()) {
                    Box(Modifier.padding(16.dp)) { ToggleRow(
                        stringResource(R.string.continue_errors), stringResource(R.string.continue_detail),
                        state.plan.continueOnError, state.editable,
                        icon = { Icon(painterResource(R.drawable.ic_skip_next), null) },
                    ) { enabled -> viewModel.edit { it.copy(continueOnError = enabled) } } }
                } }
                if (!cacheSelectionValid) item { Text(stringResource(R.string.select_cache_apps_first), color = MaterialTheme.colorScheme.error) }
                item { RecentAppsCard(state.run.packages, state.run.packagesTruncated) { navigate(Screen.DEXOPT) } }
                if (state.lastReport.isNotBlank()) item {
                    FilledTonalButton(onClick = hapticClick { history = !history }, shape = RoundedCornerShape(50)) {
                        Text(stringResource(R.string.last_report))
                    }
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
        confirmButton = { TextButton(onClick = hapticClick(viewModel::start)) { Text(stringResource(R.string.start)) } },
        dismissButton = { TextButton(onClick = hapticClick(viewModel::dismissConfirmation)) { Text(stringResource(R.string.go_back)) } },
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
            onClick = hapticClick(onClick)).padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionIconBackground(enabled) {
                Icon(if (selected) Icons.Default.Check else if (profile == Profile.QUICK) Icons.Default.PlayArrow else Icons.Default.Build,
                    contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Text(stringResource(if (profile == Profile.QUICK) R.string.quick else R.string.deep),
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RecentAppsCard(packages: List<PackageResult>, truncated: Boolean, open: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OptionIconBackground { Icon(Icons.Default.Check, null) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.recent_dexopt_apps), style = MaterialTheme.typography.titleMedium)
                Text(if (packages.isEmpty()) stringResource(R.string.no_dexopt_apps) else stringResource(R.string.recent_apps_count, packages.size, if (truncated) " +" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = hapticClick(open)) { Text(stringResource(R.string.view_all_dexopt_apps)) }
        }
    }
}

@Composable
private fun ToolsScreen(onBack: () -> Unit, bottomPadding: Dp, navigate: (Screen) -> Unit) {
    ToolScaffold(stringResource(R.string.other_tools), onBack, {}, bottomPadding = bottomPadding) {
        item { Text(stringResource(R.string.other_tools_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { ToolNavigationCard(Icons.Default.Delete, stringResource(R.string.data_cache_title), stringResource(R.string.data_cache_detail)) { navigate(Screen.DATA_CACHE) } }
        item { ToolNavigationCard(Icons.Default.Close, stringResource(R.string.rare_apps_title), stringResource(R.string.rare_apps_detail)) { navigate(Screen.RARE_APPS) } }
        item { ToolNavigationCard(Icons.Default.Settings, stringResource(R.string.resources_title), stringResource(R.string.resources_detail)) { navigate(Screen.RESOURCES) } }
    }
}

@Composable
private fun ResultsScreen(run: Run, onBack: () -> Unit) {
    ToolScaffold(stringResource(R.string.results), onBack, {}) {
        item { Text(stringResource(R.string.run_result_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(run.results, key = { "result:${it.action.key}" }) { ResultCard(it) }
    }
}

@Composable
private fun AppCacheScreen(state: OptimizerUiState, viewModel: OptimizerViewModel, onBack: () -> Unit) {
    val tool = state.tools.getValue(Tool.APP_CACHE)
    ToolScaffold(stringResource(R.string.cache_title), onBack, {
        ToolActionButton(tool, stringResource(R.string.scan_apps), state.connected && state.editable) { viewModel.startTool(Tool.APP_CACHE) }
    }, verticalArrangement = Arrangement.Top) {
        item(key = "summary:app_cache") {
            AppCacheSummary(tool, state.plan.cachePackages)
            Spacer(Modifier.height(16.dp))
        }
        item { Text(stringResource(R.string.app_cache_screen_detail),
            modifier = Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (tool.detail.isNotBlank()) item { Text(tool.detail, modifier = Modifier.padding(bottom = 16.dp)) }
        itemsIndexed(tool.rows, key = { _, row -> row.id }) { index, row ->
            AppCacheRow(row, row.id in state.plan.cachePackages,
                state.editable && !tool.active && row.selectable,
                first = index == 0, last = index == tool.rows.lastIndex) {
                viewModel.selectAppCache(row.id, it)
            }
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
            OutlinedButton(onClick = hapticClick { viewModel.startTool(Tool.RARE_APPS) }, enabled = state.connected && !state.busy && !tool.active) { Text(stringResource(R.string.scan_apps)) }
            Button(onClick = hapticClick { viewModel.startTool(Tool.RARE_APPS, state.selectedRareApps) },
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
    bottomPadding: Dp? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = hapticClick(onBack)) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
        }) },
        bottomBar = { if (bottomPadding == null) {
            Surface { Box(Modifier.navigationBarsPadding().fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { actions() } }
        } },
    ) { padding ->
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.fillMaxSize().widthIn(max = 600.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp,
                    bottom = (bottomPadding ?: padding.calculateBottomPadding()) + 20.dp),
                verticalArrangement = verticalArrangement, content = content)
        }
    }
}

@Composable
private fun ToolActionButton(tool: ToolState, label: String, enabled: Boolean, action: () -> Unit) {
    Button(onClick = hapticClick(action), enabled = enabled && !tool.active, modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
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
            onValueChange = hapticToggle(change),
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
    Card(Modifier.fillMaxWidth(), colors = maintenanceCardColors()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(stringResource(R.string.cache_title), stringResource(R.string.cache_detail), selected, enabled,
            icon = { ActionIcon(Action.CACHE) }, change = toggle)
        Text(stringResource(R.string.cache_selected_count, count), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = hapticClick(configure), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Text(stringResource(R.string.choose_apps))
        }
    } }
}

@Composable
private fun ToolNavigationCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, open: () -> Unit) {
    Card(onClick = hapticClick(open), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        OptionIconBackground { Icon(icon, null) }
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodySmall) }
    } }
}

@Composable
private fun ActionCard(action: Action, plan: Plan, enabled: Boolean, toggle: (Boolean) -> Unit,
    edit: ((Plan) -> Plan) -> Unit) {
    var expanded by rememberSaveable(action.key) { mutableStateOf(false) }
    val selected = action in plan.selected
    Card(Modifier.fillMaxWidth(), colors = maintenanceCardColors()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(stringResource(actionTitle(action)), stringResource(actionDetail(action)), selected, enabled,
            icon = { ActionIcon(action) }, change = toggle)
        if (action == Action.DEXOPT) {
            OutlinedButton(onClick = hapticClick { expanded = !expanded }, enabled = enabled && selected,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text(stringResource(R.string.configure)) }
            if (expanded) {
                ToggleRow(stringResource(R.string.adaptive_compile), stringResource(R.string.adaptive_detail),
                    plan.adaptiveCompilation, enabled && selected,
                    icon = { Icon(Icons.Default.Refresh, null) }) { value -> edit { it.copy(adaptiveCompilation = value, fullCompilation = false, profile = Profile.CUSTOM) } }
                ToggleRow(stringResource(R.string.full_compile), stringResource(R.string.deep_warning),
                    plan.fullCompilation, enabled && selected && !plan.adaptiveCompilation,
                    icon = { Icon(Icons.Default.Build, null) }) { value -> edit { it.copy(fullCompilation = value, profile = Profile.CUSTOM) } }
            }
        }
    } }
}

@Composable
private fun maintenanceCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun OptionIconBackground(enabled: Boolean = true, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.38f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 1f else 0.38f)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun ActionIcon(action: Action) {
    when (action) {
        Action.CACHE -> Icon(Icons.Default.Delete, null)
        Action.TRIM -> Icon(painterResource(R.drawable.ic_storage), null)
        Action.ZRAM -> Icon(painterResource(R.drawable.ic_memory), null)
        Action.DEXOPT -> Icon(Icons.Default.Build, null)
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, enabled: Boolean,
    icon: @Composable () -> Unit, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = hapticToggle(change),
    ),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionIconBackground(enabled, icon)
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
        Column(Modifier.padding(16.dp)) { Text(stringResource(R.string.attention)); Text(message); TextButton(onClick = hapticClick(retry)) { Text(stringResource(R.string.retry)) } }
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
