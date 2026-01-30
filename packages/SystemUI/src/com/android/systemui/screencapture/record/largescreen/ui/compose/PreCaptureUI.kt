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

package com.android.systemui.screencapture.record.largescreen.ui.compose

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureColors
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.PreCaptureViewModel
import kotlin.math.roundToInt

/** Main component for the pre-capture UI. */
@Composable
fun PreCaptureUI(viewModel: PreCaptureViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .wrapContentSize(Alignment.TopCenter)
                    .padding(top = 36.dp)
                    .zIndex(1f)
        ) {
            PreCaptureToolbar(
                viewModel = viewModel,
                expanded = true,
                onCloseClick = { viewModel.closeFromToolbar() },
                modifier =
                    Modifier.onGloballyPositioned {
                            val boundsInWindow = it.boundsInWindow()
                            viewModel.updateToolbarBounds(
                                Rect(
                                    boundsInWindow.left.roundToInt(),
                                    boundsInWindow.top.roundToInt(),
                                    boundsInWindow.right.roundToInt(),
                                    boundsInWindow.bottom.roundToInt(),
                                )
                            )
                        }
                        .graphicsLayer { alpha = viewModel.toolbarOpacity },
            )
        }

        val iconResourceId =
            when (viewModel.captureType) {
                ScreenCaptureType.SCREENSHOT -> R.drawable.ic_screen_capture_camera
                ScreenCaptureType.RECORDING -> R.drawable.ic_screenrecord
            }

        when (viewModel.captureRegion) {
            ScreenCaptureRegion.FULLSCREEN -> {
                // Dim the entire screen with a scrim before taking a fullscreen screenshot.
                Box(
                    modifier =
                        Modifier.zIndex(0f)
                            .fillMaxSize()
                            .background(color = ScreenCaptureColors.scrimColor)
                ) {
                    PrimaryButton(
                        modifier = Modifier.align(Alignment.Center),
                        icon =
                            loadIcon(
                                    viewModel = viewModel,
                                    resId = iconResourceId,
                                    contentDescription = null,
                                )
                                .value,
                        text =
                            stringResource(
                                when (viewModel.captureType) {
                                    ScreenCaptureType.SCREENSHOT ->
                                        R.string.screen_capture_fullscreen_screenshot_button
                                    ScreenCaptureType.RECORDING ->
                                        R.string.screen_capture_fullscreen_record_button
                                }
                            ),
                        onClick = viewModel::beginCapture,
                    )
                }
            }

            ScreenCaptureRegion.PARTIAL -> {
                // TODO(b/427541309) Set the initial width and height of the RegionBox based on the
                // viewmodel state.
                val icon by
                    loadIcon(
                        viewModel = viewModel,
                        resId = iconResourceId,
                        contentDescription = null,
                    )
                RegionBox(
                    initialRect = viewModel.regionBox,
                    buttonText =
                        stringResource(
                            id =
                                when (viewModel.captureType) {
                                    ScreenCaptureType.SCREENSHOT ->
                                        R.string.screen_capture_region_selection_button
                                    ScreenCaptureType.RECORDING ->
                                        R.string.screen_capture_record_region_selection_button
                                }
                        ),
                    buttonIcon = icon,
                    onRegionSelected = { regionBoxRect ->
                        viewModel.updateRegionBoxBounds(regionBoxRect)
                        viewModel.updateToolbarOpacityForRegionBox(
                            isInteracting = false,
                            regionBoxRect = regionBoxRect,
                        )
                    },
                    onCaptureClick = viewModel::beginCapture,
                    onInteractionStateChanged = { isInteracting ->
                        viewModel.updateToolbarOpacityForRegionBox(isInteracting)
                    },
                )
            }

            ScreenCaptureRegion.APP_WINDOW -> {}
        }
    }
}
