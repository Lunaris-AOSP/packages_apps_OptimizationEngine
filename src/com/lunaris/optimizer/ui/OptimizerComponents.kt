/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.lunaris.optimizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.axion.compose.scaffold.ExpressiveBackButton

internal fun groupedCardShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 28.dp else 4.dp,
    topEnd = if (first) 28.dp else 4.dp,
    bottomStart = if (last) 28.dp else 4.dp,
    bottomEnd = if (last) 28.dp else 4.dp,
)

@Composable
internal fun PageHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.displaySmallEmphasized,
        modifier = modifier.semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptimizerTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
            }
        },
        navigationIcon = {
            ExpressiveBackButton(onClick = hapticClick(onBack), modifier = Modifier.padding(start = 12.dp))
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
internal fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = hapticClick(onClick),
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = height),
        shapes = ButtonDefaults.shapes(
            shape = RoundedCornerShape(28.dp),
            pressedShape = RoundedCornerShape(16.dp),
        ),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Box(Modifier.size(ButtonDefaults.MediumIconSize)) { icon() }
        Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
        Text(label, style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center)
    }
}
