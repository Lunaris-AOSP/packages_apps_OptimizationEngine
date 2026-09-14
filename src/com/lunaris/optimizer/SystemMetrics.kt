/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer

import android.os.Bundle

data class SystemMetrics(
    val ramAvailableMb: Int? = null,
    val ramTotalMb: Long? = null,
    val storageAvailable: String = "—",
) {
    companion object {
        fun fromBundle(b: Bundle) = SystemMetrics(
            ramAvailableMb = if (b.containsKey("ram_available_mb")) b.getInt("ram_available_mb") else null,
            ramTotalMb = if (b.containsKey("ram_total_mb")) b.getLong("ram_total_mb") else null,
            storageAvailable = b.getString("storage_available", "—"),
        )
    }
}
