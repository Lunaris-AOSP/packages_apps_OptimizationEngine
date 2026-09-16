/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.ui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

@Composable
internal fun hapticClick(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onClick()
    }
}

@Composable
internal fun hapticToggle(onValueChange: (Boolean) -> Unit): (Boolean) -> Unit {
    val view = LocalView.current
    return { value ->
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onValueChange(value)
    }
}
