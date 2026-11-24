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
        view.performHapticFeedback(
            if (value) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        )
        onValueChange(value)
    }
}

@Composable
internal fun hapticConfirm(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        onClick()
    }
}

@Composable
internal fun hapticReject(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        onClick()
    }
}

@Composable
internal fun hapticSegment(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        onClick()
    }
}
