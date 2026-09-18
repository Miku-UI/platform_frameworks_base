/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.biometrics.domain.interactor

import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import android.view.Surface
import com.android.systemui.Flags
import com.android.systemui.biometrics.shared.model.PeripheralFingerprintSensorLocation
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.domain.interactor.DisplayTypeInteractor
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@SysUISingleton
class PeripheralFpsInteractor
@Inject
constructor(
    @ShadeDisplayAware val configurationInteractor: ConfigurationInteractor,
    val fingerprintPropertyInteractor: FingerprintPropertyInteractor,
    @ShadeDisplayAware val displayTypeInteractor: DisplayTypeInteractor,
) {
    /**
     * Whether fingerprint sensor location is peripheral, i.e. does not have display location. This
     * includes cases when:
     * - Sensor is of peripheral type.
     * - Lock screen is showing on non-internal display (for any sensor type).
     */
    val isSupported: Flow<Boolean>
        get() =
            if (Flags.standaloneFingerprintLockScreenUxFix()) {
                combine(
                    fingerprintPropertyInteractor.isPeripheralFps,
                    displayTypeInteractor.isInternalDisplay,
                ) { isPeripheralFps, isInternalDisplay ->
                    isPeripheralFps || !isInternalDisplay
                }
            } else {
                flowOf(false)
            }

    /** Represents the sensor's location for the purpose of ripple effect origin. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val locationForRippleEffect: Flow<PointF>
        get() =
            if (Flags.standaloneFingerprintLockScreenUxFix()) {
                combine(
                    configurationInteractor.maxBounds,
                    configurationInteractor.scaleForResolution,
                    configurationInteractor.displayRotation,
                    fingerprintPropertyInteractor.peripheralSensorLocation,
                    displayTypeInteractor.isInternalDisplay,
                ) { bounds, scale, displayRotation, peripheralSensorLocation, isInternalDisplay ->
                    // For known peripheral location, sensor location in relation to display can
                    // only be approximated for internal displays with default rotation.
                    if (
                        !peripheralSensorLocation.isUnknown() &&
                            isInternalDisplay &&
                            displayRotation == Surface.ROTATION_0
                    ) {
                        calculateNearSensorPoint(bounds, scale, peripheralSensorLocation)
                    } else {
                        calculateCenterPoint(bounds, scale)
                    }
                }
            } else {
                Log.w(
                    TAG,
                    "locationForRippleEffect should not be used when standalone_fingerprint_lock_screen_ux_fix is disabled",
                )
                flowOf(PointF(0f, 0f))
            }

    /**
     * Calculate the point along the bottom of the screen that's near the sensor based on the
     * predefined screen fractions for different peripheral locations.
     */
    private fun calculateNearSensorPoint(
        bounds: Rect,
        scale: Float,
        peripheralSensorLocation: PeripheralFingerprintSensorLocation,
    ): PointF {
        val yOffsetBottom = bounds.height() * scale
        val xOffset = bounds.width() * scale * peripheralSensorLocation.toNearSensorXFraction()
        return PointF(xOffset, yOffsetBottom)
    }

    private fun PeripheralFingerprintSensorLocation.toNearSensorXFraction(): Float =
        when (this) {
            PeripheralFingerprintSensorLocation.KEYBOARD_BOTTOM_RIGHT,
            PeripheralFingerprintSensorLocation.KEYBOARD_TOP_RIGHT,
            PeripheralFingerprintSensorLocation.RIGHT_SIDE,
            PeripheralFingerprintSensorLocation.POWER_BUTTON_TOP_RIGHT_KEY -> 0.85f
            PeripheralFingerprintSensorLocation.KEYBOARD_BOTTOM_LEFT,
            PeripheralFingerprintSensorLocation.LEFT_SIDE,
            PeripheralFingerprintSensorLocation.LEFT_OF_POWER_BUTTON_TOP_RIGHT -> 0.15f
            PeripheralFingerprintSensorLocation.UNKNOWN -> {
                Log.w(
                    TAG,
                    "toNearSensorHorizontalScreenFraction should only be used with known peripheral sensor location.",
                )
                0.5f
            }
        }

    private fun calculateCenterPoint(bounds: Rect, scale: Float) =
        PointF(bounds.width() * 0.5f * scale, bounds.height() * 0.5f * scale)

    companion object {
        private const val TAG = "PeripheralFpsInteractor"
    }
}
