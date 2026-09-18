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

package com.android.systemui.keyguard.ui.composable

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.BurnInMovementState
import com.android.systemui.keyguard.ui.viewmodel.BurnInParameters
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Produces a [BurnInTracker] that is used to track various input parameters required by burnin. */
@Composable
fun trackBurnInParameters(
    burnInViewModel: AodBurnInViewModel,
    burnInMovementState: () -> BurnInMovementState,
    clockViewModel: KeyguardClockViewModel,
): BurnInTracker {
    val clock by clockViewModel.currentClock.collectAsStateWithLifecycle()
    val (smartspaceTop, onSmartspaceTopChanged) = remember { mutableStateOf<Float?>(null) }
    val (smallClockTop, onSmallClockTopChanged) = remember(clock) { mutableStateOf<Float?>(null) }

    val systemBars = WindowInsets.systemBars
    val displayCutout = WindowInsets.displayCutout
    val density = LocalDensity.current

    val params =
        remember(systemBars, displayCutout, density, smartspaceTop, smallClockTop) {
            val topInset = { systemBars.union(displayCutout).getTop(density) }
            BurnInParameters(
                topInset = topInset,
                minViewY = {
                    // minViewY should never be below the topInset
                    val topMost = ceil(min(smartspaceTop ?: 0f, smallClockTop ?: 0f)).roundToInt()
                    max(topInset(), topMost)
                },
                translationX = { burnInMovementState().translation.x },
                translationY = { burnInMovementState().translation.y },
            )
        }

    LaunchedEffect(params) { burnInViewModel.updateBurnInParams(params) }

    return remember(onSmartspaceTopChanged, onSmallClockTopChanged, params) {
        BurnInTracker(
            parameters = params,
            onSmartspaceTopChanged = onSmartspaceTopChanged,
            onSmallClockTopChanged = onSmallClockTopChanged,
        )
    }
}

data class BurnInTracker(
    /** Parameters for use with the `LockscreenBurnInViewModel. */
    val parameters: BurnInParameters,

    /**
     * Callback to invoke when the top coordinate of the smartspace element is updated, pass `null`
     * when the element is not shown.
     */
    val onSmartspaceTopChanged: (Float?) -> Unit,

    /**
     * Callback to invoke when the top coordinate of the small clock element is updated, pass `null`
     * when the element is not shown.
     */
    val onSmallClockTopChanged: (Float?) -> Unit,
)
