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

    private suspend fun refresh(includeMetrics: Boolean) {
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
            if (run.id != 0L && !run.active && savedRun != run.id) {
                val report = java.text.DateFormat.getDateTimeInstance().format(java.util.Date()) + "\n" +
                    run.status + "\n" + run.results.joinToString("\n") { "${it.action.key}: ${it.status} — ${it.detail}" }
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
            Profile.QUICK -> Plan()
            Profile.DEEP -> Plan(profile = Profile.DEEP, fullCompilation = true)
            Profile.CUSTOM -> it.copy(profile = Profile.CUSTOM)
        }
    }
    fun toggle(action: Action, enabled: Boolean) = edit {
        it.copy(profile = Profile.CUSTOM, selected = if (enabled) it.selected + action else it.selected - action)
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
