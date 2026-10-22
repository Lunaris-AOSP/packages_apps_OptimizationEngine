/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.lunaris.optimizer.ui

import android.content.pm.PackageManager
import android.text.format.Formatter
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.drawable.toBitmap
import com.lunaris.optimizer.R
import com.lunaris.optimizer.model.ToolRow
import com.lunaris.optimizer.model.ToolState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AppCacheSummary(tool: ToolState, selectedPackages: Set<String>) {
    val context = LocalContext.current
    val totalBytes = remember(tool.rows) { tool.rows.sumOf { it.bytes.coerceAtLeast(0L) } }
    val storageBytes = remember(tool.rows) {
        if (tool.rows.any { it.storageBytes == null }) null
        else tool.rows.sumOf { (it.storageBytes ?: 0L).coerceAtLeast(0L) }
    }
    val selectedBytes = remember(tool.rows, selectedPackages) {
        tool.rows.sumOf { if (it.id in selectedPackages) it.bytes.coerceAtLeast(0L) else 0L }
    }
    val hasMeasurement = tool.rows.isNotEmpty() || tool.status == "completed" || tool.status == "partial"
    val totalSize = Formatter.formatShortFileSize(context, totalBytes)
    val selectedSize = Formatter.formatShortFileSize(context, selectedBytes)
    val appStorageColor = MaterialTheme.colorScheme.primary
    val cacheColor = MaterialTheme.colorScheme.tertiary

    Surface(modifier = Modifier.fillMaxWidth().animateContentSize(
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.total_app_cache), style = MaterialTheme.typography.titleMediumEmphasized)
            Text(if (hasMeasurement && !tool.active) totalSize else "—",
                style = MaterialTheme.typography.displaySmallEmphasized)
            when {
                tool.active -> {
                    Text(stringResource(R.string.scanning_app_cache), style = MaterialTheme.typography.bodyMedium)
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                }
                hasMeasurement -> {
                    if (storageBytes != null) {
                        val storageSize = Formatter.formatShortFileSize(context, storageBytes)
                        CacheStorageBar(storageBytes, totalBytes, appStorageColor, cacheColor,
                            stringResource(R.string.cache_storage_description, storageSize, totalSize))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StorageLegend(appStorageColor, stringResource(R.string.app_storage_size, storageSize))
                            StorageLegend(cacheColor, stringResource(R.string.app_cache_size, totalSize))
                        }
                    } else {
                        Text(stringResource(R.string.app_storage_unavailable), style = MaterialTheme.typography.bodyMedium)
                    }
                    Surface(shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = MaterialTheme.colorScheme.onSurface) {
                        Text(stringResource(R.string.cache_selected_size, selectedSize),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
                else -> Text(stringResource(R.string.cache_scan_required), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CacheStorageBar(storageBytes: Long, cacheBytes: Long, storageColor: Color,
    cacheColor: Color, description: String) {
    val storage = storageBytes.coerceAtLeast(0L).toDouble()
    val cache = cacheBytes.coerceAtLeast(0L).toDouble()
    val total = storage + cache
    val targetCacheFraction = if (total > 0.0) (cache / total).toFloat() else 0f
    val cacheFraction by animateFloatAsState(targetCacheFraction,
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = "cacheStorageShare")
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Canvas(Modifier.fillMaxWidth().height(12.dp).semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(targetCacheFraction, 0f..1f)
    }) {
        drawRoundRect(trackColor, cornerRadius = CornerRadius(size.height / 2))
        if (total <= 0.0) return@Canvas
        val fraction = cacheFraction.coerceIn(0f, 1f)
        val width = size.width * reveal.value
        val gap = minOf(4.dp.toPx(), width * fraction, width * (1f - fraction))
        val contentWidth = (width - gap).coerceAtLeast(0f)
        val appWidth = contentWidth * (1f - fraction)
        val cacheWidth = contentWidth * fraction
        fun segment(start: Float, segmentWidth: Float, color: Color) {
            if (segmentWidth <= 0f) return
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - start - segmentWidth else start
            drawRoundRect(color = color, topLeft = Offset(x, 0f), size = Size(segmentWidth, size.height),
                cornerRadius = CornerRadius(minOf(size.height, segmentWidth) / 2))
        }
        segment(0f, appWidth, storageColor)
        segment(appWidth + gap, cacheWidth, cacheColor)
    }
}

@Composable
private fun StorageLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun AppCacheRow(row: ToolRow, selected: Boolean, enabled: Boolean,
    first: Boolean, last: Boolean, change: (Boolean) -> Unit) {
    val context = LocalContext.current
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceBright,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "appCacheSelection",
    )
    val cacheSize = stringResource(R.string.app_cache_size,
        Formatter.formatShortFileSize(context, row.bytes.coerceAtLeast(0L)))
    val shape = groupedCardShape(first, last)
    Column {
        Surface(modifier = Modifier.fillMaxWidth(), shape = shape,
            color = backgroundColor,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface) {
            Row(Modifier.fillMaxWidth().toggleable(
                value = selected, enabled = enabled, role = Role.Checkbox,
                onValueChange = hapticToggle(change),
            ).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCacheIcon(row.id)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.title, style = if (selected) MaterialTheme.typography.titleMediumEmphasized
                        else MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(row.detail, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cacheSize,
                        style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                }
                Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            }
        }
        if (!last) Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AppCacheIcon(packageName: String) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, context, packageName, sizePx) {
        value = null
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            try {
                pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
            } catch (_: PackageManager.NameNotFoundException) {
                pm.defaultActivityIcon.toBitmap(sizePx, sizePx).asImageBitmap()
            } catch (_: RuntimeException) {
                pm.defaultActivityIcon.toBitmap(sizePx, sizePx).asImageBitmap()
            }
        }
    }
    Crossfade(targetState = icon, modifier = Modifier.size(40.dp),
        animationSpec = tween(150), label = "appCacheIcon") { bitmap ->
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_grid_view), contentDescription = null,
                modifier = Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
