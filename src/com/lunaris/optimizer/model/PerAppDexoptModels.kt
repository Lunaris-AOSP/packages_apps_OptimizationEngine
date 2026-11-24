/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.model

import android.os.Bundle

data class DexoptApp(
    val packageName: String,
    val title: String,
    val system: Boolean,
    val currentFilter: String,
    val currentStatus: String,
    val currentDetail: String,
    val status: String,
    val requestedFilter: String,
    val detail: String,
) {
    companion object {
        fun fromBundle(b: Bundle) = DexoptApp(
            packageName = b.getString("package", ""),
            title = b.getString("title", b.getString("package", "")),
            system = b.getBoolean("system"),
            currentFilter = b.getString("current_filter", ""),
            currentStatus = b.getString("current_status", "unavailable"),
            currentDetail = b.getString("current_detail", ""),
            status = b.getString("status", "ready"),
            requestedFilter = b.getString("requested_filter", ""),
            detail = b.getString("detail", ""),
        )
    }
}

data class PerAppDexoptState(
    val id: Long = 0,
    val revision: Long = -1,
    val status: String = "idle",
    val detail: String = "",
    val loaded: Boolean = false,
    val done: Int = 0,
    val selectedCount: Int = 0,
    val filters: List<String> = emptyList(),
    val apps: List<DexoptApp> = emptyList(),
) {
    val compiling get() = status == "running" || status == "cancelling"
    val active get() = compiling || status == "loading"
    val progress get() = if (selectedCount == 0) 0f else
        (done.toFloat() / selectedCount).coerceIn(0f, 1f)

    companion object {
        fun fromBundle(b: Bundle) = PerAppDexoptState(
            id = b.getLong("id"), revision = b.getLong("revision"),
            status = b.getString("status", "idle"), detail = b.getString("detail", ""),
            loaded = b.getBoolean("loaded"), done = b.getInt("done"),
            selectedCount = b.getInt("selected_count"),
            filters = b.getStringArrayList("filters") ?: emptyList(),
            apps = (b.getParcelableArrayList("rows", Bundle::class.java) ?: arrayListOf())
                .map(DexoptApp::fromBundle),
        )
    }
}
