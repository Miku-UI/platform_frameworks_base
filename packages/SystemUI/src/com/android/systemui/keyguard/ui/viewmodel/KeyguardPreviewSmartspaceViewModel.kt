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
 * limitations under the License
 */

package com.android.systemui.keyguard.ui.viewmodel

import android.content.Context
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardPreviewInteractor
import com.android.systemui.keyguard.shared.model.ClockSizeSetting
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPreviewConfig
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.DIM_ALPHA
import com.android.systemui.statusbar.ui.SystemBarUtilsProxy
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@AssistedFactory
interface KeyguardPreviewSmartspaceViewModelFactory {
    fun create(
        interactor: KeyguardPreviewInteractor,
        clockViewModel: KeyguardPreviewClockViewModel,
    ): KeyguardPreviewSmartspaceViewModel
}

/** View model for the smartspace. */
class KeyguardPreviewSmartspaceViewModel
@AssistedInject
constructor(
    @Assisted private val previewInteractor: KeyguardPreviewInteractor,
    private val clockInteractor: KeyguardClockInteractor,
    private val smartspaceViewModel: KeyguardSmartspaceViewModel,
    @Assisted private val clockViewModel: KeyguardPreviewClockViewModel,
    private val systemBarUtils: SystemBarUtilsProxy,
) {
    val previewClockSize = clockViewModel.previewClockSize

    val shouldDateWeatherBeBelowSmallClock
        get() = clockViewModel.shouldSmallDateWeatherBeBelowSmallClock()

    val shouldDateWeatherBeBelowLargeClock
        get() = clockViewModel.shouldSmallDateWeatherBeBelowLargeClock()

    val previewAlpha = if (previewInteractor.shouldHighlightSelectedAffordance) DIM_ALPHA else 1.0f

    val shouldHideSmartspace: Flow<Boolean> =
        combine(previewClockSize, clockInteractor.currentClockId) { size, clockId ->
                when (size) {
                    // TODO (b/284122375) This is temporary. We should use clockController
                    //      .largeClock.config.hasCustomWeatherDataDisplay instead, but
                    //      ClockRegistry.createCurrentClock is not reliable.
                    ClockSizeSetting.DYNAMIC -> clockId == "DIGITAL_CLOCK_WEATHER"
                    ClockSizeSetting.SMALL -> false
                }
            }
            .distinctUntilChanged()

    val showSmartspace =
        combine(clockViewModel.showClock, shouldHideSmartspace) { showClock, shouldHideSmartspace ->
            showClock && !shouldHideSmartspace
        }

    fun getDateWeatherStartPadding(context: Context): Int {
        return KeyguardSmartspaceViewModel.getDateWeatherStartMargin(context)
    }

    fun getDateWeatherEndPadding(context: Context): Int {
        return KeyguardSmartspaceViewModel.getDateWeatherEndMargin(context)
    }

    /** SmallClockTopPadding decides the top position of smartspace */
    fun getSmallClockSmartspaceTopPadding(context: Context, config: ClockPreviewConfig): Int {
        return config.getSmallClockTopPadding(
            systemBarUtils.getStatusBarHeaderHeightKeyguard(context)
        ) + context.resources.getDimensionPixelSize(clocksR.dimen.small_clock_height)
    }

    /** SmallClockTopPadding decides the top position of smartspace */
    fun getLargeClockSmartspaceTopPadding(context: Context, config: ClockPreviewConfig): Int {
        return config.getSmallClockTopPadding(
            systemBarUtils.getStatusBarHeaderHeightKeyguard(context)
        )
    }
}
