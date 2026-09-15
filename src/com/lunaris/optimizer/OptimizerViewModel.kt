/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lunaris.optimizer.data.*
import com.lunaris.optimizer.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OptimizerUiState(
    val plan: Plan = Plan(),
    val run: Run = Run(),
    val metrics: SystemMetrics = SystemMetrics(),
    val connected: Boolean = false,
    val busy: Boolean = false,
    val elapsedSeconds: Long = 0,
    val error: String? = null,
    val confirm: Boolean = false,
    val lastReport: String = "",
    val tools: Map<Tool, ToolState> = Tool.entries.associateWith { ToolState(it) },
    val selectedRareApps: Set<String> = emptySet(),
    val restartPrompt: Boolean = false,
) {
    val editable get() = !run.active && !busy
}

class OptimizerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OptimizationRepository(application)
    private val preferences = OptimizerPreferences(application)
    private val state = MutableStateFlow(OptimizerUiState())
    private var savedRun = 0L
    private var initialized = false
    private val preferenceLock = Mutex()
    private val refreshLock = Mutex()
    private var promptedDataRun = 0L
    // Poll only while the screen has lifecycle-aware subscribers. A snapshot reattaches after process death.
    val uiState = flow {
        if (!initialized) {
            val plan = withContext(Dispatchers.IO) { preferences.read() }
            val report = withContext(Dispatchers.IO) { preferences.lastReport() }
            state.update { it.copy(plan = plan, lastReport = report) }
            initialized = true
        }
        coroutineScope {
            launch {
                var tick = 0
                while (isActive) {
                    refresh(includeMetrics = tick++ % 15 == 0)
                    delay(1000)
                }
            }
            emitAll(state)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OptimizerUiState())

    private suspend fun refresh(includeMetrics: Boolean) = refreshLock.withLock {
        try {
            val run = repository.snapshot()
            state.update { it.copy(run = run, connected = true,
                elapsedSeconds = if (run.started == 0L) 0 else
                    ((if (run.active) SystemClock.elapsedRealtime() else run.finished) - run.started).coerceAtLeast(0) / 1000) }
            if (includeMetrics) {
                try {
                    val metrics = repository.metrics()
                    state.update { it.copy(metrics = metrics) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { state.update { it.copy(error = e.message ?: "Metrics unavailable") } }
            }
            Tool.entries.forEach { tool ->
                try {
                    val toolState = repository.toolSnapshot(tool, state.value.tools[tool])
                    state.update { current ->
                        current.copy(
                            tools = current.tools + (tool to toolState),
                            selectedRareApps = if (tool == Tool.RARE_APPS && !toolState.active)
                                current.selectedRareApps.intersect(toolState.rows.filter { it.selectable }.map { it.id }.toSet())
                                else current.selectedRareApps,
                            restartPrompt = current.restartPrompt ||
                                (tool == Tool.DATA_CACHE && toolState.status == "completed"
                                    && toolState.id != 0L && toolState.id != promptedDataRun),
                        )
                    }
                    if (tool == Tool.DATA_CACHE && toolState.status == "completed" && toolState.id != 0L) {
                        promptedDataRun = toolState.id
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    state.update { it.copy(error = "${tool.key}: ${e.message ?: "Unable to refresh tool results"}") }
                }
            }
            if (run.id != 0L && !run.active && savedRun != run.id) {
                val report = java.text.DateFormat.getDateTimeInstance().format(java.util.Date()) + "\n" +
                    run.status + "\n" + run.results.joinToString("\n") { "${it.action.key}: ${it.status} — ${it.detail}" } + "\n" +
                    run.packages.joinToString("\n") { "${it.packageName}: ${it.filter} — ${it.status}" } +
                    if (run.packagesTruncated) "\nPackage list limited to the first 300 apps." else ""
                withContext(Dispatchers.IO) { preferences.saveReport(report) }
                savedRun = run.id
                state.update { it.copy(lastReport = report) }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { state.update { it.copy(connected = false, error = e.message ?: e.javaClass.simpleName) } }
    }
    fun refreshMetrics() = viewModelScope.launch { refresh(true) }
    fun dismissError() { state.update { it.copy(error = null) } }
    fun edit(transform: (Plan) -> Plan) {
        if (!state.value.editable || !initialized) return
        val plan = transform(state.value.plan)
        state.update { it.copy(plan = plan) }
        viewModelScope.launch { preferenceLock.withLock { withContext(Dispatchers.IO) { preferences.write(plan) } } }
    }
    fun profile(profile: Profile) = edit {
        when (profile) {
            Profile.QUICK -> Plan(cachePackages = it.cachePackages)
            Profile.DEEP -> Plan(profile = Profile.DEEP, fullCompilation = true, cachePackages = it.cachePackages)
            Profile.CUSTOM -> it.copy(profile = Profile.CUSTOM)
        }
    }
    fun toggle(action: Action, enabled: Boolean) = edit {
        it.copy(profile = Profile.CUSTOM, selected = if (enabled) it.selected + action else it.selected - action)
    }
    fun selectAppCache(packageName: String, selected: Boolean) = edit {
        it.copy(
            profile = Profile.CUSTOM,
            cachePackages = if (selected) it.cachePackages + packageName else it.cachePackages - packageName,
        )
    }
    fun selectRareApp(packageName: String, selected: Boolean) {
        state.update { it.copy(selectedRareApps = if (selected)
            it.selectedRareApps + packageName else it.selectedRareApps - packageName) }
    }
    fun startTool(tool: Tool, packages: Set<String> = emptySet()) {
        if (!state.value.connected || state.value.busy || state.value.tools.getValue(tool).active) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.startTool(tool, packages)
                if (tool == Tool.RARE_APPS && packages.isNotEmpty()) {
                    state.update { it.copy(selectedRareApps = emptySet()) }
                }
                refresh(false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(error = e.message ?: "Unable to start tool") } }
            finally { state.update { it.copy(busy = false) } }
        }
    }
    fun dismissRestart() { state.update { it.copy(restartPrompt = false) } }
    fun restartDevice() {
        state.update { it.copy(busy = true, restartPrompt = false) }
        viewModelScope.launch {
            try { repository.restartDevice() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(error = e.message ?: "Unable to restart device") } }
            finally { state.update { it.copy(busy = false) } }
        }
    }
    fun requestStart() {
        if (!state.value.editable || !state.value.connected || state.value.plan.selected.isEmpty()) return
        state.update { it.copy(confirm = true) }
    }
    fun dismissConfirmation() { state.update { it.copy(confirm = false) } }
    fun start() {
        if (!state.value.editable || !state.value.confirm) return
        val plan = state.value.plan
        state.update { it.copy(busy = true, confirm = false, error = null) }
        viewModelScope.launch {
            try { repository.start(plan); refresh(true) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(error = e.message ?: "Unable to start maintenance") } }
            finally { state.update { it.copy(busy = false) } }
        }
    }
    fun cancel() {
        if (!state.value.run.active || state.value.busy) return
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { repository.cancel(); refresh(false) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(error = e.message ?: "Unable to request cancellation") } }
            finally { state.update { it.copy(busy = false) } }
        }
    }
}
