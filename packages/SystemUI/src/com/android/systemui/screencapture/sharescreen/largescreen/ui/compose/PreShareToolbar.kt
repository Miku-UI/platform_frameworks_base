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

package com.android.systemui.screencapture.sharescreen.largescreen.ui.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.RadioButtonGroup
import com.android.systemui.screencapture.common.ui.compose.RadioButtonGroupItem
import com.android.systemui.screencapture.common.ui.compose.Toolbar
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.AppContentsViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DisplaysViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModel
import com.android.systemui.screencapture.sharescreen.ui.viewmodel.ScreenCaptureShareScreenViewModel

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun PreShareToolbar(
    shareScreenViewModel: ScreenCaptureShareScreenViewModel,
    expanded: Boolean,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    shareButtonEnabled: Boolean,
) {
    val currentTargetsModel = shareScreenViewModel.currentTargetsModel
    val (tabSelected, appSelected, fullscreenSelected) =
        remember(currentTargetsModel) {
            Triple(
                currentTargetsModel is AppContentsViewModel,
                currentTargetsModel is RecentTasksViewModel,
                currentTargetsModel is DisplaysViewModel,
            )
        }

    val tabIcon by
        loadIcon(
            viewModel = shareScreenViewModel,
            resId = R.drawable.ic_screen_capture_tab,
            contentDescription =
                ContentDescription.Resource(R.string.screen_share_tab_sharing_title),
        )
    val windowIcon by
        loadIcon(
            viewModel = shareScreenViewModel,
            resId = R.drawable.ic_screen_capture_window,
            contentDescription =
                ContentDescription.Resource(R.string.screen_share_app_window_sharing_title),
        )
    val fullscreenIcon by
        loadIcon(
            viewModel = shareScreenViewModel,
            resId = R.drawable.ic_screen_capture_fullscreen,
            contentDescription =
                ContentDescription.Resource(R.string.screen_share_entire_screen_sharing_title),
        )
    val shareTargetButtonItems = buildList {
        if (shareScreenViewModel.isAppContentSharingEnabled) {
            add(
                RadioButtonGroupItem(
                    icon = tabIcon,
                    modifier = Modifier.testTag("ShareTabOption"),
                    isSelected = tabSelected,
                    stateDescription =
                        stringResource(
                            if (tabSelected) R.string.screen_share_a11y_tab_button_selected
                            else R.string.screen_share_a11y_tab_button_unselected
                        ),
                    onClick = {
                        shareScreenViewModel.setTargetViewModel(
                            ScreenCaptureTarget.AppContent(contentId = 0)
                        )
                    },
                )
            )
        }
        if (shareScreenViewModel.isAppSharingEnabled) {
            add(
                RadioButtonGroupItem(
                    icon = windowIcon,
                    modifier = Modifier.testTag("ShareAppWindowOption"),
                    isSelected = appSelected,
                    stateDescription =
                        stringResource(
                            if (appSelected) R.string.screen_share_a11y_app_button_selected
                            else R.string.screen_share_a11y_app_button_unselected
                        ),
                    onClick = {
                        shareScreenViewModel.setTargetViewModel(
                            ScreenCaptureTarget.App(displayId = 0, taskId = 0)
                        )
                    },
                )
            )
        }
        if (shareScreenViewModel.isEntireScreenSharingEnabled) {
            add(
                RadioButtonGroupItem(
                    icon = fullscreenIcon,
                    modifier = Modifier.testTag("ShareEntireScreenOption"),
                    isSelected = fullscreenSelected,
                    stateDescription =
                        stringResource(
                            if (fullscreenSelected)
                                R.string.screen_share_a11y_fullscreen_button_selected
                            else R.string.screen_share_a11y_fullscreen_button_unselected
                        ),
                    onClick = {
                        shareScreenViewModel.setTargetViewModel(
                            ScreenCaptureTarget.Fullscreen(displayId = 0)
                        )
                    },
                )
            )
        }
    }

    Toolbar(
        expanded = expanded,
        onCloseClick = onCloseClick,
        modifier = modifier,
        closeButtonDescription = stringResource(R.string.screen_share_a11y_close_button),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButtonGroup(items = shareTargetButtonItems)

            Spacer(Modifier.size(16.dp))

            val shareIcon by
                loadIcon(
                    viewModel = shareScreenViewModel,
                    resId = R.drawable.ic_present_to_all,
                    ContentDescription.Resource(R.string.screen_share_toolbar_share_button),
                )

            val selectedItemLabel =
                currentTargetsModel.selectedTarget.value?.label?.getOrNull() ?: "item"
            val shareButtonA11yDescription =
                if (shareButtonEnabled) {
                    stringResource(R.string.screen_share_a11y_share_button_on, selectedItemLabel)
                } else {
                    stringResource(R.string.screen_share_a11y_share_button_off)
                }

            PrimaryButton(
                icon = shareIcon,
                text = stringResource(R.string.screen_share_toolbar_share_button),
                onClick = { shareScreenViewModel.onShareClicked() },
                enabled = shareButtonEnabled,
                modifier = Modifier.testTag("ShareButton").heightIn(min = 40.dp),
                contentDescription = shareButtonA11yDescription,
            )
        }
    }
}
