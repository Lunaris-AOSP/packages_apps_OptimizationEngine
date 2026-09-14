/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun HealthGauge(progress: Float?, label: String, modifier: Modifier = Modifier) {
    val value by animateFloatAsState(progress?.coerceIn(0f, 1f) ?: 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f), label = "maintenanceProgress")
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val gaugeSize = (208 * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)).dp
    Box(modifier.size(gaugeSize).semantics(mergeDescendants = true) {
        if (progress != null) progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val stroke = 16.dp.toPx()
            val offset = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 135f, 270f, false, offset, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (value > 0) drawArc(primary, 135f, value * 270f, false, offset, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (progress == null) {
                Icon(Icons.Default.Build, contentDescription = null,
                    modifier = Modifier.size(48.dp), tint = primary)
            } else {
                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.displayMedium)
            }
            Text(label, style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
