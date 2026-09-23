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
    val adaptiveCompilation: Boolean = true,
    val cachePackages: Set<String> = emptySet(),
    val continueOnError: Boolean = true,
) {
    companion object {
        fun forProfile(profile: Profile) = when (profile) {
            Profile.DEEP -> Plan(
                selected = setOf(Action.TRIM, Action.ZRAM, Action.DEXOPT),
                profile = Profile.DEEP,
                fullCompilation = true,
                adaptiveCompilation = false,
            )
            else -> Plan(profile = profile)
        }
    }

    fun toBundle() = Bundle().apply {
        Action.entries.forEach { putBoolean(it.key, it in selected) }
        putString("filter", if (adaptiveCompilation) "adaptive" else if (fullCompilation) "speed" else "speed-profile")
        putStringArray("cache_packages", cachePackages.sorted().toTypedArray())
        putBoolean("continue_on_error", continueOnError)
    }
}

enum class Tool(val key: String) {
    APP_CACHE("app_cache"), DATA_CACHE("data_cache"), RARE_APPS("rare_apps"), RESOURCES("resources")
}

data class ToolRow(
    val id: String,
    val title: String,
    val detail: String,
    val bytes: Long = 0,
    val cpuPercent: Float = 0f,
    val memoryMb: Int = 0,
    val background: Boolean = false,
    val selectable: Boolean = true,
    val storageBytes: Long? = null,
)

data class ToolState(
    val kind: Tool,
    val id: Long = 0,
    val status: String = "idle",
    val detail: String = "",
    val rows: List<ToolRow> = emptyList(),
) {
    val active get() = status == "running"

    companion object {
        fun fromBundle(kind: Tool, b: Bundle) = ToolState(
            kind = kind,
            id = b.getLong("id"),
            status = b.getString("status", "idle"),
            detail = b.getString("detail", ""),
            rows = (b.getParcelableArrayList("rows", Bundle::class.java) ?: arrayListOf()).map { row ->
                ToolRow(
                    id = row.getString("id", ""),
                    title = row.getString("title", row.getString("id", "")),
                    detail = row.getString("detail", ""),
                    bytes = row.getLong("bytes"),
                    cpuPercent = row.getFloat("cpu_percent"),
                    memoryMb = row.getInt("memory_mb"),
                    background = row.getBoolean("background"),
                    selectable = row.getBoolean("selectable", true),
                    storageBytes = if (row.containsKey("storage_bytes"))
                        row.getLong("storage_bytes").coerceAtLeast(0L) else null,
                )
            },
        )
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
