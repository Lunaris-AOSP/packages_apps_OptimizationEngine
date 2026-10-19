/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.lunaris.optimizer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
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
import androidx.lifecycle.compose.LifecycleStartEffect
import com.lunaris.optimizer.OptimizerUiState
import com.lunaris.optimizer.OptimizerViewModel
import com.lunaris.optimizer.R
import com.lunaris.optimizer.model.*
import com.android.axion.compose.preferences.ExpressiveSwitch

private enum class Screen { MAIN, TOOLS, RESULTS, APP_CACHE, DATA_CACHE, RARE_APPS, RESOURCES, DEXOPT }

private val Screen.isTopLevel: Boolean get() = this == Screen.MAIN || this == Screen.TOOLS

private val BackStackSaver = listSaver<List<Screen>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names -> names.map { Screen.valueOf(it) } },
)

private fun pageTransition(forward: Boolean, slideDistance: Int, motion: MotionScheme): ContentTransform {
    val offset = if (forward) slideDistance else -slideDistance
    return (slideInHorizontally(motion.defaultSpatialSpec()) { offset } +
        fadeIn(motion.defaultEffectsSpec())) togetherWith
        (slideOutHorizontally(motion.defaultSpatialSpec()) { -offset } +
            fadeOut(motion.fastEffectsSpec()))
}

private fun tabTransition(motion: MotionScheme): ContentTransform =
    (fadeIn(motion.defaultEffectsSpec()) +
        scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f)) togetherWith
        fadeOut(motion.fastEffectsSpec())

@Composable
fun OptimizerScreen(viewModel: OptimizerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var backStack by rememberSaveable(stateSaver = BackStackSaver) { mutableStateOf(listOf(Screen.MAIN)) }
    val screen = backStack.last()
    LifecycleStartEffect(screen, viewModel) {
        viewModel.setResourceScreenActive(screen == Screen.RESOURCES)
        onStopOrDispose { viewModel.setResourceScreenActive(false) }
    }
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
    val motion = MaterialTheme.motionScheme

    AnimatedContent(
        targetState = backStack,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clipToBounds(),
        transitionSpec = { pageTransition(targetState.size > initialState.size, slideDistance, motion).using(sizeTransform = null) },
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
                    transitionSpec = { tabTransition(motion).using(sizeTransform = null) },
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
    val listState = rememberLazyListState()
    val showToolbarTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val cacheSelectionValid = Action.CACHE !in state.plan.selected || state.plan.cachePackages.isNotEmpty()
    Scaffold(
        topBar = { OptimizerTopBar(
            title = stringResource(R.string.app_name),
            showTitle = showToolbarTitle,
            onBack = onBack,
            actions = { IconButton(onClick = hapticClick(viewModel::refreshMainScreen), enabled = !state.busy) {
                Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
            } },
        ) },
    ) { padding ->
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 600.dp).fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp,
                    bottom = bottomPadding + 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageHeading(stringResource(R.string.app_name))
                        Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    StatusPanel(state) {
                        PrimaryActionButton(
                            label = stringResource(when {
                                state.run.status == "cancelling" -> R.string.cancelling
                                state.run.active -> R.string.cancel_run
                                state.showRunResult -> R.string.run_result
                                else -> R.string.optimize
                            }),
                            onClick = {
                                when {
                                    state.run.active -> viewModel.cancel()
                                    state.showRunResult -> navigate(Screen.RESULTS)
                                    else -> viewModel.requestStart()
                                }
                            },
                            enabled = state.showRunResult || (state.connected && !state.busy &&
                                state.run.status != "cancelling" &&
                                (state.run.active || state.plan.selected.isNotEmpty() && cacheSelectionValid)),
                        ) {
                            Icon(when {
                                state.run.active -> Icons.Default.Close
                                state.showRunResult -> Icons.Default.Check
                                else -> Icons.Default.PlayArrow
                            }, null, Modifier.fillMaxSize())
                        }
                    }
                }
                state.error?.let { message -> item { ErrorCard(message) { viewModel.dismissError(); viewModel.refreshMetrics() } } }
                item { MetricsSummary(state) }
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
                                            }, stacked = true) { segments.tap(index); viewModel.profile(profile) }
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
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppCacheMaintenanceCard(
                            selected = Action.CACHE in state.plan.selected,
                            count = state.plan.cachePackages.size,
                            enabled = state.editable,
                            toggle = { viewModel.toggle(Action.CACHE, it) },
                            configure = { navigate(Screen.APP_CACHE) },
                        )
                        listOf(Action.TRIM, Action.ZRAM, Action.DEXOPT).forEach { action ->
                            key(action.key) {
                                ActionCard(action, state.plan, state.editable,
                                    { viewModel.toggle(action, it) }, viewModel::edit,
                                    shape = groupedCardShape(first = false, last = action == Action.DEXOPT))
                            }
                        }
                    }
                }
                item { Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = maintenanceCardColors()) {
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
        title = { Text(stringResource(R.string.review_plan), style = MaterialTheme.typography.headlineSmallEmphasized) },
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
private fun StatusPanel(state: OptimizerUiState, action: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            FlowRow(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.maintenance), style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(end = 12.dp, top = 4.dp))
                Surface(shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface) {
                    Text(stringResource(if (!state.connected) R.string.connecting else statusLabel(state.run.status)),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            HealthGauge(if (state.run.id == 0L) null else state.run.progress,
                stringResource(if (state.run.id == 0L) R.string.on_your_terms else R.string.run_progress))
            action()
        }
    }
}

@Composable
private fun ProfileOption(profile: Profile, current: Profile, enabled: Boolean,
    modifier: Modifier, stacked: Boolean = false, onClick: () -> Unit) {
    val selected = profile == current
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "profileColor")
    Surface(modifier = modifier, shape = if (selected) RoundedCornerShape(32.dp)
        else if (stacked) groupedCardShape(profile == Profile.QUICK, profile == Profile.DEEP)
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
                style = if (selected) MaterialTheme.typography.titleMediumEmphasized
                    else MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RecentAppsCard(packages: List<PackageResult>, truncated: Boolean, open: () -> Unit) {
    Card(onClick = hapticClick(open), modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large, colors = maintenanceCardColors()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            OptionIconBackground { Icon(Icons.Default.Check, null) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.recent_dexopt_apps), style = MaterialTheme.typography.titleMediumEmphasized)
                Text(if (packages.isEmpty()) stringResource(R.string.no_dexopt_apps) else stringResource(R.string.recent_apps_count, packages.size, if (truncated) " +" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NavigationChevron()
        }
    }
}

@Composable
private fun ToolsScreen(onBack: () -> Unit, bottomPadding: Dp, navigate: (Screen) -> Unit) {
    ToolScaffold(stringResource(R.string.other_tools), onBack, bottomPadding = bottomPadding) {
        item { Text(stringResource(R.string.other_tools_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolNavigationCard(Icons.Default.Delete, stringResource(R.string.data_cache_title),
                    stringResource(R.string.data_cache_detail), groupedCardShape(true, false)) { navigate(Screen.DATA_CACHE) }
                ToolNavigationCard(Icons.Default.Close, stringResource(R.string.rare_apps_title),
                    stringResource(R.string.rare_apps_detail), groupedCardShape(false, false)) { navigate(Screen.RARE_APPS) }
                ToolNavigationCard(Icons.Default.Settings, stringResource(R.string.resources_title),
                    stringResource(R.string.resources_detail), groupedCardShape(false, true)) { navigate(Screen.RESOURCES) }
            }
        }
    }
}

@Composable
private fun ResultsScreen(run: Run, onBack: () -> Unit) {
    ToolScaffold(stringResource(R.string.results), onBack) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        ToggleRow(
            title = stringResource(R.string.resource_live_updates),
            detail = stringResource(if (state.resourceLiveUpdates) R.string.resource_live_updates_on else R.string.resource_live_updates_off),
            checked = state.resourceLiveUpdates,
            enabled = true,
            icon = { Icon(Icons.Default.Refresh, null) },
            change = viewModel::setResourceLiveUpdates,
        )
    }) {
        item { Text(stringResource(R.string.resources_screen_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.resourceLiveUpdates) item {
            Text(stringResource(when {
                !state.connected -> R.string.connecting
                state.run.active || state.busy || state.tools.any { (kind, value) -> kind != Tool.RESOURCES && value.active } -> R.string.resource_updates_waiting
                tool.rows.isEmpty() -> R.string.resource_updates_starting
                else -> R.string.resource_updates_active
            }), color = MaterialTheme.colorScheme.primary)
        }
        if (tool.detail.isNotBlank()) item { Text(tool.detail) }
        items(tool.rows, key = { it.id }) { ResourceRow(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolScaffold(title: String, onBack: () -> Unit, actions: (@Composable () -> Unit)? = null,
    bottomPadding: Dp? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: LazyListScope.() -> Unit) {
    val listState = rememberLazyListState()
    val showToolbarTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Scaffold(
        topBar = { OptimizerTopBar(title, showToolbarTitle, onBack) },
        bottomBar = { if (bottomPadding == null && actions != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 560.dp)) { actions() }
                }
            }
        } },
    ) { padding ->
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize(), state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp,
                    bottom = (bottomPadding ?: padding.calculateBottomPadding()) + 20.dp),
                verticalArrangement = verticalArrangement) {
                item(key = "page:heading") { PageHeading(title, Modifier.padding(bottom = 16.dp)) }
                content()
            }
        }
    }
}

@Composable
private fun ToolActionButton(tool: ToolState, label: String, enabled: Boolean, action: () -> Unit) {
    PrimaryActionButton(label = if (tool.active) stringResource(R.string.running) else label,
        onClick = action, enabled = enabled && !tool.active) {
        if (tool.active) CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 2.dp)
        else Icon(Icons.Default.PlayArrow, null, Modifier.fillMaxSize())
    }
}

@Composable
private fun SelectableToolRow(row: ToolRow, selected: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow)) { Row(
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
            Text(row.title, style = if (selected) MaterialTheme.typography.titleMediumEmphasized
                else MaterialTheme.typography.titleMedium)
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(android.text.format.Formatter.formatShortFileSize(context, row.bytes),
                style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
        }
    } }
}

@Composable
private fun ResourceRow(row: ToolRow) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = maintenanceCardColors()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.resource_values, row.cpuPercent, row.memoryMb,
                stringResource(if (row.background) R.string.background else R.string.foreground)),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AppCacheMaintenanceCard(selected: Boolean, count: Int, enabled: Boolean,
    toggle: (Boolean) -> Unit, configure: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = groupedCardShape(first = true, last = false), colors = maintenanceCardColors()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleRow(stringResource(R.string.cache_title), stringResource(R.string.cache_detail), selected, enabled,
                icon = { ActionIcon(Action.CACHE) }, change = toggle)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = hapticClick(configure), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.choose_apps), style = MaterialTheme.typography.labelLargeEmphasized)
                }
                Text(stringResource(R.string.cache_selected_count, count), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
private fun ToolNavigationCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String,
    shape: Shape, open: () -> Unit) {
    Card(onClick = hapticClick(open), modifier = Modifier.fillMaxWidth(), shape = shape,
        colors = maintenanceCardColors()) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        OptionIconBackground { Icon(icon, null) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NavigationChevron()
    } }
}

@Composable
private fun ActionCard(action: Action, plan: Plan, enabled: Boolean, toggle: (Boolean) -> Unit,
    edit: ((Plan) -> Plan) -> Unit, shape: Shape) {
    var expanded by rememberSaveable(action.key) { mutableStateOf(false) }
    val selected = action in plan.selected
    Card(Modifier.fillMaxWidth(), shape = shape, colors = maintenanceCardColors()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleRow(stringResource(actionTitle(action)), stringResource(actionDetail(action)), selected, enabled,
                icon = { ActionIcon(action) }, change = toggle)
            if (action == Action.DEXOPT) {
                FilledTonalButton(onClick = hapticClick { expanded = !expanded }, enabled = enabled && selected,
                    shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(if (expanded) R.string.hide_options else R.string.configure),
                        style = MaterialTheme.typography.labelLargeEmphasized)
                }
                AnimatedVisibility(expanded,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ToggleRow(stringResource(R.string.adaptive_compile), stringResource(R.string.adaptive_detail),
                            plan.adaptiveCompilation, enabled && selected,
                            icon = { Icon(Icons.Default.Refresh, null) }) { value ->
                            edit { it.copy(adaptiveCompilation = value, fullCompilation = false, profile = Profile.CUSTOM) }
                        }
                        ToggleRow(stringResource(R.string.full_compile), stringResource(R.string.deep_warning),
                            plan.fullCompilation, enabled && selected && !plan.adaptiveCompilation,
                            icon = { Icon(Icons.Default.Build, null) }) { value ->
                            edit { it.copy(fullCompilation = value, profile = Profile.CUSTOM) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun maintenanceCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun OptionIconBackground(enabled: Boolean = true, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.size(44.dp), shape = MaterialTheme.shapes.medium,
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
    val titleContent: @Composable () -> Unit = {
        Text(title, style = if (checked) MaterialTheme.typography.titleMediumEmphasized
            else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
    }
    val detailContent: @Composable () -> Unit = {
        Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f))
    }
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = hapticToggle(change),
    )) {
        if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionIconBackground(enabled, icon)
                    Box(Modifier.weight(1f)) { titleContent() }
                    ExpressiveSwitch(checked, null, enabled = enabled)
                }
                detailContent()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionIconBackground(enabled, icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    titleContent()
                    detailContent()
                }
                ExpressiveSwitch(checked, null, enabled = enabled)
            }
        }
    }
}

@Composable private fun SectionHeading(title: String) = Text(title, style = MaterialTheme.typography.titleLargeEmphasized,
    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp).semantics { heading() })

@Composable
private fun MetricsSummary(state: OptimizerUiState) {
    val memory = state.metrics.ramAvailableMb?.let { stringResource(R.string.mb, it) } ?: "—"
    val storage = state.metrics.storageAvailable
    BoxWithConstraints {
        if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.memory), memory, R.drawable.ic_memory, Modifier.fillMaxWidth())
                Metric(stringResource(R.string.storage), storage, R.drawable.ic_storage, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.memory), memory, R.drawable.ic_memory, Modifier.weight(1f))
                Metric(stringResource(R.string.storage), storage, R.drawable.ic_storage, Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun Metric(label: String, value: String, icon: Int, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavigationChevron() {
    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null,
        modifier = Modifier.rotate(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 180f else 0f),
        tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun ErrorCard(message: String, retry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.attention), style = MaterialTheme.typography.titleMediumEmphasized)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = hapticClick(retry)) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable private fun ResultCard(result: ActionResult) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = maintenanceCardColors()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(actionTitle(result.action)), style = MaterialTheme.typography.titleMediumEmphasized)
            Text(stringResource(statusLabel(result.status)), style = MaterialTheme.typography.labelLargeEmphasized,
                color = if (result.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (result.detail.isNotBlank()) SelectionContainer { Text(result.detail, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun DexoptAppsScreen(run: Run, onBack: () -> Unit) {
    ToolScaffold(stringResource(R.string.package_filters), onBack) {
        item { Text(stringResource(R.string.package_filters_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (run.packagesTruncated) item { Text(stringResource(R.string.package_limit)) }
        if (run.packages.isEmpty()) item { Text(stringResource(R.string.no_dexopt_apps)) }
        items(run.packages.asReversed(), key = { it.packageName }) { row ->
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = maintenanceCardColors()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.packageName, style = MaterialTheme.typography.titleSmallEmphasized)
                    Text(row.filter, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(statusLabel(row.status)), style = MaterialTheme.typography.labelLarge)
                }
            }
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
