/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.lunaris.optimizer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme

internal class ElasticSegments(val weights: List<Float>, val tap: (Int) -> Unit)

@Composable
internal fun rememberElasticSegments(count: Int, selectedIndex: Int, elasticity: Float = 1f): ElasticSegments {
    var pressedIndex by remember(count) { mutableStateOf<Int?>(null) }
    var tapCount by remember(count) { mutableIntStateOf(0) }
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val weights = (0 until count).map { index ->
        val restingWeight = 1f + (if (selectedIndex == index) 0.15f else -0.10f) * elasticity
        val weight = remember(count, index) { Animatable(restingWeight) }
        LaunchedEffect(tapCount, selectedIndex, elasticity, settleSpec) {
            if (tapCount > 0 && pressedIndex == selectedIndex) {
                val pushedWeight = 1f + (if (pressedIndex == index) 0.30f else -0.30f) * elasticity
                weight.animateTo(pushedWeight, tween(durationMillis = 100, easing = FastOutSlowInEasing))
            }
            weight.animateTo(restingWeight, settleSpec)
        }
        weight.value
    }
    return ElasticSegments(weights) { index ->
        pressedIndex = index
        tapCount++
    }
}
