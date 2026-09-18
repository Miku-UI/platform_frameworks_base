/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.widget.button

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DisabledByDefault
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.R
import com.android.settingslib.spa.framework.compose.contentDescription
import com.android.settingslib.spa.framework.compose.thenIf
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsDimension.buttonPaddingVertical
import com.android.settingslib.spa.framework.theme.SettingsDimension.itemPaddingEnd
import com.android.settingslib.spa.framework.theme.SettingsShape
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.framework.theme.divider
import com.android.settingslib.spa.framework.theme.isSpaExpressiveEnabled
import com.android.settingslib.spa.widget.card.SettingsCardContent

data class ActionButton(
    val text: String,
    val imageVector: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ActionButtons(actionButtons: List<ActionButton>) {
    val buttonHorizontalPadding = itemPaddingEnd
    val buttonPadding =
        PaddingValues(horizontal = buttonHorizontalPadding, vertical = buttonPaddingVertical)
    if (isSpaExpressiveEnabled) {
        BoxWithConstraints {
            val textHorizontalPadding = SettingsSpace.extraSmall4
            val textWidth = with(LocalDensity.current) {
                (constraints.maxWidth - itemPaddingEnd.toPx() * 2) / actionButtons.size -
                        textHorizontalPadding.toPx() * 2
            }
            val textMeasurer = rememberTextMeasurer()
            val textStyle = MaterialTheme.typography.titleSmallEmphasized
            val useHorizontalLayout = actionButtons.all {
                val size = textMeasurer.measure(
                    it.text,
                    textStyle,
                    constraints = Constraints(maxWidth = textWidth.toInt())
                )
                // A magic number, if any text has >= 4 lines, render vertically
                size.lineCount < 4
            }
            if (useHorizontalLayout) {
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier
                        .padding(buttonPadding)
                        .height(IntrinsicSize.Min)
                        .fillMaxWidth()
                ) {
                    for (actionButton in actionButtons) {
                        ExpressiveActionButton(
                            actionButton, Modifier
                                .weight(1f), textStyle, textHorizontalPadding
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier
                        .padding(buttonPadding)
                        .height(IntrinsicSize.Min)
                        .fillMaxWidth()
                ) {
                    for (actionButton in actionButtons) {
                        ExpressiveActionButton(
                            actionButton, Modifier
                                .weight(1f), textStyle, textHorizontalPadding
                        )
                        Spacer(Modifier.height(SettingsSpace.extraSmall3))
                    }
                }
            }
        }
    } else {
        Row(
            Modifier
                .padding(buttonPadding)
                .clip(SettingsShape.CornerExtraLarge1)
                .height(IntrinsicSize.Min)
        ) {
            for ((index, actionButton) in actionButtons.withIndex()) {
                if (index > 0) ButtonDivider()
                ActionButton(actionButton)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveActionButton(
    actionButton: ActionButton,
    modifier: Modifier,
    textStyle: TextStyle,
    textHorizontalPadding: Dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.clickable(
            onClick = actionButton.onClick,
            enabled = actionButton.enabled, // Control clickability based on state
            role = Role.Button,
        )
    ) {
        IconButton(actionButton)
        Spacer(Modifier.height(SettingsSpace.extraSmall3))
        Text(
            text = actionButton.text,
            modifier = Modifier
                .padding(horizontal = textHorizontalPadding),
            style = textStyle,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.ActionButton(actionButton: ActionButton) {
    FilledTonalButton(
        onClick = actionButton.onClick,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        enabled = actionButton.enabled,
        // Because buttons could appear, disappear or change positions, reset the interaction source
        // to prevent highlight the wrong button.
        interactionSource = remember(actionButton) { MutableInteractionSource() },
        shape = RectangleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = actionButton.imageVector,
                contentDescription = null,
                modifier = Modifier.size(SettingsDimension.itemIconSize),
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionButton.text,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun IconButton(actionButton: ActionButton) {
    FilledIconButton(
        onClick = actionButton.onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier =
            Modifier
                .size(
                    IconButtonDefaults.mediumContainerSize(
                        IconButtonDefaults.IconButtonWidthOption.Wide
                    )
                )
                .clearAndSetSemantics {}, // semantics set in IconButton
        enabled = actionButton.enabled,
        // Because buttons could appear, disappear or change positions, reset the interaction source
        // to prevent highlight the wrong button.
        interactionSource = remember(actionButton) { MutableInteractionSource() },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Icon(
            imageVector = actionButton.imageVector,
            contentDescription = actionButton.text,
            modifier = Modifier.size(SettingsDimension.itemIconSize),
        )
    }
}

@Composable
private fun ButtonDivider() {
    Box(
        Modifier
            .width(1.dp)
            .background(color = MaterialTheme.colorScheme.divider)
    )
}

@Preview
@Composable
private fun ActionButtonsPreview() {
    SettingsTheme {
        SettingsCardContent {
            ActionButtons(
                listOf(
                    ActionButton(
                        text = "Open",
                        imageVector = Icons.AutoMirrored.Outlined.Launch
                    ) {},
                    ActionButton(text = "Uninstall", imageVector = Icons.Outlined.Delete) {},
                    ActionButton(
                        text = "Force stop",
                        imageVector = Icons.Outlined.WarningAmber
                    ) {},
                    ActionButton(
                        text = "long long long long text",
                        imageVector = Icons.Outlined.DisabledByDefault
                    ) {},
                )
            )
            ActionButtons(
                listOf(
                    ActionButton(
                        text = "Open",
                        imageVector = Icons.AutoMirrored.Outlined.Launch
                    ) {},
                    ActionButton(text = "Uninstall", imageVector = Icons.Outlined.Delete) {},
                    ActionButton(
                        text = "Force stop",
                        imageVector = Icons.Outlined.WarningAmber
                    ) {},
                    ActionButton(
                        text = "long long long long long long text",
                        imageVector = Icons.Outlined.DisabledByDefault
                    ) {},
                )
            )
        }
    }
}
