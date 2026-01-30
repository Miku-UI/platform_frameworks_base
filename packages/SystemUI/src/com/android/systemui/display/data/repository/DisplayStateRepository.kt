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

package com.android.systemui.display.data.repository

import android.content.Context
import android.util.DisplayMetrics
import android.util.Size
import android.view.DisplayInfo
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.REAR_DISPLAY
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState.REAR_DISPLAY_OUTER_DEFAULT
import com.android.systemui.display.shared.model.DisplayRotation
import com.android.systemui.display.shared.model.toDisplayRotation
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import javax.inject.Inject
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Repository for the current state of the display */
interface DisplayStateRepository {
    /**
     * If true, the direction rotation is applied to get to an application's requested orientation
     * is reversed. Normally, the model is that landscape is clockwise from portrait; thus on a
     * portrait device an app requesting landscape will cause a clockwise rotation, and on a
     * landscape device an app requesting portrait will cause a counter-clockwise rotation. Setting
     * true here reverses that logic. See go/natural-orientation for context.
     */
    val isReverseDefaultRotation: Boolean

    /** Provides the current rear display state. */
    val isInRearDisplayMode: StateFlow<Boolean>

    /** Provides the current display rotation */
    val currentRotation: StateFlow<DisplayRotation>

    /** Provides the current display size */
    val currentDisplaySize: StateFlow<Size>

    /**
     * Provides whether the current display is a large screen (i.e. all edges are >= 600dp). This is
     * agnostic of display rotation.
     */
    val isLargeScreen: StateFlow<Boolean>

    /**
     * Provides whether the display's current horizontal width is large (>= 600dp).
     *
     * Note that unlike [isLargeScreen], which checks whether either one of the screen's width or
     * height is large, this flow's state is sensitive to the current display's orientation.
     */
    val isWideScreen: StateFlow<Boolean>
}

@PerDisplaySingleton
class DisplayStateRepositoryImpl
@Inject
constructor(
    @DisplayAware bgDisplayScope: CoroutineScope,
    @DisplayAware val context: Context,
    @DisplayAware val configurationRepository: ConfigurationRepository,
    deviceStateRepository: DeviceStateRepository,
    displayRepository: DisplayRepository,
) : DisplayStateRepository {
    override val isReverseDefaultRotation =
        context.resources.getBoolean(com.android.internal.R.bool.config_reverseDefaultRotation)

    override val isInRearDisplayMode: StateFlow<Boolean> =
        deviceStateRepository.state
            .map { it == REAR_DISPLAY || it == REAR_DISPLAY_OUTER_DEFAULT }
            .stateIn(bgDisplayScope, started = SharingStarted.Eagerly, initialValue = false)

    private val currentDisplayInfo: StateFlow<DisplayInfo> =
        if (ShadeWindowGoesAround.isEnabled) {
                displayRepository.displayChangeEvent.filter { it == context.displayId }
            } else {
                displayRepository.displayChangeEvent
            }
            .map { getDisplayInfo() }
            .stateIn(
                bgDisplayScope,
                started = SharingStarted.Eagerly,
                initialValue = getDisplayInfo(),
            )

    override val currentRotation: StateFlow<DisplayRotation> =
        currentDisplayInfo
            .map { rotationToDisplayRotation(it.rotation) }
            .stateIn(
                bgDisplayScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = rotationToDisplayRotation(currentDisplayInfo.value.rotation),
            )

    override val currentDisplaySize: StateFlow<Size> =
        currentDisplayInfo
            .map { Size(it.naturalWidth, it.naturalHeight) }
            .stateIn(
                bgDisplayScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    Size(
                        currentDisplayInfo.value.naturalWidth,
                        currentDisplayInfo.value.naturalHeight,
                    ),
            )

    override val isLargeScreen: StateFlow<Boolean> =
        if (ShadeWindowGoesAround.isEnabled) {
                configurationRepository.configurationValues.map {
                    it.smallestScreenWidthDp >= LARGE_SCREEN_MIN_DPS
                }
            } else {
                currentDisplayInfo.map {
                    // copied from systemui/shared/...Utilities.java
                    val smallestWidth = min(it.logicalWidth, it.logicalHeight).toDpi()
                    smallestWidth >= LARGE_SCREEN_MIN_DPS
                }
            }
            .stateIn(bgDisplayScope, started = SharingStarted.Eagerly, initialValue = false)

    override val isWideScreen: StateFlow<Boolean> =
        if (ShadeWindowGoesAround.isEnabled) {
                configurationRepository.configurationValues.map {
                    it.screenWidthDp >= LARGE_SCREEN_MIN_DPS
                }
            } else {
                currentDisplayInfo.map { it.logicalWidth.toDpi() >= LARGE_SCREEN_MIN_DPS }
            }
            .stateIn(bgDisplayScope, started = SharingStarted.Eagerly, initialValue = false)

    private fun getDisplayInfo(): DisplayInfo {
        val cachedDisplayInfo = DisplayInfo()
        context.display.getDisplayInfo(cachedDisplayInfo)
        return cachedDisplayInfo
    }

    private fun rotationToDisplayRotation(rotation: Int): DisplayRotation {
        return if (isReverseDefaultRotation) {
                (rotation + 1) % 4
            } else {
                rotation
            }
            .toDisplayRotation()
    }

    private fun Int.toDpi(): Float {
        val densityDpi = context.resources.configuration.densityDpi
        val densityRatio = densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
        return this / densityRatio
    }

    companion object {
        const val TAG = "DisplayStateRepositoryImpl"
        const val LARGE_SCREEN_MIN_DPS = 600f
    }
}
