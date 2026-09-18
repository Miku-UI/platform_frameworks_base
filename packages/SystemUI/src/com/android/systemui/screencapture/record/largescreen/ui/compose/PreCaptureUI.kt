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

import android.graphics.Point
import android.view.PointerIcon as ViewPointerIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.compose.modifiers.thenIf
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureColors
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.PreCaptureViewModel

// Scale in from the center of the top of the screen
private val scaleTransformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)

/** Main component for the pre-capture UI. */
@Composable
fun PreCaptureUI(viewModel: PreCaptureViewModel) {
    val localResources = LocalResources.current

    val uiVisibilityState = remember { MutableTransitionState(false) }

    // When the component loads, animate in.
    LaunchedEffect(Unit) { uiVisibilityState.targetState = true }

    // When the UI is hidden and animate out completes, close the UI.
    LaunchedEffect(uiVisibilityState.targetState, uiVisibilityState.isIdle) {
        if (!uiVisibilityState.targetState && uiVisibilityState.isIdle) {
            viewModel.closeUi()
        }
    }

    // Toolbar container
    AnimatedVisibility(
        visibleState = uiVisibilityState,
        enter = scaleIn(transformOrigin = scaleTransformOrigin) + slideInVertically(),
        exit = scaleOut(transformOrigin = scaleTransformOrigin) + slideOutVertically(),
        modifier = Modifier.zIndex(1f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PreCaptureToolbar(
                viewModel = viewModel.toolbarViewModel,
                selectedCaptureType = viewModel.captureType,
                selectedCaptureRegion = viewModel.captureRegion,
                onCaptureTypeSelected = viewModel::updateCaptureType,
                onCaptureRegionSelected = viewModel::updateCaptureRegion,
                onCloseClick = { uiVisibilityState.targetState = false },
                modifier = Modifier.padding(top = 36.dp).pointerHoverIcon(PointerIcon.Default),
            )

            SnackbarHost(
                hostState = viewModel.snackbarHostState,
                modifier = Modifier.padding(top = 12.dp),
            ) { data ->
                val shape = RoundedCornerShape(size = 50.dp)
                Surface(
                    modifier =
                        Modifier.width(496.dp)
                            .height(84.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = shape,
                                spotColor = Color(0x26000000),
                                ambientColor = Color(0x26000000),
                            )
                            .shadow(
                                elevation = 3.dp,
                                shape = shape,
                                spotColor = Color(0x4D000000),
                                ambientColor = Color(0x4D000000),
                            ),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceBright,
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = data.visuals.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }

    val captureIconResId =
        when (viewModel.captureType) {
            ScreenCaptureType.SCREENSHOT -> R.drawable.ic_screen_capture_camera
            ScreenCaptureType.RECORDING -> R.drawable.ic_screenrecord
        }
    val captureIcon by
        loadIcon(viewModel = viewModel, resId = captureIconResId, contentDescription = null)

    // Main content
    AnimatedVisibility(
        visibleState = uiVisibilityState,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.zIndex(0f),
    ) {
        when (viewModel.captureRegion) {
            ScreenCaptureRegion.FULLSCREEN -> {
                val fullscreenPointerIcon =
                    remember(viewModel.captureType) {
                        if (viewModel.captureType == ScreenCaptureType.SCREENSHOT) {
                            PointerIcon(
                                ViewPointerIcon.load(localResources, R.xml.pointer_camera_icon)
                            )
                        } else {
                            PointerIcon(
                                ViewPointerIcon.load(localResources, R.xml.pointer_record_icon)
                            )
                        }
                    }

                // Dim the entire screen with a scrim before taking a fullscreen screenshot.
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(color = ScreenCaptureColors.scrimColor)
                            .pointerInput(Unit) { detectTapGestures { viewModel.beginCapture() } }
                            .pointerHoverIcon(fullscreenPointerIcon)
                ) {
                    PrimaryButton(
                        modifier =
                            Modifier.align(Alignment.Center).pointerHoverIcon(PointerIcon.Default),
                        icon = captureIcon,
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
                val regionBox = viewModel.regionBox
                // To avoid a race condition where the UI is displayed before the initial region box
                // dimensions are calculated, we only compose the RegionBox once its initial state
                // is ready.
                if (regionBox != null) {
                    RegionBox(
                        initialRect = regionBox,
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
                        buttonIcon = captureIcon,
                        onRegionSelected = { regionBoxRect ->
                            viewModel.updateRegionBoxBounds(regionBoxRect)
                            viewModel.toolbarViewModel.updateOpacityForRegionBox(
                                isInteracting = false,
                                regionBoxBounds = regionBoxRect,
                            )
                        },
                        onInteractionStateChanged = { isInteracting ->
                            viewModel.toolbarViewModel.updateOpacityForRegionBox(
                                isInteracting = isInteracting,
                                regionBoxBounds = viewModel.regionBox,
                            )
                        },
                        onCaptureClick = viewModel::beginCapture,
                    )
                }
            }

            ScreenCaptureRegion.APP_WINDOW -> {
                val density = LocalDensity.current
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(viewModel.captureRegion) {
                    if (viewModel.captureRegion == ScreenCaptureRegion.APP_WINDOW) {
                        focusRequester.requestFocus()
                    }
                }

                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .pointerInput(viewModel.captureRegion) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Move) {
                                            val position = event.changes.first().position
                                            viewModel.updateTaskSelectionFromHover(
                                                Point(position.x.toInt(), position.y.toInt())
                                            )
                                        }
                                    }
                                }
                            }
                            .pointerInput(viewModel.captureRegion) {
                                detectTapGestures { offset ->
                                    viewModel.captureTaskAtPosition(
                                        Point(offset.x.toInt(), offset.y.toInt())
                                    )
                                }
                            }
                ) {
                    AppWindowBox(appWindowModel = viewModel.appWindowSelection)

                    viewModel.runningTasks.forEachIndexed { index, task ->
                        val bounds = task.configuration.windowConfiguration.bounds
                        Box(
                            modifier =
                                Modifier.offset(
                                        x = with(density) { bounds.left.toDp() },
                                        y = with(density) { bounds.top.toDp() },
                                    )
                                    .size(
                                        width = with(density) { bounds.width().toDp() },
                                        height = with(density) { bounds.height().toDp() },
                                    )
                                    .thenIf(index == 0) { Modifier.focusRequester(focusRequester) }
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            viewModel.focusTask(task)
                                        } else {
                                            viewModel.unfocusTask(task)
                                        }
                                    }
                                    .onKeyEvent { keyEvent ->
                                        if (
                                            (keyEvent.key == Key.Enter ||
                                                keyEvent.key == Key.Spacebar) &&
                                                keyEvent.type == KeyEventType.KeyDown
                                        ) {
                                            viewModel.beginCapture()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    .focusable()
                        )
                    }
                }
            }
        }
    }
}
