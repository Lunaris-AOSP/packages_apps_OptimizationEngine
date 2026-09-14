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
}
