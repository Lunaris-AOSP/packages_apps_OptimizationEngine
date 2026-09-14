/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OptimizerTypography = Typography(
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = (-2).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun OptimizerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(
        primary = Color(0xFF86D3E8), onPrimary = Color(0xFF003640),
        primaryContainer = Color(0xFF004E5C), onPrimaryContainer = Color(0xFFA8EDFF),
        secondaryContainer = Color(0xFF354A50), onSecondaryContainer = Color(0xFFD0E7EE),
        background = Color(0xFF101416), surface = Color(0xFF101416),
        onBackground = Color(0xFFE0E3E5), onSurface = Color(0xFFE0E3E5),
        surfaceVariant = Color(0xFF3F484B), onSurfaceVariant = Color(0xFFBFC8CC),
    ) else lightColorScheme(
        primary = Color(0xFF006879), onPrimary = Color.White,
        primaryContainer = Color(0xFFA8EDFF), onPrimaryContainer = Color(0xFF001F26),
        secondaryContainer = Color(0xFFD0E7EE), onSecondaryContainer = Color(0xFF0B343E),
        background = Color(0xFFF5FAFC), surface = Color(0xFFF5FAFC),
        onBackground = Color(0xFF171D1F), onSurface = Color(0xFF171D1F),
        surfaceVariant = Color(0xFFDBE4E8), onSurfaceVariant = Color(0xFF3F484B),
    )
    MaterialTheme(colorScheme = colors, typography = OptimizerTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp)), content = content)
}
