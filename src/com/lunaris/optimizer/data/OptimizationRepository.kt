/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.data

import android.content.Context
import android.os.OptimizationManager
import com.lunaris.optimizer.SystemMetrics
import com.lunaris.optimizer.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OptimizationRepository(private val context: Context) {
    private fun manager() = context.getSystemService(Context.OPTIMIZATION_SERVICE) as? OptimizationManager
        ?: error("Optimization service unavailable. Check the ROM integration.")
    suspend fun snapshot() = withContext(Dispatchers.IO) { Run.fromBundle(manager().getSnapshot()) }
    suspend fun metrics() = withContext(Dispatchers.IO) { SystemMetrics.fromBundle(manager().getMetrics()) }
    suspend fun start(plan: Plan) = withContext(Dispatchers.IO) { manager().startConfigured(plan.toBundle()) }
    suspend fun cancel() = withContext(Dispatchers.IO) { manager().cancelOptimization() }
    suspend fun startTool(tool: Tool, packages: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        manager().startTool(tool.key, packages.sorted().toTypedArray())
    }
    suspend fun toolSnapshot(tool: Tool, previous: ToolState? = null): ToolState = withContext(Dispatchers.IO) {
        readToolSnapshot(tool, previous) { offset -> manager().getToolSnapshot(tool.key, offset) }
    }
    suspend fun refreshDexoptApps() = withContext(Dispatchers.IO) { manager().refreshDexoptApps() }
    suspend fun startPerAppDexopt(packages: Set<String>, filter: String) = withContext(Dispatchers.IO) {
        manager().startPerAppDexopt(packages.sorted().toTypedArray(), filter)
    }
    suspend fun cancelPerAppDexopt() = withContext(Dispatchers.IO) { manager().cancelPerAppDexopt() }
    suspend fun perAppDexoptSnapshot(previous: PerAppDexoptState): PerAppDexoptState = withContext(Dispatchers.IO) {
        repeat(3) {
            val metadata = manager().getPerAppDexoptSnapshot(Int.MAX_VALUE)
            val header = PerAppDexoptState.fromBundle(metadata)
            if (previous.revision == header.revision) return@withContext header.copy(apps = previous.apps)
            val total = metadata.getInt("total")
            val apps = mutableListOf<DexoptApp>()
            while (apps.size < total) {
                val page = manager().getPerAppDexoptSnapshot(apps.size)
                if (page.getLong("revision") != header.revision) break
                val parsed = PerAppDexoptState.fromBundle(page).apps
                if (parsed.isEmpty() || apps.size + parsed.size > total) break
                apps += parsed
            }
            if (apps.size == total) return@withContext header.copy(apps = apps.sortedWith(
                compareBy<DexoptApp> { it.title.lowercase(java.util.Locale.ROOT) }.thenBy { it.packageName },
            ))
        }
        previous
    }
    suspend fun restartDevice() = withContext(Dispatchers.IO) { manager().restartDevice() }
}

internal fun readToolSnapshot(tool: Tool, previous: ToolState?, fetch: (Int) -> android.os.Bundle): ToolState {
    repeat(3) {
        val metadata = fetch(Int.MAX_VALUE)
        val header = ToolState.fromBundle(tool, metadata)
        val total = metadata.getInt("total")
        if (previous != null && !header.active && previous.id == header.id
            && previous.status == header.status && previous.rows.size == total) {
            return header.copy(rows = previous.rows)
        }
        if (total == 0) return header
        val rows = mutableListOf<ToolRow>()
        while (rows.size < total) {
            val page = fetch(rows.size)
            if (page.getLong("id") != header.id || page.getString("status") != header.status
                || page.getInt("total") != total) break
            val parsed = ToolState.fromBundle(tool, page)
            if (parsed.rows.isEmpty() || rows.size + parsed.rows.size > total) break
            rows += parsed.rows
        }
        if (rows.size == total) return header.copy(rows = rows)
    }
    error("Tool results changed while loading. Retry to load a consistent scan.")
}
