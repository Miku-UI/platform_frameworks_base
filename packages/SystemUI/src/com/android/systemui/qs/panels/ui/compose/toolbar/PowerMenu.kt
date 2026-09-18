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

package com.android.systemui.qs.panels.ui.compose.toolbar

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.globalactions.ui.viewmodel.GlobalActionUiState
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.PowerMenuViewModel

/**
 * A composable that displays the inline Power Menu.
 *
 * This menu replaces the global actions dialog on supported large screen devices, providing quick
 * access to power management options directly from the Quick Settings toolbar. It appears as a
 * popup anchored to the bottom end of its parent.
 */
@Composable
fun PowerMenu(
    viewModelFactory: PowerMenuViewModel.Factory,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel("PowerMenu") { viewModelFactory.create() }
    Popup(
        popupPositionProvider = AnchorBottomEndProvider,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnClickOutside = true,
                dismissOnBackPress = true,
            ),
        onDismissRequest = onDismiss,
    ) {
        Card(
            modifier =
                modifier
                    .padding(top = PowerMenuConstants.MenuTopPadding)
                    .width(PowerMenuConstants.MenuWidth),
            shape = RoundedCornerShape(PowerMenuConstants.MenuCornerRadius),
        ) {
            Column {
                viewModel.visibleActions.forEach { action -> key(action.key) { ActionRow(action) } }
            }
        }
    }
}

@Composable
private fun ActionRow(viewModel: GlobalActionUiState.Visible, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PowerMenuConstants.ActionIconTextSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = viewModel.onClick,
                    indication = ripple(bounded = true, radius = Dp.Unspecified),
                    interactionSource = remember { MutableInteractionSource() },
                )
                .padding(horizontal = PowerMenuConstants.ActionHorizontalPadding)
                .height(PowerMenuConstants.ActionHeight),
    ) {
        Icon(viewModel.icon)
        Text(modifier = Modifier.basicMarquee(), text = stringResource(viewModel.textResId))
    }
}

private object AnchorBottomEndProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.right - popupContentSize.width
        val y = anchorBounds.bottom
        return IntOffset(x, y)
    }
}

private object PowerMenuConstants {
    val MenuWidth = 172.dp
    val MenuTopPadding = 8.dp
    val MenuCornerRadius = 20.dp
    val ActionHeight = 44.dp
    val ActionHorizontalPadding = 12.dp
    val ActionIconTextSpacing = 12.dp
}
