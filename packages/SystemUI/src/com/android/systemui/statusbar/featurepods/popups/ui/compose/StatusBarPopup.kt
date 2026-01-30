/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.featurepods.popups.ui.compose

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.av.ui.compose.AvControlsChipPopup
import com.android.systemui.statusbar.featurepods.media.ui.compose.MediaControlPopup
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.sharescreen.ui.compose.ShareScreenPrivacyIndicatorPopup

/**
 * Displays a popup in the status bar area. The offset is calculated to draw the popup below the
 * status bar.
 */
@Composable
fun StatusBarPopup(
    viewModel: PopupChipModel.Shown,
    mediaViewModelFactory: MediaViewModel.Factory,
    mediaHost: MediaHost,
) {
    val density = Density(LocalContext.current)
    Popup(
        alignment = Alignment.TopCenter,
        properties =
            PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        offset =
            IntOffset(
                x = 0,
                y = with(density) { dimensionResource(R.dimen.status_bar_height).roundToPx() },
            ),
        onDismissRequest = { viewModel.hidePopup() },
    ) {
        Box(modifier = Modifier.padding(8.dp).wrapContentSize()) {
            when (viewModel.chipId) {
                is PopupChipId.MediaControl -> {
                    val viewRootImpl = LocalView.current.viewRootImpl
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    val owner =
                        object : OnBackPressedDispatcherOwner {
                            override val onBackPressedDispatcher =
                                OnBackPressedDispatcher().apply {
                                    setOnBackInvokedDispatcher(viewRootImpl.onBackInvokedDispatcher)
                                }

                            override val lifecycle: Lifecycle = lifecycle
                        }
                    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                        MediaControlPopup(
                            viewModelFactory = mediaViewModelFactory,
                            mediaHost = mediaHost,
                        )
                    }
                }

                is PopupChipId.AvControlsIndicator -> {
                    AvControlsChipPopup()
                }
                is PopupChipId.ShareScreenPrivacyIndicator -> {
                    ShareScreenPrivacyIndicatorPopup()
                }
            }
            // Future popup types will be handled here.
        }
    }
}
