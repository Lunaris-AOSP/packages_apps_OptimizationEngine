/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.model

import android.os.Bundle

enum class Action(val key: String) { CACHE("cache"), TRIM("trim"), ZRAM("zram"), DEXOPT("dexopt") }
enum class Profile { QUICK, DEEP, CUSTOM }
data class Plan(
    val selected: Set<Action> = setOf(Action.TRIM, Action.DEXOPT),
    val profile: Profile = Profile.QUICK,
    val fullCompilation: Boolean = false,
    val adaptiveCompilation: Boolean = false,
    val cacheTargetGiB: Int = 4,
    val continueOnError: Boolean = true,
) {
    fun toBundle() = Bundle().apply {
        Action.entries.forEach { putBoolean(it.key, it in selected) }
        putString("filter", if (adaptiveCompilation) "adaptive" else if (fullCompilation) "speed" else "speed-profile")
        putInt("cache_target_gib", cacheTargetGiB)
        putBoolean("continue_on_error", continueOnError)
    }
}
data class ActionResult(val action: Action, val status: String, val detail: String)
data class PackageResult(val packageName: String, val filter: String, val status: String)
data class Run(
    val id: Long = 0,
    val status: String = "idle",
    val progress: Float = 0f,
    val started: Long = 0,
    val finished: Long = 0,
    val results: List<ActionResult> = emptyList(),
    val packages: List<PackageResult> = emptyList(),
    val packagesTruncated: Boolean = false,
) {
    val active get() = status == "running" || status == "cancelling"
    companion object {
        fun fromBundle(b: Bundle) = Run(
            id = b.getLong("id"), status = b.getString("status", "idle"),
            progress = b.getFloat("progress").coerceIn(0f, 1f),
            packages = (b.getParcelableArrayList("packages", Bundle::class.java) ?: arrayListOf()).map {
                PackageResult(it.getString("package", ""), it.getString("filter", ""), it.getString("status", ""))
            },
            packagesTruncated = b.getBoolean("packages_truncated"),
            started = b.getLong("started"), finished = b.getLong("finished"),
            results = (b.getStringArrayList("actions") ?: arrayListOf()).mapNotNull { key ->
                Action.entries.find { it.key == key }?.let {
                    ActionResult(it, b.getString("$key.status", "queued"), b.getString("$key.detail", ""))
                }
            },
        )
    }
}
