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

package com.android.systemui.ambientcue.ui.compose

import android.graphics.Rect
import android.util.Log
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.systemui.ambientcue.ui.shape.TopConcaveArcShape
import com.android.systemui.res.R

@Composable
fun UnderlayContainer(modifier: Modifier = Modifier, content: AmbientCueComposableProvider) {
    val density = LocalDensity.current
    val hornRadiusPx = with(density) { HornRadius.toPx() }

    val view = LocalView.current
    var touchableRect: Rect by remember { mutableStateOf((Rect())) }

    DisposableEffect(view, touchableRect) {
        val listener =
            ViewTreeObserver.OnComputeInternalInsetsListener { inoutInfo ->
                inoutInfo.setTouchableInsets(
                    ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
                )
                inoutInfo.touchableRegion.set(touchableRect)
            }

        view.viewTreeObserver.addOnComputeInternalInsetsListener(listener)

        onDispose { view.viewTreeObserver.removeOnComputeInternalInsetsListener(listener) }
    }

    val underlayShape = remember(HornRadius) { TopConcaveArcShape(HornRadius) }

    val mandatorySystemGesturesBottomPadding =
        WindowInsets.mandatorySystemGestures.asPaddingValues().calculateBottomPadding()

    Box(
        modifier =
            modifier
                .onGloballyPositioned { layoutCoordinates ->
                    val heightPx = layoutCoordinates.size.height
                    val widthPx = layoutCoordinates.size.width
                    val hornHeightPx = hornRadiusPx.toInt()
                    touchableRect = Rect(0, hornHeightPx, widthPx, heightPx)
                }
                .background(color = MaterialTheme.colorScheme.inverseSurface, shape = underlayShape)
                .clip(underlayShape)
                // Offset above gesture region and below horns
                .padding(top = HornRadius, bottom = mandatorySystemGesturesBottomPadding)
    ) {
        content.Content(modifier = Modifier)

        // Close Button.
        PlatformIconButton(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            onClick = {
                // TODO: b/406978499 - Implement the hide logic.
                Log.d(TAG, "Close")
            },
            iconResource = R.drawable.ic_close,
            contentDescription =
                stringResource(id = R.string.underlay_close_button_content_description),
        )
    }
}

private const val TAG = "UnderlayContainer"
private val HornRadius = 28.dp
