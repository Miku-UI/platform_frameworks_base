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

import com.android.systemui.keyguard.domain.interactor.KeyguardPreviewInteractor
import com.android.systemui.keyguard.shared.model.ClockSizeSetting
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

@AssistedFactory
interface KeyguardPreviewClockViewModelFactory {
    fun create(interactor: KeyguardPreviewInteractor): KeyguardPreviewClockViewModel
}

/** View model for the small clock view, large clock view. */
class KeyguardPreviewClockViewModel
@AssistedInject
constructor(
    @Assisted private val interactor: KeyguardPreviewInteractor,
    private val keyguardClockViewModel: KeyguardClockViewModel,
) {
    // The flag indicates if the clock should be hidden for the preview the whole time. In this case
    // the clock view will not even be created.
    val shouldHideClock: Boolean
        get() = interactor.shouldHideClock

    private val _showClock: MutableStateFlow<Boolean> = MutableStateFlow(!shouldHideClock)
    val showClock: Flow<Boolean> = _showClock.asStateFlow()

    fun setShowClock(show: Boolean) {
        _showClock.value = show
    }

    val shouldHighlightSelectedAffordance: Boolean
        get() = interactor.shouldHighlightSelectedAffordance

    val previewClockSize = interactor.previewClockSize

    val isLargeClockVisible: Flow<Boolean>
        get() = previewClockSize.map { it == ClockSizeSetting.DYNAMIC }

    val isSmallClockVisible: Flow<Boolean>
        get() = previewClockSize.map { it == ClockSizeSetting.SMALL }

    val previewClock: Flow<ClockController>
        get() = interactor.previewClock

    fun shouldSmallDateWeatherBeBelowSmallClock() =
        keyguardClockViewModel.shouldDateWeatherBeBelowSmallClock.value

    fun shouldSmallDateWeatherBeBelowLargeClock() =
        keyguardClockViewModel.shouldDateWeatherBeBelowLargeClock.value
}
