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

package com.android.systemui.deviceentry.ui.viewmodel

import android.content.Context
import android.graphics.Point
import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.systemui.accessibility.domain.interactor.AccessibilityInteractor
import com.android.systemui.biometrics.UdfpsUtils
import com.android.systemui.biometrics.domain.interactor.UdfpsOverlayInteractor
import com.android.systemui.biometrics.shared.model.UdfpsOverlayParams
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryUdfpsInteractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Models the UI state for the UDFPS accessibility overlay */
abstract class UdfpsAccessibilityOverlayViewModel(
    @Application private val applicationContext: Context,
    udfpsOverlayInteractor: UdfpsOverlayInteractor,
    deviceEntryUdfpsInteractor: DeviceEntryUdfpsInteractor,
    private val udfpsUtils: UdfpsUtils = UdfpsUtils(),
    accessibilityInteractor: AccessibilityInteractor,
) : ViewModel() {

    /** Whether the under display fingerprint sensor is currently running. */
    open val isListeningForUdfps: StateFlow<Boolean> =
        deviceEntryUdfpsInteractor.isListeningForUdfps.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    /** Whether the accessibility overlay should be visible. */
    val visible: Flow<Boolean> =
        accessibilityInteractor.isTouchExplorationEnabled.flatMapLatest { touchExplorationEnabled ->
            if (touchExplorationEnabled) {
                isVisibleWhenTouchExplorationEnabled()
            } else {
                flowOf(false)
            }
        }

    private val udfpsOverlayParams: StateFlow<UdfpsOverlayParams> =
        udfpsOverlayInteractor.udfpsOverlayParams

    abstract fun isVisibleWhenTouchExplorationEnabled(): Flow<Boolean>

    open fun getUdfpsDirectionalFeedbackOnHoverEnterOrMove(
        event: MotionEvent,
        includeLockscreenContentDescription: Boolean,
    ): CharSequence? {
        return getUdfpsDirectionalFeedbackOnHoverEnterOrMove(event)
    }

    /** Give directional feedback to help the user authenticate with UDFPS. */
    fun getUdfpsDirectionalFeedbackOnHoverEnterOrMove(event: MotionEvent): CharSequence? {
        if (!isListeningForUdfps.value) {
            return null
        }
        val overlayParams = udfpsOverlayParams.value
        val scaledTouch: Point =
            udfpsUtils.getTouchInNativeCoordinates(
                event.getPointerId(0),
                event,
                overlayParams, /* rotateToPortrait */
                false,
            )

        if (
            !udfpsUtils.isWithinSensorArea(
                event.getPointerId(0),
                event,
                overlayParams,
                /* rotateTouchToPortrait */ false,
            )
        ) {
            // view only receives motionEvents when [visible] which requires touchExplorationEnabled
            val announceStr =
                udfpsUtils.onTouchOutsideOfSensorArea(
                    /* touchExplorationEnabled */ true,
                    applicationContext,
                    scaledTouch.x,
                    scaledTouch.y,
                    overlayParams,
                    /* touchRotatedToPortrait */ false,
                )
            return announceStr
        } else {
            return null
        }
    }
}
