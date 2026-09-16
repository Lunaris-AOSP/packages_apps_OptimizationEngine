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
        val profile = Profile.entries.find { it.name == store.getString("profile", "QUICK") } ?: Profile.QUICK
        val preset = Plan.forProfile(profile)
        val selected = store.getStringSet("actions", null)?.mapNotNull { key -> Action.entries.find { it.key == key } }?.toSet()
        return Plan(
            selected = if (profile == Profile.CUSTOM) selected ?: preset.selected else preset.selected,
            profile = profile,
            adaptiveCompilation = if (profile == Profile.CUSTOM) store.getBoolean("adaptive", false) else preset.adaptiveCompilation,
            fullCompilation = if (profile == Profile.CUSTOM) store.getBoolean("full", false) else preset.fullCompilation,
            cachePackages = store.getStringSet("cache_packages", emptySet()) ?: emptySet(),
            continueOnError = store.getBoolean("continue", true),
        )
    }
    fun clearedCacheRun(): Long = store.getLong("cleared_cache_run", 0L)
    fun write(plan: Plan, clearedCacheRun: Long) {
        store.edit().putStringSet("actions", plan.selected.map { it.key }.toSet())
            .putBoolean("adaptive", plan.adaptiveCompilation)
            .putString("profile", plan.profile.name).putBoolean("full", plan.fullCompilation)
            .putStringSet("cache_packages", plan.cachePackages)
            .putLong("cleared_cache_run", clearedCacheRun)
            .putBoolean("continue", plan.continueOnError).apply()
    }
    fun lastReport(): String = store.getString("last_report", "") ?: ""
    fun saveReport(report: String) { store.edit().putString("last_report", report.take(200000)).apply() }
}
