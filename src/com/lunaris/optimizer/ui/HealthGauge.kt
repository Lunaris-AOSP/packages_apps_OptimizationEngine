/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.lunaris.optimizer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

@Composable
fun HealthGauge(
    progress: Float?,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val target = progress?.coerceIn(0f, 1f) ?: 0f
    val motion = MaterialTheme.motionScheme
    val value by animateFloatAsState(
        target,
        animationSpec = motion.slowEffectsSpec(),
        label = "maintenanceProgress",
    )
    val waveAmplitude by animateFloatAsState(
        if (active) 1f else 0f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "maintenanceWaveAmplitude",
    )
    val primary = MaterialTheme.colorScheme.primary
    val locale = LocalConfiguration.current.locales[0]
    val percentFormat = remember(locale) { NumberFormat.getPercentInstance(locale) }
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedContent(
            targetState = progress != null,
            transitionSpec = {
                (scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f) +
                    fadeIn(motion.defaultEffectsSpec())) togetherWith
                    (scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f) +
                        fadeOut(motion.fastEffectsSpec()))
            },
            label = "healthGaugeMode",
        ) { hasProgress ->
            if (!hasProgress) {
                Surface(
                    modifier = Modifier.size(104.dp),
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(44.dp))
                    }
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val fontScale = LocalDensity.current.fontScale
                    val gaugeSize = minOf(maxWidth, (184 * fontScale.coerceIn(1f, 1.5f)).dp)
                    val stroke = with(LocalDensity.current) { Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round) }
                    val numberStyle = (if (fontScale > 1.3f) MaterialTheme.typography.headlineLargeEmphasized
                        else MaterialTheme.typography.displayMediumEmphasized).copy(fontFeatureSettings = "tnum")
                    Box(
                        Modifier.size(gaugeSize).semantics(mergeDescendants = true) {
                            progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                            contentDescription = label
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { value },
                            modifier = Modifier.fillMaxSize().padding(8.dp).clearAndSetSemantics {},
                            color = primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                            stroke = stroke,
                            trackStroke = stroke,
                            amplitude = { waveAmplitude },
                            waveSpeed = if (active) 16.dp else 0.dp,
                        )
                        Text(
                            percentFormat.format(value.toDouble()),
                            style = numberStyle,
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center,
        )
    }
}
