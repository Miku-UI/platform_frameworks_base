/*
 * Copyright (C) 2023 The Android Open Source Project
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

@file:OptIn(ExperimentalFoundationApi::class)

package com.android.systemui.keyguard.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.android.systemui.keyguard.ui.viewmodel.KeyguardTouchHandlingViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.plugins.keyguard.VRectF

/** Container for lockscreen content that handles inputs including long-press and double tap. */
@Composable
fun LockscreenTouchHandling(
    viewModelFactory: KeyguardTouchHandlingViewModel.Factory,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(onSettingsMenuPlaces: (coordinates: VRectF) -> Unit) -> Unit,
) {
    val viewModel = rememberViewModel("LockscreenLongPress") { viewModelFactory.create() }
    val (settingsMenuBounds, setSettingsMenuBounds) = remember { mutableStateOf(VRectF.ZERO) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .pointerInput(viewModel.isLongPressHandlingEnabled) {
                    detectTapGestures(
                        onTap = { viewModel.onClick(it.x, it.y) },
                        onDoubleTap = { viewModel.onDoubleClick() },
                        onLongPress = {
                            if (viewModel.isLongPressHandlingEnabled) {
                                viewModel.onLongPress(isA11yAction = false)
                            }
                        },
                    )
                }
                .pointerInput(settingsMenuBounds) {
                    awaitEachGesture {
                        val pointerInputChange = awaitFirstDown()
                        if (!settingsMenuBounds.contains(pointerInputChange.position)) {
                            viewModel.onTouchedOutside()
                        }
                    }
                }
                // Passing null for the indication removes the ripple effect.
                .indication(interactionSource, null)
    ) {
        content(setSettingsMenuBounds)
    }
}
