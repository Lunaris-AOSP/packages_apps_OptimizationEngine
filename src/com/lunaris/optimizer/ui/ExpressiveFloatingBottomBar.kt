/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lunaris.optimizer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lunaris.optimizer.R

@Composable
internal fun FloatingBottomNavigation(selectedTab: Int, segments: ElasticSegments, onTabSelected: (Int) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        ).padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ExpressiveFloatingBottomBar(selectedTab, segments, onTabSelected)
    }
}

@Composable
fun ExpressiveFloatingBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    ExpressiveFloatingBottomBar(selectedTab, rememberElasticSegments(2, selectedTab), onTabSelected)
}

@Composable
private fun ExpressiveFloatingBottomBar(selectedTab: Int, segments: ElasticSegments, onTabSelected: (Int) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp).heightIn(min = 52.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingNavItem(
                label = stringResource(R.string.nav_home),
                icon = { Icon(painterResource(R.drawable.ic_grid_view), contentDescription = null) },
                selected = selectedTab == 0,
                onClick = { segments.tap(0); onTabSelected(0) },
                modifier = Modifier.weight(segments.weights[0]),
            )
            FloatingNavItem(
                label = stringResource(R.string.nav_extra),
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                selected = selectedTab == 1,
                onClick = { segments.tap(1); onTabSelected(1) },
                modifier = Modifier.weight(segments.weights[1]),
            )
        }
    }
}

@Composable
private fun FloatingNavItem(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "navigationContainer",
    )
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navigationContent",
    )
    Surface(modifier = modifier, shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Row(
            modifier = Modifier.clip(CircleShape)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
