/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.lunaris.optimizer.ui.OptimizerScreen
import com.lunaris.optimizer.ui.OptimizerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: OptimizerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OptimizerTheme {
                OptimizerScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }
}
