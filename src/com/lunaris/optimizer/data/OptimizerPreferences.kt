/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.data

import android.content.Context
import com.lunaris.optimizer.model.*

class OptimizerPreferences(context: Context) {
    private val store = context.getSharedPreferences("optimizer", Context.MODE_PRIVATE)
    fun read(): Plan {
        val selected = store.getStringSet("actions", null)?.mapNotNull { key -> Action.entries.find { it.key == key } }?.toSet()
        return Plan(
            selected = selected ?: Plan().selected,
            profile = Profile.entries.find { it.name == store.getString("profile", "QUICK") } ?: Profile.QUICK,
            fullCompilation = store.getBoolean("full", false),
            cacheTargetGiB = store.getInt("target", 4).coerceIn(1, 16),
            continueOnError = store.getBoolean("continue", true),
        )
    }
    fun write(plan: Plan) {
        store.edit().putStringSet("actions", plan.selected.map { it.key }.toSet())
            .putString("profile", plan.profile.name).putBoolean("full", plan.fullCompilation)
            .putInt("target", plan.cacheTargetGiB).putBoolean("continue", plan.continueOnError).apply()
    }
    fun lastReport(): String = store.getString("last_report", "") ?: ""
    fun saveReport(report: String) { store.edit().putString("last_report", report.take(16000)).apply() }
}
