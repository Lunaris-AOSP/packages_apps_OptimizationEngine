/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.lunaris.optimizer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.lerp
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
internal fun animatedGroupedCardShape(first: Boolean, last: Boolean, selected: Boolean): RoundedCornerShape {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val top by animateDpAsState(if (first) 28.dp else if (selected) 16.dp else 4.dp, spec, label = "groupTopCorner")
    val bottom by animateDpAsState(if (last) 28.dp else if (selected) 16.dp else 4.dp, spec, label = "groupBottomCorner")
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptimizerTopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val collapsedFraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val titleStyle = lerp(
        MaterialTheme.typography.displaySmallEmphasized,
        MaterialTheme.typography.titleMediumEmphasized,
        collapsedFraction,
    )
    LargeTopAppBar(
        title = {
            Text(
                title,
                style = titleStyle,
                maxLines = if (collapsedFraction > 0.5f) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            ExpressiveBackButton(onClick = hapticClick(onBack), modifier = Modifier.padding(start = 12.dp))
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
internal fun <T> PrimaryActionButton(
    state: T,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    icon: @Composable (T) -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val motion = MaterialTheme.motionScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = height),
        shapes = ButtonDefaults.shapes(
            shape = RoundedCornerShape(28.dp),
            pressedShape = RoundedCornerShape(16.dp),
        ),
        colors = colors,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        AnimatedContent(
            targetState = state to label,
            transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) +
                    scaleIn(motion.fastSpatialSpec(), initialScale = 0.92f)) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            },
            label = "primaryActionContent",
        ) { (currentState, currentLabel) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(ButtonDefaults.MediumIconSize)) { icon(currentState) }
                Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
                Text(
                    currentLabel,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val animatedContainer by animateColorAsState(
        containerColor,
        animationSpec = motion.fastEffectsSpec(),
        label = "statusPillContainer",
    )
    val animatedContent by animateColorAsState(
        contentColor,
        animationSpec = motion.fastEffectsSpec(),
        label = "statusPillContent",
    )
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = animatedContainer,
        contentColor = animatedContent,
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "statusPillText",
        ) { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelMediumEmphasized,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
