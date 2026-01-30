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

package com.android.systemui.volume.ui.compose.slider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SliderIcon(
    icon: @Composable BoxScope.() -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    var previousIsVisible: Boolean? by remember { mutableStateOf(null) }
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            enter =
                fadeIn(
                    animationSpec =
                        if (previousIsVisible == null) {
                            snap()
                        } else {
                            tween(delayMillis = 33, durationMillis = 100)
                        }
                ),
            exit =
                fadeOut(
                    animationSpec =
                        if (previousIsVisible == null) {
                            snap()
                        } else {
                            tween(durationMillis = 50)
                        }
                ),
        ) {
            icon()
        }
    }

    previousIsVisible = isVisible
}
