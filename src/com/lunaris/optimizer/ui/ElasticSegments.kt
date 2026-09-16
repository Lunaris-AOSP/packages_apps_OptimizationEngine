/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*

internal class ElasticSegments(val weights: List<Float>, val tap: (Int) -> Unit)

@Composable
internal fun rememberElasticSegments(count: Int, selectedIndex: Int, elasticity: Float = 1f): ElasticSegments {
    var pressedIndex by remember(count) { mutableStateOf<Int?>(null) }
    var tapCount by remember(count) { mutableIntStateOf(0) }
    val weights = (0 until count).map { index ->
        val restingWeight = 1f + (if (selectedIndex == index) 0.15f else -0.10f) * elasticity
        val weight = remember(count, index) { Animatable(restingWeight) }
        LaunchedEffect(tapCount, selectedIndex, elasticity) {
            val bounce = spring<Float>(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = 600f,
            )
            if (tapCount > 0 && pressedIndex == selectedIndex) {
                val pushedWeight = 1f + (if (pressedIndex == index) 0.30f else -0.30f) * elasticity
                weight.animateTo(pushedWeight, tween(durationMillis = 100, easing = FastOutSlowInEasing))
            }
            weight.animateTo(restingWeight, bounce)
        }
        weight.value
    }
    return ElasticSegments(weights) { index ->
        pressedIndex = index
        tapCount++
    }
}
