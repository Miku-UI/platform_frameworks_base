/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.settingslib.spa.widget.scaffold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsSize
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.isSpaExpressiveEnabled
import com.android.settingslib.spa.framework.theme.settingsBackground
import com.android.settingslib.spa.framework.theme.toMediumWeight
import com.android.settingslib.spa.widget.ui.SettingsIntro

data class BottomAppBarButton(
    val text: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun GlifScaffold(
    imageVector: ImageVector,
    title: String,
    description: String = "",
    actionButton: BottomAppBarButton? = null,
    dismissButton: BottomAppBarButton? = null,
    content: @Composable () -> Unit,
) {
    ActivityTitle(title)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.settingsBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        BoxWithConstraints(
            Modifier.padding(innerPadding).padding(top = SettingsSpace.extraSmall4)
        ) {
            val movableContent = remember(content) { movableContentOf { content() } }
            // Use single column layout in portrait, two columns in landscape.
            val useSingleColumn = maxWidth < maxHeight
            if (useSingleColumn) {
                Column {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Header(imageVector, title, description)
                        movableContent()
                    }
                    BottomBar(actionButton, dismissButton)
                }
            } else {
                Column(Modifier.padding(horizontal = SettingsSpace.extraSmall4)) {
                    Row((Modifier.weight(1f))) {
                        Box(Modifier.weight(1f)) { Header(imageVector, title, description) }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            movableContent()
                        }
                    }
                    BottomBar(actionButton, dismissButton)
                }
            }
        }
    }
}

@Composable
private fun Header(imageVector: ImageVector, title: String, description: String) {
    if (isSpaExpressiveEnabled) {
        HeaderExpressive(imageVector, title, description)
    } else {
        HeaderLegacy(imageVector, title, description)
    }
}

@Composable
private fun HeaderExpressive(imageVector: ImageVector, title: String, description: String) {
    Column(
        modifier =
            Modifier.padding(horizontal = SettingsSpace.small3, vertical = SettingsSpace.small4),
        verticalArrangement = Arrangement.spacedBy(SettingsSpace.small1),
    ) {
        Box {
            NavigateBack()
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(SettingsSize.medium4),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.displaySmallEmphasized,
        )
        if (description.isNotEmpty()) {
            SettingsIntro(description)
        }
    }
}

@Composable
private fun HeaderLegacy(imageVector: ImageVector, title: String, description: String) {
    Column(
        modifier =
            Modifier.padding(
                start = SettingsDimension.itemPaddingStart,
                top = SettingsDimension.itemPaddingVertical,
                end = SettingsDimension.itemPaddingEnd,
                bottom = SettingsDimension.paddingExtraLarge,
            ),
        verticalArrangement = Arrangement.spacedBy(SettingsDimension.itemPaddingVertical),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier =
                Modifier.padding(vertical = SettingsDimension.itemPaddingAround)
                    .size(SettingsDimension.iconLarge),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.displaySmall,
        )
        if (description.isNotEmpty()) {
            SettingsIntro(description)
        }
    }
}

@Composable
private fun BottomBar(actionButton: BottomAppBarButton?, dismissButton: BottomAppBarButton?) {
    if (isSpaExpressiveEnabled) {
        BottomBarExpressive(actionButton, dismissButton)
    } else {
        BottomBarLegacy(actionButton, dismissButton)
    }
}

@Composable
private fun BottomBarExpressive(
    actionButton: BottomAppBarButton?,
    dismissButton: BottomAppBarButton?,
) {
    Row(
        modifier =
            Modifier.padding(
                start = SettingsSpace.small4,
                top = SettingsSpace.extraSmall4,
                end = SettingsSpace.small4,
                bottom = SettingsSpace.extraSmall1,
            ),
        horizontalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall4),
    ) {
        val size = ButtonDefaults.MediumContainerHeight
        dismissButton?.apply {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.weight(1f).heightIn(size),
                enabled = enabled,
                contentPadding = ButtonDefaults.contentPaddingFor(size),
            ) {
                ActionText(text)
            }
        }
        actionButton?.apply {
            Button(
                onClick = onClick,
                modifier = Modifier.weight(1f).heightIn(size),
                enabled = enabled,
                contentPadding = ButtonDefaults.contentPaddingFor(size),
            ) {
                ActionText(text)
            }
        }
    }
}

@Composable
private fun BottomBarLegacy(actionButton: BottomAppBarButton?, dismissButton: BottomAppBarButton?) {
    Row(
        Modifier.padding(
            start = SettingsDimension.itemPaddingEnd,
            top = SettingsDimension.paddingExtraLarge,
            end = SettingsDimension.itemPaddingEnd,
            bottom = SettingsDimension.itemPaddingVertical,
        )
    ) {
        dismissButton?.apply {
            TextButton(onClick = onClick, enabled = enabled) { ActionText(text) }
        }
        Spacer(modifier = Modifier.weight(1f))
        actionButton?.apply { Button(onClick = onClick, enabled = enabled) { ActionText(text) } }
    }
}

@Composable
private fun ActionText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium.toMediumWeight())
}
